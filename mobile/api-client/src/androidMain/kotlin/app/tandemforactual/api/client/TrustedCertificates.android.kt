package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

internal actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        engine { config { trustPinnedCertificates() } }
        block()
    }

/** Android's own trust, plus any certificate the user trusted for the host it's presented by. */
internal fun OkHttpClient.Builder.trustPinnedCertificates(): OkHttpClient.Builder {
    val trustManager = PinnedCertificateTrustManager(systemTrustManager())
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
    val platformVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    return sslSocketFactory(context.socketFactory, trustManager).hostnameVerifier { host, session ->
        platformVerifier.verify(host, session) ||
            TrustedCertificates.trusts(host, certificateFingerprint(session.peerCertificates.first().encoded))
    }
}

internal fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/** Checks a server with [system] first; a certificate it refuses passes only when trusted for the host presenting it. */
internal class PinnedCertificateTrustManager(
    private val system: X509TrustManager,
) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket,
    ) = check(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost)

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) = check(chain, authType, engine.peerHost)

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) = check(chain, authType, null)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket,
    ) = system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine,
    ) = system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) = system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun check(
        chain: Array<X509Certificate>,
        authType: String,
        host: String?,
    ) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (refused: CertificateException) {
            val presented = PresentedCertificate(host.orEmpty(), certificateFingerprint(chain.first().encoded))
            if (host != null && TrustedCertificates.trusts(host, presented.fingerprint)) return
            TrustedCertificates.refused(presented)
            throw refused
        }
    }
}
