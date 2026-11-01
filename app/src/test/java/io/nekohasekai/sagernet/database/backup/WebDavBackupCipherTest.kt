package io.nekohasekai.sagernet.database.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class WebDavBackupCipherTest {
    @Test fun encryptedBackupRoundTripsAcrossInstances() {
        val original = "{\"version\":1,\"profiles\":[\"secret test node\"]}"
        val encrypted = WebDavBackupCipher.encrypt(original, "a secure test passphrase")
        assertNotEquals(original, encrypted.toString(Charsets.UTF_8))
        assertEquals(original, WebDavBackupCipher.decryptOrRead(encrypted, "a secure test passphrase"))
    }

    @Test fun rejectsWrongPassphraseAndTampering() {
        val encrypted = WebDavBackupCipher.encrypt("sensitive backup", "a secure test passphrase")
        assertThrows(IOException::class.java) {
            WebDavBackupCipher.decryptOrRead(encrypted, "a different passphrase")
        }
        val damaged = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IOException::class.java) {
            WebDavBackupCipher.decryptOrRead(damaged, "a secure test passphrase")
        }
    }

    @Test fun readsLegacyWebDavJsonWithoutChangingLocalImportFormat() {
        val json = "{\"version\":1,\"profiles\":[]}"
        assertEquals(json, WebDavBackupCipher.decryptOrRead(json.toByteArray(), null))
    }

    @Test fun rejectsShortPassphrases() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavBackupCipher.encrypt("test", "short")
        }
    }
}
