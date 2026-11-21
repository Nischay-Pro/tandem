package app.tandemforactual.api.client

import io.ktor.http.Url

internal fun parseServerUrl(
    value: String,
    allowInsecurePrivateNetwork: Boolean,
): Url {
    val address = value.trim().trimEnd('/')
    val hasExplicitHost =
        address
            .substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .isNotBlank()
    val url =
        try {
            Url(address)
        } catch (error: Exception) {
            throw TandemApiException("invalid-server-url", cause = error)
        }
    val secure = url.protocol.name == "https"
    val allowedLocalHttp = allowInsecurePrivateNetwork && url.protocol.name == "http" && isLocalHttpHost(url.host)
    if (!hasExplicitHost || url.host.isBlank() || (!secure && !allowedLocalHttp) || url.user != null || url.password != null) {
        throw TandemApiException("invalid-server-url")
    }
    return url
}

fun isLocalHttpHost(value: String): Boolean {
    val host =
        value
            .lowercase()
            .removePrefix("[")
            .removeSuffix("]")
            .removeSuffix(".")
    if (
        host == "localhost" ||
        host == "::1" ||
        host.endsWith(".localhost")
    ) {
        return true
    }
    val octetStrings = host.split('.')
    if (octetStrings.size == 4 && octetStrings.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
        if (octetStrings.any { it.length > 1 && it.startsWith('0') }) return false
        val octets = octetStrings.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (first, second) = octets
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            first == 127 ||
            (first == 169 && second == 254)
    }
    if (!host.contains(':') || '%' in host) return false
    val compression = host.indexOf("::")
    val firstIpv6Segment =
        if (compression >= 0) {
            if (compression != host.lastIndexOf("::")) return false
            val (left, right) = host.split("::", limit = 2)
            val leftGroups = left.split(':').filter(String::isNotEmpty)
            val rightGroups = right.split(':').filter(String::isNotEmpty)
            val groups = leftGroups + rightGroups
            if (groups.isEmpty() || groups.size >= 8 || groups.any { !it.isIpv6Hextet() }) return false
            (leftGroups.firstOrNull() ?: rightGroups.first()).toIntOrNull(16) ?: return false
        } else {
            val groups = host.split(':')
            if (groups.size != 8 || groups.any { !it.isIpv6Hextet() }) return false
            groups.first().toIntOrNull(16) ?: return false
        }
    return firstIpv6Segment in 0xfc00..0xfdff ||
        firstIpv6Segment in 0xfe80..0xfebf
}

private fun String.isIpv6Hextet(): Boolean = length in 1..4 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
