package com.resqlink

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.data.repository.ContactRepositoryImpl
import com.resqlink.data.repository.DataControlRepositoryImpl
import com.resqlink.data.repository.EmergencyRepositoryImpl
import com.resqlink.data.repository.ProfileRepositoryImpl
import com.resqlink.domain.model.*
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SaveContactResult
import com.resqlink.domain.usecase.ActivateEmergency
import com.resqlink.notification.EmergencyNotifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositorySecurityTest {
    private lateinit var db: ResQLinkDatabase
    private lateinit var contacts: ContactRepositoryImpl
    private lateinit var emergencies: EmergencyRepositoryImpl
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var notifier: EmergencyNotifier
    private lateinit var settings: FakeSettings

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ResQLinkDatabase::class.java).build()
        contacts = ContactRepositoryImpl(db)
        emergencies = EmergencyRepositoryImpl(db.emergencyDao())
        profiles = ProfileRepositoryImpl(db.profileDao())
        notifier = EmergencyNotifier(context)
        settings = FakeSettings()
    }

    @After fun close() { notifier.cancel(); db.close() }

    private suspend fun ready() {
        contacts.save(EmergencyContact(name = "Test", phoneNumber = "+15550100001"))
        profiles.save(EmergencyProfile(name = "Test", notes = "Test notes"))
    }

    private fun activate() = ActivateEmergency(emergencies, contacts, notifier, profiles)

    @Test fun rejectsUnvalidatedRepositoryWrites() = runBlocking {
        assertEquals(SaveContactResult.INVALID,
            contacts.save(EmergencyContact(name = "Test", phoneNumber = "*21*+15550100001#")))
        assertTrue(contacts.observeContacts().first().isEmpty())
    }

    @Test fun concurrentNormalizedDuplicateSavesDoNotCrash() = runBlocking {
        val results = listOf("+1 (555) 010-0001", "+15550100001").map { phone ->
            async(Dispatchers.IO) { contacts.save(EmergencyContact(name = "Test", phoneNumber = phone)) }
        }.awaitAll()
        assertEquals(1, results.count { it == SaveContactResult.SAVED })
        assertEquals(1, results.count { it == SaveContactResult.DUPLICATE })
        assertEquals(1, contacts.enabledContacts().size)
    }

    @Test fun parallelPrimaryChangesRemainConsistent() = runBlocking {
        (1..8).map { index -> async(Dispatchers.IO) {
            contacts.save(EmergencyContact(name = "Test $index", phoneNumber = "+1555010000$index", priority = 0))
        } }.awaitAll()
        assertEquals(1, contacts.enabledContacts().count { it.priority == 0 })
        contacts.enabledContacts().map { contact -> async(Dispatchers.IO) { contacts.setPrimary(contact.id) } }.awaitAll()
        assertEquals(1, contacts.enabledContacts().count { it.priority == 0 })
    }

    @Test fun editingPreservesCreationTimeAndDeletingPrimaryPromotesBackup() = runBlocking {
        ready()
        contacts.save(EmergencyContact(name = "Backup", phoneNumber = "+15550100002"))
        val primary = contacts.enabledContacts().first { it.priority == 0 }
        val created = db.contactDao().find(primary.id)!!.createdAt
        contacts.save(primary.copy(name = "Renamed"))
        assertEquals(created, db.contactDao().find(primary.id)!!.createdAt)
        contacts.delete(primary)
        assertEquals(0, contacts.enabledContacts().single().priority)
    }

    @Test fun invalidPrimarySelectionCannotDemoteRealPrimary() = runBlocking {
        ready()
        contacts.save(EmergencyContact(name = "Disabled", phoneNumber = "+15550100002", enabled = false))
        val original = contacts.enabledContacts().single()
        contacts.setPrimary(Long.MAX_VALUE)
        val disabled = contacts.observeContacts().first().first { !it.enabled }
        contacts.setPrimary(disabled.id)
        assertEquals(original.id, contacts.enabledContacts().single { it.priority == 0 }.id)
    }

    @Test fun staleUpdateCannotRecreateDeletedContact() = runBlocking {
        ready()
        val contact = contacts.enabledContacts().single()
        contacts.delete(contact)
        assertEquals(SaveContactResult.INVALID, contacts.save(contact))
        assertTrue(contacts.enabledContacts().isEmpty())
    }

    @Test fun activationRequiresDurableProfileAndContacts() = runBlocking {
        assertTrue(runCatching { activate()(50) }.isFailure)
        assertNull(db.emergencyDao().active())
        contacts.save(EmergencyContact(name = "Test", phoneNumber = "+15550100001"))
        assertTrue(runCatching { activate()(50) }.isFailure)
        assertNull(db.emergencyDao().active())
    }

    @Test fun concurrentActivationsProduceOnlyOneDispatchDraft() = runBlocking {
        ready()
        val results = (1..12).map { async(Dispatchers.IO) { activate()(50) } }.awaitAll()
        assertEquals(1, results.map { it.eventId }.toSet().size)
        assertEquals(1, results.count { it.recipients.isNotEmpty() })
        assertEquals(1, emergencies.observeHistory().first().size)
        assertEquals(1, db.emergencyDao().alertCount(results.first().eventId))
    }

    @Test fun persistedClaimCannotBeReplayedByNewRepositoryInstance() = runBlocking {
        ready()
        val event = activate()(50)
        assertTrue(emergencies.claimDispatch(event.eventId))
        val recovered = EmergencyRepositoryImpl(db.emergencyDao())
        assertFalse(recovered.claimDispatch(event.eventId))
        assertEquals(AlertStatus.UNKNOWN.name, db.emergencyDao().alertsForEvent(event.eventId).single().status)
    }

    @Test fun outcomePersistenceIsIdempotentAndCannotChangeOtherRecipients() = runBlocking {
        ready()
        val event = activate()(50)
        val id = event.recipients.single().id
        assertTrue(emergencies.claimDispatch(event.eventId))
        emergencies.recordAlertOutcomes(event.eventId, listOf(id), emptyList())
        emergencies.recordAlertOutcomes(event.eventId, emptyList(), listOf(id, Long.MAX_VALUE))
        val attempt = db.emergencyDao().alertsForEvent(event.eventId).single()
        assertEquals(AlertStatus.DISPATCHED.name, attempt.status)
        assertEquals(1, attempt.attemptCount)
    }

    @Test fun stoppedEmergencyCannotDispatchOrBeReactivated() = runBlocking {
        ready()
        val event = activate()(50)
        emergencies.complete(event.eventId, cancelled = true)
        emergencies.markActive(event.eventId, LocationStatus.NOT_REQUESTED)
        assertFalse(emergencies.claimDispatch(event.eventId))
        assertNull(db.emergencyDao().active())
        assertEquals(AlertStatus.CANCELLED.name, db.emergencyDao().alertsForEvent(event.eventId).single().status)
        assertFalse(emergencies.prepareAlerts(event.eventId, event.recipients))
    }

    @Test fun completedEmergencyIgnoresLateLocationAndPreservesEndTime() = runBlocking {
        ready()
        val event = activate()(50)
        emergencies.complete(event.eventId, cancelled = true)
        val endedAt = emergencies.observeHistory().first().single().endedAt
        emergencies.recordLocation(event.eventId, LocationSnapshot(1.0, 2.0, 3f, 123, "test"))
        emergencies.updateLocationStatus(event.eventId, LocationStatus.CAPTURED)
        emergencies.complete(event.eventId, cancelled = false)
        assertEquals(0, db.emergencyDao().locationCount(event.eventId))
        val saved = emergencies.observeHistory().first().single()
        assertEquals(LocationStatus.NOT_REQUESTED, saved.locationStatus)
        assertEquals(EventStatus.CANCELLED, saved.status)
        assertEquals(endedAt, saved.endedAt)
    }

    @Test fun activeEmergencyCannotBeDeletedOrCleared() = runBlocking {
        ready()
        val event = activate()(50)
        emergencies.delete(event.eventId)
        assertNotNull(db.emergencyDao().active())
        assertTrue(runCatching { DataControlRepositoryImpl(db, settings, notifier).clearAll() }.isFailure)
        assertFalse(settings.cleared)
        assertTrue(profiles.observeProfile().first().isConfigured)
        assertEquals(1, contacts.enabledContacts().size)
    }

    @Test fun deletingHistoryCascadesPrivateLocationAndAttempts() = runBlocking {
        ready()
        val event = activate()(50)
        emergencies.recordLocation(event.eventId, LocationSnapshot(1.0, 2.0, 3f, 123, "test"))
        emergencies.complete(event.eventId, true)
        emergencies.delete(event.eventId)
        assertEquals(0, db.emergencyDao().alertCount(event.eventId))
        assertEquals(0, db.emergencyDao().locationCount(event.eventId))
        assertTrue(emergencies.observeHistory().first().isEmpty())
    }

    @Test fun clearingInactiveDataRemovesAllOwnedRecordsAndPreferences() = runBlocking {
        ready()
        val event = activate()(50)
        emergencies.complete(event.eventId, true)
        DataControlRepositoryImpl(db, settings, notifier).clearAll()
        assertTrue(contacts.observeContacts().first().isEmpty())
        assertFalse(profiles.observeProfile().first().isConfigured)
        assertTrue(emergencies.observeHistory().first().isEmpty())
        assertTrue(settings.cleared)
    }

    private class FakeSettings : SettingsRepository {
        var cleared = false
        override val settings = flowOf(AppSettings())
        override suspend fun completeOnboarding() = Unit
        override suspend fun setTheme(theme: ThemePreference) = Unit
        override suspend fun setConfirmation(required: Boolean) = Unit
        override suspend fun setEmergencyMessage(message: String) = Unit
        override suspend fun clearPreferences() { cleared = true }
    }
}
