package app.tandemforactual.ui.core.importing

internal data class StatementCsvStructure(
    val delimiter: Char,
    val rows: List<List<String>>,
    val headerIndex: Int?,
    val dataStart: Int,
    val dataEnd: Int = rows.size,
) {
    val header: List<String>
        get() =
            headerIndex?.let(rows::get)
                ?: List(dataRows.maxOfOrNull { it.value.size } ?: 0) { "Column ${it + 1}" }

    val dataRows: List<IndexedValue<List<String>>>
        get() = rows.withIndex().toList().subList(dataStart, dataEnd)
}

internal fun readStatementCsvStructure(content: String): StatementCsvStructure {
    val delimiter = detectCsvDelimiter(content)
    val rows = parseCsvRows(content, delimiter).filter { row -> row.any(String::isNotBlank) }
    if (rows.isEmpty()) throw StatementImportParseException("The CSV has no header row.")
    val transactionRows = rows.indices.filter { looksLikeTransactionRow(rows[it]) }
    if (transactionRows.isEmpty()) return StatementCsvStructure(delimiter, rows, 0, 1)
    val width =
        transactionRows
            .groupingBy { rows[it].size }
            .eachCount()
            .maxBy { it.value }
            .key
    val firstTransaction = transactionRows.first { kotlin.math.abs(rows[it].size - width) <= 1 }
    val headerIndex =
        (firstTransaction - 1 downTo 0).firstOrNull { index ->
            rows[index].none(::looksLikeStatementDate) && rows[index].count(String::isNotBlank) >= 2
        }
    val dataStart = headerIndex?.plus(1) ?: (0..firstTransaction).first { index -> rows[index].any(::looksLikeStatementDate) }
    val footerStart =
        (transactionRows.last() + 1 until rows.size).firstOrNull { index ->
            (index until rows.size).all { rows[it].any(footerLabel::containsMatchIn) }
        } ?: rows.size
    return StatementCsvStructure(delimiter, rows, headerIndex, dataStart, footerStart)
}

private val footerLabel = Regex("(?i)^(?:totals?|sum|closing balance|opening balance|balance|summary|end of statement)\\b")

internal fun looksLikeTransactionRow(row: List<String>): Boolean {
    val dateIndex = row.indexOfFirst(::looksLikeStatementDate)
    return dateIndex >= 0 && row.withIndex().any { (index, cell) -> index != dateIndex && looksLikeStatementAmount(cell) }
}

internal fun looksLikeStatementDate(cell: String): Boolean =
    cell.any(Char::isDigit) &&
        StatementDateOrder.entries.any { order -> runCatching { parseStatementDate(cell, order) }.isSuccess }

internal fun looksLikeStatementAmount(cell: String): Boolean =
    cell.any(Char::isDigit) &&
        !looksLikeStatementDate(cell) &&
        listOf(null, "comma-dot", "dot-comma").any { format -> runCatching { parseMoney(cell, 2, format) }.isSuccess }

private val CSV_DELIMITERS = listOf(',', ';', '\t', '|')

/** The delimiter that splits the most rows into the same number of columns. */
internal fun detectCsvDelimiter(content: String): Char {
    val sample =
        content
            .lineSequence()
            .filter(String::isNotBlank)
            .take(DELIMITER_SAMPLE_LINES)
            .joinToString("\n")
    return CSV_DELIMITERS.maxBy { delimiter ->
        val widths = parseCsvRows(sample, delimiter, strict = false).map { it.size }.filter { it > 1 }
        val modal = widths.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return@maxBy 0
        modal.value * modal.key
    }
}

private const val DELIMITER_SAMPLE_LINES = 60

internal fun parseCsvRows(
    content: String,
    delimiter: Char,
    strict: Boolean = true,
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

            char == '"' -> {
                quoted = !quoted
            }

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

            else -> {
                cell.append(char)
            }
        }
        index++
    }
    row += cell.toString().trim()
    if (row.any(String::isNotBlank)) rows += row.toList()
    if (quoted && strict) throw StatementImportParseException("The CSV contains an unfinished quoted value.")
    return rows
}

internal fun parseCsv(
    content: String,
    dateOrder: StatementDateOrder,
    currencyFractionDigits: Int,
    numberFormat: String?,
    csvMapping: StatementCsvColumnMapping?,
): StatementParseResult {
    val structure = readStatementCsvStructure(content)
    val columns = resolveCsvColumns(structure.header, csvMapping ?: suggestedCsvMapping(structure))
    var skipped = 0
    var firstSkippedReason: String? = null
    val ids = StatementImportIds()
    val dataRows = structure.dataRows
    val fileDateOrder = statementDateOrderFor(dataRows.map { it.value.cell(columns.date) }, dateOrder)
    val transactions =
        dataRows.mapNotNull { (index, row) ->
            val rowNumber = index + 1
            try {
                val date = parseStatementDate(row.cell(columns.date), fileDateOrder)
                val amount = csvRowAmount(row, columns, currencyFractionDigits, numberFormat)
                if (amount == 0L) {
                    skipped++
                    if (firstSkippedReason == null) firstSkippedReason = "Row $rowNumber: Zero-value transactions are skipped."
                    return@mapNotNull null
                }
                val payee = row.cell(columns.payee).ifBlank { "Imported transaction" }.take(200)
                StatementImportDraft(
                    date = date,
                    amountMinor = amount,
                    payeeName = payee,
                    notes = row.cell(columns.notes).take(500).ifBlank { null },
                    importedId = ids.next(date, amount, payee),
                    bankId = row.cell(columns.id).take(200).ifBlank { null },
                    rowNumber = rowNumber,
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

private val CSV_TYPE_COLUMNS = setOf("type", "transactiontype", "debitcredit", "creditdebit", "drcr", "crdr", "creditdebitindicator")

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
    val detectedIdColumn = normalizedColumns.indexOfFirst { it in CSV_REFERENCE_COLUMNS }.takeIf { it >= 0 }
    return CsvColumns(
        date = dateColumn,
        amount = amountColumn,
        debit = debitColumn,
        credit = creditColumn,
        payee = validColumn(mapping.payeeColumn),
        notes = validColumn(mapping.notesColumn),
        id = validColumn(mapping.referenceColumn) ?: detectedIdColumn,
        type = normalizedColumns.indexOfFirst { it in CSV_TYPE_COLUMNS }.takeIf { it >= 0 },
    )
}

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
    return when {
        type.containsAny("debit", "purchase", "withdrawal", "outflow") || type == "dr" || type == "d" -> -kotlin.math.abs(rawAmount)
        type == "cr" || type == "c" -> kotlin.math.abs(rawAmount)
        else -> rawAmount
    }
}
