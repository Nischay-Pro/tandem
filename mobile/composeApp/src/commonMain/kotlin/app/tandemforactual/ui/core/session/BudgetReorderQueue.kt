package app.tandemforactual.ui.core.session

import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal const val BUDGET_GROUP_ORDER_SCOPE = "category-groups"

internal fun isRetryableBudgetMutationStatus(httpStatus: Int?): Boolean =
    httpStatus == 408 || httpStatus == 425 || httpStatus == 429 || (httpStatus != null && httpStatus in 500..599)

internal fun shouldKeepBudgetMutationQueued(httpStatus: Int?): Boolean =
    httpStatus == null ||
        isRetryableBudgetMutationStatus(httpStatus)

internal enum class BudgetMutationFailureDisposition {
    OPERATION_IN_PROGRESS,
    OPERATION_UNCERTAIN,
    OPERATION_REJECTED,
    IDEMPOTENCY_KEY_REUSED,
    RESULT_UNAVAILABLE,
    INPUT_REJECTED,

    /**
     * The app stopped while a request was in flight, so this device never saw the server's answer.
     * The server journals maintenance requests by idempotency key, so resending with the same key
     * either applies the change once or returns the earlier outcome.
     */
    SEND_INTERRUPTED,
    REVIEW,
}

/** Local outbox code written when a `sending` entry is found after the app process restarts. */
internal const val SEND_INTERRUPTED_ERROR_CODE = "process-restarted-during-send"

internal fun budgetMutationFailureDisposition(errorCode: String?): BudgetMutationFailureDisposition =
    when (errorCode) {
        SEND_INTERRUPTED_ERROR_CODE -> BudgetMutationFailureDisposition.SEND_INTERRUPTED
        "operation_in_progress" -> BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS
        "operation_uncertain" -> BudgetMutationFailureDisposition.OPERATION_UNCERTAIN
        "operation_rejected" -> BudgetMutationFailureDisposition.OPERATION_REJECTED
        "idempotency_key_reused" -> BudgetMutationFailureDisposition.IDEMPOTENCY_KEY_REUSED
        "operation_result_unavailable" -> BudgetMutationFailureDisposition.RESULT_UNAVAILABLE
        "invalid_request" -> BudgetMutationFailureDisposition.INPUT_REJECTED
        else -> BudgetMutationFailureDisposition.REVIEW
    }

internal fun budgetMutationFailureMessage(errorCode: String?): String =
    when (errorCode) {
        "idempotency_key_reused" -> "Tandem matched this change to a different request. Refresh the budget, then try again."
        "operation_in_progress" -> "Tandem is still processing this change. Wait a moment, then try again."
        SEND_INTERRUPTED_ERROR_CODE ->
            "Tandem closed before Actual confirmed this change. Try again to resend it. Actual won’t apply it twice."
        "operation_uncertain" ->
            "Tandem couldn’t confirm whether Actual saved this change. " +
                "Refresh the budget and check Actual before deciding what to do with it."
        "operation_rejected" -> "Actual rejected this change earlier. Refresh the budget, then try again."
        "operation_result_unavailable" ->
            "Actual saved this change, but Tandem hasn’t refreshed the budget. Sync the budget to update this device."
        "invalid_request" ->
            "Tandem rejected this budget change because one or more details did not pass validation. Refresh the budget and check Actual before trying again."
        else -> "Tandem received an unexpected response. Refresh the budget and check Actual before deciding what to do with this change."
    }

internal fun budgetMutationFailureMessage(
    errorCode: String?,
    kind: BudgetReorderKind?,
    itemName: String?,
): String =
    when {
        errorCode == "invalid_request" && isBudgetOrderMutation(kind) -> {
            val name = itemName?.takeIf(String::isNotBlank) ?: "this item"
            "Tandem rejected the reorder for $name before changing Actual. The saved order is unchanged, and this entry does not include the server’s detailed reason."
        }
        errorCode == "invalid_request" && kind == BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val categoryName = itemName?.takeIf(String::isNotBlank) ?: "this category"
            "Tandem searched only hidden categories, treated $categoryName as missing, and stopped before assigning the amount in Actual."
        }
        else -> budgetMutationFailureMessage(errorCode)
    }

