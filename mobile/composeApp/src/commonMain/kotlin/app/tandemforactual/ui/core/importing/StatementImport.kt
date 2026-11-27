package app.tandemforactual.ui.core.importing

import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.api.client.StatementImportSummary
import app.tandemforactual.ui.core.format.MIN_TRANSACTION_DATE
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.platform.MAX_STATEMENT_IMPORT_CHARS

internal data class StatementImportDraft(
    val date: String,
    val amountMinor: Long,
    val payeeName: String,
    val notes: String?,
    val importedId: String,
    val bankId: String? = null,
    val rowNumber: Int = 0,
)

internal data class StatementParseResult(
    val transactions: List<StatementImportDraft>,
    val skippedRows: Int,
    val firstSkippedReason: String? = null,
)

internal fun StatementParseResult.toStatementImportItems(): List<StatementImportItem> =
    transactions.map { row ->
        StatementImportItem(
            date = row.date,
            amountMinor = row.amountMinor,
            payeeName = row.payeeName,
            notes = row.notes,
            importedId = row.importedId,
        )
    }

/** Whether Actual already has this row: its own id, or the bank's id an earlier import stored as `imported_id`. */
internal fun StatementImportDraft.isAlreadyIn(preview: StatementImportPreview): Boolean =
    importedId in preview.existingImportedIds || bankId?.let(preview.existingImportedIds::contains) == true

internal fun StatementParseResult.newRows(preview: StatementImportPreview): StatementParseResult =
    copy(transactions = transactions.filterNot { it.isAlreadyIn(preview) })

internal class StatementImportParseException(
    message: String,
) : IllegalArgumentException(message)

internal fun statementImportRowFailure(
    rowNumber: Int,
    error: Throwable,
): String {
    val message =
        (error as? StatementImportParseException)?.message
            ?: "Couldn’t read this transaction. Check its date and amount."
    return "Row $rowNumber: $message"
}

internal fun statementCount(
    count: Int,
    singular: String,
): String = "$count ${if (count == 1) singular else "${singular}s"}"

internal fun formatStatementImportSummary(summary: StatementImportSummary): String {
    val messages =
        buildList {
            if (summary.added > 0) add("Imported ${statementCount(summary.added, "transaction")}.")
            if (summary.updated > 0) add("Updated ${statementCount(summary.updated, "transaction")}.")
            if (summary.errorCount > 0) {
                val verb = if (summary.errorCount == 1) "needs" else "need"
                add("${statementCount(summary.errorCount, "row")} $verb review in Actual.")
            }
        }
    return messages.joinToString(" ").ifBlank { "No new transactions." }
}

@Suppress("UNUSED_PARAMETER")
internal fun parseStatementImport(
    filename: String,
    contents: String,
    dateOrder: StatementDateOrder = StatementDateOrder.MONTH_DAY_YEAR,
    currencyFractionDigits: Int = 2,
    numberFormat: String? = null,
    csvMapping: StatementCsvColumnMapping? = null,
): StatementParseResult {
    if (currencyFractionDigits !in 0..9) {
        throw StatementImportParseException("This budget’s currency format isn’t supported for statement imports.")
    }
    val content = contents.removePrefix("\uFEFF").trim()
    if (content.isBlank()) throw StatementImportParseException("This statement is empty.")
    if (content.length > MAX_STATEMENT_IMPORT_CHARS) throw StatementImportParseException("Choose a statement smaller than 10 MB.")
    val parsed =
        when (detectStatementFormat(content)) {
            StatementFormat.QIF -> parseQif(content, dateOrder, currencyFractionDigits, numberFormat)
            StatementFormat.OFX -> parseOfx(content, currencyFractionDigits, numberFormat)
            StatementFormat.DELIMITED -> parseCsv(content, dateOrder, currencyFractionDigits, numberFormat, csvMapping)
        }
    if (parsed.transactions.isEmpty()) {
        throw StatementImportParseException(parsed.firstSkippedReason ?: "No usable transactions were found in this statement.")
    }
    var rejectedPreMinimumRows = 0
    var firstPreMinimumReason: String? = null
    val transactionsOnOrAfterMinimum =
        parsed.transactions.filter { transaction ->
            if (transaction.date < MIN_TRANSACTION_DATE) {
                rejectedPreMinimumRows++
                if (firstPreMinimumReason == null) {
                    firstPreMinimumReason =
                        "Choose a date on or after ${displayTransactionDate(MIN_TRANSACTION_DATE)}."
                }
                false
            } else {
                true
            }
        }
    if (transactionsOnOrAfterMinimum.isEmpty()) {
        throw StatementImportParseException(firstPreMinimumReason ?: "No transactions are on or after the supported date.")
    }
    if (parsed.transactions.size > MAX_STATEMENT_IMPORT_ROWS) {
        throw StatementImportParseException("Import up to $MAX_STATEMENT_IMPORT_ROWS transactions at a time.")
    }
    return parsed.copy(
        transactions = transactionsOnOrAfterMinimum,
        skippedRows = parsed.skippedRows + rejectedPreMinimumRows,
        firstSkippedReason = parsed.firstSkippedReason ?: firstPreMinimumReason,
    )
}

private const val MAX_STATEMENT_IMPORT_ROWS = 1_000

/** Actual `imported_id`s derived from each row, so the same statement gives the same ids however often it's read. */
internal class StatementImportIds {
    private val occurrences = mutableMapOf<String, Int>()
    private val used = mutableSetOf<String>()

    fun next(
        date: String,
        amount: Long,
        payee: String,
    ): String {
        var generated = stableImportId(date, amount, payee, occurrences)
        while (!used.add(generated)) generated = stableImportId(date, amount, payee, occurrences)
        return generated
    }
}

private fun stableImportId(
    date: String,
    amount: Long,
    payee: String,
    occurrences: MutableMap<String, Int>,
): String {
    val normalizedPayee = payee.trim().lowercase().replace(Regex("\\s+"), " ")
    val identity = "$date|$amount|${normalizedPayee.length}:$normalizedPayee"
    val occurrence = (occurrences[identity] ?: 0) + 1
    occurrences[identity] = occurrence
    var hash = 0xcbf29ce484222325UL
    identity.forEach { character ->
        hash = (hash xor character.code.toULong()) * 0x100000001b3UL
    }
    return "tandem-import-${hash.toString(16).padStart(16, '0')}-$occurrence"
}
