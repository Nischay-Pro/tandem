package app.tandemforactual.ui.core.watch

import app.tandemforactual.data.actual.ActualDates
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.dueSchedules
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.findRecordsByIds
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.readSyncState
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.automation.AutomationException
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.watch.WatchBill
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchChoice
import app.tandemforactual.watch.WatchMoneyFormat
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.WatchTransaction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val RECENT_DAYS = 60
private const val BILL_DAYS = 31

private class MonthCategory(
    val id: String,
    val name: String,
    val budgeted: Long,
    val spent: Long,
    val balance: Long,
)

/**
 * What a paired watch shows for the open budget, or null when none is open so the watch clears. An encrypted budget
 * stays locked unless reads are allowed and [openBudget] opens it without asking; hidden amounts are never sent.
 */
suspend fun watchSummary(
    mainDatabase: TandemDatabase,
    preferences: Map<String, String>,
    todayIso: String,
    nowEpochMs: Long,
    openBudget: suspend (AutomationTarget) -> TandemDatabase,
): WatchSummary? {
    val budgetId = preferences["selectedBudgetId"]?.takeIf(String::isNotBlank) ?: return null
    val settings = WatchSettings.from(preferences)
    val target = AutomationTarget(budgetId, preferences["selectedBudgetEncrypted"] == "true")
    val locked =
        WatchSummary(
            version = WatchSummary.VERSION,
            state = WatchState.LOCKED,
            budgetName = preferences["selectedBudget"]?.takeIf(String::isNotBlank) ?: "Budget",
            sentAtEpochMs = nowEpochMs,
            quickAdd = settings.quickAdd && preferences["readOnlyMode"] != "true",
        )
    if (target.encrypted && !AutomationAccess.readsAllowed(preferences)) return locked
    val database =
        try {
            openBudget(target)
        } catch (_: AutomationException) {
            return locked
        }
    try {
        val visible = visibleSummary(LocalBudgetRepository(database) { nowEpochMs }, budgetId, locked, settings, preferences, todayIso)
        return if (preferences["privacyModeEnabled"] == "true") visible.withoutAmounts() else visible
    } finally {
        if (database !== mainDatabase) database.close()
    }
}

/** The same summary with every amount and recent transaction left out. */
fun WatchSummary.withoutAmounts(): WatchSummary =
    copy(
        state = WatchState.AMOUNTS_HIDDEN,
        toBudgetMinor = null,
        leftToSpendMinor = null,
        categories = categories.map { WatchCategory(it.id, it.name) },
        bills = bills.map { it.copy(amountMinor = null) },
    )

private suspend fun visibleSummary(
    repository: LocalBudgetRepository,
    budgetId: String,
    base: WatchSummary,
    settings: WatchSettings,
    preferences: Map<String, String>,
    todayIso: String,
): WatchSummary {
    val month = repository.findRecord(budgetId, "budget-month", todayIso.take(7))?.recordBody()
    val categories = month?.let(::monthCategories).orEmpty()
    val byId = categories.associateBy { it.id }
    val pinned = pinnedCategories(categories, settings, preferences, budgetId)
    val recent =
        repository
            .findRecords(budgetId, "transaction", fromDate = ActualDates.addDays(todayIso, -RECENT_DAYS))
            .filter { it.transfer != true && !it.isParent && it.amountMinor != null }
            .sortedByDescending { it.recordDate }
    val payees = payeeNames(repository, budgetId, recent)
    val pinnedIds = pinned.map { it.id }.toSet()
    return base.copy(
        state = WatchState.VISIBLE,
        syncedAtEpochMs = repository.readSyncState(budgetId)?.lastSyncedAtEpochMs,
        toBudgetMinor = month?.longValue("toBudget"),
        leftToSpendMinor = month?.longValue("totalBalance"),
        categories = pinned.map { it.toWatch(recent.filter { record -> record.categoryId == it.id }, payees) },
        quickCategories =
            recent
                .mapNotNull { it.categoryId?.takeUnless(pinnedIds::contains)?.let(byId::get) }
                .distinctBy { it.id }
                .take(WatchSummary.MAX_CATEGORIES)
                .map { WatchChoice(it.id, it.name) },
        recentPayees = recent.mapNotNull { it.payeeId?.let(payees::get) }.distinct().take(WatchSummary.MAX_CATEGORIES),
        accountName = watchAccount(repository, budgetId, preferences)?.second,
        bills =
            dueSchedules(repository, budgetId, todayIso, ActualDates.addDays(todayIso, BILL_DAYS))
                .take(WatchSummary.MAX_BILLS)
                .map { WatchBill(it.name, it.date, it.amountMinor) },
        money = watchMoneyFormat(repository.readBudgetDisplaySettings(budgetId)),
    )
}

