package app.tandemforactual.data.store

import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.data.database.BudgetRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val BILL_DAYS_AHEAD = 60
private const val RECENT_DAYS = 30
private const val BILL_COUNT = 3
private const val TRANSACTION_COUNT = 3
private const val ACCOUNT_COUNT = 4
private const val PAYEE_COUNT = 5
private const val RECENT_CATEGORY_COUNT = 3

/** An expense category in one budget month, with its budgeted, spent and balance in minor units. */
data class WatchMonthCategory(
    val id: String,
    val name: String,
    val group: String,
    val budgetedMinor: Long,
    val spentMinor: Long,
    val balanceMinor: Long,
)

/** [watchSummary] as the JSON the phone hands to the watch. */
suspend fun watchSummaryJson(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
    nowEpochMs: Long,
    opened: Boolean,
    format: (Long, BudgetDisplaySettings?) -> String,
): String = encodeWatchSummary(watchSummary(repository, preferences, todayIso, nowEpochMs, opened, format))

/** The watch summary for the open budget, holding no more than the watch's state allows. */
suspend fun watchSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
    todayIso: String,
    nowEpochMs: Long,
    opened: Boolean,
    format: (Long, BudgetDisplaySettings?) -> String,
): WatchSummary {
    val state = watchState(preferences, opened)
    val quickAddOpen = watchQuickAddOpen(preferences, state)
    val budgetId = preferences["selectedBudgetId"].orEmpty()
    val name = preferences["selectedBudget"]?.takeIf(String::isNotBlank) ?: "Budget"
    if (state == WatchState.NONE) return WatchSummary(state, nowEpochMs)
    if (state == WatchState.LOCKED) return WatchSummary(state, nowEpochMs, budgetId, name, quickAdd = WatchQuickAdd(quickAddOpen))
    val settings = repository.readBudgetDisplaySettings(budgetId)
    val amounts = state == WatchState.READY
    val money = { minor: Long -> format(minor, settings).takeIf { amounts } }
    val month = monthBody(repository, budgetId, todayIso)
    val all = month?.let(::watchMonthCategories).orEmpty()
    val pinned = WatchSettings.from(preferences).pinnedCategoryIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    val shown = pinned.ifEmpty { all.take(MAX_WATCH_CATEGORIES) }
    val payeeNames = payeeNames(repository, budgetId)
    val recent = recentTransactions(repository, budgetId, ActualDates.addDays(todayIso, -RECENT_DAYS))
    val allowance = month?.long("totalBalance")?.let { dailyAllowance(it, todayIso) }
    return WatchSummary(
        state = state,
        generatedAtEpochMs = nowEpochMs,
        budgetId = budgetId,
        budgetName = name,
        syncedAtEpochMs = repository.readSyncState(budgetId)?.lastSyncedAtEpochMs,
        leftToSpend = month?.long("totalBalance")?.let(money),
        leftToSpendNegative = (month?.long("totalBalance") ?: 0) < 0,
        toBudget = month?.long("toBudget")?.let(money),
        toBudgetNegative = (month?.long("toBudget") ?: 0) < 0,
        perDay = allowance?.let { money(it.perDayMinor) },
        daysLeft = allowance?.daysLeft,
        perDayFirst = WatchSettings.from(preferences).perDayFirst,
        categories =
            shown.map { category ->
                val lines = if (amounts) recent.filter { it.categoryId == category.id }.take(TRANSACTION_COUNT) else emptyList()
                watchCategory(category, money, lines.map { it.toWatch(payeeNames, money) })
            },
        bills =
            dueSchedules(repository, budgetId, todayIso, ActualDates.addDays(todayIso, BILL_DAYS_AHEAD)).take(BILL_COUNT).map { bill ->
                WatchBill(bill.name, bill.date, bill.amountMinor?.let { money(kotlin.math.abs(it)) })
            },
        quickAdd =
            if (quickAddOpen) {
                quickAddChoices(repository, budgetId, preferences, settings, recent, payeeNames, all, shown)
            } else {
                WatchQuickAdd(open = false)
            },
    )
}

