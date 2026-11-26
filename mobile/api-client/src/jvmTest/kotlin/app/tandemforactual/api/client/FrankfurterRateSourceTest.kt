package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class FrankfurterRateSourceTest {
    private class Server(
        private val status: HttpStatusCode = HttpStatusCode.OK,
        private val failure: Exception? = null,
        private val body: () -> String,
    ) {
        val seen = mutableListOf<HttpRequestData>()

        fun source(baseUrl: String = FrankfurterRateSource.DEFAULT_URL) =
            FrankfurterRateSource(
                HttpClient(
                    MockEngine { request ->
                        seen += request
                        failure?.let { throw it }
                        respond(body(), status)
                    },
                ),
                baseUrl,
            )
    }

    private val latestBody = """{"amount":1.0,"base":"EUR","date":"2026-11-25","rates":{"GBP":0.8373,"USD":1.10,"IDR":2.4E-5}}"""

    @Test
    fun readsTheLatestRatesAsExactDecimalsAndSendsOnlyTheBaseAndSymbols(): Unit =
        runBlocking {
            val server = Server { latestBody }
            val day = server.source().latest("eur", setOf("usd", "GBP", "IDR"))
            assertEquals(ExchangeRateDay("2026-11-25", "EUR", mapOf("GBP" to "0.8373", "USD" to "1.10", "IDR" to "2.4E-5")), day)
            val request = server.seen.single()
            assertEquals("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=GBP%2CIDR%2CUSD", request.url.toString())
            assertEquals(setOf("base", "symbols"), request.url.parameters.names())
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
        }

    @Test
    fun anEmptySymbolSetAsksForEveryCurrency(): Unit =
        runBlocking {
            val server = Server { latestBody }
            FrankfurterRateSource(
                HttpClient(
                    MockEngine { request ->
                        server.seen += request
                        respond(latestBody)
                    },
                ),
            ).latest("EUR", emptySet())
            assertEquals(
                "https://api.frankfurter.dev/v1/latest?base=EUR",
                server.seen
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun readsARangeOldestFirst(): Unit =
        runBlocking {
            val server =
                Server {
                    """{"amount":1.0,"base":"EUR","start_date":"2025-12-31","end_date":"2026-01-05",
                    "rates":{"2026-01-05":{"USD":1.1664},"2025-12-31":{"USD":1.175},"2026-01-02":{"USD":1.1721}}}"""
                }
            val days = server.source().range("EUR", setOf("USD"), "2026-01-01", "2026-01-05")
            assertEquals(
                listOf(
                    ExchangeRateDay("2025-12-31", "EUR", mapOf("USD" to "1.175")),
                    ExchangeRateDay("2026-01-02", "EUR", mapOf("USD" to "1.1721")),
                    ExchangeRateDay("2026-01-05", "EUR", mapOf("USD" to "1.1664")),
                ),
                days,
            )
            assertEquals(
                "https://api.frankfurter.dev/v1/2026-01-01..2026-01-05?base=EUR&symbols=USD",
                server.seen
                    .single()
                    .url
                    .toString(),
            )
        }

    @Test
    fun rejectsRangesThatArentIsoDatesOldestFirst(): Unit =
        runBlocking {
            val server = Server { "{}" }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-03-01", "2026-01-01") }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-1-1", "2026-03-01") }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-01-01", "soon") }
            assertTrue(server.seen.isEmpty())
        }

    @Test
    fun usesASelfHostedServerWithATrailingSlash(): Unit =
        runBlocking {
            val server = Server { latestBody }
            server.source("https://rates.example.com/frankfurter/").latest("EUR", setOf("USD"))
            server.source("http://192.168.1.20:8080/").latest("EUR", setOf("USD"))
            assertEquals(
                listOf(
                    "https://rates.example.com/frankfurter/latest?base=EUR&symbols=USD",
                    "http://192.168.1.20:8080/latest?base=EUR&symbols=USD",
                ),
                server.seen.map { it.url.toString() },
            )
        }

    @Test
    fun rejectsInsecureOrMalformedAddresses() {
        val server = Server { latestBody }
        listOf(
            "http://rates.example.com",
            "ftp://rates.example.com",
            "rates.example.com",
            "https://user:pass@rates.example.com",
            "https://rates.example.com/v1?key=1",
            "https://rates.example.com/v1#top",
        ).forEach { address ->
            val error = assertFailsWith<ExchangeRateException.InvalidAddress>(address) { server.source(address) }
            assertEquals("Use an https:// exchange-rate address, or http:// on your local network.", error.message)
        }
    }

    @Test
    fun mapsHttpStatusesToPlainErrors(): Unit =
        runBlocking {
            suspend fun failure(status: HttpStatusCode) =
                assertFailsWith<ExchangeRateException> {
                    Server(
                        status,
                    ) { """{"message":"not found"}""" }.source().latest("EUR", setOf("USD"))
                }
            assertTrue(failure(HttpStatusCode.TooManyRequests) is ExchangeRateException.RateLimited)
            assertTrue(failure(HttpStatusCode.NotFound) is ExchangeRateException.UnknownCurrency)
            assertTrue(failure(HttpStatusCode.UnprocessableEntity) is ExchangeRateException.UnknownCurrency)
            assertTrue(failure(HttpStatusCode.ServiceUnavailable) is ExchangeRateException.Unreachable)
            assertTrue(failure(HttpStatusCode.BadRequest) is ExchangeRateException.BadResponse)
            assertEquals("The exchange-rate server is busy. Try again later.", failure(HttpStatusCode.TooManyRequests).message)
            assertEquals(
                "The exchange-rate server doesn’t have rates for one of these currencies.",
                failure(HttpStatusCode.NotFound).message,
            )
        }

    @Test
    fun mapsNetworkFailuresToUnreachableAndKeepsCancellation(): Unit =
        runBlocking {
            val error =
                assertFailsWith<ExchangeRateException.Unreachable> {
                    Server(failure = IOException("offline")) { "" }.source().latest("EUR", setOf("USD"))
                }
            assertEquals("Couldn’t reach the exchange-rate server. Check your connection and try again.", error.message)
            assertTrue(error.cause is IOException)
            assertFailsWith<CancellationException> {
                Server(failure = CancellationException("stopped")) { "" }.source().latest("EUR", setOf("USD"))
            }
        }

    @Test
    fun rejectsCurrencyCodesBeforeAskingTheServer(): Unit =
        runBlocking {
            val server = Server { latestBody }
            assertFailsWith<ExchangeRateException.UnknownCurrency> { server.source().latest("EURO", setOf("USD")) }
            assertFailsWith<ExchangeRateException.UnknownCurrency> { server.source().latest("EUR", setOf("US&D")) }
            assertTrue(server.seen.isEmpty())
        }

    @Test
    fun rejectsMalformedResponses(): Unit =
        runBlocking {
            val latest =
                listOf(
                    "not json",
                    "[]",
                    """{"base":"USD","date":"2026-11-25","rates":{"GBP":0.8}}""",
                    """{"date":"2026-11-25","rates":{"GBP":0.8}}""",
                    """{"base":"EUR","rates":{"GBP":0.8}}""",
                    """{"base":"EUR","date":2026,"rates":{"GBP":0.8}}""",
                    """{"base":"EUR","date":"25/11/2026","rates":{"GBP":0.8}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":[]}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"GBP":"0.8"}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"GBP":{}}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"GBP":-0.8}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"GBP":0}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"GBP":1e999}}""",
                    """{"base":"EUR","date":"2026-11-25","rates":{"gbp":0.8}}""",
                )
            latest.forEach { body ->
                val error =
                    assertFailsWith<ExchangeRateException.BadResponse>(body) { Server { body }.source().latest("EUR", setOf("GBP")) }
                assertEquals("The exchange-rate server sent a response Tandem couldn’t read.", error.message)
            }
            listOf(
                """{"base":"EUR","rates":[]}""",
                """{"base":"EUR","rates":{"2026-01-02":5}}""",
                """{"base":"EUR","rates":{"soon":{"USD":1.1}}}""",
            ).forEach { body ->
                assertFailsWith<ExchangeRateException.BadResponse>(body) {
                    Server { body }.source().range("EUR", setOf("USD"), "2026-01-01", "2026-01-05")
                }
            }
        }
}
