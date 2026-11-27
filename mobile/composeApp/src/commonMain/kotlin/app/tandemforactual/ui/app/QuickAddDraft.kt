package app.tandemforactual.ui.app

/** What a shortcut or automation asked a new transaction to start with, from `?amount=12.50&payee=Corner%20Cafe`. */
internal data class QuickAddDraft(
    val amount: String?,
    val payee: String?,
) {
    /** The amount in minor units; links always write it with a dot and no grouping, whatever the budget's format. */
    fun amountMinor(fractionDigits: Int): Long? {
        val match = AMOUNT.matchEntire(amount?.trim()?.removePrefix("-") ?: return null) ?: return null
        val digits = fractionDigits.coerceIn(0, 6)
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        val kept = fraction.take(digits).padEnd(digits, '0')
        val roundUp = fraction.length > digits && fraction[digits] >= '5'
        return "$whole$kept".toLong() + if (roundUp) 1 else 0
    }

    companion object {
        private val AMOUNT = Regex("""(\d{1,12})(?:\.(\d{1,6}))?""")

        fun fromLink(link: String): QuickAddDraft? {
            val amount = quickAddParameter(link, "amount")
            val payee = quickAddParameter(link, "payee")?.take(100)
            val draft = QuickAddDraft(amount, payee)
            return draft.takeIf { it.amount != null || it.payee != null }
        }
    }
}

/** A query parameter of a link, percent-decoded, or null when it is missing, blank or badly encoded. */
internal fun quickAddParameter(
    link: String,
    name: String,
): String? =
    link
        .substringAfter('?', "")
        .substringBefore('#')
        .split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.let(::percentDecode)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

internal fun percentDecode(value: String): String? {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            val hex = value.substring(index + 1, minOf(index + 3, value.length))
            val byte = if (hex.length == 2 && hex.all(Char::isLetterOrDigit)) hex.toIntOrNull(16) else null
            bytes += (byte ?: return null).toByte()
            index += 3
        } else {
            val end = value.indexOf('%', index).takeIf { it >= 0 } ?: value.length
            bytes += value.substring(index, end).encodeToByteArray().toList()
            index = end
        }
    }
    return bytes.toByteArray().decodeToString()
}
