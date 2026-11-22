package app.tandemforactual.ui

private const val OPEN_ID_CALLBACK_URI = "app.tandemforactual://signin/complete"

internal data class ActualOpenIdReturn(
    val code: String,
    val state: String,
)

/** Actual returns `…/complete/<state>/openid-cb?token=<session>`; only that exact shape with a pending state is accepted. */
internal fun actualOpenIdReturn(uri: String): ActualOpenIdReturn? {
    if ('#' in uri) return null
    val match = openIdReturnPattern.matchEntire(uri) ?: return null
    return ActualOpenIdReturn(match.groupValues[2], match.groupValues[1])
}

private val openIdReturnPattern =
    Regex("${Regex.escape(OPEN_ID_CALLBACK_URI)}/([A-Za-z0-9_-]{43})/openid-cb\\?token=([A-Za-z0-9_.-]{8,256})")