internal fun isBudgetOrderMutation(kind: BudgetReorderKind?): Boolean =
    kind in
        setOf(
            BudgetReorderKind.CATEGORY,
            BudgetReorderKind.CATEGORY_GROUP,
            BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
            BudgetReorderKind.MOVE_ACCOUNT,
        )

internal fun isRejectedBudgetOrderRequest(
    errorCode: String?,
    kind: BudgetReorderKind?,
): Boolean = errorCode == "invalid_request" && isBudgetOrderMutation(kind)

internal fun budgetMutationStatusLabel(status: String): String =
    when (status) {
        "conflict" -> "Needs review"
        "uncertain" -> "Status unknown"
        "rejected" -> "Rejected"
        "queued" -> "Waiting to sync"
        "sending" -> "Syncing"
        else -> "Pending"
    }

internal fun budgetMutationRetryNeedsNewKey(errorCode: String?): Boolean =
    budgetMutationFailureDisposition(errorCode) in
        setOf(
            BudgetMutationFailureDisposition.OPERATION_REJECTED,
            BudgetMutationFailureDisposition.IDEMPOTENCY_KEY_REUSED,
            BudgetMutationFailureDisposition.INPUT_REJECTED,
        )

internal fun budgetMutationCanRetry(errorCode: String?): Boolean =
    budgetMutationFailureDisposition(errorCode) in
        setOf(
            BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS,
            BudgetMutationFailureDisposition.SEND_INTERRUPTED,
            BudgetMutationFailureDisposition.OPERATION_REJECTED,
            BudgetMutationFailureDisposition.IDEMPOTENCY_KEY_REUSED,
            BudgetMutationFailureDisposition.INPUT_REJECTED,
        )

internal fun budgetMutationRequiresActualCheck(errorCode: String?): Boolean =
    budgetMutationFailureDisposition(errorCode) in
        setOf(
            BudgetMutationFailureDisposition.OPERATION_UNCERTAIN,
            BudgetMutationFailureDisposition.REVIEW,
        )

/** Returns null when replay is unsafe or the result must be refreshed first. */
internal fun budgetMutationRequestForRetry(
    request: QueuedBudgetReorder,
    errorCode: String?,
    replacementOperationId: String,
): QueuedBudgetReorder? =
    when (budgetMutationFailureDisposition(errorCode)) {
        BudgetMutationFailureDisposition.OPERATION_IN_PROGRESS,
        BudgetMutationFailureDisposition.SEND_INTERRUPTED,
        -> request
        BudgetMutationFailureDisposition.OPERATION_REJECTED,
        BudgetMutationFailureDisposition.IDEMPOTENCY_KEY_REUSED,
        BudgetMutationFailureDisposition.INPUT_REJECTED,
        -> request.copy(operationId = replacementOperationId)
        BudgetMutationFailureDisposition.OPERATION_UNCERTAIN,
        BudgetMutationFailureDisposition.RESULT_UNAVAILABLE,
        BudgetMutationFailureDisposition.REVIEW,
        -> null
    }

internal fun budgetMutationNeedsRefresh(errorCode: String?): Boolean =
    budgetMutationFailureDisposition(errorCode) == BudgetMutationFailureDisposition.RESULT_UNAVAILABLE

internal fun shouldCompleteBudgetMutationAfterOutcome(
    mutationApplied: Boolean,
    refreshFailedAfterMutation: Boolean,
    resultUnavailable: Boolean,
    resultRefreshSucceeded: Boolean,
): Boolean =
    mutationApplied ||
        refreshFailedAfterMutation ||
        (resultUnavailable && resultRefreshSucceeded)

