package app.tandemforactual.ui.core.state

internal data class LiveAccountSummary(
    val id: String,
    val name: String,
    val balanceMinor: Long?,
    val type: String?,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountGroupId: String? = null,
    val accountGroupName: String? = null,
    val sortOrder: Double? = null,
)

/** A new transaction's account: the one asked for, else the last or first on-budget one; never a closed one. */
internal fun defaultNewTransactionAccount(
    accounts: List<LiveAccountSummary>,
    requestedId: String?,
    lastUsedId: String?,
): LiveAccountSummary? {
    val open = accounts.filterNot { it.closed }
    val onBudget = open.filterNot { it.offBudget }
    return open.firstOrNull { it.id == requestedId }
        ?: onBudget.firstOrNull { it.id == lastUsedId }
        ?: onBudget.firstOrNull()
        ?: open.firstOrNull()
}
