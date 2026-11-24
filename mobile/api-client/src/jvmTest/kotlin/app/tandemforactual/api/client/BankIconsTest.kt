package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class BankIconsTest {
    private class MemoryStore : BankIconStore {
        val icons = mutableMapOf<String, BankIcon>()
        val missing = mutableMapOf<String, Long>()

        override suspend fun icon(host: String) = icons[host]

        override suspend fun saveIcon(
            host: String,
            icon: BankIcon,
        ) {
            icons[host] = icon
        }

        override suspend fun missingSince(host: String) = missing[host]

        override suspend fun saveMissing(
            host: String,
            at: Long,
        ) {
            missing[host] = at
        }
    }

    private var requests = 0
    private var offline = false
    private var hasIcon = true
    private var clock = 1_000_000_000L
    private val store = MemoryStore()
    private val icons =
        BankIcons(
            store,
            BankIconFetcher(
                HttpClient(
                    MockEngine { request ->
                        requests++
                        if (offline) throw IOException("offline")
                        if (hasIcon && request.url.encodedPath == "/favicon.ico") {
                            respond(byteArrayOf(0, 0, 1, 0))
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                    },
                ),
            ),
        ) { clock }

    @Test
    fun fetchesEachDomainOnceAndServesItFromTheCache(): Unit =
        runBlocking {
            assertNull(icons.cached("bank.com"))
            assertNotNull(icons.icon("https://www.bank.com/login"))
            assertNotNull(icons.icon("www.bank.com"))
            assertNotNull(icons.cached("WWW.BANK.COM"))
            assertEquals(1, requests)
            assertNull(icons.icon("not a domain"))
            assertNull(icons.cached("not a domain"))
            assertEquals(1, requests)
        }

    @Test
    fun waitsAWeekBeforeAskingASiteWithoutAnIconAgain(): Unit =
        runBlocking {
            hasIcon = false
            assertNull(icons.icon("bank.com"))
            val asked = requests
            clock += BankIcons.RETRY_AFTER_MILLIS - 1
            assertNull(icons.icon("bank.com"))
            assertEquals(asked, requests)
            hasIcon = true
            clock += 1
            assertNotNull(icons.icon("bank.com"))
        }

    @Test
    fun triesAnUnreachableSiteAgainSooner(): Unit =
        runBlocking {
            offline = true
            assertNull(icons.icon("bank.com"))
            val asked = requests
            clock += BankIcons.UNREACHABLE_RETRY_MILLIS - 1
            assertNull(icons.icon("bank.com"))
            assertEquals(asked, requests)
            offline = false
            clock += 1
            assertNotNull(icons.icon("bank.com"))
        }
}
