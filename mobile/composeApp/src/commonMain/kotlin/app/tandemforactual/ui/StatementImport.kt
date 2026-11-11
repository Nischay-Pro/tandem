package app.tandemforactual.ui

import app.tandemforactual.api.client.StatementImportSummary

internal enum class StatementDateOrder {
    MONTH_DAY_YEAR,
    DAY_MONTH_YEAR,
}

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
            extension == "ofx" || content.contains("<STMTTRN", ignoreCase = true) ->
                parseOfx(content, currencyFractionDigits, numberFormat)
            extension == "csv" -> parseCsv(content, dateOrder, currencyFractionDigits, numberFormat)
            else -> throw StatementImportParseException("Choose a CSV, QIF, or OFX statement.")
        }
    if (parsed.transactions.isEmpty()) {
        throw StatementImportParseException(parsed.firstSkippedReason ?: "No usable transactions were found in this statement.")
    }
    if (parsed.transactions.size > MAX_STATEMENT_IMPORT_ROWS) {
        throw StatementImportParseException("Import up to $MAX_STATEMENT_IMPORT_ROWS transactions at a time.")
    }
    return parsed
}

private const val MAX_STATEMENT_IMPORT_ROWS = 1_000

private fun parseCsv(
    content: String,
    dateOrder: StatementDateOrder,
    currencyFractionDigits: Int,
    numberFormat: String?,
): StatementParseResult {
    val rows = parseCsvRows(content, detectCsvDelimiter(content)).filter { row -> row.any(String::isNotBlank) }
    val header = rows.firstOrNull() ?: throw StatementImportParseException("The CSV has no header row.")
    val columns = header.map(::normalizeColumn)

    fun column(vararg aliases: String): Int? = columns.indexOfFirst { it in aliases }.takeIf { it >= 0 }
    val dateColumn =
        column("date", "transactiondate", "posteddate", "postingdate", "transactiondateposted")
            ?: throw StatementImportParseException("The CSV needs a Date column.")
    val amountColumn = column("amount", "transactionamount", "value")
    val debitColumn = column("debit", "withdrawal", "outflow", "moneyout")
    val creditColumn = column("credit", "deposit", "inflow", "moneyin")
    if (amountColumn == null && debitColumn == null && creditColumn == null) {
        throw StatementImportParseException("The CSV needs an Amount, Debit, or Credit column.")
    }
    val payeeColumn = column("payee", "name", "description", "merchant", "transaction")
    val notesColumn = column("memo", "notes", "note")
    val idColumn = column("fitid", "transactionid", "reference", "id")
    val typeColumn = column("type", "transactiontype")
    var skipped = 0
    var firstSkippedReason: String? = null
    val generatedIds = mutableMapOf<String, Int>()
    val usedIds = mutableSetOf<String>()
    val sourceIds =
        rows.drop(1).mapNotNull { row -> row.cell(idColumn).takeIf(String::isNotBlank)?.let { it.take(200) } }
    val duplicateSourceIds =
        sourceIds
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    val transactions =
        rows.drop(1).mapIndexedNotNull { index, row ->
            val rowNumber = index + 2
            try {
                val date = parseStatementDate(row.cell(dateColumn), dateOrder)
                val debit = parseOptionalMoney(row.cell(debitColumn), currencyFractionDigits, numberFormat)
                val credit = parseOptionalMoney(row.cell(creditColumn), currencyFractionDigits, numberFormat)
                val amount =
                    if (debit != null || credit != null) {
                        kotlin.math.abs(credit ?: 0L) - kotlin.math.abs(debit ?: 0L)
                    } else {
                        val rawAmount = parseMoney(row.cell(amountColumn), currencyFractionDigits, numberFormat)
                        val type = row.cell(typeColumn).lowercase()
                        if (type.containsAny(
                                "debit",
                                "purchase",
                                "withdrawal",
                                "payment",
                                "outflow",
                            )
                        ) {
                            -kotlin.math.abs(rawAmount)
                        } else {
                            rawAmount
                        }
                    }
                if (amount == 0L) {
                    skipped++
                    if (firstSkippedReason == null) firstSkippedReason = "Row $rowNumber: Zero-value transactions are skipped."
                    return@mapIndexedNotNull null
                }
                val payee = row.cell(payeeColumn).ifBlank { "Imported transaction" }.take(200)
                val sourceId =
                    row
                        .cell(idColumn)
                        .take(200)
                        .takeUnless { it.isBlank() || it in duplicateSourceIds }
                        .orEmpty()
                val importedId = uniqueImportId(sourceId, date, amount, payee, generatedIds, usedIds)
                StatementImportDraft(
                    date = date,
                    amountMinor = amount,
                    payeeName = payee,
                    notes = row.cell(notesColumn).take(500).ifBlank { null },
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

private fun parseQif(
    content: String,
    dateOrder: StatementDateOrder,
    currencyFractionDigits: Int,
    numberFormat: String?,
): StatementParseResult {
    val records = mutableListOf<Map<Char, String>>()
    var current = mutableMapOf<Char, String>()
    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trim().removeSuffix("\r")
        when {
            line == "^" -> {
                if (current.isNotEmpty()) records += current
                current = mutableMapOf()
            }
            line.length > 1 && line[0].isLetter() -> current[line[0]] = line.drop(1).trim()
        }
    }
    if (current.isNotEmpty()) records += current
    var skipped = 0
    var firstSkippedReason: String? = null
    val generatedIds = mutableMapOf<String, Int>()
    val usedIds = mutableSetOf<String>()
    val transactions =
        records.mapIndexedNotNull { index, fields ->
            val rowNumber = index + 1
            try {
                val date = parseStatementDate(fields['D'].orEmpty(), dateOrder)
                val amount = parseMoney(fields['T'].orEmpty(), currencyFractionDigits, numberFormat)
                if (amount == 0L) {
                    skipped++
                    if (firstSkippedReason == null) firstSkippedReason = "Row $rowNumber: Zero-value transactions are skipped."
                    return@mapIndexedNotNull null
                }
                val payee = fields['P'].orEmpty().ifBlank { "Imported transaction" }.take(200)
                StatementImportDraft(
                    date,
                    amount,
                    payee,
                    fields['M']?.take(500),
                    uniqueImportId("", date, amount, payee, generatedIds, usedIds),
                )
            } catch (error: IllegalArgumentException) {
                skipped++
                if (firstSkippedReason == null) firstSkippedReason = statementImportRowFailure(rowNumber, error)
                null
            }
        }
    return StatementParseResult(transactions, skipped, firstSkippedReason)
}

private fun parseOfx(
    content: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
): StatementParseResult {
    val records =
        Regex("(?is)<STMTTRN>(.*?)(?=<STMTTRN>|</BANKTRANLIST>|</STMTRS>|$)")
            .findAll(content)
            .map(MatchResult::value)
            .toList()
    var skipped = 0
    var firstSkippedReason: String? = null
    val generatedIds = mutableMapOf<String, Int>()
    val usedIds = mutableSetOf<String>()
    val sourceIds = records.map { ofxField(it, "FITID").take(200) }.filter(String::isNotBlank)
    val duplicateSourceIds =
        sourceIds
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    val transactions =
        records.mapIndexedNotNull { index, record ->
            val rowNumber = index + 1
            try {
                val date = parseStatementDate(ofxField(record, "DTPOSTED"), StatementDateOrder.MONTH_DAY_YEAR)
                val amount = parseMoney(ofxField(record, "TRNAMT"), currencyFractionDigits, numberFormat)
                if (amount == 0L) {
                    skipped++
                    if (firstSkippedReason == null) firstSkippedReason = "Row $rowNumber: Zero-value transactions are skipped."
                    return@mapIndexedNotNull null
                }
                val payee = ofxField(record, "NAME").ifBlank { ofxField(record, "MEMO") }.ifBlank { "Imported transaction" }.take(200)
                val sourceId = ofxField(record, "FITID").take(200).takeUnless { it.isBlank() || it in duplicateSourceIds }.orEmpty()
                StatementImportDraft(
                    date,
                    amount,
                    payee,
                    ofxField(record, "MEMO").take(500).ifBlank { null },
                    uniqueImportId(sourceId, date, amount, payee, generatedIds, usedIds),
                )
            } catch (error: IllegalArgumentException) {
                skipped++
                if (firstSkippedReason == null) firstSkippedReason = statementImportRowFailure(rowNumber, error)
                null
            }
        }
    return StatementParseResult(transactions, skipped, firstSkippedReason)
}

private fun ofxField(
    record: String,
    name: String,
): String =
    Regex("(?is)<$name>\\s*([^<\\r\\n]+)")
        .find(record)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()

private fun detectCsvDelimiter(content: String): Char {
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

private fun parseCsvRows(
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

private fun normalizeColumn(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

private fun List<String>.cell(index: Int?): String = index?.let { getOrNull(it)?.trim() }.orEmpty()

private fun parseOptionalMoney(
    value: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
): Long? = value.takeIf(String::isNotBlank)?.let { parseMoney(it, currencyFractionDigits, numberFormat) }

private fun parseMoney(
    value: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
): Long {
    val trimmed = value.trim()
    if (trimmed.isBlank()) throw StatementImportParseException("An amount is missing. Enter an amount for this row.")
    val currencyFree = stripCurrencyMarkers(trimmed)
    val parenthesized = currencyFree.startsWith('(') && currencyFree.endsWith(')')
    var unsigned = currencyFree.removeSurrounding("(", ")").trim()
    val trailingMarker =
        Regex("(?i)\\s*(DR|CR)\\.?$")
            .find(unsigned)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
    if (trailingMarker != null) unsigned = unsigned.dropLast(Regex("(?i)\\s*(DR|CR)\\.?$").find(unsigned)?.value?.length ?: 0).trim()
    val trailingMinus = unsigned.endsWith('-') || unsigned.endsWith('−')
    if (trailingMinus) unsigned = unsigned.dropLast(1).trim()
    val leadingMinus = unsigned.startsWith('-') || unsigned.startsWith('−')
    if (leadingMinus) unsigned = unsigned.drop(1).trim()
    val leadingPlus = unsigned.startsWith('+')
    if (leadingPlus) unsigned = unsigned.drop(1).trim()
    if ((trailingMarker == "CR" || leadingPlus) && (parenthesized || trailingMinus || leadingMinus)) {
        throw StatementImportParseException("This amount has conflicting signs. Use a single sign.")
    }
    val negative = parenthesized || trailingMinus || leadingMinus || trailingMarker == "DR"
    if (unsigned.any { !it.isDigit() && it != '.' && it != ',' && !it.isWhitespace() }) throw invalidAmount(trimmed)
    val numeric = unsigned.filter { it.isDigit() || it == '.' || it == ',' }
    if (numeric.isBlank()) throw invalidAmount(value)
    val lastSeparator = maxOf(numeric.lastIndexOf('.'), numeric.lastIndexOf(','))
    val separators = numeric.count { it == '.' || it == ',' }
    val fractionLength = if (lastSeparator >= 0) numeric.length - lastSeparator - 1 else 0
    val decimalSeparator =
        when {
            lastSeparator < 0 -> null
            numeric.contains('.') && numeric.contains(',') -> {
                if (fractionLength !in 1..currencyFractionDigits) throw invalidAmount(trimmed, "Check its decimal places.")
                numeric[lastSeparator]
            }
            separators > 1 -> {
                if (!isGroupedInteger(numeric)) throw invalidAmount(trimmed, "Check its separators.")
                null
            }
            fractionLength in 1..currencyFractionDigits -> {
                if (fractionLength == 3 && separators == 1) {
                    when {
                        numeric[lastSeparator] == groupingSeparatorFor(numberFormat) && isGroupedInteger(numeric) ->
                            throw invalidAmount(trimmed, "This amount is ambiguous. Remove the grouping mark or include the decimal mark.")
                        numeric[lastSeparator] == decimalSeparatorFor(numberFormat) -> numeric[lastSeparator]
                        else -> throw invalidAmount(trimmed, "Check its separators.")
                    }
                } else {
                    numeric[lastSeparator]
                }
            }
            fractionLength == 3 && currencyFractionDigits == 0 -> {
                if (!isGroupedInteger(numeric)) throw invalidAmount(trimmed, "Check its separators.")
                null
            }
            fractionLength == 3 && currencyFractionDigits != 3 -> throw invalidAmount(trimmed, "Check its decimal places.")
            else -> throw invalidAmount(trimmed, "Check its decimal places.")
        }
    val split = decimalSeparator?.let(numeric::lastIndexOf) ?: -1
    val wholeText = if (split >= 0) numeric.substring(0, split) else numeric
    val wholeDigits = wholeText.filter(Char::isDigit)
    val fractionDigits = if (split >= 0) numeric.substring(split + 1).filter(Char::isDigit) else ""
    if (fractionDigits.length > currencyFractionDigits) throw invalidAmount(trimmed, "Check its decimal places.")
    val scale = decimalScale(currencyFractionDigits)
    val normalizedFraction = fractionDigits.padEnd(currencyFractionDigits, '0')
    val whole = wholeDigits.toLongOrNull() ?: throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val fraction = normalizedFraction.toLongOrNull() ?: 0L
    if (whole > (Long.MAX_VALUE - fraction) / scale) throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val minorUnits = (whole * scale) + fraction
    return if (negative) -minorUnits else minorUnits
}

private fun decimalScale(fractionDigits: Int): Long {
    var scale = 1L
    repeat(fractionDigits) { scale *= 10L }
    return scale
}

private fun decimalSeparatorFor(numberFormat: String?): Char? =
    when (numberFormat) {
        "comma-dot", "comma-dot-in", "apostrophe-dot" -> '.'
        "dot-comma", "space-comma" -> ','
        else -> null
    }

private fun groupingSeparatorFor(numberFormat: String?): Char? =
    when (numberFormat) {
        "comma-dot", "comma-dot-in" -> ','
        "dot-comma" -> '.'
        else -> null
    }

private fun stripCurrencyMarkers(value: String): String =
    value
        .replace(Regex("(?i)\\b(?:USD|CAD|EUR|GBP|INR|AUD|NZD|JPY|CHF|SGD|HKD)\\b"), "")
        .filterNot { it in setOf('$', '€', '£', '₹', '¥', '₩') }
        .trim()

private fun isGroupedInteger(value: String): Boolean {
    val separator = value.firstOrNull { it == '.' || it == ',' } ?: return true
    val groups = value.split(separator)
    return groups.size > 1 && groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
}

private fun parseStatementDate(
    value: String,
    order: StatementDateOrder,
): String {
    val dateText = value.trim().substringBefore('T').trim()
    val numericText = dateText.substringBefore(' ')
    val yearFirst = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").matchEntire(numericText)
    if (yearFirst != null) {
        val result = formatIsoDate(yearFirst.groupValues[1].toInt(), yearFirst.groupValues[2].toInt(), yearFirst.groupValues[3].toInt())
        return result.also { validateDate(it, value) }
    }
    if (numericText.length >= 8 && numericText.take(8).all(Char::isDigit)) {
        return "${numericText.take(4)}-${numericText.substring(4, 6)}-${numericText.substring(6, 8)}".also { validateDate(it, value) }
    }
    val monthNamed = parseMonthNamedDate(dateText)
    if (monthNamed != null) return monthNamed.also { validateDate(it, value) }
    val numeric =
        Regex("^(\\d{1,2})[./\\\\-](\\d{1,2})[./\\\\-](\\d{2,4})$").matchEntire(numericText)
            ?: throw invalidDate(value)
    val first = numeric.groupValues[1].toIntOrNull() ?: throw invalidDate(value)
    val second = numeric.groupValues[2].toIntOrNull() ?: throw invalidDate(value)
    var year = numeric.groupValues[3].trimEnd('\'').toIntOrNull() ?: throw invalidDate(value)
    if (year < 100) year += if (year < 50) 2000 else 1900
    val month =
        if (first > 12) {
            second
        } else if (order == StatementDateOrder.MONTH_DAY_YEAR) {
            first
        } else {
            second
        }
    val day =
        if (first > 12) {
            first
        } else if (order == StatementDateOrder.MONTH_DAY_YEAR) {
            second
        } else {
            first
        }
    return formatIsoDate(year, month, day).also { validateDate(it, value) }
}

private fun parseMonthNamedDate(value: String): String? {
    val parts =
        value
            .replace(',', ' ')
            .trim()
            .split(Regex("\\s+"))
            .take(3)
    if (parts.size != 3) return null
    val monthByName =
        mapOf(
            "jan" to 1,
            "january" to 1,
            "feb" to 2,
            "february" to 2,
            "mar" to 3,
            "march" to 3,
            "apr" to 4,
            "april" to 4,
            "may" to 5,
            "jun" to 6,
            "june" to 6,
            "jul" to 7,
            "july" to 7,
            "aug" to 8,
            "august" to 8,
            "sep" to 9,
            "sept" to 9,
            "september" to 9,
            "oct" to 10,
            "october" to 10,
            "nov" to 11,
            "november" to 11,
            "dec" to 12,
            "december" to 12,
        )
    val monthIndex = parts.indexOfFirst { token -> monthByName.containsKey(token.trimEnd('.').lowercase()) }
    if (monthIndex < 0) return null
    val month = monthByName.getValue(parts[monthIndex].trimEnd('.').lowercase())
    val numberParts = parts.filterIndexed { index, _ -> index != monthIndex }
    if (numberParts.size != 2) return null
    val yearToken = numberParts.last()
    var year = yearToken.trimEnd('\'').toIntOrNull() ?: return null
    val day = numberParts.first().trimEnd('\'').toIntOrNull() ?: return null
    if (year < 100) year += if (year < 50) 2000 else 1900
    return formatIsoDate(year, month, day)
}

private fun invalidDate(value: String) =
    StatementImportParseException("Couldn’t read the date “${value.take(48)}”. Check the selected date format.")

private fun invalidAmount(
    value: String,
    guidance: String = "Use a number such as 12.34.",
) = StatementImportParseException("Couldn’t read the amount “${value.take(48)}”. $guidance")

private fun validateDate(
    isoDate: String,
    input: String,
) {
    try {
        validateIsoDate(isoDate)
    } catch (_: StatementImportParseException) {
        throw invalidDate(input)
    }
}

private fun formatIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String = year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')

private fun validateIsoDate(value: String) {
    val parts = value.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val days =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            in 1..12 -> 31
            else -> 0
        }
    if (year !in 1..9999 || day !in 1..days) throw StatementImportParseException("Couldn’t read this date. Check the selected date format.")
}

private fun uniqueImportId(
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

private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)
