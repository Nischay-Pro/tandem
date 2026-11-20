package app.tandemforactual.api.client

import app.tandemforactual.api.generated.Api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalServerIdentityTest {
    private val deviceId = "c2d791c1-d2bc-4e94-9f39-2d28d45e7c10"
    private val token = "stored-device-token"
    private val storedSession =
        """{"serverUrl":"https://tandem.example","accessToken":"$token",""" +
            """"device":{"deviceId":"$deviceId","principalId":"owner","budgetIds":["budget-1"]}}"""

    @Test
    fun hmacMatchesRfc4231() {
        val shortKey = hmacSha256(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray())
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", shortKey.hex())
        val longKey =
            hmacSha256(ByteArray(131) { 0xaa.toByte() }, "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray())
        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54", longKey.hex())
    }

    @Test
    fun proofMatchesTheServerAlgorithm() {
        val nonce = "A".repeat(43)
        val credentialHash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).hex()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(credentialHash.toByteArray(), "HmacSHA256")) }
        val expected =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal("$LOCAL_IDENTITY_CONTEXT\n$deviceId\n$nonce".toByteArray()))
        assertEquals(expected, localIdentityProof(token, deviceId, nonce))
        assertEquals(43, createLocalIdentityNonce().length)
        assertTrue(constantTimeEquals("abc", "abc"))
        assertFalse(constantTimeEquals("abc", "abd"))
        assertFalse(constantTimeEquals("abc", "abcd"))
    }

    @Test
    fun aProvenLocalServerCarriesRequestsUntilItFails() =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            withEngine(
                MockEngine { request ->
                    requests += request
                    if (request.url.encodedPath == "/api/v1/local-identity") {
                        val nonce =
                            Json
                                .parseToJsonElement((request.body as TextContent).text)
                                .jsonObject["nonce"]!!
                                .jsonPrimitive.content
                        respond("""{"proof":"${localIdentityProof(token, deviceId, nonce)}"}""", headers = jsonHeaders())
                    } else {
                        respond("""{"budgets":[]}""", headers = jsonHeaders())
                    }
                },
            ) { client ->
                client.setLocalServerUrl("http://192.168.1.10:19753")
                assertTrue(client.refreshLocalServer())
                assertTrue(client.isUsingLocalServer)
                assertEquals("http://192.168.1.10:19753", client.activeServerUrl)
                client.listBudgets()
                assertEquals("192.168.1.10", requests.last().url.host)

                client.useExternalServerAfterLocalFailure()
                client.listBudgets()
                assertEquals("tandem.example", requests.last().url.host)

                client.setLocalServerUrl(" ")
                assertFalse(client.refreshLocalServer())
                client.forgetSession()
                assertNull(client.activeServerUrl)
            }
        }

    @Test
    fun anUnprovenLocalServerIsNeverUsed() =
        runBlocking {
            var reply = 0
            withEngine(
                MockEngine {
                    reply += 1
                    when (reply) {
                        1 -> respond("""{"proof":"forged"}""", headers = jsonHeaders())
                        2 -> respond("""{"error":{"code":"route_not_found","message":"x"}}""", HttpStatusCode.NotFound, jsonHeaders())
                        3 -> respond("""{}""", headers = jsonHeaders())
                        4 -> error("connection refused")
                        else -> {
                            delay(5_000)
                            respond("""{}""", headers = jsonHeaders())
                        }
                    }
                },
            ) { client ->
                client.setLocalServerUrl("http://192.168.1.10:19753")
                repeat(4) { assertFalse(client.refreshLocalServer()) }
                assertFalse(client.refreshLocalServer(timeoutMillis = 50))
                assertFalse(client.isUsingLocalServer)
                assertEquals("https://tandem.example", client.activeServerUrl)
            }
        }

    @Test
    fun noLocalAddressOrSessionMeansNoCheck() =
        runBlocking {
            val client = TandemApiClient(allowInsecurePrivateNetwork = true)
            assertFalse(client.refreshLocalServer())
            client.setLocalServerUrl(null)
            assertFalse(client.refreshLocalServer())
        }

    private suspend fun withEngine(
        engine: MockEngine,
        block: suspend (TandemApiClient) -> Unit,
    ) {
        val previousClient = Api.client
        val previousBaseUrl = Api.baseUrl
        val client = TandemApiClient(allowInsecurePrivateNetwork = true)
        Api.baseUrl = Url("https://tandem.example")
        Api.setAuthProvider(client.bearerAuth)
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
            client.restoreStoredSession(storedSession)
            block(client)
        } finally {
            Api.clearAuthProvider(client.bearerAuth)
            Api.baseUrl = previousBaseUrl
            Api.client.close()
            previousClient.close()
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
