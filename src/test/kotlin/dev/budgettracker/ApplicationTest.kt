package dev.budgettracker

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun healthAndNavigation() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/dashboard").status)
        assertEquals(HttpStatusCode.OK, client.get("/mp2").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/invalid").status)
    }
}
