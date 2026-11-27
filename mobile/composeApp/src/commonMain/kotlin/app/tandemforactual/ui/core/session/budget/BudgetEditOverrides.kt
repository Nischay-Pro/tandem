package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.BudgetReorderStatus
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.categoryGroupIdOverridesFor
import app.tandemforactual.ui.core.session.categoryGroupNameOverridesFor
import app.tandemforactual.ui.core.session.categoryGroupVisibilityOverridesFor
import app.tandemforactual.ui.core.session.categoryNameOverridesFor
import app.tandemforactual.ui.core.session.categoryVisibilityOverridesFor
import app.tandemforactual.ui.core.session.deletedCategoryIdsFor
import app.tandemforactual.ui.core.session.movesFor
import app.tandemforactual.ui.core.session.parseQueuePayload
import app.tandemforactual.ui.core.session.pendingCategoryCreatesFor
import app.tandemforactual.ui.core.session.pendingCategoryGroupCreatesFor
import app.tandemforactual.ui.core.session.requiredAssignments
import app.tandemforactual.ui.core.session.resolveCategoryGroupId
import app.tandemforactual.ui.core.session.statusFor
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun BudgetEditQueue.categoryVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
    budgetReorderQueue.categoryVisibilityOverridesFor(budgetId)

internal fun BudgetEditQueue.categoryGroupNameOverridesFor(budgetId: String): Map<String, String> =
    budgetReorderQueue.categoryGroupNameOverridesFor(budgetId)

internal fun BudgetEditQueue.categoryNameOverridesFor(budgetId: String): Map<String, String> =
    budgetReorderQueue.categoryNameOverridesFor(budgetId)

internal fun BudgetEditQueue.categoryGroupIdOverridesFor(budgetId: String): Map<String, String> =
    budgetReorderQueue.categoryGroupIdOverridesFor(budgetId)

internal fun BudgetEditQueue.categoryGroupVisibilityOverridesFor(budgetId: String): Map<String, Boolean> =
    budgetReorderQueue.categoryGroupVisibilityOverridesFor(budgetId)

internal fun BudgetEditQueue.deletedCategoryIdsFor(budgetId: String): Set<String> = budgetReorderQueue.deletedCategoryIdsFor(budgetId)

internal fun BudgetEditQueue.pendingCategoryGroupCreatesFor(budgetId: String): List<LiveCategoryGroupSummary> =
    budgetReorderQueue.pendingCategoryGroupCreatesFor(budgetId)

internal fun BudgetEditQueue.pendingCategoryCreatesFor(budgetId: String) = budgetReorderQueue.pendingCategoryCreatesFor(budgetId)

internal fun BudgetEditQueue.pendingCategoryBudgetOverrides(
    budgetId: String,
    month: String,
): Map<String, Long> {
    val overrides = mutableMapOf<String, Long>()
    budgetReorderQueue.moves.filter { it.budgetId == budgetId }.forEach { move ->
        val payload = move.payloadJson?.let(::parseQueuePayload) ?: return@forEach
        if (payload.stringValue("month") != month) return@forEach
        when (move.kind) {
            BudgetReorderKind.SET_CATEGORY_AMOUNT -> payload.longValue("amountMinor")?.let { overrides[move.itemId] = it }
            BudgetReorderKind.APPLY_BUDGET_TEMPLATE ->
                payload.requiredAssignments().forEach { (categoryId, amountMinor) -> overrides[categoryId] = amountMinor }
            else -> Unit
        }
    }
    return overrides
}

internal fun BudgetEditQueue.pendingCategoryCarryoverOverrides(
    budgetId: String,
    month: String,
): Map<String, Boolean> =
    budgetReorderQueue.moves
        .filter { move ->
            move.budgetId == budgetId &&
                move.kind == BudgetReorderKind.SET_CATEGORY_CARRYOVER &&
                move.payloadJson?.let(::parseQueuePayload)?.stringValue("month") == month
        }.associate {
            it.itemId to (
                it.payloadJson
                    ?.let(::parseQueuePayload)
                    ?.get("enabled")
                    ?.jsonPrimitive
                    ?.booleanOrNull ?: false
            )
        }

internal fun BudgetEditQueue.accountNameOverridesFor(budgetId: String): Map<String, String> =
    budgetReorderQueue.moves
        .filter { it.budgetId == budgetId && it.kind == BudgetReorderKind.RENAME_ACCOUNT }
        .associate { it.itemId to (it.payloadJson?.let(::parseQueuePayload)?.stringValue("name") ?: it.itemId) }

internal fun BudgetEditQueue.categoryNoteOverridesFor(budgetId: String): Map<String, String> =
    budgetReorderQueue.moves
        .filter { it.budgetId == budgetId && it.kind == BudgetReorderKind.SET_CATEGORY_NOTE }
        .associate { move ->
            val payload = move.payloadJson?.let(::parseQueuePayload)
            move.itemId to payload?.stringValue("note").orEmpty()
        }

