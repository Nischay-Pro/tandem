package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** A certificate a server presented that this device doesn't trust: its host and SHA-256 fingerprint, to check by eye. */
data class PresentedCertificate(
    val host: String,
    val fingerprint: String,
)

/**
 * Self-signed or private certificates the user chose to trust, each only for its own host and recognised by its
 * SHA-256 fingerprint. Every client in the process uses them, background sync included.
 */
object TrustedCertificates {
    private var byHost: Map<String, Set<String>> = emptyMap()

    /** The last certificate refused, so sign-in can offer to trust it. */
    var lastRefused: PresentedCertificate? = null
        private set

    /** How many certificates have been refused, so a failed call can tell whether its own handshake was the cause. */
    var refusals: Int = 0
        private set

    fun trusts(
        host: String,
        fingerprint: String,
    ): Boolean = fingerprint in byHost[host.lowercase()].orEmpty()

    fun trust(certificate: PresentedCertificate) {
        val host = certificate.host.lowercase()
        byHost = byHost + (host to (byHost[host].orEmpty() + certificate.fingerprint))
    }

    fun forget(host: String) {
        byHost = byHost - host.lowercase()
    }

    internal fun refused(certificate: PresentedCertificate) {
        lastRefused = certificate
        refusals += 1
    }

    /** One `host=fingerprint` line per trusted certificate, the form settings keep them in. */
    fun encode(): String =
        byHost
            .flatMap { (host, fingerprints) -> fingerprints.map { "$host=$it" } }
            .sorted()
            .joinToString("\n")

    /** Replaces what's trusted with [encoded], skipping lines that aren't `host=fingerprint`. */
    fun restore(encoded: String?) {
        byHost =
            encoded
                .orEmpty()
                .lines()
                .mapNotNull { line ->
                    val (host, fingerprint) = line.split('=', limit = 2).map(String::trim).takeIf { it.size == 2 } ?: return@mapNotNull null
                    (host.lowercase() to fingerprint).takeIf { host.isNotEmpty() && fingerprint.isNotEmpty() }
                }.groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
    }
}

/** SHA-256 of a certificate's DER bytes as colon-separated uppercase hex, the way browsers show fingerprints. */
fun certificateFingerprint(der: ByteArray): String =
    sha256(der).joinToString(":") {
        it
            .toUByte()
            .toString(16)
            .padStart(2, '0')
            .uppercase()
    }

/** The platform's HTTP engine, also accepting a certificate in [TrustedCertificates] for its own host. */
internal expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
