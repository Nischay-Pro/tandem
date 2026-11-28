package app.tandemforactual.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** What the net worth widget shows for the open budget: what its open accounts hold and owe; amounts are null while hidden. */
data class NetWorthWidgetSummary(
    val budgetName: String,
    val netWorthMinor: Long?,
    val assetsMinor: Long?,
    val debtsMinor: Long?,
    val amountsHidden: Boolean,
    val displaySettings: BudgetDisplaySettings?,
    val locked: Boolean = false,
)

/**
 * Null when no budget is open. Every open account counts, on and off budget, with edits not yet synced; debts are the
 * accounts below zero, as a positive amount. Amounts stay hidden while app lock, privacy mode or encryption protects it.
 */
suspend fun netWorthWidgetSummary(
    repository: LocalBudgetRepository,
    preferences: Map<String, String>,
): NetWorthWidgetSummary? {
    val budget = widgetBudget(preferences) ?: return null
    if (budget.amountsHidden) {
        return NetWorthWidgetSummary(budget.name, null, null, null, amountsHidden = true, displaySettings = null, locked = budget.locked)
    }
    val balances =
        repository
            .findRecords(budget.id, "account")
            .filterNot { record -> isClosed(record.localJson ?: record.remoteJson) }
            .map { repository.readAccountBalance(budget.id, it.recordId) }
    val assets = balances.filter { it > 0 }.sum()
    val debts = balances.filter { it < 0 }.sumOf { -it }
    return NetWorthWidgetSummary(
        budget.name,
        assets - debts,
        assets,
        debts,
        amountsHidden = false,
        displaySettings = repository.readBudgetDisplaySettings(budget.id),
    )
}

private fun isClosed(json: String?): Boolean =
    json
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?.let { (it["closed"] as? JsonPrimitive)?.booleanOrNull } == true
