package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A transaction an automation app added, kept so repeats are ignored and Change History can name the app. */
data class AutomatedWrite(
    val transactionId: String,
    val operationId: String,
    val externalId: String?,
    val source: String,
    val atEpochMs: Long,
)

const val AUTOMATION_REPEAT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
private const val KEEP_MS = 90L * 24 * 60 * 60 * 1000
private const val MAX_ENTRIES = 500

internal class AutomationLog(
    database: TandemDatabase,
    budgetId: String,
) {
    private val store = AppPreferencesStore(database)
    private val key = "internal:automation-writes-v1:$budgetId"

    suspend fun entries(): List<AutomatedWrite> = decode(store.read()[key])

    /** The earlier write with [externalId] within the repeat window, if there is one. */
    suspend fun repeatOf(
        externalId: String,
        nowEpochMs: Long,
    ): AutomatedWrite? = entries().lastOrNull { it.externalId == externalId && nowEpochMs - it.atEpochMs < AUTOMATION_REPEAT_WINDOW_MS }

    suspend fun add(entry: AutomatedWrite) {
        val kept = entries().filter { entry.atEpochMs - it.atEpochMs < KEEP_MS }.takeLast(MAX_ENTRIES - 1) + entry
        store.putValue(key, encode(kept))
    }

    private fun encode(entries: List<AutomatedWrite>): String =
        JsonArray(
            entries.map {
                buildJsonObject {
                    put("transaction", it.transactionId)
                    put("operation", it.operationId)
                    put("external", it.externalId?.let(::JsonPrimitive) ?: JsonNull)
                    put("source", it.source)
                    put("at", it.atEpochMs)
                }
            },
        ).toString()

    private fun decode(value: String?): List<AutomatedWrite> {
        val array = value?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null

            fun text(name: String) = (item[name] as? JsonPrimitive)?.contentOrNull
            AutomatedWrite(
                transactionId = text("transaction") ?: return@mapNotNull null,
                operationId = text("operation") ?: return@mapNotNull null,
                externalId = text("external"),
                source = text("source") ?: return@mapNotNull null,
                atEpochMs = (item["at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
            )
        }
    }
}

/** Which app added each recent automated transaction in [budgetId], by transaction id. */
suspend fun automatedTransactionSources(
    database: TandemDatabase,
    budgetId: String,
): Map<String, String> = AutomationLog(database, budgetId).entries().associate { it.transactionId to it.source }
