package app.tandemforactual.ui.core.importing

import app.tandemforactual.ui.core.platform.MAX_STATEMENT_IMPORT_CHARS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal enum class StatementDateOrder {
    MONTH_DAY_YEAR,
    DAY_MONTH_YEAR,
}

internal data class StatementCsvColumn(
    val index: Int,
    val name: String,
)

internal data class StatementCsvColumnMapping(
    val dateColumn: Int? = null,
    val amountColumn: Int? = null,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val referenceColumn: Int? = null,
    val payeeColumn: Int? = null,
    val notesColumn: Int? = null,
) {
    val isUsable: Boolean
        get() = dateColumn != null && (amountColumn != null || debitColumn != null || creditColumn != null)

    fun isValidFor(columnCount: Int): Boolean =
        isUsable &&
            listOf(dateColumn, amountColumn, debitColumn, creditColumn, referenceColumn, payeeColumn, notesColumn)
                .filterNotNull()
                .all { it in 0 until columnCount }
}

internal data class StatementCsvLayout(
    val columns: List<StatementCsvColumn>,
    val suggestedMapping: StatementCsvColumnMapping,
)

internal data class StatementCsvMappingPreference(
    val serverUrl: String,
    val budgetId: String,
    val accountId: String,
    val headers: List<String>,
    val dateOrder: StatementDateOrder,
    val columns: StatementCsvColumnMapping,
)

internal fun inspectStatementCsv(contents: String): StatementCsvLayout {
    val content = contents.removePrefix("\uFEFF").trim()
    if (content.isBlank()) throw StatementImportParseException("This statement is empty.")
    if (content.length > MAX_STATEMENT_IMPORT_CHARS) throw StatementImportParseException("Choose a statement smaller than 10 MB.")
    val structure = readStatementCsvStructure(content)
    return StatementCsvLayout(
        columns = structure.header.mapIndexed { index, name -> StatementCsvColumn(index, name.ifBlank { "Column ${index + 1}" }) },
        suggestedMapping = suggestedCsvMapping(structure),
    )
}

internal val CSV_REFERENCE_COLUMNS =
    setOf(
        "fitid",
        "transactionid",
        "transactionreference",
        "transactionref",
        "reference",
        "referenceid",
        "referencenumber",
        "bankreference",
        "traceid",
        "id",
    )

/** Columns named the way banks name them, with the date, amount and payee found from the rows when a name doesn't say. */
internal fun suggestedCsvMapping(structure: StatementCsvStructure): StatementCsvColumnMapping {
    val normalized = if (structure.headerIndex == null) emptyList() else structure.header.map(::normalizeColumn)

    fun column(
        aliases: Collection<String>,
        prefix: String? = null,
    ): Int? =
        aliases.firstNotNullOfOrNull { alias -> normalized.indexOf(alias).takeIf { it >= 0 } }
            ?: prefix?.let { p -> normalized.indexOfFirst { it.startsWith(p) }.takeIf { it >= 0 } }

    val samples = structure.dataRows.take(CONTENT_SAMPLE_ROWS).map { it.value }
    val dateColumn = column(DATE_COLUMNS) ?: columnWhereMost(samples, structure.header.size) { looksLikeStatementDate(it) }
    val debitColumn = column(DEBIT_COLUMNS)
    val creditColumn = column(CREDIT_COLUMNS)
    val amountColumn =
        column(AMOUNT_COLUMNS, prefix = "amount")
            ?: if (debitColumn == null && creditColumn == null) onlyAmountColumn(samples, structure.header.size, dateColumn) else null
    val referenceColumn = column(CSV_REFERENCE_COLUMNS)
    val taken = setOfNotNull(dateColumn, amountColumn, debitColumn, creditColumn, referenceColumn)
    return StatementCsvColumnMapping(
        dateColumn = dateColumn,
        amountColumn = amountColumn,
        debitColumn = debitColumn,
        creditColumn = creditColumn,
        referenceColumn = referenceColumn,
        payeeColumn = column(PAYEE_COLUMNS) ?: longestTextColumn(samples, taken + setOfNotNull(column(NOTES_COLUMNS))),
        notesColumn = column(NOTES_COLUMNS),
    )
}

private const val CONTENT_SAMPLE_ROWS = 50

private val DATE_COLUMNS =
    listOf(
        "date",
        "transactiondate",
        "posteddate",
        "postingdate",
        "postedon",
        "transactiondateposted",
        "bookingdate",
        "valuedate",
        "datum",
        "buchungsdatum",
        "buchungstag",
        "fecha",
    )
private val AMOUNT_COLUMNS = listOf("amount", "transactionamount", "value", "betrag", "bedrag", "importe", "montant")
private val DEBIT_COLUMNS =
    setOf("debit", "debits", "debitamount", "withdrawal", "withdrawals", "outflow", "moneyout", "paidout")
private val CREDIT_COLUMNS =
    setOf("credit", "credits", "creditamount", "deposit", "deposits", "inflow", "moneyin", "paidin")
private val PAYEE_COLUMNS =
    listOf(
        "payee",
        "name",
        "merchant",
        "description",
        "transactiondescription",
        "counterparty",
        "narrative",
        "details",
        "transaction",
        "empfänger",
        "auftraggeberempfänger",
        "begünstigterzahlungspflichtiger",
    )
