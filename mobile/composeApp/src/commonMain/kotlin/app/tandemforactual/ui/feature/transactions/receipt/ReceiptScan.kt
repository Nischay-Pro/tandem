package app.tandemforactual.ui.feature.transactions.receipt
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.matchSharedPayee
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.suggestedCategoryForPayee
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.recordName

/** What Tandem read from a receipt photo. */
internal data class ScannedReceipt(
    val amountMinor: Long?,
    val merchant: String?,
    val date: String?,
) {
    val isEmpty get() = amountMinor == null && merchant == null && date == null
}

internal data class ReceiptScanResult(
    val lines: List<String>?,
    val errorMessage: String? = null,
)

internal class ReceiptScanner(
    val canTakePhoto: Boolean,
    val takePhoto: () -> Unit,
    val choosePhoto: () -> Unit,
)

/** Opens the camera or photo library and reads the text of the chosen receipt on the device. */
@Composable
internal expect fun rememberReceiptScanner(
    onStarted: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    onResult: (ReceiptScanResult) -> Unit,
): ReceiptScanner

private val money = Regex("""(?<![\d.,])(\d{1,3}(?:[,. ]\d{3})+|\d+)[.,](\d{2})(?![\d%])""")
private val totalWords =
    listOf(
        Regex(
            """\b(grand\s*total|amount\s*due|total\s*due|balance\s*due|total\s*to\s*pay|amount\s*paid|total\s*amount)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\b(total|totaal|summe|gesamt|montant|importe|totale)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(visa|mastercard|amex|debit|credit|card|cash|paid)\b""", RegexOption.IGNORE_CASE),
    )
private val notTotal =
    Regex("""\b(sub\s*-?\s*total|savings?|saved|discount|tax|vat|gst|tip|change|items?|qty|points)\b""", RegexOption.IGNORE_CASE)
private val notMerchant =
    Regex(
        """\b(receipt|invoice|welcome|thank|tel|phone|fax|www|http|store\s*#|order|table|server|cashier|date|time|copy|tax|vat|gst)\b|@|\.com\b""",
        RegexOption.IGNORE_CASE,
    )
private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val isoDate = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
private val numericDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})\b""")
private val dayMonthName = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s-]+([A-Za-z]{3,9})\.?,?[\s-]+(\d{4}|\d{2})\b""")
private val monthNameDay = Regex("""\b([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""")

/** Finds the total, the merchant and the purchase date in the lines read from a receipt, top to bottom. */
internal fun parseReceiptText(
    lines: List<String>,
    today: String,
): ScannedReceipt {
    val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
    return ScannedReceipt(receiptTotal(cleaned), receiptMerchant(cleaned), receiptDate(cleaned, today))
}

private fun amounts(line: String): List<Long> =
    money
        .findAll(line)
        .mapNotNull { match ->
            val whole = match.groupValues[1].filter(Char::isDigit).toLongOrNull() ?: return@mapNotNull null
            whole * 100 + match.groupValues[2].toLong()
        }.toList()

private fun receiptTotal(lines: List<String>): Long? {
    for (words in totalWords) {
        val found =
            lines.indices.mapNotNull { index ->
                val line = lines[index]
                if (!words.containsMatchIn(line) || notTotal.containsMatchIn(line)) return@mapNotNull null
                amounts(line).lastOrNull() ?: lines.getOrNull(index + 1)?.takeIf { amounts(it).size == 1 }?.let { amounts(it).single() }
            }
        found.filter { it > 0 }.maxOrNull()?.let { return it }
    }
    return lines.flatMap(::amounts).filter { it > 0 }.maxOrNull()
}

private fun receiptMerchant(lines: List<String>): String? =
    lines
        .take(6)
        .firstOrNull { line ->
            val letters = line.count(Char::isLetter)
            letters >= 3 &&
                letters * 2 >= line.count { !it.isWhitespace() } &&
                !notMerchant.containsMatchIn(line) &&
                amounts(line).isEmpty()
        }?.replace(Regex("""\s+"""), " ")
        ?.trim('*', '-', '=', '#', ' ')
        ?.let { name -> if (name.none(Char::isLowerCase)) capitalizeWords(name) else name }
        ?.takeIf { it.length >= 2 }

private fun capitalizeWords(name: String) =
    name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }

private fun receiptDate(
    lines: List<String>,
    today: String,
): String? {
    val candidates = mutableListOf<String>()
    for (line in lines) {
        isoDate.findAll(line).forEach { candidates += listOfNotNull(validDate(it.groupValues[1], it.groupValues[2], it.groupValues[3])) }
        numericDate.findAll(line).forEach { m ->
            val (a, b, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            candidates += listOfNotNull(validDate(year, b, a), validDate(year, a, b)).distinct()
        }
        dayMonthName.findAll(line).forEach { m ->
            val month = monthNumber(m.groupValues[2]) ?: return@forEach
            val year = m.groupValues[3].let { if (it.length == 2) "20$it" else it }
            candidates += listOfNotNull(validDate(year, month.toString(), m.groupValues[1]))
        }
        monthNameDay.findAll(line).forEach { m ->
            val month = monthNumber(m.groupValues[1]) ?: return@forEach
            candidates += listOfNotNull(validDate(m.groupValues[3], month.toString(), m.groupValues[2]))
        }
    }
    return candidates.filter { it <= today }.maxOrNull()
}

private fun monthNumber(name: String): Int? =
    months.indexOf(name.take(3).lowercase()).takeIf { it >= 0 && (name.length == 3 || fullMonth(it).startsWith(name.lowercase())) }?.plus(1)

private fun fullMonth(index: Int) =
    listOf(
        "january",
        "february",
        "march",
        "april",
        "may",
        "june",
        "july",
        "august",
        "september",
        "october",
        "november",
        "december",
    )[index]

private fun validDate(
    year: String,
    month: String,
    day: String,
): String? {
    val y = year.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    if (y !in 2000..2099 || m !in 1..12) return null
    val leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)
    val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1]
    if (d !in 1..days) return null
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

/** Fills the new transaction from what was read, matching the payee to an existing one and suggesting its usual category. */
internal suspend fun fillNewTransaction(
    state: AppState,
    mobileSession: TandemMobileSession,
    amountMinor: Long?,
    payeeHint: String?,
    date: String? = null,
    isIncome: Boolean = false,
    unmatchedNote: String? = payeeHint,
) {
    amountMinor?.let { state.transactionAmount = state.formatAmount(it) }
    if (isIncome) state.transactionType = "Income"
    date?.let { state.transactionDate = it }
    val payees = mobileSession.payeeRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
    val payee = matchSharedPayee(payeeHint, payees)
    if (payee == null) {
        if (unmatchedNote != null && state.transactionNotes.isBlank()) state.transactionNotes = unmatchedNote.take(200)
        return
    }
    state.newTransactionPayeeId = payee.first
    state.transactionPayee = payee.second
    mobileSession.budgetEdits.suggestedCategoryForPayee(state.selectedBudgetId, payee.first)?.let { categoryId ->
        mobileSession.categoryRecords.firstOrNull { it.recordId == categoryId }?.recordName()?.let { name ->
            state.newTransactionCategoryId = categoryId
            state.transactionCategory = name
        }
    }
}
