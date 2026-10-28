package dev.budgettracker.android.network

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android Keystore; no network or real personal credentials. */
@RunWith(AndroidJUnit4::class)
class SecureSessionAcceptanceTest {
    private val token = "ab".repeat(32)

    @Test fun tokenIsAuthenticatedEncryptedAndNeverPersistedAsPlaintext() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("mobile_session",0)
        prefs.edit().remove("credentials").commit()
        val session=SecureSession(context)
        try {
            session.save("https://budget.example.test/", token)
            val encrypted=requireNotNull(prefs.getString("credentials",null))
            assertFalse(encrypted.contains(token))
            assertFalse(encrypted.contains("budget.example.test"))
            assertNotEquals(token,encrypted)
            val recovered=requireNotNull(session.read())
            assertEquals("https://budget.example.test",recovered.origin)
            assertEquals(token,recovered.token)
        } finally { session.clear() }
        assertNull(session.read())
        assertFalse(prefs.contains("credentials"))
    }

    @Test fun modifiedEncryptedSessionIsRejectedAndCleared() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("mobile_session",0)
        prefs.edit().remove("credentials").commit()
        val session=SecureSession(context)
        try {
            session.save("https://budget.example.test",token)
            val saved=requireNotNull(prefs.getString("credentials",null))
            val ciphertext=Base64.decode(saved,Base64.NO_WRAP)
            ciphertext[ciphertext.lastIndex]=(ciphertext.last().toInt() xor 1).toByte()
            prefs.edit().putString("credentials",
                Base64.encodeToString(ciphertext,Base64.NO_WRAP)).commit()
            assertNull("Tampered GCM ciphertext must not deserialize",session.read())
            assertFalse("Invalid session should be deleted",prefs.contains("credentials"))
        } finally { session.clear() }
    }

    @Test fun invalidOriginOrBearerTokenCannotBeStored() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("mobile_session",0)
        prefs.edit().remove("credentials").commit()
        val session=SecureSession(context)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                session.save("http://budget.example.test",token)
            }
            assertThrows(IllegalArgumentException::class.java) {
                session.save("https://budget.example.test","not-a-token")
            }
            assertNull(session.read())
        } finally { session.clear() }
    }
}
