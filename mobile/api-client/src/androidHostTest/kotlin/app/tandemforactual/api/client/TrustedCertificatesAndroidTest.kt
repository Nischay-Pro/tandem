package app.tandemforactual.api.client

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.Socket
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

class TrustedCertificatesAndroidTest {
    private val held =
        HeldCertificate
            .Builder()
            .commonName("Tandem test")
            .addSubjectAlternativeName("budget.home")
            .build()
    private val chain = arrayOf(held.certificate)
    private val fingerprint = certificateFingerprint(held.certificate.encoded)

    @AfterTest
    fun forgetTrust() = TrustedCertificates.restore(null)

    private fun engine(host: String) = SSLContext.getDefault().createSSLEngine(host, 443)

    @Test
    fun aSelfSignedCertificatePassesOnlyForTheHostItWasTrustedFor() {
        val manager = PinnedCertificateTrustManager(systemTrustManager())
        assertFailsWith<CertificateException> { manager.checkServerTrusted(chain, "RSA", engine("budget.home")) }
        assertEquals(PresentedCertificate("budget.home", fingerprint), TrustedCertificates.lastRefused)
        TrustedCertificates.trust(PresentedCertificate("Budget.Home", fingerprint))
        manager.checkServerTrusted(chain, "RSA", engine("budget.home"))
        assertFailsWith<CertificateException> { manager.checkServerTrusted(chain, "RSA", engine("other.home")) }
        assertFailsWith<CertificateException> { manager.checkServerTrusted(chain, "RSA") }
        assertFailsWith<CertificateException> { manager.checkServerTrusted(chain, "RSA", Socket()) }
        assertFailsWith<CertificateException> { manager.checkClientTrusted(chain, "RSA") }
        assertFailsWith<CertificateException> { manager.checkClientTrusted(chain, "RSA", Socket()) }
        assertFailsWith<CertificateException> { manager.checkClientTrusted(chain, "RSA", engine("budget.home")) }
        assertTrue(manager.acceptedIssuers.isNotEmpty())
    }

    @Test
    fun requestsReachAServerOnceItsCertificateIsTrusted(): Unit =
        runBlocking {
            val server = MockWebServer()
            server.useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(held)
                    .build()
                    .sslSocketFactory(),
            )
            server.enqueue(MockResponse.Builder().body("ok").build())
            server.start()
            val client = platformHttpClient {}
            try {
                val url = server.url("/").toString()
                assertFailsWith<Exception> { client.get(url) }
                val refused = TrustedCertificates.lastRefused!!
                assertEquals(fingerprint, refused.fingerprint)
                TrustedCertificates.trust(refused)
                assertEquals("ok", client.get(url).bodyAsText())
            } finally {
                client.close()
                server.close()
            }
        }
}
