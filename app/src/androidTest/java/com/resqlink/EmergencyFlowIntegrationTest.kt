package com.resqlink

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.data.repository.EmergencyRepositoryImpl
import com.resqlink.domain.model.AlertStatus
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationSnapshot
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import com.resqlink.domain.repository.SaveContactResult
import com.resqlink.domain.usecase.ActivateEmergency
import com.resqlink.domain.usecase.CaptureEmergencyLocation
import com.resqlink.domain.usecase.CompleteEmergency
import com.resqlink.notification.EmergencyNotifier
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyFlowIntegrationTest {
    private lateinit var database: ResQLinkDatabase
    private lateinit var notifier: EmergencyNotifier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ResQLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notifier = EmergencyNotifier(context)
    }

    @After
    fun tearDown() {
        notifier.cancel()
        database.close()
    }

    @Test
    fun activationIsDurableDeduplicatedAndLocationDoesNotBlockIt() = runBlocking {
        val primary = EmergencyContact(
            id = 7,
            name = "Primary caregiver",
            phoneNumber = "+15550100001",
            priority = 0,
        )
        val secondary = EmergencyContact(
            id = 8,
            name = "Secondary caregiver",
            phoneNumber = "+15550100002",
            priority = 1,
        )
        val disabled = EmergencyContact(
            id = 9,
            name = "Disabled caregiver",
            phoneNumber = "+15550100003",
            priority = 2,
            enabled = false,
        )
        val repository = EmergencyRepositoryImpl(database.emergencyDao())
        val activate = ActivateEmergency(
            repository,
            FakeContacts(listOf(primary, secondary, disabled)),
            notifier,
        )

        val first = activate(batteryLevel = 61)
        val second = activate(batteryLevel = 60)

        assertEquals(first.eventId, second.eventId)
        assertEquals(listOf(primary, secondary), first.recipients)
        val activeBeforeLocation = database.emergencyDao().active()
        assertNotNull(activeBeforeLocation)
        assertEquals(EventStatus.ACTIVE.name, activeBeforeLocation?.status)
        assertEquals(LocationStatus.NOT_REQUESTED.name, activeBeforeLocation?.locationStatus)
        assertEquals(2, database.emergencyDao().alertCount(first.eventId))
        assertEquals(
            setOf(AlertStatus.PREPARED.name),
            database.emergencyDao().alertsForEvent(first.eventId).map { it.status }.toSet(),
        )

        repository.recordAlertOutcomes(
            eventId = first.eventId,
            dispatchedRecipientIds = listOf(primary.id),
            failedRecipientIds = listOf(secondary.id),
        )
        val outcomes = database.emergencyDao().alertsForEvent(first.eventId).associateBy { it.recipientId }
        assertEquals(AlertStatus.DISPATCHED.name, outcomes.getValue(primary.id).status)
        assertEquals(AlertStatus.FAILED.name, outcomes.getValue(secondary.id).status)
        assertEquals(1, outcomes.getValue(primary.id).attemptCount)
        assertEquals(1, outcomes.getValue(secondary.id).attemptCount)

        CaptureEmergencyLocation(repository, FakeLocation())(first.eventId)

        assertEquals(LocationStatus.CAPTURED.name, database.emergencyDao().active()?.locationStatus)
        assertEquals(1, database.emergencyDao().locationCount(first.eventId))

        val activeEvent = repository.observeActive().first()
        assertNotNull(activeEvent)
        CompleteEmergency(repository, notifier)(activeEvent!!)
        assertEquals(EventStatus.CANCELLED.name, repository.observeHistory().first().first().status.name)
    }

    private class FakeContacts(
        private val contacts: List<EmergencyContact>,
    ) : ContactRepository {
        override fun observeContacts() = flowOf(contacts)
        override suspend fun enabledContacts() = contacts.filter(EmergencyContact::enabled)
        override suspend fun save(contact: EmergencyContact) = SaveContactResult.SAVED
        override suspend fun setPrimary(contactId: Long) = Unit
        override suspend fun delete(contact: EmergencyContact) = Unit
    }

    private class FakeLocation : LocationRepository {
        override suspend fun currentLocation() = LocationResult.Available(
            LocationSnapshot(
                latitude = 23.8103,
                longitude = 90.4125,
                accuracyMeters = 8.5f,
                capturedAt = 1_725_420_000_000,
                provider = "test",
            ),
        )
    }
}
