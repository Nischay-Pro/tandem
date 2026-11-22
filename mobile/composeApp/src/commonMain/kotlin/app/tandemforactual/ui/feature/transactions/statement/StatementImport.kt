package app.tandemforactual.ui.feature.transactions.statement

import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.api.client.StatementImportSummary
import app.tandemforactual.ui.core.format.MIN_TRANSACTION_DATE
import app.tandemforactual.ui.core.platform.MAX_STATEMENT_IMPORT_CHARS
import app.tandemforactual.ui.feature.transactions.displayTransactionDate

internal data class StatementImportDraft(
    val date: String,
    val amountMinor: Long,
    val payeeName: String,
    val notes: String?,
    val importedId: String,
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
    val extension = filename.substringAfterLast('.', "").lowercase()
    val parsed =
        when {
            extension == "qif" || content.startsWith("!Type:", ignoreCase = true) ->
                parseQif(content, dateOrder, currencyFractionDigits, numberFormat)
            extension == "ofx" || extension == "qfx" || content.contains("<STMTTRN", ignoreCase = true) ->
                parseOfx(content, currencyFractionDigits, numberFormat)
            extension == "csv" -> parseCsv(content, dateOrder, currencyFractionDigits, numberFormat, csvMapping)
            else -> throw StatementImportParseException("Choose a CSV, QIF, OFX, or QFX statement.")
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

private fun parseCsv(
    content: String,
    dateOrder: StatementDateOrder,
    currencyFractionDigits: Int,
    numberFormat: String?,
    csvMapping: StatementCsvColumnMapping?,
): StatementParseResult {
    val rows = parseCsvRows(content, detectCsvDelimiter(content)).filter { row -> row.any(String::isNotBlank) }
    val header = rows.firstOrNull() ?: throw StatementImportParseException("The CSV has no header row.")
    val columns = resolveCsvColumns(header, csvMapping ?: inspectStatementCsv(content).suggestedMapping)
    var skipped = 0
    var firstSkippedReason: String? = null
    val generatedIds = mutableMapOf<String, Int>()
    val usedIds = mutableSetOf<String>()
    val duplicateSourceIds = duplicateCsvSourceIds(rows, columns.id)
    val transactions =
        rows.drop(1).mapIndexedNotNull { index, row ->
            val rowNumber = index + 2
            try {
                val date = parseStatementDate(row.cell(columns.date), dateOrder)
                val amount = csvRowAmount(row, columns, currencyFractionDigits, numberFormat)
                if (amount == 0L) {
                    skipped++
                    if (firstSkippedReason == null) firstSkippedReason = "Row $rowNumber: Zero-value transactions are skipped."
                    return@mapIndexedNotNull null
                }
                val payee = row.cell(columns.payee).ifBlank { "Imported transaction" }.take(200)
                val sourceId =
                    row
                        .cell(columns.id)
                        .take(200)
                        .takeUnless { it.isBlank() || it in duplicateSourceIds }
                        .orEmpty()
                val importedId = uniqueImportId(sourceId, date, amount, payee, generatedIds, usedIds)
                StatementImportDraft(
                    date = date,
                    amountMinor = amount,
                    payeeName = payee,
                    notes = row.cell(columns.notes).take(500).ifBlank { null },
                    importedId = importedId,
                )
            } catch (error: IllegalArgumentException) {
                skipped++
                if (firstSkippedReason == null) firstSkippedReason = statementImportRowFailure(rowNumber, error)
                null
            }
        }
    return StatementParseResult(transactions, skipped, firstSkippedReason)
}

private class CsvColumns(
    val date: Int,
    val amount: Int?,
    val debit: Int?,
    val credit: Int?,
    val payee: Int?,
    val notes: Int?,
    val id: Int?,
    val type: Int?,
)

private val CSV_ID_COLUMNS =
    setOf(
        "fitid",
        "transactionid",
        "transactionreference",
        "transactionref",
        "reference",
        "referenceid",
        "bankreference",
        "traceid",
        "id",
    )

private fun resolveCsvColumns(
    header: List<String>,
    mapping: StatementCsvColumnMapping,
): CsvColumns {
    fun validColumn(index: Int?): Int? = index?.takeIf { it in header.indices }
    val dateColumn = validColumn(mapping.dateColumn)
    if (dateColumn == null) {
        throw StatementImportParseException("Choose the column that contains each transaction date.")
    }
    val amountColumn = validColumn(mapping.amountColumn)
    val debitColumn = validColumn(mapping.debitColumn)
    val creditColumn = validColumn(mapping.creditColumn)
    if (amountColumn == null && debitColumn == null && creditColumn == null) {
        throw StatementImportParseException("The CSV needs an Amount, Debit, or Credit column.")
    }
    val normalizedColumns = header.map(::normalizeColumn)
    val detectedIdColumn = normalizedColumns.indexOfFirst { it in CSV_ID_COLUMNS }.takeIf { it >= 0 }
    return CsvColumns(
        date = dateColumn,
        amount = amountColumn,
        debit = debitColumn,
        credit = creditColumn,
        payee = validColumn(mapping.payeeColumn),
        notes = validColumn(mapping.notesColumn),
        id = validColumn(mapping.referenceColumn) ?: detectedIdColumn,
        type = normalizedColumns.indexOfFirst { it in setOf("type", "transactiontype") }.takeIf { it >= 0 },
    )
}

private fun duplicateCsvSourceIds(
    rows: List<List<String>>,
    idColumn: Int?,
): Set<String> =
    rows
        .drop(1)
        .mapNotNull { row -> row.cell(idColumn).takeIf(String::isNotBlank)?.let { it.take(200) } }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

private fun csvRowAmount(
    row: List<String>,
    columns: CsvColumns,
    currencyFractionDigits: Int,
    numberFormat: String?,
): Long {
    val debit = parseOptionalMoney(row.cell(columns.debit), currencyFractionDigits, numberFormat)
    val credit = parseOptionalMoney(row.cell(columns.credit), currencyFractionDigits, numberFormat)
    if (debit != null || credit != null) return kotlin.math.abs(credit ?: 0L) - kotlin.math.abs(debit ?: 0L)
    val rawAmount = parseMoney(row.cell(columns.amount), currencyFractionDigits, numberFormat)
    val type = row.cell(columns.type).lowercase()
    return if (type.containsAny("debit", "purchase", "withdrawal", "payment", "outflow")) {
        -kotlin.math.abs(rawAmount)
    } else {
        rawAmount
    }
}

internal fun detectCsvDelimiter(content: String): Char {
    val firstLine = content.substringBefore('\n').substringBefore('\r')
    val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
    var quoted = false
    var index = 0
    while (index < firstLine.length) {
        val char = firstLine[index]
        when {
            char == '"' && quoted && firstLine.getOrNull(index + 1) == '"' -> index++
            char == '"' -> quoted = !quoted
            !quoted && char in counts.keys -> counts[char] = counts.getValue(char) + 1
        }
        index++
    }
    return counts.maxBy { it.value }.key
}

internal fun parseCsvRows(
    content: String,
    delimiter: Char,
): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    while (index < content.length) {
        val char = content[index]
        when {
            char == '"' && quoted && content.getOrNull(index + 1) == '"' -> {
                cell.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == delimiter && !quoted -> {
                row += cell.toString().trim()
                cell.clear()
            }
            (char == '\n' || char == '\r') && !quoted -> {
                row += cell.toString().trim()
                cell.clear()
                rows += row.toList()
                row.clear()
                if (char == '\r' && content.getOrNull(index + 1) == '\n') index++
            }
            else -> cell.append(char)
        }
        index++
    }
    row += cell.toString().trim()
    if (row.any(String::isNotBlank)) rows += row.toList()
    if (quoted) throw StatementImportParseException("The CSV contains an unfinished quoted value.")
    return rows
}

internal fun uniqueImportId(
    preferred: String,
    date: String,
    amount: Long,
    payee: String,
    occurrences: MutableMap<String, Int>,
    usedIds: MutableSet<String>,
): String {
    if (preferred.isNotBlank() && usedIds.add(preferred)) return preferred
    var generated = stableImportId(date, amount, payee, occurrences)
    while (!usedIds.add(generated)) generated = stableImportId(date, amount, payee, occurrences)
    return generated
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
