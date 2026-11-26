package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

class HttpClientLifecycleTest {
    @Test
    fun aStalledServerFailsTheCallInsteadOfHangingIt(): Unit =
        runBlocking {
            val http =
                HttpClient(MockEngine { awaitCancellation() }) {
                    install(HttpTimeout) { requestTimeoutMillis = 50 }
                }
            val client = TandemApiClient(http = http)
            val error = assertFailsWith<TandemApiException> { client.readActualLoginMethods("https://actual.example") }
            assertEquals("server_timeout", error.errorCode)
        }

    @Test
    fun closingAClientClosesItsHttpClient(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles())
            client.signInWithPassword("https://budget.example", "test", "Phone")
            client.budget("group")
            client.close()
            assertFalse(server.http.isActive)

            val dav = HttpClient(MockEngine { respond("") })
            WebDavClient(dav, "https://cloud.example/dav", "a", "b").use { assertTrue(dav.isActive) }
            assertFalse(dav.isActive)
        }
}
