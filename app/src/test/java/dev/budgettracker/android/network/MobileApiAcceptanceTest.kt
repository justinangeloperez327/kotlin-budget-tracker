package dev.budgettracker.android.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** In-process mobile API wire tests. No network, real accounts, or production tokens. */
class MobileApiAcceptanceTest {
    private val token = "ab".repeat(32)

    private class FakeHttps(
        url: URL,
        private val status: Int,
        private val response: String = "{}",
        private val error: String = """{"error":"Conflict detected"}"""
    ): HttpsURLConnection(url) {
        val uploaded = ByteArrayOutputStream()
        var disconnected = false
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream? =
            if(status in 200..299) null else ByteArrayInputStream(error.toByteArray(Charsets.UTF_8))
        override fun getOutputStream(): OutputStream = uploaded
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun loginUsesPostBodyNotQueryStringAndDoesNotSendBearer() {
        lateinit var sent: FakeHttps
        val api=MobileApi("https://budget.example.test/"){url->
            sent=FakeHttps(url,200,"""{"accessToken":"$token"}""")
            sent
        }
        assertEquals(token,api.authenticate("login","test@example.com","not-a-real-password")
            .getString("accessToken"))
        assertEquals("https://budget.example.test/api/mobile/auth/login",sent.url.toExternalForm())
        assertEquals("POST",sent.requestMethod)
        assertFalse(sent.instanceFollowRedirects)
        assertFalse(sent.useCaches)
        assertEquals("no-store",sent.getRequestProperty("Cache-Control"))
        assertNull(sent.getRequestProperty("Authorization"))
        assertFalse(sent.url.toExternalForm().contains("not-a-real-password"))
        assertEquals("not-a-real-password",
            JSONObject(sent.uploaded.toString("UTF-8")).getString("password"))
        assertTrue(sent.disconnected)
    }

    @Test fun getWorkspaceUsesBearerOnlyInHttpsHeader() {
        lateinit var sent: FakeHttps
        val api=MobileApi("https://budget.example.test"){url->
            sent=FakeHttps(url,200,
                """{"revision":7,"user":{"email":"test@example.com"},"data":{"budgets":{},"categoryBudgets":{},"expenses":[]}}""")
            sent
        }
        val snapshot=api.workspace(token)
        assertEquals(7,snapshot.revision)
        assertEquals("test@example.com",snapshot.email)
        assertEquals("GET",sent.requestMethod)
        assertEquals("Bearer $token",sent.getRequestProperty("Authorization"))
        assertFalse(sent.url.toExternalForm().contains(token))
        assertTrue(sent.disconnected)
    }

    @Test fun invalidTokenAndOversizedSaveRejectedBeforeOpeningConnection() {
        var opens=0
        val api=MobileApi("https://budget.example.test"){url->
            opens++
            FakeHttps(url,200)
        }
        assertThrows(IllegalArgumentException::class.java){api.workspace("not-valid")}
        val tooLarge=JSONObject().put("payload","x".repeat(2*1024*1024+10))
        assertThrows(IllegalArgumentException::class.java){api.save(token,1,tooLarge)}
        assertEquals(0,opens)
    }

    @Test fun http401ForbiddenConflictAndServerErrorsAreNotSuccessfulSaves() {
        for(status in listOf(401,403,409,500,503)) {
            lateinit var sent: FakeHttps
            val api=MobileApi("https://budget.example.test"){url->
                sent=FakeHttps(url,status)
                sent
            }
            val thrown=assertThrows(MobileApiException::class.java){
                api.save(token,7,JSONObject().put("budgets",JSONObject()))
            }
            assertEquals(status,thrown.status)
            assertEquals("Conflict detected",thrown.message)
            assertEquals(7,JSONObject(sent.uploaded.toString("UTF-8")).getInt("revision"))
            assertTrue(sent.disconnected)
        }
    }

    @Test fun savePreservesWholeWorkspaceAndRequiresRevisionPlusOne() {
        lateinit var sent: FakeHttps
        val api=MobileApi("https://budget.example.test"){url->
            sent=FakeHttps(url,200,"""{"revision":8}""")
            sent
        }
        val data=JSONObject().put("budgets",JSONObject().put("2026-10",40000L))
            .put("categoryBudgets",JSONObject()).put("expenses",org.json.JSONArray())
            .put("remittances",org.json.JSONArray().put(JSONObject().put("id","retained")))
        assertEquals(8,api.save(token,7,data))
        val submitted=JSONObject(sent.uploaded.toString("UTF-8"))
        assertEquals(7,submitted.getInt("revision"))
        assertEquals("retained",submitted.getJSONObject("data")
            .getJSONArray("remittances").getJSONObject(0).getString("id"))
        assertEquals("PUT",sent.requestMethod)
        assertTrue(sent.disconnected)
        val unexpected=MobileApi("https://budget.example.test"){url->
            FakeHttps(url,200,"""{"revision":13}""")
        }
        assertThrows(IllegalArgumentException::class.java){
            unexpected.save(token,7,data)
        }
    }

    @Test fun redirectsStayDisabledAndInvalidHtmlCannotBeAccountData() {
        lateinit var sent: FakeHttps
        val redirected=MobileApi("https://budget.example.test"){url->
            sent=FakeHttps(url,302,error="""{"error":"Redirect denied"}""")
            sent
        }
        assertEquals(302,assertThrows(MobileApiException::class.java){
            redirected.workspace(token)
        }.status)
        assertFalse(sent.instanceFollowRedirects)
        assertTrue(sent.disconnected)
        val html=MobileApi("https://budget.example.test"){url->
            FakeHttps(url,200,"<html>proxy error</html>")
        }
        val malformed=assertThrows(MobileApiException::class.java){html.workspace(token)}
        assertEquals(200,malformed.status)
        assertTrue(malformed.message!!.contains("mobile API response"))
    }

    @Test fun oversizedResponseIsRejectedWithBoundedMemory() {
        val bad=MobileApi("https://budget.example.test"){url->
            FakeHttps(url,200,"x".repeat(3*1024*1024+1))
        }
        assertThrows(IllegalArgumentException::class.java){bad.workspace(token)}
    }

    @Test fun noContentLogoutDoesNotSendFinancialData() {
        lateinit var sent: FakeHttps
        val api=MobileApi("https://budget.example.test"){url->
            sent=FakeHttps(url,204,"")
            sent
        }
        api.logout(token)
        assertEquals("POST",sent.requestMethod)
        assertEquals("Bearer $token",sent.getRequestProperty("Authorization"))
        assertEquals(0,sent.uploaded.size())
        assertTrue(sent.disconnected)
    }
}
