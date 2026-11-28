@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.core.format.formatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.format.keepTogether
import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.liveAccountGroupLabel

internal data class AccountListItemUi(
    val id: String,
    val name: String,
    val kind: AccountKind,
    val status: String,
    val balance: String,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val balanceMinor: Long? = null,
)

internal data class AccountListGroupUi(
    val name: String,
    val total: String,
    val accounts: List<AccountListItemUi>,
)

internal data class AccountListUi(
    val groups: List<AccountListGroupUi>,
)

internal fun accountListSubtitle(
    account: LiveAccountSummary,
    lastSyncedAtEpochMs: Long?,
): String =
    when {
        account.closed -> {
            "Closed"
        }

        else -> {
            buildList {
                if (account.offBudget) add("Off budget")
                val syncState =
                    lastSyncedAtEpochMs?.let { "Last synced ${keepTogether(formatDateAtEpochMilliseconds(it))}" }
                        ?: "Not synced yet"
                add(syncState)
            }.joinToString(" · ")
        }
    }

internal fun accountBalanceAccessibilityDescription(
    balanceMinor: Long?,
    formattedBalance: String,
): String =
    when {
        balanceMinor == null -> "Balance unavailable"
        balanceMinor < 0L -> "Negative balance, $formattedBalance"
        else -> "Balance, $formattedBalance"
    }

/** Groups follow the order of [AccountKind], with closed accounts last. */
internal fun accountGroupOrder(groupName: String): Int {
    val name = groupName.trim().lowercase()
    if (name == "closed") return Int.MAX_VALUE
    return AccountKind.entries.indexOfFirst { it.groupLabel.lowercase() == name }.takeIf { it >= 0 } ?: AccountKind.entries.size
}

internal fun sortAccountsForList(accounts: List<LiveAccountSummary>): List<LiveAccountSummary> =
    accounts.sortedWith(
        compareBy(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
            ::actualSection,
            { it.sortOrder ?: Double.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )

internal fun orderAccountsForList(
    accounts: List<LiveAccountSummary>,
    orderedIds: List<String>,
): List<LiveAccountSummary> {
    val positions = orderedIds.withIndex().associate { it.value to it.index }
    return sortAccountsForList(accounts).sortedWith(
        compareBy(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
            ::actualSection,
            { positions[it.id] ?: Int.MAX_VALUE },
            { it.name.lowercase() },
        ),
    )
}

internal fun reorderAccountsWithinSlots(
    accountIds: List<String>,
    movableIds: List<String>,
    reorderedMovableIds: List<String>,
): List<String> {
    val movableSet = movableIds.toSet()
    val movablePositions = accountIds.indices.filter { accountIds[it] in movableSet }
    if (movablePositions.size != reorderedMovableIds.size) return accountIds
    val reordered = accountIds.toMutableList()
    movablePositions.zip(reorderedMovableIds).forEach { (position, accountId) -> reordered[position] = accountId }
    return reordered
}

/** Applies [sort] within each account group, keeping the groups and, for ties, the current order. */
internal fun sortAccountsWithinGroups(
    accounts: List<LiveAccountSummary>,
    sort: AccountSort,
): List<LiveAccountSummary> =
    accounts.sortedWith(
        compareBy<LiveAccountSummary>(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
            ::actualSection,
        ).then(sort.comparator()),
    )

/**
 * Actual's sidebar section: on budget, off budget, then closed. Actual numbers accounts across all sections, so lists
 * split by section first or on- and off-budget accounts interleave.
 */
private fun actualSection(account: LiveAccountSummary): Int =
    when {
        account.closed -> 2
        account.offBudget -> 1
        else -> 0
    }

/** The accounts in the order Actual keeps them, by `sort_order` then name, which a sort is applied on top of. */
internal fun actualAccountOrder(accounts: List<LiveAccountSummary>): List<String> =
    accounts.sortedWith(compareBy({ it.sortOrder ?: Double.MAX_VALUE }, { it.name })).map { it.id }

/**
 * The order to save for [sort]: sorted within Actual's own sections, on budget, off budget and closed, so Actual's
 * sidebar reads sorted too. Tandem's type groups follow the saved order, so they stay sorted as well.
 */
internal fun sortAccountsForBudget(
    accounts: List<LiveAccountSummary>,
    sort: AccountSort,
): List<LiveAccountSummary> =
    accounts.sortedWith(
        compareBy(::actualSection).then(sort.comparator()),
    )

private fun AccountSort.comparator(): Comparator<LiveAccountSummary> =
    when (this) {
        AccountSort.NAME_ASCENDING -> compareBy { it.name.lowercase() }
        AccountSort.NAME_DESCENDING -> compareByDescending { it.name.lowercase() }
        AccountSort.BALANCE_ASCENDING -> compareBy { it.balanceMinor ?: Long.MAX_VALUE }
        AccountSort.BALANCE_DESCENDING -> compareByDescending { it.balanceMinor ?: Long.MIN_VALUE }
    }

/** The sort [accounts] are already in, so the menu can tick it; null once they were arranged by hand. */
internal fun activeAccountSort(accounts: List<LiveAccountSummary>): AccountSort? {
    if (accounts.size < 2) return null
    val ids = accounts.map { it.id }
    return AccountSort.entries.firstOrNull { sort -> sortAccountsWithinGroups(accounts, sort).map { it.id } == ids }
}
