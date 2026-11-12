package app.tandemforactual.ui.model

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal data class LiveAccountSummary(
    val id: String,
    val name: String,
    val balanceMinor: Long?,
    val type: String?,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountGroupId: String? = null,
    val accountGroupName: String? = null,
    val sortOrder: Long? = null,
)

internal data class LiveCategorySummary(
    val id: String,
    val name: String,
    val assignedMinor: Long,
    val activityMinor: Long,
    val balanceMinor: Long,
    val carryover: Boolean = false,
)

internal data class PendingCategorySummary(
    val groupId: String,
    val category: LiveCategorySummary,
)

internal data class LiveCategoryGroupSummary(
    val id: String,
    val name: String,
    val categories: List<LiveCategorySummary>,
    val isIncome: Boolean = false,
) {
    val assignedMinor: Long get() = categories.sumOf { it.assignedMinor }
    val activityMinor: Long get() = categories.sumOf { it.activityMinor }
    val balanceMinor: Long get() = categories.sumOf { it.balanceMinor }
}

internal data class LiveBudgetMonthSummary(
    val toBudgetMinor: Long?,
    val incomeAvailableMinor: Long?,
    val totalBudgetedMinor: Long?,
    val totalSpentMinor: Long?,
    val groups: List<LiveCategoryGroupSummary>,
    val heldForNextMonthMinor: Long? = null,
)

internal data class LiveScheduleSummary(
    val id: String,
    val name: String,
    val nextDate: String,
    val dateValueJson: String,
    val accountId: String?,
    val accountName: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val amountRangeMinor: Pair<Long, Long>?,
    val amountOp: String,
    val canSkip: Boolean,
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

internal fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.booleanValue(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

internal fun liveAccountSummaries(
    accountRecords: List<BudgetRecordEntity>,
    accountGroupRecords: List<BudgetRecordEntity> = emptyList(),
    nameOverrides: Map<String, String> = emptyMap(),
    closedOverrides: Map<String, Boolean> = emptyMap(),
): List<LiveAccountSummary> {
    val groupNames = accountGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    return accountRecords.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        val accountGroupId = body.stringValue("account_group_id") ?: body.stringValue("accountGroupId")
        LiveAccountSummary(
            id = record.recordId,
            name = nameOverrides[record.recordId] ?: record.recordName() ?: return@mapNotNull null,
            balanceMinor = body.longValue("balance_current"),
            type = body.stringValue("type"),
            offBudget = body.booleanValue("offbudget") ?: body.booleanValue("offBudget") ?: false,
            closed = closedOverrides[record.recordId] ?: body.booleanValue("closed") ?: false,
            accountGroupId = accountGroupId,
            accountGroupName = accountGroupId?.let(groupNames::get),
            sortOrder = body.longValue("sort_order"),
        )
    }
}

internal fun liveAccountGroupLabel(account: LiveAccountSummary): String {
    if (account.closed) return "Closed"
    val typeLabel =
        when (account.type?.lowercase()) {
            "checking", "chequing" -> "Checking"
            "savings", "saving" -> "Savings"
            "credit", "credit-card", "credit card" -> "Credit"
            "loan" -> "Loan"
            "mortgage" -> "Mortgage"
            "debt" -> "Debt"
            "investment" -> "Investment"
            "cash" -> "Cash"
            "other" -> "Other"
            else -> "Other"
        }
    return typeLabel
}

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
): LiveBudgetMonthSummary? {
    monthRecord ?: return null
    val month = monthRecord.recordBody() ?: return null
    val categoryNames = categoryRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groupNames = categoryGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    var assignedDelta = 0L
    val groups =
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
                            val assigned = categoryBudgetOverrides[categoryId] ?: originalAssigned
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
                                carryover = categoryCarryoverOverrides[categoryId] ?: category.booleanValue("carryover") ?: false,
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
    val completeGroups = groups.toMutableList()
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
    val groupsWithPendingCreates =
        completeGroups + pendingCategoryGroups.filterNot { pending -> completeGroups.any { it.id == pending.id } }
    val groupsWithPendingCategories = groupsWithPendingCreates.toMutableList()
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
                        ),
            )
    }
    pendingCategories.forEach { pending ->
        if (pending.category.id in deletedCategoryIds) return@forEach
        val groupIndex = groupsWithPendingCategories.indexOfFirst { it.id == pending.groupId }
        if (groupIndex < 0) return@forEach
        val group = groupsWithPendingCategories[groupIndex]
        if (group.categories.none { it.id == pending.category.id }) {
            groupsWithPendingCategories[groupIndex] = group.copy(categories = group.categories + pending.category)
        }
    }
    return LiveBudgetMonthSummary(
        toBudgetMinor = month.longValue("toBudget")?.minus(assignedDelta),
        incomeAvailableMinor = month.longValue("incomeAvailable"),
        totalBudgetedMinor = month.longValue("totalBudgeted")?.plus(assignedDelta),
        totalSpentMinor = month.longValue("totalSpent"),
        groups = groupsWithPendingCategories,
        heldForNextMonthMinor = month.longValue("forNextMonth"),
    )
}

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
            .mapNotNull { record ->
                val body = record.recordBody() ?: return@mapNotNull null
                if (body.booleanValue("completed") == true) return@mapNotNull null
                val nextDate = body.stringValue("next_date") ?: return@mapNotNull null
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
                LiveScheduleSummary(
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
                )
            }.associateBy(LiveScheduleSummary::id)
    val projectedSchedules = baseSchedules.toMutableMap()
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
