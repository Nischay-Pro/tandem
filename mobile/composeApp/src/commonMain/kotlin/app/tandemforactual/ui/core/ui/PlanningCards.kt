@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

internal fun BudgetDisplaySettings?.amountOptions() =
    TextFieldOptions(
        placeholder = "0",
        currencySymbol = this?.currencySymbol ?: "$",
        currencySymbolAfter = this?.currencySymbolPosition == "after",
        currencySymbolSpace = this?.currencySpaceBetweenAmountAndSymbol == true,
        amountFractionDigits = this?.currencyFractionDigits ?: 2,
    )

/** Parses a percentage such as "19.99" into basis points exactly, without floating point. */
internal fun parseRateBasisPoints(value: String): Int? {
    val text = value.trim().removeSuffix("%").trim()
    if (!Regex("^\\d{1,3}(\\.\\d{1,2})?$").matches(text)) return null
    val (whole, fraction) = (text.split('.') + "").let { it[0] to it[1].padEnd(2, '0') }
    return (whole.toInt() * 100 + fraction.toInt()).takeIf { it <= 10_000 }
}

internal fun formatRateBasisPoints(basisPoints: Int): String = "${basisPoints / 100}.${(basisPoints % 100).toString().padStart(2, '0')}%"

/** How much a goal has: its linked category's balance, else its linked account's balance, else unknown. */
internal fun SavingsGoal.saved(
    month: LiveBudgetMonthSummary?,
    accounts: List<LiveAccountSummary>,
): Long? =
    (
        categoryId?.let { id ->
            month
                ?.groups
                ?.flatMap { it.categories }
                ?.firstOrNull { it.id == id }
                ?.balanceMinor
        }
            ?: accountId?.let { id -> accounts.firstOrNull { it.id == id }?.balanceMinor }
    )?.coerceAtLeast(0)

@Composable
internal fun DebtOverviewCard(
    debts: List<LiveAccountSummary>,
    terms: List<DebtTerms>,
    settings: BudgetDisplaySettings?,
    privacy: Boolean,
    money: @Composable (Long) -> Unit,
    onEdit: (LiveAccountSummary) -> Unit,
) {
    SectionCard("Debt overview", "Balances owed across your accounts") {
        debts.forEachIndexed { index, account ->
            if (index > 0) DividerLine()
            val owed = -(account.balanceMinor ?: 0)
            val term = terms.firstOrNull { it.accountId == account.id }
            val apr = term?.aprBasisPoints
            val minimum = term?.minimumPaymentMinor
            val detail =
                when {
                    apr == null || minimum == null -> "Add the APR and minimum payment to estimate payoff"
                    privacy -> "${formatRateBasisPoints(apr)} APR"
                    else ->
                        PlanningMath.payoff(owed, apr, minimum)?.let {
                            "Estimate: ${it.months} months at the minimum, ${formatCurrency(it.totalInterestMinor, settings)} interest"
                        } ?: "The minimum doesn’t cover the interest at ${formatRateBasisPoints(apr)} APR"
                }
            ListRow(account.name, detail, leading = RowLeading.Icon(Lucide.CreditCard), trailingContent = {
                money(owed)
            }, onClick = { onEdit(account) })
        }
    }
}

/** Opens the planning sheets from Home and saves through the session. */
@Composable
internal fun rememberPlanningEditors(
    state: AppState,
    mobileSession: TandemMobileSession,
    month: LiveBudgetMonthSummary?,
): PlanningEditors {
    val scope = rememberCoroutineScope()
    val editors = remember { PlanningEditors() }
    val settings = mobileSession.budgetDisplaySettings
    SavingsGoalSheet(
        editors.goal,
        editors.goalVisible,
        month,
        settings,
        onSave = { goal ->
            scope.launch { mobileSession.planning.saveSavingsGoal(state.selectedBudgetId, goal) }
            editors.goalVisible = false
        },
        onDelete = { goal ->
            scope.launch { mobileSession.planning.deleteSavingsGoal(state.selectedBudgetId, goal.id) }
            editors.goalVisible = false
        },
        onDismiss = { editors.goalVisible = false },
    )
    DebtTermsSheet(
        editors.debtAccount,
        mobileSession.planning.debtTerms.firstOrNull { it.accountId == editors.debtAccount?.id },
        settings,
        onSave = { terms ->
            scope.launch { mobileSession.planning.saveDebtTerms(state.selectedBudgetId, terms) }
            editors.debtAccount = null
        },
        onDismiss = { editors.debtAccount = null },
    )
    return editors
}

internal class PlanningEditors {
    var goal by mutableStateOf<SavingsGoal?>(null)
    var goalVisible by mutableStateOf(false)
    var debtAccount by mutableStateOf<LiveAccountSummary?>(null)

    fun editGoal(goal: SavingsGoal?) {
        this.goal = goal
        goalVisible = true
    }
}

internal fun planningSyncDetail(status: PlanningSyncStatus): Pair<String, String>? =
    when (status) {
        PlanningSyncStatus.OFF -> null
        PlanningSyncStatus.SYNCED -> "Planning details synced" to "Savings goals and debt details match your other devices."
        PlanningSyncStatus.WAITING ->
            "Planning details saved on this device" to
                "They’ll sync when Tandem’s extension service is reachable. Your budget syncs as usual."
        PlanningSyncStatus.NEEDS_RECOVERY_KEY ->
            "Enter your planning recovery key" to
                "Another device already shares planning details for this budget. Enter its recovery key in Settings."
    }
