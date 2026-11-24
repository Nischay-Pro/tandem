@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.format.keepTogether
import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.liveAccountGroupLabel

internal data class AccountListItemUi(
    val id: String,
    val name: String,
    val typeLabel: String,
    val status: String,
    val balance: String,
    val offBudget: Boolean,
    val closed: Boolean = false,
    val accountType: String? = null,
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
        account.closed -> "Closed"
        else ->
            buildList {
                if (account.offBudget) add("Off budget")
                val syncState =
                    lastSyncedAtEpochMs?.let { "Last synced ${keepTogether(formatDateAtEpochMilliseconds(it))}" }
                        ?: "Not synced yet"
                add(syncState)
            }.joinToString(" · ")
    }

internal fun accountListTypeLabel(accountType: String?): String =
    when (accountType?.trim()?.lowercase()) {
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

internal fun accountBalanceAccessibilityDescription(
    balanceMinor: Long?,
    formattedBalance: String,
): String =
    when {
        balanceMinor == null -> "Balance unavailable"
        balanceMinor < 0L -> "Negative balance, $formattedBalance"
        else -> "Balance, $formattedBalance"
    }

internal fun accountGroupOrder(groupName: String): Int {
    val name = groupName.trim().lowercase()
    val type = name.substringAfter("·", name).trim()
    val typeOrder =
        when (type) {
            "cash" -> 0
            "checking" -> 1
            "savings" -> 2
            "credit" -> 3
            "loan" -> 4
            "mortgage" -> 5
            "debt" -> 6
            "investment" -> 7
            "other" -> 8
            else -> 9
        }
    return when {
        name == "closed" -> Int.MAX_VALUE
        else -> typeOrder
    }
}

internal fun sortAccountsForList(accounts: List<LiveAccountSummary>): List<LiveAccountSummary> =
    accounts.sortedWith(
        compareBy(
            { accountGroupOrder(liveAccountGroupLabel(it)) },
            { liveAccountGroupLabel(it).lowercase() },
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
): List<LiveAccountSummary> {
    val within: Comparator<LiveAccountSummary> =
        when (sort) {
            AccountSort.NAME_ASCENDING -> compareBy { it.name.lowercase() }
            AccountSort.NAME_DESCENDING -> compareByDescending { it.name.lowercase() }
            AccountSort.BALANCE_ASCENDING -> compareBy { it.balanceMinor ?: Long.MAX_VALUE }
            AccountSort.BALANCE_DESCENDING -> compareByDescending { it.balanceMinor ?: Long.MIN_VALUE }
        }
    return accounts.sortedWith(
        compareBy<LiveAccountSummary>({ accountGroupOrder(liveAccountGroupLabel(it)) }, { liveAccountGroupLabel(it).lowercase() })
            .then(within),
    )
}

/** The sort [accounts] are already in, so the menu can tick it; null once they were arranged by hand. */
internal fun activeAccountSort(accounts: List<LiveAccountSummary>): AccountSort? {
    if (accounts.size < 2) return null
    val ids = accounts.map { it.id }
    return AccountSort.entries.firstOrNull { sort -> sortAccountsWithinGroups(accounts, sort).map { it.id } == ids }
}
