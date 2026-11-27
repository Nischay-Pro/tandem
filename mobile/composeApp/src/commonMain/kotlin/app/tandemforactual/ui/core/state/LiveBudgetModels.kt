package app.tandemforactual.ui.core.state

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.resolveAccountKind
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal data class LiveCategorySummary(
    val id: String,
    val name: String,
    val assignedMinor: Long,
    val activityMinor: Long,
    val balanceMinor: Long,
    val carryover: Boolean = false,
    val pendingChanges: Boolean = false,
)

internal data class PendingCategorySummary(
    val groupId: String,
    val category: LiveCategorySummary,
)

internal data class LiveBudgetMonthSummary(
    val toBudgetMinor: Long?,
    val incomeAvailableMinor: Long?,
    val totalBudgetedMinor: Long?,
    val totalSpentMinor: Long?,
    val groups: List<LiveCategoryGroupSummary>,
    val heldForNextMonthMinor: Long? = null,
    val heldForNextMonthPending: Boolean = false,
    val totalIncomeMinor: Long? = null,
)

internal fun BudgetRecordEntity.recordBody(): JsonObject? {
    val json = localJson ?: remoteJson ?: return null
    return try {
        kotlinx.serialization.json.Json
            .parseToJsonElement(json)
            .let { it as? JsonObject }
    } catch (_: Exception) {
        null
    }
}

internal fun BudgetRecordEntity.recordName(): String? = displayName?.takeIf { it.isNotBlank() }

