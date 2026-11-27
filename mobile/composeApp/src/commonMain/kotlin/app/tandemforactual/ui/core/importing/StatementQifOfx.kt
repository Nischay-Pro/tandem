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
    val fileDateOrder = statementDateOrderFor(records.map { it['D'].orEmpty() }, dateOrder)
    val transactions =
        records.mapIndexedNotNull { index, fields ->
            val rowNumber = index + 1
            try {
                val date = parseStatementDate(fields['D'].orEmpty(), fileDateOrder)
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
        ?.let(::decodeOfxEntities)
        ?.trim()
        .orEmpty()

private val ofxEntity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#\\d{1,7}|amp|lt|gt|quot|apos|nbsp);")

/** OFX is SGML, so “AT&amp;T” in a payee is AT&T. */
internal fun decodeOfxEntities(value: String): String =
    ofxEntity.replace(value) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::codePointText)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointText)
            else -> namedOfxEntities[name]
        } ?: match.value
    }

private val namedOfxEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

private fun codePointText(codePoint: Int): String? =
    when {
        codePoint !in 1..0x10FFFF || codePoint in 0xD800..0xDFFF -> null
        codePoint < 0x10000 -> codePoint.toChar().toString()
        else -> {
            val offset = codePoint - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
    }
