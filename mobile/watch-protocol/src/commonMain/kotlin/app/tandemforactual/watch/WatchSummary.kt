package app.tandemforactual.watch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How much of the budget a watch may show. */
@Serializable
enum class WatchState {
    @SerialName("ok")
    VISIBLE,

    @SerialName("hidden")
    AMOUNTS_HIDDEN,

    @SerialName("locked")
    LOCKED,
}

/** Enough of the budget's currency settings for a watch to write amounts the way the phone does. */
@Serializable
data class WatchMoneyFormat(
    @SerialName("s") val symbol: String = "$",
    @SerialName("a") val symbolAfter: Boolean = false,
    @SerialName("sp") val space: Boolean = false,
    @SerialName("d") val fractionDigits: Int = 2,
    @SerialName("g") val group: String = ",",
    @SerialName("m") val decimal: String = ".",
)

@Serializable
data class WatchTransaction(
    @SerialName("p") val payee: String,
    @SerialName("a") val amountMinor: Long,
    @SerialName("d") val date: String,
)

/** A pinned category; a negative [leftMinor] means it's over. */
@Serializable
data class WatchCategory(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    @SerialName("b") val budgetedMinor: Long? = null,
    @SerialName("s") val spentMinor: Long? = null,
    @SerialName("l") val leftMinor: Long? = null,
    @SerialName("t") val recent: List<WatchTransaction> = emptyList(),
) {
    val over: Boolean get() = (leftMinor ?: 0) < 0

    /** How much of the budgeted amount is spent, from 0 to 1; full once it's over. */
    val progress: Float
        get() {
            val budgeted = budgetedMinor ?: return 0f
            val spent = spentMinor ?: return 0f
            if (over) return 1f
            if (budgeted <= 0) return 0f
            return (spent.toFloat() / budgeted).coerceIn(0f, 1f)
        }
}

/** A category quick add can file under, by id and name. */
@Serializable
data class WatchChoice(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
)

@Serializable
data class WatchBill(
    @SerialName("n") val name: String,
    @SerialName("d") val date: String,
    @SerialName("a") val amountMinor: Long? = null,
)

/**
 * What the phone sends a paired watch after each sync or budget change. Amounts are left out unless [state] is
 * [WatchState.VISIBLE], and a locked budget sends only its name.
 */
@Serializable
data class WatchSummary(
    @SerialName("v") val version: Int,
    @SerialName("st") val state: WatchState,
    @SerialName("bn") val budgetName: String,
    @SerialName("at") val sentAtEpochMs: Long,
    @SerialName("sy") val syncedAtEpochMs: Long? = null,
    @SerialName("tb") val toBudgetMinor: Long? = null,
    @SerialName("lf") val leftToSpendMinor: Long? = null,
    @SerialName("c") val categories: List<WatchCategory> = emptyList(),
    @SerialName("qc") val quickCategories: List<WatchChoice> = emptyList(),
    @SerialName("py") val recentPayees: List<String> = emptyList(),
    @SerialName("ac") val accountName: String? = null,
    @SerialName("bl") val bills: List<WatchBill> = emptyList(),
    @SerialName("q") val quickAdd: Boolean = true,
    @SerialName("f") val money: WatchMoneyFormat = WatchMoneyFormat(),
) {
    val amountsShown: Boolean get() = state == WatchState.VISIBLE

    /** Pinned categories first, then the recent ones, each once. */
    val categoryChoices: List<WatchChoice>
        get() = (categories.map { WatchChoice(it.id, it.name) } + quickCategories).distinctBy { it.id }

    companion object {
        const val VERSION = 1
        const val MAX_CATEGORIES = 6
        const val MAX_BILLS = 3
        const val MAX_RECENT = 3
    }
}