internal enum class BudgetReorderKind {
    CATEGORY,
    CATEGORY_GROUP,
    HIDE_CATEGORY,
    SHOW_CATEGORY,
    CREATE_CATEGORY_GROUP,
    REFRESH_CATEGORY_GROUP_CREATE,
    RENAME_CATEGORY_GROUP,
    REFRESH_CATEGORY_GROUP_RENAME,
    CREATE_CATEGORY,
    REFRESH_CATEGORY_CREATE,
    RENAME_CATEGORY,
    REFRESH_CATEGORY_RENAME,
    MOVE_CATEGORY_TO_GROUP,
    DELETE_CATEGORY,
    DELETE_CATEGORY_GROUP,
    HIDE_CATEGORY_GROUP,
    SHOW_CATEGORY_GROUP,
    CREATE_PAYEE,
    SET_CATEGORY_AMOUNT,
    SET_CATEGORY_CARRYOVER,
    COPY_BUDGET_MONTH,
    APPLY_BUDGET_TEMPLATE,
    HOLD_BUDGET_NEXT_MONTH,
    RESET_BUDGET_HOLD,
    MOVE_BUDGET_MONEY,
    CREATE_ACCOUNT,
    CLOSE_ACCOUNT,
    RENAME_ACCOUNT,
    REOPEN_ACCOUNT,
    MOVE_ACCOUNT,
    RENAME_PAYEE,
    MERGE_PAYEE,
    DELETE_PAYEE,
    SET_PAYEE_DEFAULT_CATEGORY,
    SET_CATEGORY_NOTE,
    IMPORT_STATEMENT,
    SET_BUDGET_TYPE,

    /** One request that saves every folded reorder draft (see [BudgetReorderQueueState.foldOrderDrafts]). */
    SET_ORDER,
}

internal data class QueuedBudgetReorder(
    val budgetId: String,
    val scopeId: String,
    val kind: BudgetReorderKind,
    val itemId: String,
    val targetId: String?,
    val desiredOrder: List<String>,
    val encryptionPassword: String?,
    /**
     * For a reorder draft: the server order shown before the user started this
     * burst of reordering. The server merges `baseOrder -> desiredOrder` into the
     * live order, so reorders from other devices are kept.
     */
    val baseOrder: List<String> = emptyList(),
    val name: String? = null,
    val isIncome: Boolean = false,
    val parentGroupId: String? = null,
    val refreshOnly: Boolean = false,
    val refreshBeforeSend: Boolean = false,
    val requiresEncryptionPassword: Boolean = false,
    val payloadJson: String? = null,
    val operationId: String = Uuid.random().toString(),
)

internal data class BudgetReorderStatus(
    val pendingCount: Int = 0,
    val message: String? = null,
    val warning: Boolean = false,
    val optimisticOrder: List<String>? = null,
)

internal fun budgetReorderFailureMessage(message: String?): String {
    val detail = message?.trim()?.trimEnd('.', '!', '?')?.takeIf(String::isNotEmpty)
    return if (detail == null) {
        "Couldn’t reorder this item. The original order was restored."
    } else {
        "$detail. The original order was restored."
    }
}

internal fun budgetMutationFailureMessage(
    kind: BudgetReorderKind,
    message: String?,
): String {
    if (kind == BudgetReorderKind.CATEGORY || kind == BudgetReorderKind.CATEGORY_GROUP) {
        return budgetReorderFailureMessage(message)
    }
    val detail = message?.trim()?.trimEnd('.', '!', '?')?.takeIf(String::isNotEmpty)
    val fallback =
        when (kind) {
            BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.SHOW_CATEGORY -> "Couldn’t update this category’s visibility."
            BudgetReorderKind.CREATE_CATEGORY_GROUP -> "Couldn’t create this category group."
            BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME ->
                "Couldn’t rename this category group."
            BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE -> "Couldn’t create this category."
            BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME -> "Couldn’t rename this category."
            BudgetReorderKind.MOVE_CATEGORY_TO_GROUP -> "Couldn’t move this category."
            BudgetReorderKind.DELETE_CATEGORY -> "Couldn’t delete this category."
            BudgetReorderKind.DELETE_CATEGORY_GROUP -> "Couldn’t delete this category group."
            BudgetReorderKind.HIDE_CATEGORY_GROUP, BudgetReorderKind.SHOW_CATEGORY_GROUP ->
                "Couldn’t update this category group’s visibility."
            BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Couldn’t refresh the new category group."
            BudgetReorderKind.CREATE_PAYEE -> "Couldn’t add this payee."
            BudgetReorderKind.SET_CATEGORY_AMOUNT -> "Couldn’t update the assigned amount."
            BudgetReorderKind.SET_CATEGORY_CARRYOVER -> "Couldn’t update overspending rollover."
            BudgetReorderKind.COPY_BUDGET_MONTH -> "Couldn’t copy last month’s assignments."
            BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> "Couldn’t apply this template."
            BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "Couldn’t hold this money for next month."
            BudgetReorderKind.RESET_BUDGET_HOLD -> "Couldn’t reset the next-month hold."
            BudgetReorderKind.MOVE_BUDGET_MONEY -> "Couldn’t move money between categories."
            BudgetReorderKind.CREATE_ACCOUNT -> "Couldn’t add this account."
            BudgetReorderKind.CLOSE_ACCOUNT -> "Couldn’t close this account."
            BudgetReorderKind.RENAME_ACCOUNT -> "Couldn’t rename this account."
            BudgetReorderKind.REOPEN_ACCOUNT -> "Couldn’t reopen this account."
            BudgetReorderKind.MOVE_ACCOUNT -> "Couldn’t reorder this account."
            BudgetReorderKind.RENAME_PAYEE -> "Couldn’t rename this payee."
            BudgetReorderKind.MERGE_PAYEE -> "Couldn’t merge this payee."
            BudgetReorderKind.DELETE_PAYEE -> "Couldn’t delete this payee."
            BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY -> "Couldn’t update this payee’s category."
            BudgetReorderKind.SET_CATEGORY_NOTE -> "Couldn’t save this category’s note."
            BudgetReorderKind.IMPORT_STATEMENT -> "Couldn’t import this statement."
            BudgetReorderKind.SET_BUDGET_TYPE -> "Couldn’t switch the budget type."
            BudgetReorderKind.SET_ORDER -> "Couldn’t save the new order."
        }
    return if (detail == null) fallback else "$detail."
}