internal fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.doubleValue(name: String): Double? =
    (this[name] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.takeIf { it.isFinite() }

internal fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.booleanValue(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

/** Whether an account record is off budget; its transactions carry no category. */
internal fun JsonObject?.isOffBudgetAccount(): Boolean = this?.booleanValue("offbudget") == true || this?.booleanValue("offBudget") == true

internal fun budgetMonthCategoryCarryover(
    remoteJson: String?,
    categoryId: String,
): Boolean? {
    val month =
        remoteJson
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return null
    val groups = month["categoryGroups"] as? JsonArray ?: return null
    for (groupElement in groups) {
        val categories = (groupElement as? JsonObject)?.get("categories") as? JsonArray ?: continue
        for (categoryElement in categories) {
            val category = categoryElement as? JsonObject ?: continue
            if (category.stringValue("id") == categoryId) return category.booleanValue("carryover")
        }
    }
    return null
}

internal fun liveAccountSummaries(
    accountRecords: List<BudgetRecordEntity>,
    accountGroupRecords: List<BudgetRecordEntity> = emptyList(),
    nameOverrides: Map<String, String> = emptyMap(),
    closedOverrides: Map<String, Boolean> = emptyMap(),
    accountKinds: Map<String, AccountKind> = emptyMap(),
): List<LiveAccountSummary> {
    val groupNames = accountGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    return accountRecords.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        val accountGroupId = body.stringValue("account_group_id") ?: body.stringValue("accountGroupId")
        val name = nameOverrides[record.recordId] ?: record.recordName() ?: return@mapNotNull null
        val kind = resolveAccountKind(accountKinds[record.recordId], name, body.stringValue("official_name"), body.stringValue("type"))
        LiveAccountSummary(
            id = record.recordId,
            name = name,
            balanceMinor = body.longValue("balance_current"),
            kind = kind.kind,
            kindChosen = kind.chosen,
            offBudget = body.booleanValue("offbudget") ?: body.booleanValue("offBudget") ?: false,
            closed = closedOverrides[record.recordId] ?: body.booleanValue("closed") ?: false,
            accountGroupId = accountGroupId,
            accountGroupName = accountGroupId?.let(groupNames::get),
            sortOrder = body.doubleValue("sort_order"),
        )
    }
}

internal fun liveAccountGroupLabel(account: LiveAccountSummary): String = if (account.closed) "Closed" else account.kind.groupLabel

internal fun liveBudgetMonthSummary(
    monthRecord: BudgetRecordEntity?,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    categoryVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    categoryGroupNameOverrides: Map<String, String> = emptyMap(),
    categoryNameOverrides: Map<String, String> = emptyMap(),
    categoryGroupIdOverrides: Map<String, String> = emptyMap(),
    categoryGroupVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    deletedCategoryIds: Set<String> = emptySet(),
    pendingCategoryGroups: List<LiveCategoryGroupSummary> = emptyList(),
    pendingCategories: List<PendingCategorySummary> = emptyList(),
    categoryBudgetOverrides: Map<String, Long> = emptyMap(),
    categoryCarryoverOverrides: Map<String, Boolean> = emptyMap(),
    pendingBudgetMutations: List<QueuedBudgetReorder> = emptyList(),
): LiveBudgetMonthSummary? {
    monthRecord ?: return null
    val month = monthRecord.recordBody() ?: return null
    return LiveBudgetMonthBuilder(
        monthRecord,
        month,
        categoryRecords,
        categoryGroupRecords,
        categoryVisibilityOverrides,
        categoryGroupNameOverrides,
        categoryNameOverrides,
        categoryGroupIdOverrides,
        categoryGroupVisibilityOverrides,
        deletedCategoryIds,
        pendingCategoryGroups,
        pendingCategories,
        categoryBudgetOverrides,
        categoryCarryoverOverrides,
        pendingBudgetMutations,
    ).build()
}

/** Builds one month's budget summary, replaying changes still waiting on the device over the synced month. */
private class LiveBudgetMonthBuilder(
    val monthRecord: BudgetRecordEntity,
    val month: JsonObject,
    val categoryRecords: List<BudgetRecordEntity>,
    val categoryGroupRecords: List<BudgetRecordEntity>,
    val categoryVisibilityOverrides: Map<String, Boolean>,
    val categoryGroupNameOverrides: Map<String, String>,
    val categoryNameOverrides: Map<String, String>,
    val categoryGroupIdOverrides: Map<String, String>,
    val categoryGroupVisibilityOverrides: Map<String, Boolean>,
    val deletedCategoryIds: Set<String>,
    val pendingCategoryGroups: List<LiveCategoryGroupSummary>,
    val pendingCategories: List<PendingCategorySummary>,
    val categoryBudgetOverrides: Map<String, Long>,
    val categoryCarryoverOverrides: Map<String, Boolean>,
    val pendingBudgetMutations: List<QueuedBudgetReorder>,
) {
    val categoryNames = categoryRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groupNames = categoryGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val effectiveBudgetOverrides = categoryBudgetOverrides.toMutableMap()
    val effectiveCarryoverOverrides = categoryCarryoverOverrides.toMutableMap()
    val pendingCategoryIds = (categoryBudgetOverrides.keys + categoryCarryoverOverrides.keys).toMutableSet()
    var heldForNextMonthOverride: Long? = null
    var heldForNextMonthPending = false
    var assignedDelta = 0L

    fun build(): LiveBudgetMonthSummary {
        val monthId = month.stringValue("month") ?: monthRecord.recordId
        val matchingPlanMutations = pendingBudgetMutations.filter { it.isForBudgetMonth(monthId) }
        val originalAssignments = originalAssignments()
        if (matchingPlanMutations.isNotEmpty()) replayPlanMutations(matchingPlanMutations, originalAssignments)
        val groups = monthGroups()
        val completeGroups = groups.toMutableList()
        addRecordOnlyCategories(completeGroups)
        val groupsWithPendingCreates =
            completeGroups + pendingCategoryGroups.filterNot { pending -> completeGroups.any { it.id == pending.id } }
        val groupsWithPendingCategories = groupsWithPendingCreates.toMutableList()
        applyCategoryGroupMoves(groupsWithPendingCategories)
        addPendingCategories(groupsWithPendingCategories)
        return LiveBudgetMonthSummary(
            toBudgetMinor =
                month.longValue("toBudget")?.minus(assignedDelta)?.plus(
                    heldForNextMonthOverride?.let { (month.longValue("forNextMonth") ?: 0L) - it } ?: 0L,
                ),
            incomeAvailableMinor = month.longValue("incomeAvailable"),
            totalBudgetedMinor = month.longValue("totalBudgeted")?.plus(assignedDelta),
            totalSpentMinor = month.longValue("totalSpent"),
            groups = groupsWithPendingCategories,
            heldForNextMonthMinor = heldForNextMonthOverride ?: month.longValue("forNextMonth"),
            heldForNextMonthPending = heldForNextMonthPending,
            totalIncomeMinor = month.longValue("totalIncome"),
        )
    }

    fun originalAssignments(): Map<String, Long> =
        (month["categoryGroups"] as? JsonArray)
            ?.flatMap { element ->
                val group = element as? JsonObject ?: return@flatMap emptyList()
                (group["categories"] as? JsonArray)
                    ?.mapNotNull { categoryElement ->
                        val category = categoryElement as? JsonObject ?: return@mapNotNull null
                        val id = category.stringValue("id") ?: return@mapNotNull null
                        id to (category.longValue("budgeted") ?: category.longValue("assigned") ?: 0L)
                    }.orEmpty()
            }.orEmpty()
            .toMap()

    fun replayPlanMutations(
        matchingPlanMutations: List<QueuedBudgetReorder>,
        originalAssignments: Map<String, Long>,
    ) {
        val replayedAssignments = originalAssignments.toMutableMap()
        var replayedHold = month.longValue("forNextMonth")
        matchingPlanMutations.forEach { move ->
            val payload = move.payloadJson?.let(::parseQueuedBudgetPayload) ?: return@forEach
            when (move.kind) {
                BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
                    payload.longValue("amountMinor")?.let { amount ->
                        replayedAssignments[move.itemId] = amount
                        effectiveBudgetOverrides[move.itemId] = amount
                        pendingCategoryIds += move.itemId
                    }
                }
                BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> {
                    payload.assignmentEntries().forEach { (categoryId, amountMinor) ->
                        replayedAssignments[categoryId] = amountMinor
                        effectiveBudgetOverrides[categoryId] = amountMinor
                        pendingCategoryIds += categoryId
                    }
                }
                BudgetReorderKind.MOVE_BUDGET_MONEY -> {
                    val fromCategoryId = payload.stringValue("fromCategoryId") ?: return@forEach
                    val toCategoryId = payload.stringValue("toCategoryId") ?: return@forEach
                    val amount = payload.longValue("amountMinor") ?: return@forEach
                    replayedAssignments[fromCategoryId] = (replayedAssignments[fromCategoryId] ?: 0L) - amount
                    replayedAssignments[toCategoryId] = (replayedAssignments[toCategoryId] ?: 0L) + amount
                    effectiveBudgetOverrides[fromCategoryId] = replayedAssignments.getValue(fromCategoryId)
                    effectiveBudgetOverrides[toCategoryId] = replayedAssignments.getValue(toCategoryId)
                    pendingCategoryIds += fromCategoryId
                    pendingCategoryIds += toCategoryId
                }
                BudgetReorderKind.SET_CATEGORY_CARRYOVER -> {
                    payload.booleanValue("enabled")?.let { enabled ->
                        effectiveCarryoverOverrides[move.itemId] = enabled
                        pendingCategoryIds += move.itemId
                    }
                }
                BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> {
                    replayedHold = (replayedHold ?: 0L) + (payload.longValue("amountMinor") ?: 0L)
                    heldForNextMonthPending = true
                }
                BudgetReorderKind.RESET_BUDGET_HOLD -> {
                    replayedHold = 0L
                    heldForNextMonthPending = true
                }
                else -> Unit
            }
        }
        heldForNextMonthOverride = replayedHold
    }

    fun monthGroups(): List<LiveCategoryGroupSummary> =
        (month["categoryGroups"] as? JsonArray)
            ?.mapNotNull { element ->
                val group = element as? JsonObject ?: return@mapNotNull null
                if (categoryGroupVisibilityOverrides[group.stringValue("id").orEmpty()] ?: group.booleanValue("hidden") == true) {
                    return@mapNotNull null
                }
                val groupId = group.stringValue("id") ?: return@mapNotNull null
                val categories =
                    (group["categories"] as? JsonArray)
                        ?.mapNotNull { categoryElement ->
                            val category = categoryElement as? JsonObject ?: return@mapNotNull null
                            val categoryId = category.stringValue("id") ?: return@mapNotNull null
                            if (categoryId in deletedCategoryIds) return@mapNotNull null
                            if (categoryGroupIdOverrides[categoryId]?.let { it != groupId } == true) return@mapNotNull null
                            val isHidden = categoryVisibilityOverrides[categoryId] ?: category.booleanValue("hidden") ?: false
                            if (isHidden || category.booleanValue("deleted") == true) return@mapNotNull null
                            val originalAssigned = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0
                            val assigned = effectiveBudgetOverrides[categoryId] ?: originalAssigned
                            assignedDelta += assigned - originalAssigned
                            val activity = category.longValue("activity") ?: category.longValue("spent") ?: 0
                            val originalBalance = category.longValue("balance") ?: (originalAssigned + activity)
                            LiveCategorySummary(
                                id = categoryId,
                                name =
                                    categoryNameOverrides[categoryId] ?: categoryNames[categoryId] ?: category.stringValue("name")
                                        ?: categoryId,
                                assignedMinor = assigned,
                                activityMinor = activity,
                                balanceMinor = originalBalance + (assigned - originalAssigned),
                                carryover = effectiveCarryoverOverrides[categoryId] ?: category.booleanValue("carryover") ?: false,
                                pendingChanges = categoryId in pendingCategoryIds,
                            )
                        }.orEmpty()
                LiveCategoryGroupSummary(
                    id = groupId,
                    name = categoryGroupNameOverrides[groupId] ?: groupNames[groupId] ?: group.stringValue("name") ?: groupId,
                    categories = categories,
                    isIncome =
                        group.booleanValue("is_income")
                            ?: group.booleanValue("isIncome")
                            ?: categoryGroupRecords
                                .firstOrNull { it.recordId == groupId }
                                ?.recordBody()
                                ?.booleanValue("is_income")
                            ?: false,
                )
            }.orEmpty()

    fun addRecordOnlyCategories(completeGroups: MutableList<LiveCategoryGroupSummary>) {
        categoryRecords.forEach { record ->
            if (record.recordId in deletedCategoryIds) return@forEach
            val categoryBody = record.recordBody() ?: return@forEach
            val isHidden = categoryVisibilityOverrides[record.recordId] ?: categoryBody.booleanValue("hidden") ?: false
            if (isHidden || categoryBody.booleanValue("deleted") == true) return@forEach
            val groupId =
                categoryGroupIdOverrides[record.recordId]
                    ?: categoryBody.stringValue("group")
                    ?: categoryBody.stringValue("group_id")
                    ?: return@forEach
            var groupIndex = completeGroups.indexOfFirst { it.id == groupId }
            if (groupIndex < 0) {
                val groupRecord = categoryGroupRecords.firstOrNull { it.recordId == groupId } ?: return@forEach
                val groupBody = groupRecord.recordBody()
                if (categoryGroupVisibilityOverrides[groupId] == true ||
                    groupBody?.booleanValue("hidden") == true ||
                    groupBody?.booleanValue("deleted") == true
                ) {
                    return@forEach
                }
                completeGroups +=
                    LiveCategoryGroupSummary(
                        id = groupId,
                        name = categoryGroupNameOverrides[groupId] ?: groupNames[groupId] ?: groupId,
                        categories = emptyList(),
                        isIncome = groupBody?.booleanValue("is_income") ?: groupBody?.booleanValue("isIncome") ?: false,
                    )
                groupIndex = completeGroups.lastIndex
            }
            val group = completeGroups[groupIndex]
            if (group.categories.any { it.id == record.recordId }) return@forEach
            completeGroups[groupIndex] =
                group.copy(
                    categories =
                        group.categories +
                            LiveCategorySummary(
                                id = record.recordId,
                                name = categoryNameOverrides[record.recordId] ?: categoryNames[record.recordId] ?: record.recordId,
                                assignedMinor = 0,
                                activityMinor = 0,
                                balanceMinor = 0,
                            ),
                )
        }
    }

    fun applyCategoryGroupMoves(groupsWithPendingCategories: MutableList<LiveCategoryGroupSummary>) {
        categoryGroupIdOverrides.forEach { (categoryId, targetGroupId) ->
            if (categoryId in deletedCategoryIds || categoryVisibilityOverrides[categoryId] == true) return@forEach
            val categoryRecord = categoryRecords.firstOrNull { it.recordId == categoryId } ?: return@forEach
            val name = categoryNameOverrides[categoryId] ?: categoryRecord.recordName() ?: categoryId
            val targetIndex = groupsWithPendingCategories.indexOfFirst { it.id == targetGroupId }
            if (targetIndex < 0) return@forEach
            val movedCategory = groupsWithPendingCategories.flatMap { it.categories }.firstOrNull { it.id == categoryId }
            val body = categoryRecord.recordBody()
            val assigned = movedCategory?.assignedMinor ?: body?.longValue("budgeted") ?: body?.longValue("assigned") ?: 0
            val activity = movedCategory?.activityMinor ?: body?.longValue("activity") ?: body?.longValue("spent") ?: 0
            groupsWithPendingCategories.indices.forEach { index ->
                val existing = groupsWithPendingCategories[index]
                if (existing.categories.any { it.id == categoryId }) {
                    groupsWithPendingCategories[index] = existing.copy(categories = existing.categories.filterNot { it.id == categoryId })
                }
            }
            val target = groupsWithPendingCategories[targetIndex]
            groupsWithPendingCategories[targetIndex] =
                target.copy(
                    categories =
                        target.categories +
                            LiveCategorySummary(
                                id = categoryId,
                                name = name,
                                assignedMinor = assigned,
                                activityMinor = activity,
                                balanceMinor = movedCategory?.balanceMinor ?: assigned + activity,
                                carryover = movedCategory?.carryover ?: body?.booleanValue("carryover") ?: false,
                                pendingChanges = movedCategory?.pendingChanges ?: categoryId in pendingCategoryIds,
                            ),
                )
        }
    }

    fun addPendingCategories(groupsWithPendingCategories: MutableList<LiveCategoryGroupSummary>) {
        pendingCategories.forEach { pending ->
            if (pending.category.id in deletedCategoryIds) return@forEach
            val groupIndex = groupsWithPendingCategories.indexOfFirst { it.id == pending.groupId }
            if (groupIndex < 0) return@forEach
            val group = groupsWithPendingCategories[groupIndex]
            if (group.categories.none { it.id == pending.category.id }) {
                groupsWithPendingCategories[groupIndex] = group.copy(categories = group.categories + pending.category)
            }
        }
    }
}