/** Expense categories in budget order; income and hidden groups and categories are left out. */
fun watchMonthCategories(month: JsonObject): List<WatchMonthCategory> =
    (month["categoryGroups"] as? JsonArray)
        .orEmpty()
        .mapNotNull { it as? JsonObject }
        .filterNot { it.flag("is_income") || it.flag("hidden") }
        .flatMap { group ->
            (group["categories"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { category -> category to group } }
        }.filterNot { (category, _) -> category.flag("is_income") || category.flag("hidden") }
        .mapNotNull { (category, group) ->
            WatchMonthCategory(
                id = category.text("id") ?: return@mapNotNull null,
                name = category.text("name") ?: return@mapNotNull null,
                group = group.text("name").orEmpty(),
                budgetedMinor = category.long("budgeted") ?: 0,
                spentMinor = -(category.long("spent") ?: 0),
                balanceMinor = category.long("balance") ?: 0,
            )
        }

/** Categories the watch can pin, from this month of the open budget. */
suspend fun watchCategoryChoices(
    repository: LocalBudgetRepository,
    budgetId: String,
    todayIso: String,
): List<WatchMonthCategory> = monthBody(repository, budgetId, todayIso)?.let(::watchMonthCategories).orEmpty()

/** How much of the budgeted amount is spent, as a whole percent; spending with nothing budgeted counts as all of it. */
fun watchSpentPercent(
    budgetedMinor: Long,
    spentMinor: Long,
): Int =
    when {
        budgetedMinor > 0 -> (spentMinor.coerceAtLeast(0) * 100 / budgetedMinor).coerceAtMost(999).toInt()
        spentMinor > 0 -> 100
        else -> 0
    }

private fun watchCategory(
    category: WatchMonthCategory,
    money: (Long) -> String?,
    transactions: List<WatchTransaction>,
): WatchCategory {
    val left = money(category.balanceMinor)
    return WatchCategory(
        id = category.id,
        name = category.name,
        left = left,
        overspent = left != null && category.balanceMinor < 0,
        spentPercent = left?.let { watchSpentPercent(category.budgetedMinor, category.spentMinor) },
        budgeted = money(category.budgetedMinor),
        spent = money(category.spentMinor),
        transactions = transactions,
    )
}

private suspend fun quickAddChoices(
    repository: LocalBudgetRepository,
    budgetId: String,
    preferences: Map<String, String>,
    settings: BudgetDisplaySettings?,
    recent: List<BudgetRecordEntity>,
    payeeNames: Map<String, String>,
    all: List<WatchMonthCategory>,
    shown: List<WatchMonthCategory>,
): WatchQuickAdd {
    val lastUsed = preferences["lastTransactionAccountId"].orEmpty()
    val accounts =
        repository
            .findRecords(budgetId, "account")
            .mapNotNull { record -> record.json()?.takeUnless { it.flag("closed") || it.flag("offbudget") } }
            .mapNotNull { account -> account.text("id")?.let { WatchChoice(it, account.text("name") ?: it) } }
            .sortedBy { if (it.id == lastUsed) 0 else 1 }
            .take(ACCOUNT_COUNT)
    val shownIds = shown.map { it.id }.toSet()
    return WatchQuickAdd(
        open = true,
        fractionDigits = (settings?.currencyFractionDigits ?: 2).coerceIn(0, 2),
        accounts = accounts,
        payees = recent.mapNotNull { it.payeeId?.let(payeeNames::get) }.distinct().take(PAYEE_COUNT),
        recentCategories =
            recent
                .mapNotNull { it.categoryId }
                .distinct()
                .filterNot(shownIds::contains)
                .mapNotNull { id -> all.firstOrNull { it.id == id }?.let { WatchChoice(it.id, it.name) } }
                .take(RECENT_CATEGORY_COUNT),
    )
}

private suspend fun recentTransactions(
    repository: LocalBudgetRepository,
    budgetId: String,
    fromDate: String,
): List<BudgetRecordEntity> =
    repository
        .findRecords(budgetId, "transaction", fromDate = fromDate)
        .filter { !it.isParent && !it.localDeleted && it.transfer != true }
        .sortedByDescending { it.recordDate.orEmpty() }

private suspend fun payeeNames(
    repository: LocalBudgetRepository,
    budgetId: String,
): Map<String, String> =
    repository
        .findRecords(budgetId, "payee")
        .filter { it.json()?.text("transfer_acct").isNullOrBlank() }
        .mapNotNull { record -> (record.displayName ?: record.json()?.text("name"))?.let { record.recordId to it } }
        .toMap()

private fun BudgetRecordEntity.toWatch(
    payeeNames: Map<String, String>,
    money: (Long) -> String?,
) = WatchTransaction(
    payee = payeeId?.let(payeeNames::get) ?: "No payee",
    amount = money(amountMinor ?: 0).orEmpty(),
    date = recordDate.orEmpty(),
)

private suspend fun monthBody(
    repository: LocalBudgetRepository,
    budgetId: String,
    todayIso: String,
): JsonObject? = repository.findRecord(budgetId, "budget-month", todayIso.take(7))?.json()

private fun BudgetRecordEntity.json(): JsonObject? =
    (localJson ?: remoteJson)?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

private fun JsonObject.flag(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
