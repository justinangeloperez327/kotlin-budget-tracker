package dev.budgettracker.android.network

import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

class MobileApiException(val status: Int, message: String): Exception(message)

/** HTTPS-only mobile client. No browser cookies, redirects, or plaintext credentials in URLs. */
class MobileApi(origin: String) {
    private val base = validatedServerOrigin(origin)

    private fun readBounded(stream: InputStream, maximum: Int = 3 * 1024 * 1024): String {
        stream.use {
            val result = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                require(total <= maximum) { "Response exceeds safe size limit" }
                result.write(buf, 0, n)
            }
            return result.toString(Charsets.UTF_8.name())
        }
    }

    fun request(path: String, method: String, token: String? = null, body: JSONObject? = null): JSONObject {
        require(path.startsWith("/api/mobile/") && !path.contains(".."))
        val connection = URL(base + path).openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.useCaches = false
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        if (token != null) {
            require(token.matches(Regex("[a-f0-9]{64}")))
            connection.setRequestProperty("Authorization", "Bearer " + token)
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 2 * 1024 * 1024) { "Workspace save exceeds 2 MiB" }
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val result = if (stream == null) JSONObject() else JSONObject(readBounded(stream))
            if (status !in 200..299)
                throw MobileApiException(status, result.optString("error", "Server request failed (HTTP $status)"))
            return result
        } finally {
            connection.disconnect()
        }
    }

    fun authenticate(action: String, email: String, password: String, name: String = ""): JSONObject {
        require(action == "login" || action == "register")
        return request("/api/mobile/auth/" + action, "POST", body = JSONObject()
            .put("email", email).put("password", password).put("name", name))
    }
    fun workspace(token: String): ServerSnapshot =
        parseWorkspace(request("/api/mobile/workspace", "GET", token))
    fun save(token: String, revision: Int, data: JSONObject): Int {
        val body = JSONObject().put("revision", revision).put("data", data)
        val response = request("/api/mobile/workspace", "PUT", token, body)
        val updated = response.getInt("revision")
        require(updated == revision + 1) { "Server returned an unexpected workspace revision" }
        return updated
    }
    fun logout(token: String) {
        request("/api/mobile/auth/logout", "POST", token)
    }
}