internal fun BudgetEditQueue.accountClosedOverridesFor(budgetId: String): Map<String, Boolean> =
    budgetReorderQueue.moves
        .filter { it.budgetId == budgetId && it.kind in setOf(BudgetReorderKind.CLOSE_ACCOUNT, BudgetReorderKind.REOPEN_ACCOUNT) }
        .associate { it.itemId to (it.kind == BudgetReorderKind.CLOSE_ACCOUNT) }

internal fun BudgetEditQueue.accountRecordsWithPendingCreates(
    budgetId: String,
    records: List<BudgetRecordEntity>,
): List<BudgetRecordEntity> {
    val pending =
        budgetReorderQueue.moves
            .filter { it.budgetId == budgetId && it.kind == BudgetReorderKind.CREATE_ACCOUNT }
            .mapNotNull { move ->
                val payload = move.payloadJson?.let(::parseQueuePayload) ?: return@mapNotNull null
                val name = payload.stringValue("name") ?: move.name ?: return@mapNotNull null
                BudgetRecordEntity(
                    budgetId = budgetId,
                    recordType = "account",
                    recordId = move.itemId,
                    remoteJson = null,
                    remoteRevision = null,
                    localJson =
                        buildJsonObject {
                            put("id", move.itemId)
                            put("name", name)
                            put("offbudget", payload["offbudget"]?.jsonPrimitive?.booleanOrNull ?: false)
                            put("closed", false)
                        }.toString(),
                    localDeleted = false,
                    remoteDeleted = false,
                    syncGeneration = "local-account",
                    recordDate = null,
                    accountId = null,
                    categoryId = null,
                    payeeId = null,
                    amountMinor = null,
                    budgetMonth = null,
                    displayName = name,
                )
            }
    val pendingIds = pending.mapTo(mutableSetOf()) { it.recordId }
    return records.filterNot { it.recordId in pendingIds } + pending
}

internal fun BudgetEditQueue.resolveCategoryGroupId(
    budgetId: String,
    groupId: String,
): String = budgetReorderQueue.resolveCategoryGroupId(budgetId, groupId)

internal fun BudgetEditQueue.hasPendingTransactionDependency(budgetId: String): Boolean =
    budgetReorderQueue.moves.any {
        it.budgetId == budgetId &&
            it.kind in setOf(BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.CREATE_PAYEE, BudgetReorderKind.CREATE_ACCOUNT)
    }

internal fun BudgetEditQueue.payeeRecordsWithPendingCreates(
    budgetId: String,
    records: List<BudgetRecordEntity>,
): List<BudgetRecordEntity> {
    val pending =
        budgetReorderQueue.moves
            .filter { it.budgetId == budgetId && it.kind == BudgetReorderKind.CREATE_PAYEE }
            .mapNotNull { move -> move.name?.let { pendingPayeeRecord(budgetId, move.itemId, it) } }
    val pendingIds = pending.mapTo(mutableSetOf()) { it.recordId }
    return records.filterNot { it.recordId in pendingIds } + pending
}

internal fun BudgetEditQueue.pendingPayeeRecord(
    budgetId: String,
    payeeId: String,
    name: String,
): BudgetRecordEntity =
    BudgetRecordEntity(
        budgetId = budgetId,
        recordType = "payee",
        recordId = payeeId,
        remoteJson = null,
        remoteRevision = null,
        localJson =
            buildJsonObject {
                put("id", payeeId)
                put("name", name)
            }.toString(),
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "local-payee",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
        displayName = name,
    )

internal fun BudgetEditQueue.budgetReorderMovesFor(
    budgetId: String,
    scopeId: String,
): List<QueuedBudgetReorder> = budgetReorderQueue.movesFor(budgetId, scopeId)

internal fun BudgetEditQueue.budgetReorderStatus(
    budgetId: String,
    scopeId: String,
): BudgetReorderStatus = budgetReorderQueue.statusFor(budgetId, scopeId)

internal fun BudgetEditQueue.pendingBudgetMutationCount(budgetId: String): Int = budgetReorderQueue.moves.count { it.budgetId == budgetId }

internal fun BudgetEditQueue.pendingBudgetMutationsFor(budgetId: String): List<QueuedBudgetReorder> =
    budgetReorderQueue.moves.filter {
        it.budgetId ==
            budgetId
    }

internal fun BudgetEditQueue.budgetMutationReviewsFor(budgetId: String): List<OutboxOperationEntity> =
    host.pendingWrites.filter {
        it.budgetId == budgetId &&
            it.recordType == "budget-mutation" &&
            it.status in setOf("uncertain", "conflict", "rejected")
    }

internal fun BudgetEditQueue.budgetMutationWaitingForConnection(move: QueuedBudgetReorder): Boolean =
    budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull() == move

internal fun BudgetEditQueue.budgetMutationsWaitingForConnection(budgetId: String): Boolean =
    budgetReorderQueue.waitingForConnection && budgetReorderQueue.moves.firstOrNull()?.budgetId == budgetId