private val NOTES_COLUMNS = listOf("memo", "notes", "note", "verwendungszweck")

private fun columnWhereMost(
    rows: List<List<String>>,
    width: Int,
    test: (String) -> Boolean,
): Int? =
    (0 until width)
        .map { index -> index to rows.count { test(it.cell(index)) } }
        .filter { (_, count) -> count > 0 && count * 2 >= rows.size }
        .maxByOrNull { it.second }
        ?.first

private fun onlyAmountColumn(
    rows: List<List<String>>,
    width: Int,
    dateColumn: Int?,
): Int? =
    (0 until width)
        .filter { index -> index != dateColumn && rows.isNotEmpty() && rows.all { looksLikeStatementAmount(it.cell(index)) } }
        .singleOrNull()

private fun longestTextColumn(
    rows: List<List<String>>,
    taken: Set<Int>,
): Int? =
    (0 until (rows.maxOfOrNull { it.size } ?: 0))
        .filter { it !in taken }
        .map { index -> index to rows.sumOf { row -> row.cell(index).count(Char::isLetter) } }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

internal fun decodeStatementCsvMappingPreferences(serialized: String): List<StatementCsvMappingPreference> {
    val root = runCatching { Json.parseToJsonElement(serialized) as? JsonArray }.getOrNull() ?: return emptyList()
    return root
        .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val serverUrl = item.stringValue("serverUrl")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val budgetId = item.stringValue("budgetId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val accountId = item.stringValue("accountId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val headers =
                (item["headers"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val columnsObject = item["columns"] as? JsonObject ?: return@mapNotNull null
            val columns =
                StatementCsvColumnMapping(
                    dateColumn = columnsObject.intValue("dateColumn"),
                    amountColumn = columnsObject.intValue("amountColumn"),
                    debitColumn = columnsObject.intValue("debitColumn"),
                    creditColumn = columnsObject.intValue("creditColumn"),
                    referenceColumn = columnsObject.intValue("referenceColumn"),
                    payeeColumn = columnsObject.intValue("payeeColumn"),
                    notesColumn = columnsObject.intValue("notesColumn"),
                )
            if (!columns.isUsable) return@mapNotNull null
            StatementCsvMappingPreference(
                serverUrl = serverUrl,
                budgetId = budgetId,
                accountId = accountId,
                headers = headers,
                dateOrder =
                    StatementDateOrder.entries.firstOrNull { it.name == item.stringValue("dateOrder") }
                        ?: StatementDateOrder.MONTH_DAY_YEAR,
                columns = columns,
            )
        }.takeLast(MAX_SAVED_STATEMENT_CSV_MAPPINGS)
}

internal fun encodeStatementCsvMappingPreferences(preferences: List<StatementCsvMappingPreference>): String =
    buildJsonArray {
        preferences
            .filter {
                it.serverUrl.isNotBlank() &&
                    it.budgetId.isNotBlank() &&
                    it.accountId.isNotBlank() &&
                    it.headers.isNotEmpty() &&
                    it.columns.isUsable
            }.distinctBy { Triple(it.serverUrl, it.budgetId, it.accountId) }
            .takeLast(MAX_SAVED_STATEMENT_CSV_MAPPINGS)
            .forEach { preference ->
                add(
                    buildJsonObject {
                        put("budgetId", preference.budgetId)
                        put("serverUrl", preference.serverUrl)
                        put("accountId", preference.accountId)
                        put("headers", buildJsonArray { preference.headers.forEach { add(JsonPrimitive(it)) } })
                        put("dateOrder", preference.dateOrder.name)
                        put(
                            "columns",
                            buildJsonObject {
                                putNullableInt("dateColumn", preference.columns.dateColumn)
                                putNullableInt("amountColumn", preference.columns.amountColumn)
                                putNullableInt("debitColumn", preference.columns.debitColumn)
                                putNullableInt("creditColumn", preference.columns.creditColumn)
                                putNullableInt("referenceColumn", preference.columns.referenceColumn)
                                putNullableInt("payeeColumn", preference.columns.payeeColumn)
                                putNullableInt("notesColumn", preference.columns.notesColumn)
                            },
                        )
                    },
                )
            }
    }.toString()

internal fun statementCsvMappingPreferenceFor(
    serialized: String,
    serverUrl: String,
    budgetId: String,
    accountId: String,
    headers: List<String>,
): StatementCsvMappingPreference? =
    decodeStatementCsvMappingPreferences(serialized).lastOrNull {
        it.serverUrl == serverUrl && it.budgetId == budgetId && it.accountId == accountId && it.headers == headers
    }

internal fun updateStatementCsvMappingPreference(
    serialized: String,
    preference: StatementCsvMappingPreference,
): String {
    val previous = decodeStatementCsvMappingPreferences(serialized)
    return encodeStatementCsvMappingPreferences(
        previous.filterNot {
            it.serverUrl == preference.serverUrl && it.budgetId == preference.budgetId && it.accountId == preference.accountId
        } + preference,
    )
}

private const val MAX_SAVED_STATEMENT_CSV_MAPPINGS = 100

private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.intValue(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 }

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(
    key: String,
    value: Int?,
) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