private fun MonthCategory.toWatch(
    transactions: List<BudgetRecordEntity>,
    payees: Map<String, String>,
): WatchCategory =
    WatchCategory(
        id = id,
        name = name,
        budgetedMinor = budgeted,
        spentMinor = spent,
        leftMinor = balance,
        recent =
            transactions.take(WatchSummary.MAX_RECENT).map {
                WatchTransaction(it.payeeId?.let(payees::get) ?: "No payee", it.amountMinor ?: 0, it.recordDate.orEmpty())
            },
    )

private suspend fun payeeNames(
    repository: LocalBudgetRepository,
    budgetId: String,
    transactions: List<BudgetRecordEntity>,
): Map<String, String> {
    val ids = transactions.mapNotNull { it.payeeId }.distinct()
    return repository
        .findRecordsByIds(budgetId, "payee", ids)
        .mapNotNull { payee -> payee.recordName()?.let { payee.recordId to it } }
        .toMap()
}

/** The watch's categories: its own pins, else the budget's pins, else every category with money budgeted. */
private fun pinnedCategories(
    categories: List<MonthCategory>,
    settings: WatchSettings,
    preferences: Map<String, String>,
    budgetId: String,
): List<MonthCategory> {
    val byId = categories.associateBy { it.id }
    val budgetPins =
        preferences["pinnedCategoryIds"]
            .orEmpty()
            .split('\u001f')
            .mapNotNull { pin ->
                when {
                    pin.startsWith("$budgetId:") -> pin.removePrefix("$budgetId:")
                    ':' in pin -> null
                    else -> pin
                }
            }
    val chosen = settings.pinnedCategoryIds.ifEmpty { budgetPins }.mapNotNull(byId::get)
    return chosen.ifEmpty { categories.filter { it.budgeted != 0L } }.take(WatchSummary.MAX_CATEGORIES)
}

private fun monthCategories(month: JsonObject): List<MonthCategory> =
    (month["categoryGroups"] as? JsonArray)
        .orEmpty()
        .mapNotNull { it as? JsonObject }
        .filterNot { it.booleanValue("is_income") == true || it.booleanValue("hidden") == true }
        .flatMap { group -> (group["categories"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
        .filterNot { it.booleanValue("is_income") == true || it.booleanValue("hidden") == true }
        .mapNotNull { category ->
            MonthCategory(
                id = category.stringValue("id") ?: return@mapNotNull null,
                name = category.stringValue("name") ?: return@mapNotNull null,
                budgeted = category.longValue("budgeted") ?: 0,
                spent = -(category.longValue("spent") ?: 0),
                balance = category.longValue("balance") ?: 0,
            )
        }

/** The account watch adds go to: the one last used for a new transaction, else the first open account. */
internal suspend fun watchAccount(
    repository: LocalBudgetRepository,
    budgetId: String,
    preferences: Map<String, String>,
): Pair<String, String>? {
    val open =
        repository
            .findRecords(budgetId, "account")
            .filterNot { it.recordBody()?.booleanValue("closed") == true }
    val account = open.firstOrNull { it.recordId == preferences["lastTransactionAccountId"] } ?: open.firstOrNull()
    return account?.let { it.recordId to (it.recordName() ?: it.recordBody()?.stringValue("name") ?: "Account") }
}

/** The budget's symbol, separators and decimals, read off how Tandem writes a sample amount. */
internal fun watchMoneyFormat(settings: BudgetDisplaySettings?): WatchMoneyFormat {
    val sample = formatAmount(123_456_789, settings?.copy(hideFraction = false, currencyFractionDigits = 2))
    val separators = sample.filterNot(Char::isDigit).filterNot { it == '-' }
    val digits = if (settings?.hideFraction == true) 0 else (settings?.currencyFractionDigits ?: 2).coerceIn(0, 2)
    return WatchMoneyFormat(
        symbol = settings?.currencySymbol.orEmpty(),
        symbolAfter = settings?.currencySymbolPosition == "after",
        space = settings?.currencySpaceBetweenAmountAndSymbol == true,
        fractionDigits = digits,
        group = separators.dropLast(1).take(1).ifEmpty { "," },
        decimal = separators.takeLast(1).ifEmpty { "." },
    )
}