internal fun optimisticBudgetOrder(
    itemIds: List<String>,
    queuedMoves: List<QueuedBudgetReorder>,
): List<String> =
    queuedMoves.fold(itemIds) { order, move ->
        val itemIndex = order.indexOf(move.itemId)
        if (itemIndex < 0) {
            order
        } else {
            val withoutItem = order.toMutableList().apply { removeAt(itemIndex) }
            val targetIndex = move.targetId?.let(withoutItem::indexOf)?.takeIf { it >= 0 } ?: withoutItem.size
            withoutItem.apply { add(targetIndex, move.itemId) }
        }
    }

internal fun budgetOrderForScreen(
    serverOrder: List<String>,
    status: BudgetReorderStatus,
    queuedMoves: List<QueuedBudgetReorder>,
): List<String> = status.optimisticOrder ?: optimisticBudgetOrder(serverOrder, queuedMoves)

internal fun shouldShowBudgetCreateAction(
    isLiveBudget: Boolean,
    isReordering: Boolean,
): Boolean = isLiveBudget && !isReordering

internal fun budgetReorderStatusKey(
    budgetId: String,
    scopeId: String,
): String = "$budgetId::$scopeId"

internal fun categoryVisibilityKey(
    budgetId: String,
    categoryId: String,
): String = "$budgetId::$categoryId"

internal fun QueuedBudgetReorder.replaceGroupId(
    budgetId: String,
    previousId: String,
    nextId: String,
): QueuedBudgetReorder =
    if (this.budgetId != budgetId) {
        this
    } else {
        copy(
            itemId = if (itemId == previousId) nextId else itemId,
            targetId = if (targetId == previousId) nextId else targetId,
            parentGroupId = if (parentGroupId == previousId) nextId else parentGroupId,
            desiredOrder = desiredOrder.map { if (it == previousId) nextId else it },
            baseOrder = baseOrder.map { if (it == previousId) nextId else it },
        )
    }

internal fun QueuedBudgetReorder.replaceCategoryId(
    budgetId: String,
    previousId: String,
    nextId: String,
): QueuedBudgetReorder =
    if (this.budgetId != budgetId) {
        this
    } else {
        copy(
            itemId = if (itemId == previousId) nextId else itemId,
            targetId = if (targetId == previousId) nextId else targetId,
            desiredOrder = desiredOrder.map { if (it == previousId) nextId else it },
            baseOrder = baseOrder.map { if (it == previousId) nextId else it },
        )
    }

internal fun Map<String, String>.remapKey(
    oldKey: String,
    newKey: String,
): Map<String, String> = get(oldKey)?.let { value -> this - oldKey + (newKey to value) } ?: this

