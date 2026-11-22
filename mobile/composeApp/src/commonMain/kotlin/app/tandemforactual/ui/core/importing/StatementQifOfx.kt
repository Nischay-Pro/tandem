package app.tandemforactual.ui.core.importing

internal fun parseQif(
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

internal fun parseOfx(
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
