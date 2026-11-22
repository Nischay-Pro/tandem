@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.runtime.Composable
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
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
                category.name,
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
        listOfNotNull(
            month?.incomeAvailableMinor?.let { "Income" to it },
            month?.totalBudgetedMinor?.let { "Budgeted" to kotlin.math.abs(it) },
            month?.totalSpentMinor?.let { "Spent" to kotlin.math.abs(it) },
        ).forEachIndexed { index, (label, amount) ->
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

/** On-budget accounts and their total, or the net worth total alone. */
@Composable
internal fun AccountsSummaryCard(
    module: HomeModule,
    state: AppState,
    navigation: AppNavigation,
    open: List<LiveAccountSummary>,
    money: @Composable (Long) -> Unit,
) {
    val shown =
        when (module) {
            HomeModule.NET_WORTH -> emptyList()
            else -> open.filterNot { it.offBudget }.take(4)
        }
    val total = (if (module == HomeModule.NET_WORTH) open else shown).sumOf { it.balanceMinor ?: 0 }
    SectionCard(stringResource(module.title()), stringResource(module.detail()), "Accounts", onClick = {
        navigation.selectTab(AppRoute.ACCOUNTS)
    }) {
        shown.forEach { account ->
            ListRow(
                account.name,
                leading =
                    RowLeading.Icon(
                        Lucide.Landmark,
                    ),
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
