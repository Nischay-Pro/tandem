package app.tandemforactual.ui

/** What Tandem could read from bank text shared into the app, such as a card alert or a receipt line. */
internal data class SharedTransactionText(
    val amountMinor: Long?,
    val isIncome: Boolean,
    val payeeHint: String?,
    val original: String,
)

private val amountPattern =
    Regex("""(?:[$€£₹¥]|USD|EUR|GBP|INR|CAD|AUD)\s?(\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
private val trailingCurrencyPattern =
    Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s?(?:USD|EUR|GBP|INR|CAD|AUD)\b""", RegexOption.IGNORE_CASE)
private val payeePattern =
    Regex(
        """\b(?:at|to|from|@)\s+([A-Za-z0-9&'.*\- ]{2,40}?)(?=\s+(?:on|for|using|with|via|card|ending|ref|\d)|[.,;:!\n]|$)""",
        RegexOption.IGNORE_CASE,
    )
private val incomeWords = Regex("""\b(received|credited|deposit|refund|incoming|paid you|sent you)\b""", RegexOption.IGNORE_CASE)

internal fun parseSharedTransactionText(text: String): SharedTransactionText? {
    val trimmed = text.trim().take(MAX_SHARED_TEXT)
    if (trimmed.isEmpty()) return null
    val amountText = (amountPattern.find(trimmed) ?: trailingCurrencyPattern.find(trimmed))?.groupValues?.get(1)
    val amountMinor =
        amountText
            ?.replace(",", "")
            ?.replace(" ", "")
            ?.toBigDecimalOrNullCompat()
    val payee =
        payeePattern
            .findAll(trimmed)
            .map { it.groupValues[1].trim().trimEnd('.', '-', '*') }
            .firstOrNull { candidate -> candidate.any(Char::isLetter) && !candidate.equals("card", ignoreCase = true) }
    return SharedTransactionText(amountMinor, incomeWords.containsMatchIn(trimmed), payee, trimmed)
}

/** Converts "1234.5" into minor units without floating-point rounding. */
private fun String.toBigDecimalOrNullCompat(): Long? {
    val parts = split('.')
    if (parts.size > 2 || parts[0].isEmpty() || !parts[0].all(Char::isDigit)) return null
    val fraction = parts.getOrNull(1).orEmpty()
    if (fraction.length > 2 || !fraction.all(Char::isDigit)) return null
    return parts[0].toLongOrNull()?.let { it * 100 + fraction.padEnd(2, '0').ifEmpty { "00" }.toLong() }
}

/** Picks the existing payee whose name best matches the hint, ignoring case and punctuation. */
internal fun matchSharedPayee(
    hint: String?,
    payees: List<Pair<String, String>>,
): Pair<String, String>? {
    val needle = hint?.normalizedPayee()?.takeIf { it.length >= 2 } ?: return null
    return payees.firstOrNull { it.second.normalizedPayee() == needle }
        ?: payees
            .filter { (_, name) -> name.normalizedPayee().let { it.length >= 3 && (needle.startsWith(it) || it.startsWith(needle)) } }
            .maxByOrNull { it.second.length }
}

private fun String.normalizedPayee(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MAX_SHARED_TEXT = 1_000
