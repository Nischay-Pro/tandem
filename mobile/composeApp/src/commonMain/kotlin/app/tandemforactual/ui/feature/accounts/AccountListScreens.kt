@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.core.format.formatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.liveAccountGroupLabel
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.WalletCards

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
                    lastSyncedAtEpochMs?.let { "Last synced ${formatDateAtEpochMilliseconds(it)}" }
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

internal fun accountTypeIcon(
    accountName: String,
    accountType: String? = null,
): ImageVector {
    val name = accountName.lowercase()
    val type = accountType.orEmpty().lowercase()
    return when {
        "savings" in name || "saving" in name -> Lucide.PiggyBank
        "checking" in name || "chequing" in name -> Lucide.Landmark
        listOf("credit", "card", "visa", "mastercard", "amex", "discover").any { it in name } -> Lucide.CreditCard
        listOf("loan", "mortgage", "debt").any { it in name } -> Lucide.HandCoins
        listOf("investment", "brokerage", "401k", "403b", "ira", "retirement", "stocks").any { it in name } ->
            Lucide.ChartNoAxesCombined
        "checking" in type || "chequing" in type -> Lucide.Landmark
        "savings" in type || "saving" in type -> Lucide.PiggyBank
        listOf("credit", "card").any { it in type } -> Lucide.CreditCard
        listOf("loan", "debt").any { it in type } -> Lucide.HandCoins
        listOf("tracking", "investment").any { it in type } -> Lucide.ChartNoAxesCombined
        else -> Lucide.WalletCards
    }
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
