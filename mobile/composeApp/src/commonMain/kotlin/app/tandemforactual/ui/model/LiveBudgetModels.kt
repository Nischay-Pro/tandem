package app.tandemforactual.ui.model

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
)

internal data class LiveCategorySummary(
    val id: String,
    val name: String,
    val assignedMinor: Long,
    val activityMinor: Long,
    val balanceMinor: Long,
)

internal data class LiveCategoryGroupSummary(
    val id: String,
    val name: String,
    val categories: List<LiveCategorySummary>,
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
)

internal data class LiveScheduleSummary(
    val id: String,
    val name: String,
    val nextDate: String,
    val accountName: String?,
    val amountMinor: Long?,
    val amountRangeMinor: Pair<Long, Long>?,
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
): List<LiveAccountSummary> {
    val groupNames = accountGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    return accountRecords.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        val accountGroupId = body.stringValue("account_group_id") ?: body.stringValue("accountGroupId")
        LiveAccountSummary(
            id = record.recordId,
            name = record.recordName() ?: return@mapNotNull null,
            balanceMinor = body.longValue("balance_current"),
            type = body.stringValue("type"),
            offBudget = body.booleanValue("offbudget") ?: body.booleanValue("offBudget") ?: false,
            closed = body.booleanValue("closed") ?: false,
            accountGroupId = accountGroupId,
            accountGroupName = accountGroupId?.let(groupNames::get),
        )
    }
}

internal fun liveAccountGroupLabel(account: LiveAccountSummary): String {
    if (account.closed) return "Closed"
    account.accountGroupName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return if (account.offBudget) "Off budget" else "On budget"
}

internal fun liveBudgetMonthSummary(
    monthRecord: BudgetRecordEntity?,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
): LiveBudgetMonthSummary? {
    monthRecord ?: return null
    val month = monthRecord.recordBody() ?: return null
    val categoryNames = categoryRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groupNames = categoryGroupRecords.associate { it.recordId to (it.recordName() ?: it.recordId) }
    val groups =
        (month["categoryGroups"] as? JsonArray)
            ?.mapNotNull { element ->
                val group = element as? JsonObject ?: return@mapNotNull null
                if (group.booleanValue("hidden") == true) return@mapNotNull null
                val groupId = group.stringValue("id") ?: return@mapNotNull null
                val categories =
                    (group["categories"] as? JsonArray)
                        ?.mapNotNull { categoryElement ->
                            val category = categoryElement as? JsonObject ?: return@mapNotNull null
                            if (category.booleanValue("hidden") == true || category.booleanValue("deleted") == true) {
                                return@mapNotNull null
                            }
                            val categoryId = category.stringValue("id") ?: return@mapNotNull null
                            val assigned = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0
                            val activity = category.longValue("activity") ?: category.longValue("spent") ?: 0
                            LiveCategorySummary(
                                id = categoryId,
                                name = category.stringValue("name") ?: categoryNames[categoryId] ?: categoryId,
                                assignedMinor = assigned,
                                activityMinor = activity,
                                balanceMinor = category.longValue("balance") ?: (assigned + activity),
                            )
                        }.orEmpty()
                LiveCategoryGroupSummary(
                    id = groupId,
                    name = group.stringValue("name") ?: groupNames[groupId] ?: groupId,
                    categories = categories,
                )
            }.orEmpty()
    return LiveBudgetMonthSummary(
        toBudgetMinor = month.longValue("toBudget"),
        incomeAvailableMinor = month.longValue("incomeAvailable"),
        totalBudgetedMinor = month.longValue("totalBudgeted"),
        totalSpentMinor = month.longValue("totalSpent"),
        groups = groups,
    )
}

internal fun liveScheduleSummaries(
    scheduleRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
): List<LiveScheduleSummary> {
    val payees = payeeRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap()
    val accounts = accountRecords.mapNotNull { it.recordName()?.let { name -> it.recordId to name } }.toMap()
    return scheduleRecords
        .mapNotNull { record ->
            val body = record.recordBody() ?: return@mapNotNull null
            if (body.booleanValue("completed") == true) return@mapNotNull null
            val nextDate = body.stringValue("next_date") ?: return@mapNotNull null
            val payeeId = body.stringValue("payee")
            val accountId = body.stringValue("account")
            val amountElement = body["amount"]
            val amountObject = amountElement as? JsonObject
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
                accountName = accountId?.let(accounts::get),
                amountMinor = if (amountRange == null) body.longValue("amount") else null,
                amountRangeMinor = amountRange,
            )
        }.sortedBy(LiveScheduleSummary::nextDate)
}
