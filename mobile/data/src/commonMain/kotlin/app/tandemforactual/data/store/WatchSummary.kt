package app.tandemforactual.data.store

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What the paired watch may show: nothing, only the budget's name, names without amounts, or everything. */
enum class WatchState(
    val code: String,
) {
    NONE("none"),
    LOCKED("locked"),
    HIDDEN("hidden"),
    READY("ready"),
}

/**
 * The watch's state for the open budget. [opened] is true only while Tandem is on screen with the budget unlocked;
 * an encrypted budget can't be read any other time, and app lock waits for it unless the person allowed otherwise.
 */
fun watchState(
    preferences: Map<String, String>,
    opened: Boolean,
): WatchState {
    if (preferences["selectedBudgetId"].isNullOrBlank()) return WatchState.NONE
    val encrypted = preferences["selectedBudgetEncrypted"] == "true"
    val appLock = preferences["biometricLockEnabled"] == "true"
    val afterUnlock = WatchSettings.from(preferences).summaryAfterUnlock
    return when {
        !opened && (encrypted || (appLock && afterUnlock)) -> WatchState.LOCKED
        preferences["privacyModeEnabled"] == "true" -> WatchState.HIDDEN
        else -> WatchState.READY
    }
}

/** Whether the watch can add a transaction right now; it's offered only when the phone would take it. */
fun watchQuickAddOpen(
    preferences: Map<String, String>,
    state: WatchState,
): Boolean = state != WatchState.NONE && watchQuickAddRefusal(preferences) == null

/** Why the phone won't take a watch entry right now, or null when it will. */
fun watchQuickAddRefusal(preferences: Map<String, String>): String? =
    when {
        !WatchSettings.from(preferences).quickAddEnabled -> "Quick add is off. Turn it on in Tandem under Settings › Watch."
        preferences["selectedBudgetId"].isNullOrBlank() -> "No budget is open. Open a budget in Tandem first."
        preferences["readOnlyMode"] == "true" -> "Lock editing is on in Tandem. Turn it off to add from your watch."
        else -> null
    }

data class WatchTransaction(
    val payee: String,
    val amount: String,
    val date: String,
)

data class WatchCategory(
    val id: String,
    val name: String,
    val left: String? = null,
    val overspent: Boolean = false,
    val spentPercent: Int? = null,
    val budgeted: String? = null,
    val spent: String? = null,
    val transactions: List<WatchTransaction> = emptyList(),
)

data class WatchBill(
    val name: String,
    val date: String,
    val amount: String? = null,
)

data class WatchChoice(
    val id: String,
    val name: String,
)

data class WatchQuickAdd(
    val open: Boolean,
    val fractionDigits: Int = 2,
    val accounts: List<WatchChoice> = emptyList(),
    val payees: List<String> = emptyList(),
    val recentCategories: List<WatchChoice> = emptyList(),
)

/** The small summary the phone sends the watch after each sync or budget change; amounts are already formatted. */
data class WatchSummary(
    val state: WatchState,
    val generatedAtEpochMs: Long,
    val budgetId: String? = null,
    val budgetName: String? = null,
    val syncedAtEpochMs: Long? = null,
    val leftToSpend: String? = null,
    val leftToSpendNegative: Boolean = false,
    val toBudget: String? = null,
    val toBudgetNegative: Boolean = false,
    val categories: List<WatchCategory> = emptyList(),
    val bills: List<WatchBill> = emptyList(),
    val quickAdd: WatchQuickAdd = WatchQuickAdd(open = false),
)

/** WatchConnectivity's application context is small; the summary stays under this many bytes of UTF-8. */
const val WATCH_SUMMARY_MAX_BYTES = 2048
private const val NAME_LIMIT = 18

/** The summary as compact JSON that fits [maxBytes], dropping the least useful detail first. */
fun encodeWatchSummary(
    summary: WatchSummary,
    maxBytes: Int = WATCH_SUMMARY_MAX_BYTES,
): String {
    var current = summary
    var json = encode(current)
    for (step in trimSteps) {
        if (json.encodeToByteArray().size <= maxBytes) return json
        current = step(current)
        json = encode(current)
    }
    return json
}

