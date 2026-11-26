package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

enum class BankIconFormat {
    ICO,
    PNG,
    JPEG,
    WEBP,
    SVG,
}

class BankIcon(
    val bytes: ByteArray,
    val format: BankIconFormat,
)

/**
 * Finds a bank's icon on its own website: `/favicon.ico` first, then the icons its home page declares. Only the bank's
 * domain is ever sent, over HTTPS, with every redirect checked, small size limits and short timeouts.
 */
class BankIconFetcher private constructor(
    private val http: HttpClient,
    private val formats: Set<BankIconFormat>,
    @Suppress("UNUSED_PARAMETER") configured: Unit,
) : AutoCloseable {
    constructor(
        http: HttpClient,
        formats: Set<BankIconFormat> = BankIconFormat.entries.toSet(),
    ) : this(http.config { limits() }, formats, Unit)

    override fun close() = http.close()

    /** The icon for [domain], or null when the site has none Tandem can use; throws when the site can't be reached. */
    suspend fun fetch(domain: String): BankIcon? {
        val host = bankIconHost(domain) ?: return null
        val origin = Url("https://$host/")
        iconAt(Url("https://$host/favicon.ico"))?.let { return it }
        val (page, html) = download(origin, MAX_PAGE_BYTES) ?: return null
        return declaredIcons(html.decodeToString(), page)
            .take(MAX_CANDIDATES)
            .firstNotNullOfOrNull { iconAt(it) }
    }

    private suspend fun iconAt(url: Url): BankIcon? {
        val bytes =
            try {
                download(url, MAX_ICON_BYTES)?.second
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return null
        return bankIconFormat(bytes)?.takeIf { it in formats }?.let { BankIcon(bytes, it) }
    }

    /** The final address and body, following up to a few HTTPS redirects within public hosts. */
    private suspend fun download(
        start: Url,
        limit: Int,
    ): Pair<Url, ByteArray>? {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            val outcome = get(url, limit) ?: return null
            when (outcome) {
                is Fetched.Body -> return url to outcome.bytes
                is Fetched.Redirect -> url = safeUrl(outcome.location, url) ?: return null
            }
        }
        return null
    }

    private suspend fun get(
        url: Url,
        limit: Int,
    ): Fetched? =
        http.prepareGet(url).execute { response ->
            val status = response.status
            val location = response.headers[HttpHeaders.Location]
            when {
                status.value in 300..399 && location != null -> Fetched.Redirect(location)
                !status.isSuccess() || status == HttpStatusCode.NoContent -> null
                (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > limit -> null
                else ->
                    response
                        .bodyAsChannel()
                        .readRemaining(limit + 1L)
                        .readByteArray()
                        .takeIf { it.isNotEmpty() && it.size <= limit }
                        ?.let { Fetched.Body(it) }
            }
        }

    private sealed interface Fetched {
        class Body(
            val bytes: ByteArray,
        ) : Fetched

        class Redirect(
            val location: String,
        ) : Fetched
    }

    companion object {
        const val MAX_ICON_BYTES = 200 * 1024
        const val MAX_PAGE_BYTES = 512 * 1024
        private const val MAX_REDIRECTS = 3
        private const val MAX_CANDIDATES = 4
        private const val TIMEOUT_MILLIS = 10_000L

        /** A fetcher with the platform's default HTTP engine, keeping only icons in [formats]. */
        fun connect(formats: Set<BankIconFormat> = BankIconFormat.entries.toSet()) = BankIconFetcher(HttpClient { limits() }, formats, Unit)

        private fun HttpClientConfig<*>.limits() {
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
            }
        }
    }
}

private val HOST_LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
private val PRIVATE_SUFFIXES = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".arpa")

/** The public host name in a SimpleFIN domain or website address, or null for anything else, IP addresses included. */
fun bankIconHost(domain: String): String? {
    val host =
        domain
            .trim()
            .lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .removeSuffix(".")
    val labels = host.split('.')
    return host.takeIf {
        labels.size >= 2 &&
            labels.all { label -> label.length <= 63 && HOST_LABEL.matches(label) } &&
            labels.last().all { it in 'a'..'z' } &&
            PRIVATE_SUFFIXES.none { suffix -> host.endsWith(suffix) }
    }
}

/** Resolves [reference] against [base], keeping it only when it is an HTTPS address on a public host. */
internal fun safeUrl(
    reference: String,
    base: Url,
): Url? {
    val value = reference.trim().replace("&amp;", "&")
    val absolute =
        when {
            value.isEmpty() -> return null
            value.startsWith("//") -> "https:$value"
            value.contains("://") -> value
            value.startsWith("/") -> "https://${base.host}$value"
            value.contains(':') -> return null
            else -> "https://${base.host}${base.encodedPath.substringBeforeLast('/')}/$value"
        }
    val url = runCatching { Url(absolute) }.getOrNull() ?: return null
    return url.takeIf {
        it.protocol == URLProtocol.HTTPS && it.port == 443 && it.user == null && bankIconHost(it.host) == it.host
    }
}

private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)

/** Icon addresses a home page declares, the large touch icons first, then the biggest of the rest. */
internal fun declaredIcons(
    html: String,
    page: Url,
): List<Url> =
    LINK_TAG
        .findAll(html)
        .mapNotNull { tag ->
            val rel = attribute(tag.value, "rel")?.lowercase()?.split(' ')?.filter { it.isNotBlank() } ?: return@mapNotNull null
            val touch = rel.any { it.startsWith("apple-touch-icon") }
            if (!touch && "icon" !in rel) return@mapNotNull null
            val url = attribute(tag.value, "href")?.let { safeUrl(it, page) } ?: return@mapNotNull null
            val size =
                attribute(tag.value, "sizes")
                    ?.split(' ')
                    ?.maxOfOrNull { it.lowercase().substringBefore('x').toIntOrNull() ?: 0 } ?: 0
            Triple(url, touch, size)
        }.sortedWith(compareByDescending<Triple<Url, Boolean, Int>> { it.second }.thenByDescending { it.third })
        .map { it.first }
        .distinct()
        .toList()

private fun attribute(
    tag: String,
    name: String,
): String? {
    val match =
        Regex("\\s$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag) ?: return null
    return match.groupValues
        .drop(2)
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}

/** The image format from the file's first bytes, or null when it isn't an icon Tandem can show safely. */
internal fun bankIconFormat(bytes: ByteArray): BankIconFormat? {
    fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
    return when {
        startsWith(0, 0, 1, 0) -> BankIconFormat.ICO
        startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> BankIconFormat.PNG
        startsWith(0xFF, 0xD8, 0xFF) -> BankIconFormat.JPEG
        bytes.size >= 12 && bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WEBP" -> BankIconFormat.WEBP
        isSafeSvg(bytes) -> BankIconFormat.SVG
        else -> null
    }
}

private fun isSafeSvg(bytes: ByteArray): Boolean {
    val text = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()?.lowercase() ?: return false
    val head = text.take(1024).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    val opens = head.startsWith("<svg") || (head.startsWith("<?xml") || head.startsWith("<!--")) && "<svg" in head
    return opens && "<script" !in text && "<foreignobject" !in text && "javascript:" !in text
}
