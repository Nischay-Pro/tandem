package app.tandemforactual.ui.core.format

/**
 * One way a bank or payment app words a payment. [pattern] names what it reads: `amount` (required), and any of `type`,
 * `payee`, `account` (last digits), `date` and `ref`. [income] fixes the direction when the wording has no `type`.
 * [examples] are anonymised real messages; tests read each one with its own format.
 */
internal data class PaymentTextFormat(
    val name: String,
    val pattern: Regex,
    val examples: List<String>,
    val income: Boolean? = null,
)

/** What a payment format read from a message. */
internal data class ReadPayment(
    val amountMinor: Long,
    val isIncome: Boolean,
    val payee: String?,
    val accountDigits: String?,
    val dateText: String?,
    val reference: String?,
)

private val INCOME_TYPE = Regex("""credit|receiv|deposit|refund|incoming""", RegexOption.IGNORE_CASE)

private fun format(
    name: String,
    pattern: String,
    vararg examples: String,
    income: Boolean? = null,
) = PaymentTextFormat(name, Regex(pattern, RegexOption.IGNORE_CASE), examples.toList(), income)

private const val MONEY = """(?:[$€£₹]|rs\.?|inr|usd|eur|gbp)?\s?(?<amount>\d[\d,]*(?:\.\d{1,2})?)"""

internal val BUILT_IN_PAYMENT_FORMATS: List<PaymentTextFormat> =
    listOf(
        format(
            "Card charge",
            """(?:a\s+)?(?:charge|purchase|transaction) of\s+$MONEY\s+(?:at|with)\s+(?<payee>.+?)\s+(?:was|has been)\s+""" +
                """(?:made|authori[sz]ed|approved).*?(?:card|account)\s+ending\s+(?:in\s+)?(?<account>\d{4})""" +
                """(?:.*?\bon\s+(?<date>\d{1,2}/\d{1,2}(?:/\d{2,4})?))?""",
            "A charge of \$12.40 at BLUE BOTTLE COFFEE was made on your card ending in 1234 on 11/28/2026.",
            "A purchase of £8.50 at Pret A Manger has been approved on your account ending 5678.",
            income = false,
        ),
        format(
            "Refund",
            """refund of\s+$MONEY\s+from\s+(?<payee>.+?)(?:\s+(?:has|was|is)\b|[.!]|$)""",
            "A refund of €23.99 from Zalando has been issued to your card.",
            income = true,
        ),
        format(
            "Money received",
            """you(?:\x27|’)ve\s+received\s+$MONEY\s+from\s+(?<payee>.+?)(?:[.!]|\s+(?:on|for)\b|$)""",
            "You’ve received \$40.00 from Sam Lee.",
            income = true,
        ),
    )

/** The first format in [formats] that reads [text], or null when none does. */
internal fun readPaymentText(
    text: String,
    formats: List<PaymentTextFormat>,
): ReadPayment? =
    formats.firstNotNullOfOrNull { format ->
        val match = format.pattern.find(text) ?: return@firstNotNullOfOrNull null

        fun group(name: String): String? = runCatching { match.groups[name]?.value?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

        val amount = group("amount")?.let(::sharedAmountHundredths) ?: return@firstNotNullOfOrNull null
        ReadPayment(
            amountMinor = amount,
            isIncome = group("type")?.let(INCOME_TYPE::containsMatchIn) ?: format.income ?: false,
            payee = group("payee")?.trimEnd('.', ',', '-', '*'),
            accountDigits = group("account"),
            dateText = group("date"),
            reference = group("ref"),
        )
    }
