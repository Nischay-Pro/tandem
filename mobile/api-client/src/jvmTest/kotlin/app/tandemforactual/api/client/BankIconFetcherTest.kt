package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class BankIconFetcherTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
    private val ico = byteArrayOf(0, 0, 1, 0, 1, 0)

    private class Site(
        val routes: Map<String, MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>,
    ) {
        val seen = mutableListOf<String>()
        val fetcher =
            BankIconFetcher(
                HttpClient(
                    MockEngine { request ->
                        val url = request.url.toString()
                        seen += url
                        val route = routes[url] ?: return@MockEngine respond("", HttpStatusCode.NotFound)
                        route(request)
                    },
                ),
            )
    }

    private fun bytes(body: ByteArray): MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond(body) }

    private fun redirect(to: String): MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, to)) }

    @Test
    fun takesFaviconIcoFirstAndSendsOnlyTheDomain(): Unit =
        runBlocking {
            val site = Site(mapOf("https://bank.example.com/favicon.ico" to bytes(ico)))
            val icon = site.fetcher.fetch("https://Bank.Example.com/accounts?id=42")!!
            assertEquals(BankIconFormat.ICO, icon.format)
            assertContentEquals(ico, icon.bytes)
            assertEquals(listOf("https://bank.example.com/favicon.ico"), site.seen)
        }

    @Test
    fun fallsBackToTheIconsTheHomePageDeclares(): Unit =
        runBlocking {
            val html =
                """
                <link rel="stylesheet" href="/a.css"><link href="/x.png">
                <link rel="icon" sizes="16x16" href="/small.png">
                <LINK REL='apple-touch-icon' HREF='/touch.png'>
                <link rel=icon sizes=any href=/any.svg>
                """.trimIndent()
            val site =
                Site(
                    mapOf(
                        "https://bank.com/" to bytes(html.encodeToByteArray()),
                        "https://bank.com/touch.png" to { respond("", HttpStatusCode.InternalServerError) },
                        "https://bank.com/small.png" to bytes(png),
                    ),
                )
            assertEquals(BankIconFormat.PNG, site.fetcher.fetch("bank.com")!!.format)
            assertEquals(
                listOf("https://bank.com/favicon.ico", "https://bank.com/", "https://bank.com/touch.png", "https://bank.com/small.png"),
                site.seen,
            )
        }

    @Test
    fun followsHttpsRedirectsButNotDowngradesOrLoops(): Unit =
        runBlocking {
            val followed =
                Site(
                    mapOf(
                        "https://bank.com/favicon.ico" to redirect("https://www.bank.com/favicon.ico"),
                        "https://www.bank.com/favicon.ico" to bytes(ico),
                    ),
                )
            assertEquals(BankIconFormat.ICO, followed.fetcher.fetch("bank.com")!!.format)

            val downgraded =
                Site(
                    mapOf(
                        "https://bank.com/favicon.ico" to redirect("http://bank.com/favicon.ico"),
                        "https://bank.com/" to redirect("https://10.0.0.1/"),
                    ),
                )
            assertNull(downgraded.fetcher.fetch("bank.com"))
            assertEquals(2, downgraded.seen.size)

            val looping = Site(mapOf("https://bank.com/favicon.ico" to redirect("/favicon.ico"), "https://bank.com/" to redirect("/")))
            assertNull(looping.fetcher.fetch("bank.com"))
            assertEquals(8, looping.seen.size)
        }

    @Test
    fun refusesOversizedEmptyAndUnknownFiles(): Unit =
        runBlocking {
            val tooBig = ByteArray(BankIconFetcher.MAX_ICON_BYTES + 1).also { ico.copyInto(it) }
            val declaredTooBig =
                Site(
                    mapOf(
                        "https://bank.com/favicon.ico" to {
                            respond(
                                ByteReadChannel(tooBig),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentLength, tooBig.size.toString()),
                            )
                        },
                        "https://bank.com/" to { respond("", HttpStatusCode.NoContent) },
                    ),
                )
            assertNull(declaredTooBig.fetcher.fetch("bank.com"))

            val streamedTooBig =
                Site(
                    mapOf(
                        "https://bank.com/favicon.ico" to { respond(ByteReadChannel(tooBig), HttpStatusCode.OK) },
                        "https://bank.com/" to bytes("<link rel=icon href=/i.png><link rel=icon href=/e.png>".encodeToByteArray()),
                        "https://bank.com/i.png" to bytes("not an image".encodeToByteArray()),
                        "https://bank.com/e.png" to { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.OK) },
                    ),
                )
            assertNull(streamedTooBig.fetcher.fetch("bank.com"))
            assertEquals(4, streamedTooBig.seen.size)
        }

    @Test
    fun reportsAnUnreachableSiteAndSkipsAddressesThatAreNotBanks(): Unit =
        runBlocking {
            val offline =
                BankIconFetcher(HttpClient(MockEngine { throw IOException("offline") }))
            assertFailsWith<IOException> { offline.fetch("bank.com") }
            assertNull(offline.fetch("192.168.1.1"))
            assertNull(BankIconFetcher.connect().fetch("localhost"))
        }

    @Test
    fun acceptsOnlyPublicHostNames() {
        assertEquals("bank.com", bankIconHost(" HTTPS://Bank.com./path "))
        assertEquals("my-bank.co.uk", bankIconHost("my-bank.co.uk#x"))
        listOf("bank", "10.0.0.1", "router.local", "nas.home", "-bank.com", "bank_.com", "a".repeat(64) + ".com", "").forEach {
            assertNull(bankIconHost(it), it)
        }
    }

    @Test
    fun resolvesIconAddressesSafely() {
        val page = Url("https://www.bank.com/en/home.html")
        assertEquals("https://www.bank.com/en/i.png", safeUrl("i.png", page).toString())
        assertEquals("https://www.bank.com/i.png?a=1&b=2", safeUrl("/i.png?a=1&amp;b=2", page).toString())
        assertEquals("https://cdn.bank.com/i.png", safeUrl("//cdn.bank.com/i.png", page).toString())
        assertEquals("https://cdn.bank.com/i.png", safeUrl("https://cdn.bank.com/i.png", page).toString())
        listOf(
            "",
            "data:image/png;base64,AAAA",
            "http://bank.com/i.png",
            "https://bank.com:8443/i.png",
            "https://user@bank.com/i.png",
            "https://[::1]/i.png",
            "https://bad host/i.png",
        ).forEach { assertNull(safeUrl(it, page), it) }
    }

    @Test
    fun recognisesImageFormatsAndRejectsActiveSvg() {
        assertEquals(BankIconFormat.ICO, bankIconFormat(ico))
        assertEquals(BankIconFormat.PNG, bankIconFormat(png))
        assertEquals(BankIconFormat.JPEG, bankIconFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals(BankIconFormat.WEBP, bankIconFormat("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
        assertEquals(BankIconFormat.SVG, bankIconFormat(" <svg xmlns='x'/>".encodeToByteArray()))
        assertEquals(BankIconFormat.SVG, bankIconFormat("<?xml version='1.0'?><svg/>".encodeToByteArray()))
        assertEquals(BankIconFormat.SVG, bankIconFormat("<!-- logo --><svg/>".encodeToByteArray()))
        listOf(
            "<svg><script>alert(1)</script></svg>",
            "<svg><foreignObject/></svg>",
            "<svg><a href='javascript:x'/></svg>",
            "<?xml version='1.0'?><html/>",
            "<html><svg/></html>",
            "RIFF0000WAVE",
        ).forEach { assertNull(bankIconFormat(it.encodeToByteArray()), it) }
        assertNull(bankIconFormat(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(bankIconFormat(byteArrayOf(0, 0, 1)))
    }

    @Test
    fun skipsFormatsTheCallerCannotShow(): Unit =
        runBlocking {
            val svg = "<svg/>".encodeToByteArray()
            val routes: Map<String, MockRequestHandleScope.(HttpRequestData) -> HttpResponseData> =
                mapOf(
                    "https://bank.com/favicon.ico" to bytes(svg),
                    "https://bank.com/" to bytes("<link rel=icon href=/i.png>".encodeToByteArray()),
                    "https://bank.com/i.png" to bytes(png),
                )
            val rasterOnly =
                BankIconFetcher(
                    HttpClient(MockEngine { request -> routes.getValue(request.url.toString())(this, request) }),
                    BankIconFormat.entries.toSet() - BankIconFormat.SVG,
                )
            assertEquals(BankIconFormat.PNG, rasterOnly.fetch("bank.com")!!.format)
            assertEquals(BankIconFormat.SVG, Site(routes).fetcher.fetch("bank.com")!!.format)
        }
}