internal fun Map<String, Boolean>.remapBooleanKey(
    oldKey: String,
    newKey: String,
): Map<String, Boolean> = get(oldKey)?.let { value -> this - oldKey + (newKey to value) } ?: this

internal fun Set<String>.remapId(
    oldId: String,
    newId: String,
): Set<String> = if (oldId in this) (this - oldId) + newId else this

/** Scope suffix for the separately listed hidden categories of a group. */
internal const val HIDDEN_CATEGORY_SCOPE_SUFFIX = "::hidden"

/** Status scope for a batched reorder; the lists it covers are named in its payload. */
internal const val ORDER_BATCH_SCOPE = "order-batch"

internal const val REFRESH_FAILED_MESSAGE =
    "Actual was updated, but Tandem couldn’t refresh the budget. Sync to load the latest data."

/** Reorder kinds that are held on the device and sent together in one request. */
private val batchableOrderKinds =
    setOf(BudgetReorderKind.CATEGORY, BudgetReorderKind.CATEGORY_GROUP, BudgetReorderKind.MOVE_ACCOUNT)

/** A reorder that waits on the device until the next flush. Older moves without a starting order are sent one by one. */
internal fun QueuedBudgetReorder.isOrderDraft(): Boolean = kind in batchableOrderKinds && baseOrder.isNotEmpty()

/** The lists a [BudgetReorderKind.SET_ORDER] entry covers. */
internal fun QueuedBudgetReorder.orderBatchScopes(): List<String> =
    if (kind != BudgetReorderKind.SET_ORDER) {
        emptyList()
    } else {
        payloadJson
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                        .jsonObject
                }.getOrNull()
            }?.get("scopes")
            ?.let { it as? JsonArray }
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
    }

/**
 * Makes `base` and `order` contain the same items. Items that appeared since the
 * user started (for example, a new category) are placed in `base` where they sit
 * in `order`, so the server treats them as not moved. Items that disappeared are dropped.
 */
internal fun normalizeOrderChange(
    base: List<String>,
    order: List<String>,
): Pair<List<String>, List<String>> {
    val orderSet = order.toSet()
    val normalizedBase = base.filter { it in orderSet }.distinct().toMutableList()
    val baseSet = normalizedBase.toMutableSet()
    order.forEachIndexed { index, id ->
        if (id in baseSet) return@forEachIndexed
        val previous = order.subList(0, index).lastOrNull { it in baseSet }
        val insertAt = previous?.let { normalizedBase.indexOf(it) + 1 } ?: 0
        normalizedBase.add(insertAt, id)
        baseSet.add(id)
    }
    return normalizedBase to order.distinct()
}

internal val budgetOrderKinds =
    setOf(
        BudgetReorderKind.CATEGORY,
        BudgetReorderKind.CATEGORY_GROUP,
        BudgetReorderKind.HIDE_CATEGORY,
        BudgetReorderKind.SHOW_CATEGORY,
        BudgetReorderKind.CREATE_CATEGORY_GROUP,
        BudgetReorderKind.CREATE_CATEGORY,
    )

