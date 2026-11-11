package app.tandemforactual.ui

private const val OPEN_ID_CALLBACK_URI = "app.tandemforactual://signin/complete"

internal data class ActualOpenIdReturn(
    val code: String,
    val state: String,
)

/** Extracts only Tandem's one-use code and pending-login state from its private-use URI. */
internal fun actualOpenIdReturn(uri: String): ActualOpenIdReturn? {
    if (!uri.startsWith("$OPEN_ID_CALLBACK_URI?")) return null
    if ('#' in uri) return null

    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    val values = mutableMapOf<String, String>()
    for (parameter in query.split('&')) {
        val separator = parameter.indexOf('=')
        if (separator <= 0) return null
        val key = parameter.substring(0, separator)
        if (key !in setOf("code", "state") || key in values) return null
        values[key] = parameter.substring(separator + 1)
    }
    if (values.size != 2) return null
    val code = values["code"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) } ?: return null
    val state = values["state"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) } ?: return null
    return ActualOpenIdReturn(code, state)
}
