package io.nekohasekai.sagernet.database.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores WebDAV credentials encrypted with an app-local Android Keystore key. */
object WebDavConfigStore {
    data class Config(val serverUrl: String, val username: String, val password: String)

    private const val PREFS = "webdav_secure_config"
    private const val VALUE = "encrypted_config"
    private const val KEY_ALIAS = "nekobox_webdav_config_v1"

    fun load(context: Context): Config? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null)
            ?: return null
        val packed = Base64.decode(value, Base64.NO_WRAP)
        val ivSize = packed.firstOrNull()?.toInt()?.and(0xff) ?: throw IllegalStateException("Invalid WebDAV settings")
        if (ivSize !in 12..16 || packed.size <= ivSize) throw IllegalStateException("Invalid WebDAV settings")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, packed.copyOfRange(1, 1 + ivSize)))
        val json = String(cipher.doFinal(packed.copyOfRange(1 + ivSize, packed.size)), Charsets.UTF_8)
        val obj = JSONObject(json)
        return Config(obj.getString("server"), obj.getString("username"), obj.getString("password"))
    }

    fun save(context: Context, config: Config) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val iv = cipher.iv
        val json = JSONObject().put("server", config.serverUrl).put("username", config.username)
            .put("password", config.password).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(json)
        val packed = byteArrayOf(iv.size.toByte()) + iv + encrypted
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save WebDAV settings"
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw IllegalStateException("The encrypted WebDAV settings key is unavailable; re-enter your credentials")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
