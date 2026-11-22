package app.tandemforactual.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal const val BUDGET_MUTATION_RECORD_TYPE = "budget-mutation"
internal const val SCHEDULE_ACTION_RECORD_TYPE = "schedule-action"

/** Record types whose sends happen in their own queues rather than inside sync. */
internal val QUEUE_OWNED_RECORD_TYPES = listOf(BUDGET_MUTATION_RECORD_TYPE, SCHEDULE_ACTION_RECORD_TYPE)
internal const val BUDGET_MUTATION_METADATA_RECORD_TYPE = "budget-mutation-metadata"
internal const val BUDGET_MUTATION_METADATA_OPERATION_ID = "tandem-budget-mutation-metadata"
internal const val BUDGET_MUTATION_METADATA_IDEMPOTENCY_KEY = "tandem-budget-mutation-metadata-v1"
internal const val BUDGET_MUTATION_METADATA_BUDGET_ID = "tandem-internal"
internal const val BUDGET_MUTATION_METADATA_RECORD_ID = "category-queue"
internal const val TERMINAL_OUTBOX_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1_000

internal fun scheduleActionExpectedDate(payloadJson: String): String? =
    runCatching {
        Json
            .parseToJsonElement(payloadJson)
            .jsonObject["expectedNextDate"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

internal fun parseDisplayMetadata(bodyJson: String?): BudgetRecordDisplayMetadata? =
    bodyJson?.let { json ->
        runCatching {
            val body = Json.parseToJsonElement(json).jsonObject
            BudgetRecordDisplayMetadata(
                displayName = body.displayString("name")?.takeIf { it.isNotBlank() },
                notes = body.displayString("notes")?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

internal data class TransactionSearchMetadata(
    val cleared: Boolean?,
    val reconciled: Boolean?,
    val approved: Boolean?,
    val matched: Boolean?,
    val transfer: Boolean?,
    val isParent: Boolean,
)

internal fun parseTransactionSearchMetadata(bodyJson: String?): TransactionSearchMetadata? =
    bodyJson?.let { json ->
        runCatching {
            val body = Json.parseToJsonElement(json).jsonObject
            val legacyClearedState =
                body
                    .displayString("cleared")
                    ?.lowercase()
                    ?.takeUnless { it == "true" || it == "false" }
            TransactionSearchMetadata(
                cleared =
                    body.displayBooleanOrNull("cleared")
                        ?: when (legacyClearedState) {
                            "cleared", "reconciled" -> true
                            "uncleared" -> false
                            else -> null
                        },
                reconciled =
                    body.displayBooleanOrNull("reconciled") ?: (legacyClearedState == "reconciled").takeIf { legacyClearedState != null },
                approved = body.displayBooleanOrNull("approved"),
                matched = body.displayBooleanOrNull("matched"),
                transfer =
                    body.displayString("transfer_id") != null ||
                        body.displayString("transferId") != null ||
                        body.displayBooleanOrNull("is_transfer") == true ||
                        body.displayBooleanOrNull("isTransfer") == true,
                isParent = body.displayBooleanOrNull("is_parent") ?: body.displayBooleanOrNull("isParent") ?: false,
            )
        }.getOrNull()
    }

internal const val SNAPSHOT_LOCAL_BATCH_SIZE = 500

internal fun snapshotKey(
    generation: String,
    cursor: String,
): String = "$generation:$cursor"

internal fun JsonObject.displayString(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

private fun JsonObject.displayBooleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let { value ->
        value.booleanOrNull ?: when (value.contentOrNull?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

internal fun JsonObject.displayInt(name: String): Int? = (this[name] as? JsonPrimitive)?.longOrNull?.takeIf { it in 0..6 }?.toInt()

internal fun JsonObject.displayBoolean(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } ?: false

@OptIn(FlowPreview::class)
internal fun <T> Flow<T>.stabilized(): Flow<T> =
    debounce(75)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
