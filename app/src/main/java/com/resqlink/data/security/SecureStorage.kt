package com.resqlink.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Keys never leave Android Keystore; only an authenticated, wrapped database password is saved. */
@Singleton
class SecureStorage(
    private val context: Context,
    private val alias: String,
    private val keyFile: File,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context, context.packageName + ".storage.v1", File(context.noBackupFilesDir, "database-key.v1"),
    )

    private val atomicKey = AtomicFile(keyFile)

    @Synchronized
    fun databasePassword(databaseExists: Boolean): ByteArray {
        // AtomicFile restores an interrupted write before returning existing data.
        if (keyFile.exists() || File(keyFile.path + ".bak").exists()) {
            return decrypt(atomicKey.readFully(), "database-password").also {
                check(it.size == 32) { "Invalid database key." }
            }
        }
        check(!databaseExists) { "The database key is unavailable." }
        val password = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val wrapped = encrypt(password, "database-password")
            val stream = atomicKey.startWrite()
            try {
                stream.write(wrapped)
                atomicKey.finishWrite(stream)
            } catch (failure: Exception) {
                atomicKey.failWrite(stream)
                throw failure
            }
            return password
        } catch (failure: Exception) {
            password.fill(0)
            throw failure
        }
    }

    @Synchronized
    fun encryptMessage(message: String): String =
        Base64.encodeToString(encrypt(message.toByteArray(Charsets.UTF_8), "emergency-message"), Base64.NO_WRAP)

    @Synchronized
    fun decryptMessage(encoded: String): String {
        require(encoded.length <= 16_384) { "Invalid encrypted message." }
        val plaintext = decrypt(Base64.decode(encoded, Base64.NO_WRAP), "emergency-message")
        return try { plaintext.toString(Charsets.UTF_8) } finally { plaintext.fill(0) }
    }

    private fun masterKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing != null) return existing as SecretKey
        check(create) { "The storage key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private fun encrypt(plaintext: ByteArray, purpose: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey(create = !keyFile.exists() && !File(keyFile.path + ".bak").exists()))
        cipher.updateAAD((context.packageName + ":" + purpose + ":1").toByteArray(Charsets.UTF_8))
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }

    private fun decrypt(envelope: ByteArray, purpose: String): ByteArray {
        require(envelope.size in 29..16_384 && envelope[0] == 1.toByte()) { "Invalid encrypted storage." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(create = false), GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD((context.packageName + ":" + purpose + ":1").toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope, 13, envelope.size - 13)
    }
}
