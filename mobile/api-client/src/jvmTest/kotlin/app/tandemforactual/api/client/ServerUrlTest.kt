package app.tandemforactual.api.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerUrlTest {
    @Test
    fun allowsHttpOnlyForPrivateAddresses() {
        listOf(
            "192.168.1.20",
            "10.0.0.12",
            "172.31.0.8",
            "127.0.0.1",
            "169.254.1.1",
            "[fd12::1]",
            "fe80::1",
            "localhost",
            "tandem.localhost.",
            "::1",
            "fc00:0000:0000:0000:0000:0000:0000:0001",
            "fe80:0000:0000:0000:0000:0000:0000:0001",
        ).forEach { assertTrue(isLocalHttpHost(it), it) }
        listOf(
            "1.1.1.1",
            "172.32.0.1",
            "192.169.0.1",
            "tandem.example",
            "actual.local",
            "fe80",
            "2001:0db8:0000:0000:0000:0000:0000:0001",
            "fe80::not-an-address",
            "192.168..1",
            "192.168.001.1",
            "300.1.1.1",
            "fe80::1%wlan0",
            "fe80::1::2",
            "1:2:3:4:5:6:7:8:9",
            "fe80::1:2:3:4:5:6:7",
            "::",
            "zzzz::1",
            "1:2:3:4:5:6:7:zzzzz",
        ).forEach { assertFalse(isLocalHttpHost(it), it) }
    }

    @Test
    fun parsesServerAddresses() {
        assertEquals("budget.example", parseServerUrl(" https://budget.example/ ", false).host)
        assertEquals("192.168.1.2", parseServerUrl("http://192.168.1.2:5006", true).host)
        listOf(
            "http://192.168.1.2:5006" to false,
            "http://1.1.1.1" to true,
            "https://" to false,
            "https://u:p@budget.example" to false,
            "budget.example" to false,
            "https://budget.example:99999" to false,
        ).forEach { (address, insecure) ->
            assertEquals("invalid-server-url", assertFailsWith<TandemApiException> { parseServerUrl(address, insecure) }.errorCode)
        }
        val capabilities = ServerCapabilities(0, 99, emptySet(), null)
        assertTrue(capabilities.isLegacy)
        assertTrue(capabilities.clientUpdateRequired)
        assertTrue(ServerCapabilities.LEGACY.isLegacy)
        assertEquals(StatementImportSummary(1, 0, 0), StatementImportSummary(1, 0, 0))
        assertEquals("x", StatementImportItem("2026-11-01", 1, "x", null, "i").payeeName)
    }
}