internal fun encodeBudgetReorderQueue(state: BudgetReorderQueueState): String =
    buildJsonObject {
        put("waitingForConnection", state.waitingForConnection)
        put(
            "moves",
            buildJsonArray {
                state.moves.forEach { move ->
                    add(
                        buildJsonObject {
                            put("budgetId", move.budgetId)
                            put("operationId", move.operationId)
                            put("scopeId", move.scopeId)
                            put("kind", move.kind.name)
                            put("itemId", move.itemId)
                            put("targetId", move.targetId?.let(::JsonPrimitive) ?: JsonNull)
                            put("desiredOrder", buildJsonArray { move.desiredOrder.forEach { add(JsonPrimitive(it)) } })
                            put("baseOrder", buildJsonArray { move.baseOrder.forEach { add(JsonPrimitive(it)) } })
                            put("name", move.name?.let(::JsonPrimitive) ?: JsonNull)
                            put("isIncome", move.isIncome)
                            put("parentGroupId", move.parentGroupId?.let(::JsonPrimitive) ?: JsonNull)
                            put("refreshOnly", move.refreshOnly)
                            put("refreshBeforeSend", move.refreshBeforeSend)
                            put("requiresEncryptionPassword", move.requiresEncryptionPassword || move.encryptionPassword != null)
                            put("payloadJson", move.payloadJson?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                }
            },
        )
        put("categoryGroupIdAliases", encodeAliases(state.categoryGroupIdAliases))
        put("categoryIdAliases", encodeAliases(state.categoryIdAliases))
    }.toString()

internal fun encodeBudgetReorderQueueMetadata(state: BudgetReorderQueueState): String =
    buildJsonObject {
        put("waitingForConnection", state.waitingForConnection)
        put("categoryGroupIdAliases", encodeAliases(state.categoryGroupIdAliases))
        put("categoryIdAliases", encodeAliases(state.categoryIdAliases))
        put("operationOrder", buildJsonArray { state.moves.forEach { add(JsonPrimitive(it.operationId)) } })
    }.toString()

internal fun decodeBudgetReorderQueueOperationOrder(encoded: String?): List<String> =
    encoded
        ?.let {
            runCatching {
                (
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                        .jsonObject["operationOrder"] as? JsonArray
                )?.mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
            }.getOrNull()
        }.orEmpty()

internal fun <T> orderBudgetMutationQueueItems(
    items: List<T>,
    operationOrder: List<String>,
    operationId: (T) -> String,
    createdAtEpochMs: (T) -> Long,
): List<T> {
    val sequenceById = operationOrder.withIndex().associate { it.value to it.index }
    return items.sortedWith(
        compareBy<T> { sequenceById[operationId(it)] ?: Int.MAX_VALUE }
            .thenBy(createdAtEpochMs)
            .thenBy(operationId),
    )
}

internal fun decodeBudgetReorderQueue(encoded: String?): BudgetReorderQueueState {
    val body =
        encoded?.let {
            runCatching {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(it)
                    .jsonObject
            }.getOrNull()
        }
            ?: return BudgetReorderQueueState()
    val moves =
        (body["moves"] as? JsonArray)
            ?.mapNotNull { element ->
                val move = element as? JsonObject ?: return@mapNotNull null
                val budgetId = move.queueString("budgetId") ?: return@mapNotNull null
                val scopeId = move.queueString("scopeId") ?: return@mapNotNull null
                val kind =
                    move.queueString("kind")?.let { runCatching { BudgetReorderKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
                val itemId = move.queueString("itemId") ?: return@mapNotNull null
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = scopeId,
                    kind = kind,
                    itemId = itemId,
                    targetId = move.queueString("targetId"),
                    desiredOrder = (move["desiredOrder"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    encryptionPassword = null,
                    baseOrder = (move["baseOrder"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    name = move.queueString("name"),
                    isIncome = move["isIncome"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    parentGroupId = move.queueString("parentGroupId"),
                    refreshOnly = move["refreshOnly"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    refreshBeforeSend = move["refreshBeforeSend"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    requiresEncryptionPassword = move["requiresEncryptionPassword"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                    payloadJson = move.queueString("payloadJson"),
                    operationId = move.queueString("operationId") ?: Uuid.random().toString(),
                )
            }.orEmpty()
    val reconstructed = moves.fold(BudgetReorderQueueState()) { current, move -> current.enqueue(move) }
    return reconstructed.copy(
        categoryGroupIdAliases = decodeAliases(body["categoryGroupIdAliases"]),
        categoryIdAliases = decodeAliases(body["categoryIdAliases"]),
        waitingForConnection = (body["waitingForConnection"] as? JsonPrimitive)?.booleanOrNull ?: false,
    )
}

private fun encodeAliases(aliases: Map<String, String>): JsonArray =
    buildJsonArray {
        aliases.forEach { (localId, serverId) ->
            add(
                buildJsonObject {
                    put("localId", localId)
                    put("serverId", serverId)
                },
            )
        }
    }

private fun decodeAliases(value: kotlinx.serialization.json.JsonElement?): Map<String, String> =
    (value as? JsonArray)
        ?.mapNotNull { element ->
            val alias = element as? JsonObject ?: return@mapNotNull null
            val localId = alias.queueString("localId") ?: return@mapNotNull null
            val serverId = alias.queueString("serverId") ?: return@mapNotNull null
            localId to serverId
        }?.toMap()
        .orEmpty()

private fun JsonObject.queueString(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
