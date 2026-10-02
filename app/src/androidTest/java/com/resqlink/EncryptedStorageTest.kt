package com.resqlink

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.data.repository.ContactRepositoryImpl
import com.resqlink.data.repository.EmergencyRepositoryImpl
import com.resqlink.data.repository.ProfileRepositoryImpl
import com.resqlink.data.repository.SettingsRepositoryImpl
import com.resqlink.data.security.EncryptedDatabaseFactory
import com.resqlink.data.security.SecureStorage
import com.resqlink.data.security.StorageHealth
import com.resqlink.domain.model.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefix = "storage-test-" + UUID.randomUUID()
    private val legacyName = prefix + "-legacy.db"
    private val encryptedName = prefix + "-encrypted.db"
    private val alias = prefix + "-key"
    private val keyFile = File(context.noBackupFilesDir, prefix + ".key")
    private val settingsFile = File(context.noBackupFilesDir, prefix + ".preferences_pb")
    private val secure = SecureStorage(context, alias, keyFile)
    private val databases = mutableListOf<ResQLinkDatabase>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun encrypted() = EncryptedDatabaseFactory(context, secure).build(encryptedName, legacyName)
        .also { databases += it }

    @After fun cleanup(): Unit = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        databases.forEach { it.close() }
        listOf(legacyName, encryptedName, encryptedName + ".migrating").forEach(context::deleteDatabase)
        listOf(keyFile, File(keyFile.path + ".bak"), File(keyFile.path + ".new"),
            settingsFile, File(settingsFile.path + ".tmp")).forEach { it.delete() }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test fun databaseAndWalHidePrivateDataAndSurviveReopen() = runBlocking {
        val marker = "PRIVATE-ADDRESS-" + UUID.randomUUID()
        val first = encrypted()
        ProfileRepositoryImpl(first.profileDao()).save(EmergencyProfile(name = "Test", notes = marker))
        val event = EmergencyRepositoryImpl(first.emergencyDao()).createOrGetActive(42)
        val emergencies = EmergencyRepositoryImpl(first.emergencyDao())
        assertTrue(emergencies.prepareAlerts(event.id, listOf(EmergencyContact(id = 1, name = "Test", phoneNumber = "+15550100001"))))
        assertTrue(emergencies.claimDispatch(event.id))
        assertEncryptedFiles(marker)
        first.close()
        val reopened = encrypted()
        assertEquals(marker, ProfileRepositoryImpl(reopened.profileDao()).observeProfile().first().notes)
        assertFalse(EmergencyRepositoryImpl(reopened.emergencyDao()).claimDispatch(event.id))
        assertEquals(AlertStatus.UNKNOWN.name, reopened.emergencyDao().alertsForEvent(event.id).single().status)
        assertFalse(String(context.getDatabasePath(encryptedName).readBytes(), Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        assertFails {
            SQLiteDatabase.openDatabase(context.getDatabasePath(encryptedName).path, null, SQLiteDatabase.OPEN_READONLY,
                { throw IllegalStateException("Preserve test file") }).use {
                it.rawQuery("SELECT * FROM emergency_profile", null).use { cursor -> cursor.moveToFirst() }
            }
        }
        assertTrue(context.getDatabasePath(encryptedName).exists())
    }

    @Test fun migrationPreservesEveryTableAndRemovesPlaintextOnlyAfterValidation() = runBlocking {
        val original = Room.databaseBuilder(context, ResQLinkDatabase::class.java, legacyName).build().also { databases += it }
        val contactRepo = ContactRepositoryImpl(original)
        contactRepo.save(EmergencyContact(name = "Migration Name", phoneNumber = "+15550100001", email = "private@example.invalid", priority = 0))
        ProfileRepositoryImpl(original.profileDao()).save(EmergencyProfile("Migrated Profile", "PRIVATE-MIGRATION", "Private Street", "Medical information"))
        val emergency = EmergencyRepositoryImpl(original.emergencyDao())
        val event = emergency.createOrGetActive(63)
        assertTrue(emergency.prepareAlerts(event.id, contactRepo.enabledContacts()))
        assertTrue(emergency.claimDispatch(event.id))
        emergency.recordLocation(event.id, LocationSnapshot(12.34, 56.78, 4f, 123L, "test"))
        val expected = snapshot(original.openHelper.writableDatabase)
        original.close()
        // Simulate a previous interrupted export. This file is never selected as saved data.
        File(context.getDatabasePath(encryptedName).path + ".migrating").writeText("partial export")
        assertTrue(context.getDatabasePath(legacyName).exists())

        val secured = encrypted()
        assertEquals(expected, snapshot(secured.openHelper.writableDatabase))
        assertFalse(context.getDatabasePath(legacyName).exists())
        assertFalse(File(context.getDatabasePath(legacyName).path + "-wal").exists())
        assertEncryptedFiles("PRIVATE-MIGRATION")
        secured.close()
        assertEquals(expected, snapshot(encrypted().openHelper.writableDatabase))
    }

    @Test fun unsupportedLegacySchemaIsPreservedWithoutCreatingReplacement() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(legacyName), null).use {
            it.execSQL("CREATE TABLE future_data(value TEXT)")
            it.execSQL("INSERT INTO future_data VALUES ('keep me')")
            it.version = 99
        }
        val before = context.getDatabasePath(legacyName).readBytes()
        assertFails { encrypted().openHelper.writableDatabase }
        assertArrayEquals(before, context.getDatabasePath(legacyName).readBytes())
        assertFalse(context.getDatabasePath(encryptedName).exists())
    }

    @Test fun missingWrappedKeyNeverReplacesExistingDatabase() = runBlocking {
        val database = encrypted()
        ProfileRepositoryImpl(database.profileDao()).save(EmergencyProfile(name = "Keep", notes = "Saved data"))
        database.close()
        val before = context.getDatabasePath(encryptedName).readBytes()
        assertTrue(keyFile.delete())
        assertFails { encrypted().openHelper.writableDatabase }
        assertFalse(keyFile.exists())
        assertArrayEquals(before, context.getDatabasePath(encryptedName).readBytes())
    }

    @Test fun tamperedWrappedKeyBlocksReadsAndRetryRecoversOriginalData() = runBlocking {
        val database = encrypted()
        ContactRepositoryImpl(database).save(EmergencyContact(name = "Keep", phoneNumber = "+15550100001"))
        database.close()
        val validKey = keyFile.readBytes()
        keyFile.writeBytes(validKey.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val before = context.getDatabasePath(encryptedName).readBytes()
        val health = StorageHealth()
        val result = async { ContactRepositoryImpl(encrypted(), health).observeContacts().first() }
        withTimeout(10_000) { health.failedSources.first { it.isNotEmpty() } }
        assertFalse(result.isCompleted)
        assertArrayEquals(before, context.getDatabasePath(encryptedName).readBytes())
        keyFile.writeBytes(validKey)
        health.retry()
        assertEquals("Keep", withTimeout(10_000) { result.await() }.single().name)
        assertTrue(health.failedSources.value.isEmpty())
    }

    @Test fun damagedDatabaseIsPreserved() {
        val database = encrypted()
        database.openHelper.writableDatabase
        database.close()
        val file = context.getDatabasePath(encryptedName)
        file.writeBytes(ByteArray(4096) { 0x6b.toByte() })
        val before = file.readBytes()
        assertFails { encrypted().openHelper.writableDatabase }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun messageEncryptionAuthenticatesContentAndUsesFreshNonces() {
        val message = "Private message"
        val first = secure.encryptMessage(message)
        val second = secure.encryptMessage(message)
        assertNotEquals(first, second)
        assertEquals(message, secure.decryptMessage(first))
        val envelope = android.util.Base64.decode(first, android.util.Base64.NO_WRAP)
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        assertFails { secure.decryptMessage(android.util.Base64.encodeToString(envelope, android.util.Base64.NO_WRAP)) }
        val other = SecureStorage(context, prefix + "-unused", keyFile)
        assertFails { other.decryptMessage(first) }
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(prefix + "-unused"))
    }

    @Test fun legacyMessageMigratesAtomicallyAndFurtherWritesStayEncrypted() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { settingsFile }
        val plaintextKey = stringPreferencesKey("emergency_message")
        val ciphertextKey = stringPreferencesKey("encrypted_emergency_message")
        val marker = "PRIVATE-MESSAGE-" + UUID.randomUUID()
        store.edit { it[plaintextKey] = marker }
        val repository = SettingsRepositoryImpl(store, secure, StorageHealth())
        assertEquals(marker, withTimeout(10_000) { repository.settings.first() }.emergencyMessage)
        assertNull(store.data.first()[plaintextKey])
        assertNotNull(store.data.first()[ciphertextKey])
        assertFalse(settingsFile.readText().contains(marker))
        repository.setEmergencyMessage(marker + "-new")
        assertEquals(marker + "-new", repository.settings.first().emergencyMessage)
        assertFalse(settingsFile.readText().contains(marker))
    }

    @Test fun missingMessageKeyShowsFailureWithoutDefaultingOrResettingPreferences() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { settingsFile }
        val health = StorageHealth()
        val repository = SettingsRepositoryImpl(store, secure, health)
        repository.setEmergencyMessage("Private saved message")
        val before = settingsFile.readBytes()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        val result = async { repository.settings.first() }
        withTimeout(10_000) { health.failedSources.first { it.isNotEmpty() } }
        assertFalse(result.isCompleted)
        assertArrayEquals(before, settingsFile.readBytes())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
        result.cancelAndJoin()
    }

    @Test fun onboardingWriteFailureShowsFeedback() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { settingsFile }
        val repository = object : com.resqlink.domain.repository.SettingsRepository by SettingsRepositoryImpl(store, secure, StorageHealth()) {
            override suspend fun completeOnboarding() { throw java.io.IOException("Test write failure") }
        }
        withContext(Dispatchers.Main) {
            val viewModel = com.resqlink.feature.onboarding.OnboardingViewModel(repository)
            viewModel.complete()
            assertNotNull(withTimeout(5_000) { viewModel.userMessage.first { it != null } })
        }
    }

    @Test fun historyWriteFailureShowsFeedback() = runBlocking {
        val repository = object : com.resqlink.domain.repository.EmergencyRepository by EmergencyRepositoryImpl(encrypted().emergencyDao()) {
            override suspend fun delete(eventId: Long) { throw java.io.IOException("Test write failure") }
        }
        withContext(Dispatchers.Main) {
            val viewModel = com.resqlink.feature.history.HistoryViewModel(repository)
            viewModel.delete(1).join()
            assertNotNull(viewModel.userMessage.value)
        }
    }

    private fun assertEncryptedFiles(marker: String) {
        for (suffix in listOf("", "-wal", "-journal")) {
            val file = File(context.getDatabasePath(encryptedName).path + suffix)
            if (file.exists()) assertFalse(file.readText(Charsets.ISO_8859_1).contains(marker))
        }
        assertFalse(keyFile.readText(Charsets.ISO_8859_1).contains(marker))
    }

    private fun snapshot(database: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        listOf("emergency_contacts", "emergency_profile", "emergency_events", "alert_attempts", "location_points")
            .associateWith { table ->
                database.query("SELECT * FROM " + table + " ORDER BY id").use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
                    }
                }
            }

    private fun assertFails(action: () -> Unit) {
        try { action() } catch (_: Exception) { return }
        fail("Expected saved storage to be rejected")
    }
}