private fun parseQueuedBudgetPayload(payload: String): JsonObject? =
    runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()

private val pendingBudgetPlanMutationKinds =
    setOf(
        BudgetReorderKind.SET_CATEGORY_AMOUNT,
        BudgetReorderKind.SET_CATEGORY_CARRYOVER,
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE,
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH,
        BudgetReorderKind.RESET_BUDGET_HOLD,
        BudgetReorderKind.MOVE_BUDGET_MONEY,
    )

private fun QueuedBudgetReorder.isForBudgetMonth(monthId: String): Boolean {
    if (kind !in pendingBudgetPlanMutationKinds) return false
    return payloadJson?.let(::parseQueuedBudgetPayload)?.stringValue("month") == monthId
}

private fun JsonObject.assignmentEntries(): List<Pair<String, Long>> =
    (this["assignments"] as? JsonArray)
        ?.mapNotNull { element ->
            val assignment = element as? JsonObject ?: return@mapNotNull null
            val categoryId = assignment.stringValue("categoryId") ?: return@mapNotNull null
            val amountMinor = assignment.longValue("amountMinor") ?: return@mapNotNull null
            categoryId to amountMinor
        }.orEmpty()

internal fun liveScheduleSummaries(
    scheduleRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    pendingActions: List<OutboxOperationEntity> = emptyList(),
): List<LiveScheduleSummary> {
    val payees = payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap()
    val accounts = accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap()
    val baseSchedules =
        scheduleRecords
            .mapNotNull { record -> baseScheduleSummary(record, payees, accounts) }
            .associateBy(LiveScheduleSummary::id)
    val projectedSchedules = baseSchedules.toMutableMap()
    pendingActions
        .filter { it.action == "create" && it.status in setOf("queued", "sending", "uncertain") && it.recordId !in baseSchedules }
        .forEach { action -> queuedScheduleSummary(action, payees, accounts)?.let { projectedSchedules[action.recordId] = it } }
    pendingActions
        .filter { it.action == "edit" && it.recordId in projectedSchedules }
        .sortedWith(compareBy(OutboxOperationEntity::createdAtEpochMs, OutboxOperationEntity::operationId))
        .forEach { action ->
            val fields =
                runCatching {
                    Json.parseToJsonElement(action.patchJson).jsonObject["fields"]?.jsonObject
                }.getOrNull() ?: return@forEach
            val previous = projectedSchedules[action.recordId] ?: return@forEach
            val dateValue = fields["date"] ?: JsonPrimitive(previous.nextDate)
            val dateObject = dateValue as? JsonObject
            val nextDate =
                dateObject?.stringValue("start")
                    ?: (dateValue as? JsonPrimitive)?.contentOrNull
                    ?: previous.nextDate
            val amountRange =
                (fields["amountRangeMinor"] as? JsonObject)?.let { range ->
                    val first = range.longValue("num1")
                    val second = range.longValue("num2")
                    if (first != null && second != null) first to second else null
                }
            val isAmountRange = fields.stringValue("amountOp") == "isbetween" || "amountRangeMinor" in fields
            val accountId = fields.stringValue("accountId") ?: previous.accountId
            projectedSchedules[action.recordId] =
                previous.copy(
                    name = fields.stringValue("name") ?: previous.name,
                    nextDate = nextDate,
                    dateValueJson = dateValue.toString(),
                    accountId = accountId,
                    accountName = accountId?.let { accounts[it] ?: it },
                    payeeId = fields.stringValue("payeeId") ?: previous.payeeId,
                    amountMinor =
                        if (isAmountRange) {
                            null
                        } else {
                            fields.longValue("amountMinor") ?: previous.amountMinor
                        },
                    amountRangeMinor = if (isAmountRange) amountRange else null,
                    amountOp = fields.stringValue("amountOp") ?: previous.amountOp,
                    canSkip = dateObject?.stringValue("frequency") != null,
                    postsTransaction = fields.booleanValue("postsTransaction") ?: previous.postsTransaction,
                )
        }
    val locallyDeletedScheduleIds =
        pendingActions
            .filter { it.action == "delete" && it.status in setOf("queued", "sending", "uncertain") }
            .mapTo(mutableSetOf(), OutboxOperationEntity::recordId)
    return projectedSchedules.values
        .filterNot { it.id in locallyDeletedScheduleIds }
        .sortedBy(LiveScheduleSummary::nextDate)
}

