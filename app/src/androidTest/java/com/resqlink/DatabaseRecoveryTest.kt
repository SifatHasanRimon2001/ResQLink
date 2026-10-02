package com.resqlink

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.data.repository.EmergencyRepositoryImpl
import com.resqlink.domain.model.AlertStatus
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.LocationSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryTest {
    @Test fun dispatchClaimSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recovery-test-" + UUID.randomUUID() + ".db"
        var database = Room.databaseBuilder(context, ResQLinkDatabase::class.java, name).build()
        try {
            val original = EmergencyRepositoryImpl(database.emergencyDao())
            val event = original.createOrGetActive(50)
            assertTrue(original.prepareAlerts(event.id, listOf(EmergencyContact(id = 1, name = "Test", phoneNumber = "+15550100001"))))
            assertTrue(original.claimDispatch(event.id))
            database.close()

            database = Room.databaseBuilder(context, ResQLinkDatabase::class.java, name).build()
            val recovered = EmergencyRepositoryImpl(database.emergencyDao())
            assertEquals(event.id, recovered.observeActive().first()?.id)
            assertFalse(recovered.claimDispatch(event.id))
            assertEquals(AlertStatus.UNKNOWN.name, database.emergencyDao().alertsForEvent(event.id).single().status)
            assertEquals(1, database.emergencyDao().alertsForEvent(event.id).single().attemptCount)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun deletedEventCannotReceiveLateLocationAfterDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "deletion-test-" + UUID.randomUUID() + ".db"
        var database = Room.databaseBuilder(context, ResQLinkDatabase::class.java, name).build()
        try {
            val original = EmergencyRepositoryImpl(database.emergencyDao())
            val event = original.createOrGetActive(50)
            original.complete(event.id, true)
            original.delete(event.id)
            database.close()

            database = Room.databaseBuilder(context, ResQLinkDatabase::class.java, name).build()
            val recovered = EmergencyRepositoryImpl(database.emergencyDao())
            recovered.recordLocation(event.id, LocationSnapshot(1.0, 2.0, 3f, 123, "test"))
            assertTrue(recovered.observeHistory().first().isEmpty())
            assertEquals(0, database.emergencyDao().locationCount(event.id))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
