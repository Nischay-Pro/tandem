package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerCapabilitiesTest {
    @Test
    fun readsCapabilitiesAndTreatsMissingEndpointAsLegacy() =
        runBlocking {
            var reply = 0
            val paths = mutableListOf<String>()
            withEngine(
                MockEngine { request ->
                    paths += request.url.encodedPath
                    reply += 1
                    when (reply) {
                        1 ->
                            respond(
                                """{"apiVersion":1,"minimumClientApiVersion":1,"features":["hold-total"],"actualVersion":"26.11.0"}""",
                                headers = jsonHeaders(),
                            )
                        2 -> respond("", HttpStatusCode.NotFound, jsonHeaders())
                        3 -> respond("", HttpStatusCode.InternalServerError, jsonHeaders())
                        4 -> respond("""{"apiVersion":"one"}""", headers = jsonHeaders())
                        else -> error("connection refused")
                    }
                },
            ) { client ->
                val current = client.readServerCapabilities()!!
                assertEquals(1, current.apiVersion)
                assertTrue(current.supports("hold-total"))
                assertFalse(current.supports("future-feature"))
                assertFalse(current.isLegacy)
                assertFalse(current.clientUpdateRequired)
                assertEquals("26.11.0", current.actualVersion)
                assertEquals("/api/v1/capabilities", paths.first())

                val legacy = client.readServerCapabilities()!!
                assertTrue(legacy.isLegacy)
                assertFalse(legacy.supports("hold-total"))

                assertNull(client.readServerCapabilities())
                assertNull(client.readServerCapabilities())
                assertNull(client.readServerCapabilities())
            }
        }

    @Test
    fun aNewerMinimumAsksForAnAppUpdate() {
        val capabilities = ServerCapabilities(2, CLIENT_API_VERSION + 1, emptySet(), null)
        assertTrue(capabilities.clientUpdateRequired)
    }

    @Test
    fun noServerMeansNoCapabilities() =
        runBlocking {
            assertNull(TandemApiClient().readServerCapabilities())
        }

    private suspend fun withEngine(
        engine: MockEngine,
        block: suspend (TandemApiClient) -> Unit,
    ) {
        val previousClient = Api.client
        val previousBaseUrl = Api.baseUrl
        val client = TandemApiClient()
        Api.baseUrl = Url("https://tandem.example")
        Api.updateClient(
            createHttpClient = {
                HttpClient(engine) {
                    followRedirects = false
                    install(ContentNegotiation) { json(Api.json) }
                    install(ContentEncoding) {
                        gzip()
                        deflate()
                    }
                    install(DefaultRequest) { url.takeFrom(Api.baseUrl) }
                }
            },
        )
        try {
            client.restoreSession("https://tandem.example", "stored-device-token")
            block(client)
        } finally {
            Api.baseUrl = previousBaseUrl
            Api.client.close()
            previousClient.close()
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
}
