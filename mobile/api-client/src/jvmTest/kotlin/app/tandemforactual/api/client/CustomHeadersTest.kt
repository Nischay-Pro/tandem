package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class CustomHeadersTest {
    @Test
    fun customHeadersGoWithEveryRequestToTheServer() =
        runBlocking {
            val seen = mutableListOf<String?>()
            val engine =
                MockEngine { request ->
                    seen += request.headers["CF-Access-Client-Id"]
                    when (request.url.encodedPath) {
                        "/account/needs-bootstrap" -> respond("""{"data":{"bootstrapped":true}}""")
                        else -> respond("""{"methods":[{"method":"password","active":1}]}""")
                    }
                }
            val client = TandemApiClient(http = HttpClient(engine))
            client.readActualLoginMethods("https://budget.example")
            client.customHeaders = mapOf("CF-Access-Client-Id" to "proxy-id")
            client.customHeaders = mapOf("CF-Access-Client-Id" to "proxy-id")
            client.readActualLoginMethods("https://budget.example")
            client.customHeaders = emptyMap()
            client.readActualLoginMethods("https://budget.example")
            assertEquals(listOf(null, null, "proxy-id", "proxy-id", null, null), seen)
        }

    @Test
    fun noHeadersKeepTheSameClient() {
        val http = HttpClient(MockEngine { respond("") })
        assertSame(http, http.withHeaders(emptyMap()))
    }
}
