package app.tandemforactual.ui.core.format

/** What Tandem could read from bank text shared into the app, such as a card alert or a receipt line. */
internal data class SharedTransactionText(
    val amountMinor: Long?,
    val isIncome: Boolean,
    val payeeHint: String?,
    val original: String,
    val accountDigits: String? = null,
    val dateText: String? = null,
    val reference: String? = null,
)

private const val CURRENCY_CODES = "USD|EUR|GBP|INR|CAD|AUD|NZD|JPY|CHF|SEK|NOK|DKK|PLN|BRL|MXN|ZAR|SGD|HKD|CNY|KRW"
private const val SHARED_NUMBER = """\d{1,3}(?:[,.'’\s]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""
private val amountPattern =
    Regex("""(?:R\$|CHF|[$€£₹¥₩]|\b(?:$CURRENCY_CODES))\s?($SHARED_NUMBER)""", RegexOption.IGNORE_CASE)
private val trailingCurrencyPattern =
    Regex("""($SHARED_NUMBER)\s?(?:€|zł|kr|$CURRENCY_CODES)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
private val sharedFraction = Regex("""[.,](\d{1,2})$""")
private val payeePattern =
    Regex(
        """\b(?:at|to|from|@)\s+([A-Za-z0-9&'.*\- ]{2,40}?)(?=\s+(?:on|for|using|with|via|card|ending|ref|\d)|[.,;:!\n]|$)""",
        RegexOption.IGNORE_CASE,
    )
private val incomeWords = Regex("""\b(received|credited|deposit|refund|incoming|paid you|sent you)\b""", RegexOption.IGNORE_CASE)

/** A known payment format first, then a general reading of the amount, direction and payee for anything else. */
internal fun parseSharedTransactionText(
    text: String,
    formats: List<PaymentTextFormat> = BUILT_IN_PAYMENT_FORMATS,
): SharedTransactionText? {
    val trimmed = text.trim().take(MAX_SHARED_TEXT)
    if (trimmed.isEmpty()) return null
    readPaymentText(trimmed, formats)?.let { read ->
        return SharedTransactionText(
            read.amountMinor,
            read.isIncome,
            read.payee,
            trimmed,
            read.accountDigits,
            read.dateText,
            read.reference,
        )
    }
    val amountText = (amountPattern.find(trimmed) ?: trailingCurrencyPattern.find(trimmed))?.groupValues?.get(1)
    val amountMinor = amountText?.let(::sharedAmountHundredths)
    val payee =
        payeePattern
            .findAll(trimmed)
            .map { it.groupValues[1].trim().trimEnd('.', '-', '*') }
            .firstOrNull { candidate -> candidate.any(Char::isLetter) && !candidate.equals("card", ignoreCase = true) }
    return SharedTransactionText(amountMinor, incomeWords.containsMatchIn(trimmed), payee, trimmed)
}

/**
 * Reads “1,234.50”, “1.234,50”, “1 234,50” or “1’234.50” as hundredths: a mark followed by one or two digits at the end
 * is the decimal mark and every other mark groups thousands.
 */
internal fun sharedAmountHundredths(text: String): Long? {
    val fraction = sharedFraction.find(text)
    val wholeText = if (fraction != null) text.substring(0, fraction.range.first) else text
    val whole = wholeText.filter(Char::isDigit).toLongOrNull() ?: return null
    val cents = fraction?.let { it.groupValues[1].padEnd(2, '0').toLong() } ?: 0L
    if (whole > (Long.MAX_VALUE - cents) / 100) return null
    return whole * 100 + cents
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

/** The one account whose name carries [digits], such as "Visa 1234" for a message about the card ending 1234. */
internal fun matchAccountByDigits(
    digits: String?,
    accounts: List<Pair<String, String>>,
): Pair<String, String>? {
    val needle = digits?.takeIf { it.length >= 3 } ?: return null
    return accounts.filter { (_, name) -> Regex("""(?<!\d)${Regex.escape(needle)}(?!\d)""").containsMatchIn(name) }.singleOrNull()
}

private fun String.normalizedPayee(): String = lowercase().filter(Char::isLetterOrDigit)

internal const val MAX_SHARED_TEXT = 1_000
