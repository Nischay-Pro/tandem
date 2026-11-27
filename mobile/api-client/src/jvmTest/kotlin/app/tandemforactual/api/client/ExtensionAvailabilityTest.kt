package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ExtensionAvailabilityTest {
    private val session =
        """{"serverUrl":"https://actual.example","token":"t","deviceId":"d","principalId":"p","budgetIds":["b"]}"""

    @Test
    fun asksAgainAfterAPauseWhenTheServerCouldNotSay(): Unit =
        runBlocking {
            var now = 0L
            var requests = 0
            var status = HttpStatusCode.ServiceUnavailable
            val http =
                HttpClient(
                    MockEngine {
                        requests++
                        respond("""{"features":["planning-records"]}""", status, headersOf("Content-Type", "application/json"))
                    },
                )
            val client = TandemApiClient(http = http, now = { now })
            client.restoreStoredSession(session)
            assertNull(assertFailsWith<ExtensionSyncException> { client.planningTransport("b") }.status)
            assertFailsWith<ExtensionSyncException> { client.planningTransport("b") }
            assertEquals(1, requests)
            now = 30_000
            assertFailsWith<ExtensionSyncException> { client.planningTransport("b") }
            assertEquals(2, requests)
            now = 89_999
            assertFailsWith<ExtensionSyncException> { client.planningTransport("b") }
            assertEquals(2, requests)
            now = 90_000
            status = HttpStatusCode.OK
            assertTrue(client.planningTransport("b") is ExtensionSyncClient)
            assertTrue(client.planningTransport("b") is ExtensionSyncClient)
            assertEquals(3, requests)
        }

    @Test
    fun remembersAServerWithoutExtensionSync(): Unit =
        runBlocking {
            var requests = 0
            val http =
                HttpClient(
                    MockEngine {
                        requests++
                        respond("Not found", HttpStatusCode.NotFound)
                    },
                )
            val client = TandemApiClient(http = http)
            client.restoreStoredSession(session)
            assertNull(client.planningTransport("b"))
            assertNull(client.planningTransport("b"))
            assertEquals(1, requests)
            client.restoreStoredSession(session.replace("actual.example", "other.example"))
            assertNull(client.planningTransport("b"))
            assertEquals(2, requests)
        }
}