private fun baseScheduleSummary(
    record: BudgetRecordEntity,
    payees: Map<String, String>,
    accounts: Map<String, String>,
): LiveScheduleSummary? {
    val body = record.recordBody() ?: return null
    if (body.booleanValue("completed") == true) return null
    val nextDate = body.stringValue("next_date") ?: return null
    val payeeId = body.stringValue("payee")
    val accountId = body.stringValue("account")
    val amountElement = body["amount"]
    val amountObject = amountElement as? JsonObject
    val dateObject = body["date"] as? JsonObject
    val amountRange =
        amountObject?.let { amount ->
            val first = amount.longValue("num1")
            val second = amount.longValue("num2")
            if (first != null && second != null) first to second else null
        }
    return LiveScheduleSummary(
        id = record.recordId,
        name = body.stringValue("name") ?: payeeId?.let(payees::get) ?: "Scheduled transaction",
        nextDate = nextDate,
        dateValueJson = body["date"]?.toString() ?: JsonPrimitive(nextDate).toString(),
        accountId = accountId,
        accountName = accountId?.let(accounts::get),
        payeeId = payeeId,
        amountMinor = if (amountRange == null) body.longValue("amount") else null,
        amountRangeMinor = amountRange,
        amountOp = body.stringValue("amountOp") ?: "is",
        canSkip = dateObject?.stringValue("frequency") != null,
        ruleId = body.stringValue("rule"),
        postsTransaction = body.booleanValue("posts_transaction") == true,
    )
}
