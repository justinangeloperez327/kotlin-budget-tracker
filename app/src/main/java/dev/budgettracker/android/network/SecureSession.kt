package dev.budgettracker.android.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredSession(val origin: String, val token: String)

/** AndroidKeyStore-wrapped credentials; never log or back up mobile bearer tokens. */
class SecureSession(context: Context) {
    private val prefs = context.getSharedPreferences("mobile_session", Context.MODE_PRIVATE)
    private val alias = "budget-tracker-mobile-key-v1"

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keystore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    fun save(origin: String, token: String) {
        val canonical = validatedServerOrigin(origin)
        require(token.matches(Regex("[a-f0-9]{64}")))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = (canonical + "\n" + token).toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(payload), Base64.NO_WRAP)
        check(prefs.edit().putString("credentials", encoded).commit()) {
            "Unable to securely store the mobile session"
        }
    }

    fun read(): StoredSession? {
        val encoded = prefs.getString("credentials", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val decrypted = cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
            val parts = decrypted.split("\n", limit = 2)
            require(parts.size == 2 && parts[1].matches(Regex("[a-f0-9]{64}")))
            StoredSession(validatedServerOrigin(parts[0]), parts[1])
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().remove("credentials").commit()
    }
}
