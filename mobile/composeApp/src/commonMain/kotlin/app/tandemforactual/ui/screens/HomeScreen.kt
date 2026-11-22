@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.PrivacyAmountText
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.home_more
import app.tandemforactual.ui.generated.resources.home_set_up
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.stringResource

/** What Home can show from the data on this device. */
internal fun homeData(
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
): HomeData {
    val open = accounts.filterNot { it.closed }
    return HomeData(
        uncategorizedCount = transactions.uncategorized().size,
        hasSchedules = schedules.isNotEmpty(),
        hasBudget = month != null,
        hasAccounts = open.isNotEmpty(),
        hasActivity = transactions.isNotEmpty(),
        hasDebtAccounts = open.any { (it.balanceMinor ?: 0) < 0 },
    )
}

private fun List<TransactionItem>.uncategorized() = filter { it.categoryId == null && !it.isTransfer && !it.isSplitParent }

@Composable
internal fun HomeScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
    onSyncNow: () -> Unit = {},
) {
    val data =
        remember(accounts, month, schedules, transactions, mobileSession.savingsGoals) {
            homeData(accounts, month, schedules, transactions).copy(savingsGoalCount = mobileSession.savingsGoals.size)
        }
    val editors = rememberPlanningEditors(state, mobileSession, month)
    val plan = planHome(state.experience ?: ExperienceSettings(), state.interfaceMode, data)
    val settings = mobileSession.budgetDisplaySettings
    val money: @Composable (Long) -> Unit = { amount ->
        PrivacyAmountText(formatCurrency(amount, settings), state.privacyModeEnabled, color = tandemPalette.muted)
    }
    PageLazyColumn(
        isRefreshing = mobileSession.isSyncing,
        onRefresh = onSyncNow.takeIf { state.selectedBudgetId.isNotBlank() && mobileSession.isAuthenticated },
    ) {
        if (mobileSession.syncError != null && !mobileSession.isSyncing) {
            item(key = "home-sync") {
                SyncNoticeCard(Lucide.CircleAlert, tandemPalette.warning, "Needs attention", mobileSession.syncError.orEmpty(), {
                    navigation.open(AppRoute.SYNC_CENTER)
                })
            }
        }
        plan.prominent.forEach { entry ->
            item(
                key = "home-${entry.module.name}",
            ) {
                Box(tandemListItemModifier(tandemReduceMotion)) {
                    HomeEntryCard(entry, state, mobileSession, editors, navigation, accounts, month, schedules, transactions, settings, money)
                }
            }
        }
        if (plan.more.isNotEmpty()) {
            item(key = "home-more-title") {
                Text(
                    modifier = tandemListItemModifier(tandemReduceMotion),
                    text =
                    stringResource(Res.string.home_more),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            plan.more.forEach { entry ->
                item(
                    key = "home-more-${entry.module.name}",
                ) {
                    Box(tandemListItemModifier(tandemReduceMotion)) {
                    HomeEntryCard(
                        entry,
                        state,
                        mobileSession,
                        editors,
                        navigation,
                        accounts,
                        month,
                        schedules,
                        transactions,
                        settings,
                        money,
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEntryCard(
    entry: HomeEntry,
    state: AppState,
    mobileSession: TandemMobileSession,
    editors: PlanningEditors,
    navigation: AppNavigation,
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
    settings: app.tandemforactual.data.store.BudgetDisplaySettings?,
    money: @Composable (Long) -> Unit,
) {
    val module = entry.module
    if (entry is HomeEntry.Setup) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Text(
                    stringResource(module.title()),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(module.detail()), color = tandemPalette.muted, fontSize = TandemTypography.caption)
                SecondaryButton(stringResource(Res.string.home_set_up), Modifier.fillMaxWidth()) {
                    if (module == HomeModule.SAVINGS_GOALS) editors.editGoal(null) else navigation.selectTab(module.setupRoute())
                }
            }
        }
        return
    }
    val open = accounts.filterNot { it.closed }
    when (module) {
        HomeModule.UPCOMING_BILLS -> LiveUpcomingBillsCard(state, navigation, schedules)
        HomeModule.REVIEW_INBOX -> {
            val items = transactions.uncategorized()
            SectionCard(
                stringResource(module.title()),
                "${items.size} to review",
                "See all",
                onClick = { navigation.selectTab(AppRoute.ACTIVITY) },
            ) {
                items.take(3).forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    ListRow(
                        item.payee,
                        item.dateLabel,
                        leading =
                            RowLeading.Icon(
                                Lucide.Inbox,
                            ),
                        trailingContent = { money(item.amountMinorUnits) },
                        onClick = {
                            openTransaction(state, navigation, item, isLive = true)
                        },
                    )
                }
            }
        }
        HomeModule.CATEGORY_PROGRESS -> {
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
        HomeModule.MONTH_SUMMARY -> {
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
        HomeModule.BUDGET_SUMMARY ->
            SectionCard(
                stringResource(module.title()),
                stringResource(module.detail()),
                "Open",
                onClick = { navigation.selectTab(AppRoute.BUDGET) },
            ) {
                ListRow(
                    "To budget",
                    trailingContent = { money(month?.toBudgetMinor ?: 0) },
                    leading = RowLeading.None,
                    options = ListRowOptions(showChevron = false),
                )
            }
        HomeModule.DEBT_OVERVIEW ->
            DebtOverviewCard(
                open.filter { (it.balanceMinor ?: 0) < 0 },
                mobileSession.debtTerms,
                settings,
                state.privacyModeEnabled,
                money,
            ) { editors.debtAccount = it }
        HomeModule.ACCOUNTS, HomeModule.NET_WORTH -> {
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
        HomeModule.RECENT_ACTIVITY ->
            SectionCard(stringResource(module.title()), null, "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                transactions.take(4).forEachIndexed { index, item ->
                    if (index > 0) DividerLine()
                    ListRow(item.payee, item.dateLabel, trailingContent = {
                        money(item.amountMinorUnits)
                    }, onClick = { openTransaction(state, navigation, item, isLive = true) })
                }
            }
        HomeModule.SAVINGS_GOALS ->
            SavingsGoalsCard(
                mobileSession.savingsGoals,
                month,
                accounts,
                settings,
                platformTodayIsoDate(),
                state.privacyModeEnabled,
                editors::editGoal,
            )
    }
}

/** Where a module's skippable setup lives when it isn't a sheet on Home. */
internal fun HomeModule.setupRoute(): AppRoute = if (this == HomeModule.DEBT_OVERVIEW) AppRoute.ACCOUNTS else AppRoute.BUDGET
