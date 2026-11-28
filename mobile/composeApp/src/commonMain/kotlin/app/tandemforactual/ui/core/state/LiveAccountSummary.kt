package app.tandemforactual.ui.core.state

import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.CardNetwork

internal data class LiveAccountSummary(
    val id: String,
    val name: String,
    val balanceMinor: Long?,
    val kind: AccountKind,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountGroupId: String? = null,
    val accountGroupName: String? = null,
    val sortOrder: Double? = null,
    val kindChosen: Boolean = false,
    val cardNetwork: CardNetwork? = null,
)

/**
 * A new transaction's account: the one asked for, else the person's default, else the last or first on-budget one;
 * never a closed one.
 */
internal fun defaultNewTransactionAccount(
    accounts: List<LiveAccountSummary>,
    requestedId: String?,
    lastUsedId: String?,
    preferredId: String? = null,
): LiveAccountSummary? {
    val open = accounts.filterNot { it.closed }
    val onBudget = open.filterNot { it.offBudget }
    return open.firstOrNull { it.id == requestedId }
        ?: open.firstOrNull { it.id == preferredId }
        ?: onBudget.firstOrNull { it.id == lastUsedId }
        ?: onBudget.firstOrNull()
        ?: open.firstOrNull()
}
