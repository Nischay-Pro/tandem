@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.runtime.Composable
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.BudgetCurrencyConverter
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.ui.accountTypeIcon
import app.tandemforactual.ui.core.ui.maskedName
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import org.jetbrains.compose.resources.stringResource

/** The categories with the most spending this month. */
@Composable
internal fun CategoryProgressCard(
    module: HomeModule,
    state: AppState,
    navigation: AppNavigation,
    month: LiveBudgetMonthSummary?,
    settings: BudgetDisplaySettings?,
    money: @Composable (Long) -> Unit,
) {
    val categories =
        month
            ?.groups
            .orEmpty()
            .flatMap { it.categories }
            .filter { it.activityMinor < 0 }
            .sortedBy { it.activityMinor }
            .take(
                4,
            )
    SectionCard(stringResource(module.title()), stringResource(module.detail()), "Budget", onClick = {
        navigation.selectTab(AppRoute.BUDGET)
    }) {
        categories.forEachIndexed { index, category ->
            if (index > 0) DividerLine()
            ListRow(
                maskedName(category.name),
                if (category.balanceMinor <
                    0
                ) {
                    "Over by ${formatCurrency(-category.balanceMinor, settings)}"
                } else {
                    "Left ${formatCurrency(
                        category.balanceMinor,
                        settings,
                    )}".takeUnless { state.privacyModeEnabled }
                },
                trailingContent = {
                    money(-category.activityMinor)
                },
                leading = RowLeading.None,
                options = ListRowOptions(showChevron = false),
            )
        }
    }
}

/** The month's income, budgeted and net spending; refunds beyond spending show as nothing spent. */
internal fun monthSummaryRows(month: LiveBudgetMonthSummary?): List<Pair<String, Long>> =
    listOfNotNull(
        month?.totalIncomeMinor?.let { "Income" to it },
        month?.totalBudgetedMinor?.let { "Budgeted" to kotlin.math.abs(it) },
        month?.totalSpentMinor?.let { "Spent" to (-it).coerceAtLeast(0) },
    )

/** Income, budgeted and spent for the month. */
@Composable
internal fun MonthSummaryCard(
    module: HomeModule,
    navigation: AppNavigation,
    month: LiveBudgetMonthSummary?,
    money: @Composable (Long) -> Unit,
) {
    SectionCard(stringResource(module.title()), stringResource(module.detail()), "Reports", onClick = {
        navigation.selectTab(AppRoute.REPORTS)
    }) {
        monthSummaryRows(month).forEachIndexed { index, (label, amount) ->
            if (index > 0) DividerLine()
            ListRow(
                label,
                trailingContent = { money(amount) },
                leading = RowLeading.None,
                options = ListRowOptions(showChevron = false),
            )
        }
    }
}

/** The accounts the card lists and the total under them: every on-budget account, or for net worth just the total. */
internal fun homeAccountRows(
    module: HomeModule,
    open: List<LiveAccountSummary>,
    converter: BudgetCurrencyConverter,
): Pair<List<LiveAccountSummary>, Long> {
    val shown = if (module == HomeModule.NET_WORTH) emptyList() else open.filterNot { it.offBudget }
    val counted = if (module == HomeModule.NET_WORTH) open else shown
    return shown to converter.total(counted.map { it.id to it.balanceMinor }).totalMinor
}

/** Every open on-budget account and their total, or the net worth total alone. */
@Composable
internal fun AccountsSummaryCard(
    module: HomeModule,
    state: AppState,
    navigation: AppNavigation,
    open: List<LiveAccountSummary>,
    converter: BudgetCurrencyConverter,
    money: @Composable (Long) -> Unit,
) {
    val (shown, total) = homeAccountRows(module, open, converter)
    SectionCard(stringResource(module.title()), stringResource(module.detail()), "Accounts", onClick = {
        navigation.selectTab(AppRoute.ACCOUNTS)
    }) {
        shown.forEach { account ->
            ListRow(
                maskedName(account.name),
                leading = RowLeading.Account(maskedName(account.name), fallback = accountTypeIcon(account.name, account.kind)),
                trailingContent = { money(account.balanceMinor ?: 0) },
                onClick = {
                    state.selectedAccountId = account.id
                    navigation.open(AppRoute.ACCOUNT_DETAIL)
                },
            )
            DividerLine()
        }
        ListRow(
            "Total",
            trailingContent = { money(total) },
            leading = RowLeading.None,
            options = ListRowOptions(showChevron = false),
        )
    }
}
