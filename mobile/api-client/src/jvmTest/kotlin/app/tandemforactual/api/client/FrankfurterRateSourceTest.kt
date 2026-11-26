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

    private fun row(
        date: String,
        quote: String,
        rate: String,
        base: String = "EUR",
    ) = """{"date":"$date","base":"$base","quote":"$quote","rate":$rate}"""

    private val latestBody =
        listOf(
            row("2026-11-26", "GBP", "0.83656"),
            row("2026-11-26", "USD", "1.10"),
            row("2026-11-25", "IDR", "2.4E-5"),
        ).joinToString(",", "[", "]")

    @Test
    fun readsTheLatestRatesAsExactDecimalsAndSendsOnlyTheBaseAndQuotes(): Unit =
        runBlocking {
            val server = Server { latestBody }
            val day = server.source().latest("eur", setOf("usd", "GBP", "IDR"))
            assertEquals(ExchangeRateDay("2026-11-25", "EUR", mapOf("IDR" to "2.4E-5", "GBP" to "0.83656", "USD" to "1.10")), day)
            val request = server.seen.single()
            assertEquals("https://api.frankfurter.dev/v2/rates?base=EUR&quotes=GBP%2CIDR%2CUSD", request.url.toString())
            assertEquals(setOf("base", "quotes"), request.url.parameters.names())
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
        }

    @Test
    fun anEmptySymbolSetAsksForEveryCurrencyAndDropsTheBaseItself(): Unit =
        runBlocking {
            val seen = mutableListOf<HttpRequestData>()
            val body = listOf(row("2026-11-26", "EUR", "1.0"), row("2026-11-26", "AAVE", "0.011515")).joinToString(",", "[", "]")
            val day =
                FrankfurterRateSource(
                    HttpClient(
                        MockEngine { request ->
                            seen += request
                            respond(body)
                        },
                    ),
                ).latest("EUR", emptySet())
            assertEquals(ExchangeRateDay("2026-11-26", "EUR", mapOf("AAVE" to "0.011515")), day)
            assertEquals("https://api.frankfurter.dev/v2/rates?base=EUR", seen.single().url.toString())
        }

    @Test
    fun readsARangeGroupedByDayOldestFirst(): Unit =
        runBlocking {
            val server =
                Server {
                    listOf(
                        row("2026-01-02", "USD", "1.1744"),
                        row("2026-01-02", "GBP", "0.87"),
                        row("2025-12-31", "GBP", "0.871"),
                        row("2026-01-01", "USD", "1.1748"),
                    ).joinToString(",", "[", "]")
                }
            val days = server.source().range("EUR", setOf("USD", "GBP"), "2026-01-01", "2026-01-02")
            assertEquals(
                listOf(
                    ExchangeRateDay("2025-12-31", "EUR", mapOf("GBP" to "0.871")),
                    ExchangeRateDay("2026-01-01", "EUR", mapOf("USD" to "1.1748")),
                    ExchangeRateDay("2026-01-02", "EUR", mapOf("USD" to "1.1744", "GBP" to "0.87")),
                ),
                days,
            )
            assertEquals(
                "https://api.frankfurter.dev/v2/rates?base=EUR&quotes=GBP%2CUSD&from=2026-01-01&to=2026-01-02",
                server.seen
                    .single()
                    .url
                    .toString(),
            )
            assertEquals(emptyList(), Server { "[]" }.source().range("EUR", setOf("USD"), "2026-01-01", "2026-01-02"))
        }

    @Test
    fun rejectsRangesThatArentIsoDatesOldestFirst(): Unit =
        runBlocking {
            val server = Server { "[]" }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-03-01", "2026-01-01") }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-1-1", "2026-03-01") }
            assertFailsWith<IllegalArgumentException> { server.source().range("EUR", setOf("USD"), "2026-01-01", "soon") }
            assertTrue(server.seen.isEmpty())
        }

    @Test
    fun usesASelfHostedServerWithATrailingSlash(): Unit =
        runBlocking {
            val server = Server { latestBody }
            server.source("https://rates.example.com/frankfurter/v2/").latest("EUR", setOf("USD"))
            server.source("http://192.168.1.20:8080/v2/").latest("EUR", setOf("USD"))
            assertEquals(
                listOf(
                    "https://rates.example.com/frankfurter/v2/rates?base=EUR&quotes=USD",
                    "http://192.168.1.20:8080/v2/rates?base=EUR&quotes=USD",
                ),
                server.seen.map { it.url.toString() },
            )
        }

    @Test
    fun rejectsInsecureOrMalformedAddresses() {
        val server = Server { latestBody }
        listOf(
            "http://rates.example.com/v2",
            "ftp://rates.example.com/v2",
            "rates.example.com",
            "https://user:pass@rates.example.com/v2",
            "https://rates.example.com/v2?key=1",
            "https://rates.example.com/v2#top",
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
                    Server(status) { """{"status":${status.value},"message":"invalid currency: ABC"}""" }
                        .source()
                        .latest("EUR", setOf("USD"))
                }
            assertTrue(failure(HttpStatusCode.TooManyRequests) is ExchangeRateException.RateLimited)
            assertTrue(failure(HttpStatusCode.NotFound) is ExchangeRateException.UnknownCurrency)
            assertTrue(failure(HttpStatusCode.UnprocessableEntity) is ExchangeRateException.UnknownCurrency)
            assertTrue(failure(HttpStatusCode.ServiceUnavailable) is ExchangeRateException.Unreachable)
            assertTrue(failure(HttpStatusCode.BadRequest) is ExchangeRateException.BadResponse)
            assertEquals("The exchange-rate server is busy. Try again later.", failure(HttpStatusCode.TooManyRequests).message)
            assertEquals(
                "The exchange-rate server doesn’t have rates for one of these currencies.",
                failure(HttpStatusCode.UnprocessableEntity).message,
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
            assertFailsWith<ExchangeRateException.UnknownCurrency> { server.source().latest("E", setOf("USD")) }
            assertFailsWith<ExchangeRateException.UnknownCurrency> { server.source().latest("EUR", setOf("US&D")) }
            assertTrue(server.seen.isEmpty())
        }

    @Test
    fun rejectsMalformedResponses(): Unit =
        runBlocking {
            listOf(
                "not json",
                "{}",
                "[]",
                "[1]",
                """[{"base":"EUR","quote":"GBP","rate":0.8}]""",
                """[{"date":2026,"base":"EUR","quote":"GBP","rate":0.8}]""",
                """[{"date":"25/11/2026","base":"EUR","quote":"GBP","rate":0.8}]""",
                "[${row("2026-11-25", "GBP", "0.8", base = "USD")}]",
                """[{"date":"2026-11-25","base":"EUR","rate":0.8}]""",
                "[${row("2026-11-25", "gbp", "0.8")}]",
                """[{"date":"2026-11-25","base":"EUR","quote":"GBP"}]""",
                "[${row("2026-11-25", "GBP", "\"0.8\"")}]",
                "[${row("2026-11-25", "GBP", "{}")}]",
                "[${row("2026-11-25", "GBP", "-0.8")}]",
                "[${row("2026-11-25", "GBP", "0")}]",
                "[${row("2026-11-25", "GBP", "1e999")}]",
            ).forEach { body ->
                val error =
                    assertFailsWith<ExchangeRateException.BadResponse>(body) { Server { body }.source().latest("EUR", setOf("GBP")) }
                assertEquals("The exchange-rate server sent a response Tandem couldn’t read.", error.message)
            }
        }
}
