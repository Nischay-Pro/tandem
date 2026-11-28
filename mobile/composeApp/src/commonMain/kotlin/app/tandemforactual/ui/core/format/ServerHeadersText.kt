package app.tandemforactual.ui.core.format

private val HEADER_NAME = Regex("""[A-Za-z0-9!#$%&'*+.^_`|~-]+""")

/** Headers written one per line as `Name: value`, or the first line that can't be read. Blank lines are skipped. */
internal fun parseServerHeaders(text: String): Result<Map<String, String>> {
    val headers = linkedMapOf<String, String>()
    val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
    for (line in lines) {
        val name = line.substringBefore(':', "").trim()
        if (!HEADER_NAME.matches(name)) {
            return Result.failure(IllegalArgumentException("Write each header as Name: value. “$line” isn’t."))
        }
        headers[name] = line.substringAfter(':').trim()
    }
    return Result.success(headers)
}

/** [headers] written back one per line as `Name: value`, the way [parseServerHeaders] reads them. */
internal fun formatServerHeaders(headers: Map<String, String>): String =
    headers.entries.joinToString("\n") { (name, value) -> "$name: $value" }