private val trimSteps: List<(WatchSummary) -> WatchSummary> =
    listOf(
        { it.withTransactions(2) },
        { it.shortenNames() },
        { it.withTransactions(1) },
        { it.withChoices(payees = 3, categories = 2, accounts = 4) },
        { it.withTransactions(0) },
        { it.withChoices(payees = 0, categories = 0, accounts = 1) },
        { it.copy(bills = it.bills.take(1)) },
        { it.copy(categories = it.categories.take(3)) },
        { it.copy(categories = emptyList(), bills = emptyList()) },
    )

private fun WatchSummary.withTransactions(count: Int) =
    copy(categories = categories.map { it.copy(transactions = it.transactions.take(count)) })

private fun WatchSummary.withChoices(
    payees: Int,
    categories: Int,
    accounts: Int,
) = copy(
    quickAdd =
        quickAdd.copy(
            payees = quickAdd.payees.take(payees),
            recentCategories = quickAdd.recentCategories.take(categories),
            accounts = quickAdd.accounts.take(accounts),
        ),
)

private fun WatchSummary.shortenNames(): WatchSummary {
    fun String.short() = if (length <= NAME_LIMIT) this else take(NAME_LIMIT - 1).trimEnd() + "…"
    return copy(
        budgetName = budgetName?.short(),
        categories =
            categories.map { category ->
                category.copy(name = category.name.short(), transactions = category.transactions.map { it.copy(payee = it.payee.short()) })
            },
        bills = bills.map { it.copy(name = it.name.short()) },
        quickAdd =
            quickAdd.copy(
                accounts = quickAdd.accounts.map { it.copy(name = it.name.short()) },
                recentCategories = quickAdd.recentCategories.map { it.copy(name = it.name.short()) },
            ),
    )
}

private fun encode(summary: WatchSummary): String =
    buildJsonObject {
        put("v", 1)
        put("s", summary.state.code)
        put("g", summary.generatedAtEpochMs)
        summary.budgetId?.let { put("k", it) }
        summary.budgetName?.let { put("b", it) }
        summary.syncedAtEpochMs?.let { put("y", it) }
        summary.leftToSpend?.let {
            put("l", it)
            put("lo", summary.leftToSpendNegative)
        }
        summary.toBudget?.let {
            put("r", it)
            put("ro", summary.toBudgetNegative)
        }
        if (summary.categories.isNotEmpty()) putJsonArray("c") { summary.categories.forEach { addJsonObject { putCategory(it) } } }
        if (summary.bills.isNotEmpty()) {
            putJsonArray("u") {
                summary.bills.forEach { bill ->
                    addJsonObject {
                        put("n", bill.name)
                        put("d", bill.date)
                        bill.amount?.let { put("a", it) }
                    }
                }
            }
        }
        putJsonObject("q") { putQuickAdd(summary.quickAdd) }
    }.toString()

private fun JsonObjectBuilder.putCategory(category: WatchCategory) {
    put("i", category.id)
    put("n", category.name)
    category.left?.let {
        put("l", it)
        put("o", category.overspent)
    }
    category.spentPercent?.let { put("p", it) }
    category.budgeted?.let { put("bu", it) }
    category.spent?.let { put("sp", it) }
    if (category.transactions.isNotEmpty()) {
        putJsonArray("x") {
            category.transactions.forEach { transaction ->
                addJsonObject {
                    put("n", transaction.payee)
                    put("a", transaction.amount)
                    put("d", transaction.date)
                }
            }
        }
    }
}

private fun JsonObjectBuilder.putQuickAdd(quickAdd: WatchQuickAdd) {
    put("on", quickAdd.open)
    if (!quickAdd.open) return
    put("f", quickAdd.fractionDigits)
    if (quickAdd.accounts.isNotEmpty()) putJsonArray("a") { quickAdd.accounts.forEach { addJsonObject { putChoice(it) } } }
    if (quickAdd.payees.isNotEmpty()) putJsonArray("p") { quickAdd.payees.forEach { add(it) } }
    if (quickAdd.recentCategories.isNotEmpty()) {
        putJsonArray("k") { quickAdd.recentCategories.forEach { addJsonObject { putChoice(it) } } }
    }
}

private fun JsonObjectBuilder.putChoice(choice: WatchChoice) {
    put("i", choice.id)
    put("n", choice.name)
}
