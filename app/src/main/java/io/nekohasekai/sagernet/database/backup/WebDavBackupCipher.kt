package io.nekohasekai.sagernet.database.backup

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Cross-device encryption for WebDAV backup payloads. The passphrase is never persisted. */
internal object WebDavBackupCipher {
    private val MAGIC = "NB4AWDB1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256

    fun encrypt(plainText: String, passphrase: String): ByteArray {
        require(passphrase.length >= 12) { "Use a backup passphrase with at least 12 characters" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + encrypted
    }

    /** Decode new encrypted files or the legacy remote JSON format. Local .json imports stay untouched. */
    fun decryptOrRead(bytes: ByteArray, passphrase: String?): String {
        if (!bytes.startsWith(MAGIC)) return bytes.toString(Charsets.UTF_8)
        if (passphrase.isNullOrEmpty()) throw IOException("Enter the WebDAV backup passphrase")
        val headerSize = MAGIC.size + SALT_BYTES + IV_BYTES
        if (bytes.size < headerSize + 16) throw IOException("Encrypted WebDAV backup is incomplete")
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val cipherStart = ivStart + IV_BYTES
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, bytes.copyOfRange(saltStart, ivStart)),
            GCMParameterSpec(TAG_BITS, bytes.copyOfRange(ivStart, cipherStart)),
        )
        return try {
            cipher.doFinal(bytes.copyOfRange(cipherStart, bytes.size)).toString(Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw IOException("Wrong passphrase or damaged WebDAV backup", e)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS)
        chars.fill('\u0000')
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
