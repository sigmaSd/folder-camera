package org.foldercamera.data

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

data class Receiver(val id: String, val endpoint: String, val fingerprint: String, val token: String)
class Configuration(context: Context) {
    private val prefs = context.getSharedPreferences("configuration", Context.MODE_PRIVATE)
    var root: String?
        get() = prefs.getString("root", null)
        set(value) { prefs.edit().putString("root", value).commit() }
    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync", false)
        set(value) { prefs.edit().putBoolean("sync", value).commit() }
    var lastPath: String
        get() = prefs.getString("lastPath", "") ?: ""
        set(value) { prefs.edit().putString("lastPath", value).apply() }
    fun receiver(): Receiver? {
        val encoded = prefs.getString("receiver", null) ?: return null
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8))
        return Receiver(json.getString("id"), json.getString("endpoint"), json.getString("fingerprint"), json.getString("token"))
    }
    fun setReceiver(r: Receiver) {
        val json = JSONObject().put("id", r.id).put("endpoint", r.endpoint).put("fingerprint", r.fingerprint).put("token", r.token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("receiver", Base64.encodeToString(cipher.iv + cipher.doFinal(json.toString().toByteArray()), Base64.NO_WRAP)).commit()
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("folder-camera", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("folder-camera", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
