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
    val rows = parseCsvRows(content, detectCsvDelimiter(content)).filter { row -> row.any(String::isNotBlank) }
    val header = rows.firstOrNull() ?: throw StatementImportParseException("The CSV has no header row.")
    val normalized = header.map(::normalizeColumn)

    fun column(vararg aliases: String): Int? = normalized.indexOfFirst { it in aliases }.takeIf { it >= 0 }

    return StatementCsvLayout(
        columns = header.mapIndexed { index, name -> StatementCsvColumn(index, name.ifBlank { "Column ${index + 1}" }) },
        suggestedMapping =
            StatementCsvColumnMapping(
                dateColumn = column("date", "transactiondate", "posteddate", "postingdate", "transactiondateposted"),
                amountColumn = column("amount", "transactionamount", "value"),
                debitColumn = column("debit", "withdrawal", "outflow", "moneyout"),
                creditColumn = column("credit", "deposit", "inflow", "moneyin"),
                referenceColumn =
                    column(
                        "fitid",
                        "transactionid",
                        "transactionreference",
                        "transactionref",
                        "reference",
                        "referenceid",
                        "bankreference",
                        "traceid",
                        "id",
                    ),
                payeeColumn = column("payee", "name", "description", "merchant", "transaction"),
                notesColumn = column("memo", "notes", "note"),
            ),
    )
}

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
