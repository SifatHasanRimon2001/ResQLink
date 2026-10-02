package com.resqlink.data.security

import android.content.Context
import android.system.Os
import android.system.OsConstants
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.resqlink.data.local.ResQLinkDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

@Singleton
class EncryptedDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
) {
    fun build(name: String = "resqlink-encrypted.db", legacyName: String = "resqlink.db"): ResQLinkDatabase {
        require(name != legacyName)
        return Room.databaseBuilder(context, ResQLinkDatabase::class.java, name)
            .openHelperFactory { configuration -> EncryptedHelper(configuration, legacyName) }
            .build()
    }

    private inner class EncryptedHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
        private val legacyName: String,
    ) : SupportSQLiteOpenHelper {
        private var delegate: SQLiteOpenHelper? = null
        private var password: ByteArray? = null
        private var walEnabled = false
        override val databaseName: String? get() = configuration.name

        @Synchronized
        private fun open(): SupportSQLiteDatabase {
            if (delegate == null) {
                initializeCipher()
                val destination = context.getDatabasePath(requireNotNull(databaseName))
                val key = secureStorage.databasePassword(destination.exists())
                try {
                    migrateLegacy(context.getDatabasePath(legacyName), destination, key)
                    val callback = configuration.callback
                    delegate = object : SQLiteOpenHelper(
                        context, databaseName, key, null, callback.version, 0, preserveOnCorruption, null, walEnabled,
                    ) {
                        override fun onCreate(db: SQLiteDatabase) = callback.onCreate(db)
                        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                            callback.onUpgrade(db, oldVersion, newVersion)
                        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                            callback.onDowngrade(db, oldVersion, newVersion)
                        override fun onConfigure(db: SQLiteDatabase) = callback.onConfigure(db)
                        override fun onOpen(db: SQLiteDatabase) = callback.onOpen(db)
                    }
                    password = key
                } catch (failure: Exception) {
                    key.fill(0)
                    throw failure
                }
            }
            // Room validates the schema here. Never remove the legacy file before this succeeds.
            val opened = requireNotNull(delegate).writableDatabase
            removeDatabaseFiles(context.getDatabasePath(legacyName))
            return opened
        }

        override val writableDatabase: SupportSQLiteDatabase get() = open()
        override val readableDatabase: SupportSQLiteDatabase get() = open()

        @Synchronized
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            walEnabled = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
        }

        @Synchronized
        override fun close() {
            delegate?.close()
            delegate = null
            password?.fill(0)
            password = null
        }
    }

    private fun migrateLegacy(source: File, destination: File, key: ByteArray) {
        if (destination.exists() || !source.exists()) return
        val temporary = File(destination.path + ".migrating")
        removeDatabaseFiles(temporary)
        check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        SQLiteDatabase.openDatabase(source.path, byteArrayOf(), null, SQLiteDatabase.OPEN_READWRITE,
            preserveOnCorruption, null).use { original ->
            check(original.version == 1) { "Unsupported saved database version." }
            checkIntegrity(original)
            // The source is intentionally opened without CREATE; attached files inherit that mode.
            check(temporary.createNewFile()) { "Could not create the encrypted migration file." }
            original.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(temporary.path, key))
            try {
                original.beginTransaction()
                try {
                    original.rawQuery("SELECT sqlcipher_export('encrypted')").use { check(it.moveToFirst()) }
                    original.execSQL("PRAGMA encrypted.user_version = 1")
                    for (table in tables) {
                        check(count(original, "main", table) == count(original, "encrypted", table)) {
                            "Encrypted migration validation failed."
                        }
                    }
                    original.setTransactionSuccessful()
                } finally {
                    original.endTransaction()
                }
            } finally {
                original.execSQL("DETACH DATABASE encrypted")
            }
        }
        SQLiteDatabase.openDatabase(temporary.path, key, null, SQLiteDatabase.OPEN_READONLY,
            preserveOnCorruption, null).use {
            check(it.version == 1)
            checkIntegrity(it)
        }
        RandomAccessFile(temporary, "rw").use { it.fd.sync() }
        Os.rename(temporary.path, destination.path)
        // Make the rename durable before any later removal of the plaintext source.
        val directory = Os.open(requireNotNull(destination.parent), OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
    }

    private fun checkIntegrity(database: SQLiteDatabase) {
        database.rawQuery("PRAGMA integrity_check").use {
            check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "Saved database is damaged." }
        }
    }

    private fun count(database: SQLiteDatabase, schema: String, table: String): Long =
        database.rawQuery("SELECT count(*) FROM " + schema + "." + table).use { it.moveToFirst(); it.getLong(0) }

    private fun removeDatabaseFiles(file: File) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val target = File(file.path + suffix)
            check(!target.exists() || target.delete()) { "Could not finish securing saved data." }
        }
    }

    companion object {
        private val tables = listOf("emergency_contacts", "emergency_profile", "emergency_events", "alert_attempts", "location_points")
        // SQLCipher's default corruption handler deletes files. Always preserve them for recovery.
        private val preserveOnCorruption = DatabaseErrorHandler { _, failure -> throw failure }

        @Synchronized
        private fun initializeCipher() {
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())
        }
    }
}
