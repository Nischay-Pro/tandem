package app.tandemforactual.api.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException

class TrustedCertificatesTest {
    private val home = PresentedCertificate("Budget.Home", "AA:BB")

    @AfterTest
    fun forgetTrust() = TrustedCertificates.restore(null)

    @Test
    fun trustIsPerHostAndKeptAsLines() {
        TrustedCertificates.trust(home)
        TrustedCertificates.trust(PresentedCertificate("budget.home", "CC:DD"))
        assertTrue(TrustedCertificates.trusts("BUDGET.home", "AA:BB"))
        assertFalse(TrustedCertificates.trusts("other.home", "AA:BB"))
        val saved = TrustedCertificates.encode()
        assertEquals("budget.home=AA:BB\nbudget.home=CC:DD", saved)
        TrustedCertificates.forget("budget.home")
        assertFalse(TrustedCertificates.trusts("budget.home", "AA:BB"))
        TrustedCertificates.restore("$saved\nnot a line\n=EE\nhost=\n")
        assertEquals(saved, TrustedCertificates.encode())
        TrustedCertificates.restoreFrom(emptyMap())
        assertEquals("", TrustedCertificates.encode())
        TrustedCertificates.restoreFrom(mapOf(TrustedCertificates.PREFERENCE_KEY to saved))
        assertEquals(saved, TrustedCertificates.encode())
        assertEquals("BA:78:16:BF", certificateFingerprint("abc".encodeToByteArray()).take(11))
    }

    @Test
    fun aRefusedCertificateDuringACallIsReportedAsUntrusted(): Unit =
        runBlocking {
            fun failing(error: Exception): suspend () -> Unit =
                {
                    TrustedCertificates.refused(home)
                    throw error
                }
            assertEquals("certificate_untrusted", assertFailsWith<TandemApiException> { callActual(failing(IOException("tls"))) }.errorCode)
            assertEquals(home, TrustedCertificates.lastRefused)
            assertEquals(
                "certificate_untrusted",
                assertFailsWith<TandemApiException> { callActual(failing(IllegalStateException())) }.errorCode,
            )
            assertEquals("address_unreachable", assertFailsWith<TandemApiException> { callActual { throw IOException("down") } }.errorCode)
            assertEquals("unexpected_error", assertFailsWith<TandemApiException> { callActual { throw IllegalStateException() } }.errorCode)
        }
}
