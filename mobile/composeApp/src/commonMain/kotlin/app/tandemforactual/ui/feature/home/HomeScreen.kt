@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.budgetCurrencyConverter
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.ExperienceSettings
import app.tandemforactual.ui.core.state.HomeData
import app.tandemforactual.ui.core.state.HomeEntry
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.HomePlan
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.isTrackingBudget
import app.tandemforactual.ui.core.state.planHome
import app.tandemforactual.ui.core.state.trackingMonthFigures
import app.tandemforactual.ui.core.state.uncategorized
import app.tandemforactual.ui.core.ui.AutoAssignSheet
import app.tandemforactual.ui.core.ui.DebtOverviewCard
import app.tandemforactual.ui.core.ui.LiveUpcomingBillsCard
import app.tandemforactual.ui.core.ui.PlanningEditors
import app.tandemforactual.ui.core.ui.SavingsGoalsCard
import app.tandemforactual.ui.core.ui.SyncNoticeCard
import app.tandemforactual.ui.core.ui.activityTransactionLeading
import app.tandemforactual.ui.core.ui.rememberPlanningEditors
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.DashboardCard
import app.tandemforactual.ui.design.templates.PageDashboardLazyColumn
import app.tandemforactual.ui.design.templates.dashboardItems
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.home_more
import app.tandemforactual.ui.generated.resources.home_set_up
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.stringResource

/** What Home can show from the data on this device. */
internal fun homeData(
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
    uncategorized: List<TransactionItem> = transactions.uncategorized(),
): HomeData {
    val open = accounts.filterNot { it.closed }
    return HomeData(
        uncategorizedCount = uncategorized.size,
        hasSchedules = schedules.isNotEmpty(),
        hasBudget = month != null,
        hasAccounts = open.isNotEmpty(),
        hasActivity = transactions.isNotEmpty(),
        hasDebtAccounts = open.any { (it.balanceMinor ?: 0) < 0 },
    )
}

@Composable
internal fun HomeScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
    uncategorized: List<TransactionItem> = emptyList(),
    onSyncNow: () -> Unit = {},
) {
    val data = rememberHomeData(mobileSession, accounts, month, schedules, transactions, uncategorized)
    val editors = rememberPlanningEditors(state, mobileSession, month)
    val experience = state.experience ?: ExperienceSettings()
    val plan = pinReviewInbox(planHome(experience, state.interfaceMode, data))
    val showReview = data.uncategorizedCount > 0 && HomeModule.REVIEW_INBOX !in experience.hiddenModules
    var autoAssigning by remember { mutableStateOf(false) }
    val settings = mobileSession.budgetDisplaySettings
    val money: @Composable (Long) -> Unit = { amount ->
        PrivacyAmountText(formatCurrency(amount, settings), state.privacyModeEnabled, color = tandemPalette.muted)
    }
    val card: @Composable (HomeEntry) -> Unit = { entry ->
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
            uncategorized,
            settings,
            money,
        )
    }
    val cards =
        homeCards(
            pinned =
                listOfNotNull(
                    month?.toBudgetMinor?.let { toBudget ->
                        DashboardCard("home-to-budget", fullWidth = true) {
                            HomeToBudgetCard(
                                toBudget,
                                settings,
                                state.privacyModeEnabled,
                                onOpenBudget = { navigation.selectTab(AppRoute.BUDGET) },
                                onAutoAssign = { autoAssigning = true }.takeUnless { state.readOnlyMode },
                            )
                        }
                    },
                    DashboardCard("home-review", fullWidth = true) {
                        ReviewInboxCard(HomeModule.REVIEW_INBOX, state, navigation, uncategorized, money)
                    }.takeIf { showReview },
                    DashboardCard("home-sync", fullWidth = true) {
                        SyncNoticeCard(Lucide.CircleAlert, tandemPalette.warning, "Needs attention", mobileSession.syncError.orEmpty(), {
                            navigation.open(AppRoute.SYNC_CENTER)
                        })
                    }.takeIf { mobileSession.syncError != null && !mobileSession.isSyncing },
                ),
            plan = plan,
            card = card,
        )
    PageDashboardLazyColumn(
        isRefreshing = mobileSession.isSyncing,
        onRefresh = onSyncNow.takeIf { state.selectedBudgetId.isNotBlank() && mobileSession.isAuthenticated },
    ) { columns ->
        dashboardItems("home-dashboard", columns, TandemSpacing.large, cards)
    }
    AutoAssignSheet(autoAssigning, state, mobileSession, month) { autoAssigning = false }
}

/** The review inbox sits at the top of Home under the budget total, so it is left out of the arranged modules. */
internal fun pinReviewInbox(plan: HomePlan): HomePlan =
    HomePlan(
        plan.prominent.filterNot { it.module == HomeModule.REVIEW_INBOX },
        plan.more.filterNot { it.module == HomeModule.REVIEW_INBOX },
    )

@Composable
private fun rememberHomeData(
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
    schedules: List<LiveScheduleSummary>,
    transactions: List<TransactionItem>,
    uncategorized: List<TransactionItem>,
): HomeData =
    remember(accounts, month, schedules, transactions, uncategorized, mobileSession.planning.savingsGoals) {
        homeData(accounts, month, schedules, transactions, uncategorized).copy(
            savingsGoalCount = mobileSession.planning.savingsGoals.size,
            sharedBillCount = mobileSession.planning.sharedBills.size,
        )
    }

/** Home's cards in reading order: the pinned cards, the arranged modules, then the rest under a heading. */
private fun homeCards(
    pinned: List<DashboardCard>,
    plan: HomePlan,
    card: @Composable (HomeEntry) -> Unit,
): List<DashboardCard> {
    val prominent = plan.prominent.map { entry -> DashboardCard("home-${entry.module.name}") { card(entry) } }
    if (plan.more.isEmpty()) return pinned + prominent
    val title =
        DashboardCard("home-more-title", fullWidth = true) {
            Text(
                stringResource(Res.string.home_more),
                color = tandemPalette.muted,
                fontSize = TandemTypography.label,
                fontWeight = FontWeight.SemiBold,
            )
        }
    return pinned + prominent + title + plan.more.map { entry -> DashboardCard("home-more-${entry.module.name}") { card(entry) } }
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
    uncategorized: List<TransactionItem>,
    settings: BudgetDisplaySettings?,
    money: @Composable (Long) -> Unit,
) {
    val module = entry.module
    if (entry is HomeEntry.Setup) {
        HomeSetupCard(module, editors, navigation)
        return
    }
    val open = accounts.filterNot { it.closed }
    when (module) {
        HomeModule.UPCOMING_BILLS -> {
            LiveUpcomingBillsCard(state, navigation, schedules)
        }

        HomeModule.REVIEW_INBOX -> {
            ReviewInboxCard(module, state, navigation, uncategorized, money)
        }

        HomeModule.CATEGORY_PROGRESS -> {
            CategoryProgressCard(module, state, navigation, month, settings, money)
        }

        HomeModule.MONTH_SUMMARY -> {
            MonthSummaryCard(module, navigation, month, money)
        }

        HomeModule.BUDGET_SUMMARY -> {
            SectionCard(
                stringResource(module.title()),
                stringResource(module.detail()),
                "Open",
                onClick = { navigation.selectTab(AppRoute.BUDGET) },
            ) {
                val (label, amount) = homeBudgetFigure(state.isTrackingBudget, month)
                ListRow(
                    label,
                    trailingContent = { money(amount) },
                    leading = RowLeading.None,
                    options = ListRowOptions(showChevron = false),
                )
            }
        }

        HomeModule.DEBT_OVERVIEW -> {
            DebtOverviewCard(
                open.filter { (it.balanceMinor ?: 0) < 0 },
                mobileSession.planning.debtTerms,
                settings,
                state.privacyModeEnabled,
                money,
            ) { editors.debtAccount = it }
        }

        HomeModule.ACCOUNTS, HomeModule.NET_WORTH -> {
            AccountsSummaryCard(
                module,
                state,
                navigation,
                open,
                mobileSession.budgetCurrencyConverter(state),
                money,
            )
        }

        HomeModule.RECENT_ACTIVITY -> {
            RecentActivityCard(module, state, navigation, transactions, money)
        }

        HomeModule.SHARED_BILLS -> {
            SharedBillsHomeCard(module, state, mobileSession, navigation)
        }

        HomeModule.SAVINGS_GOALS -> {
            SavingsGoalsCard(
                mobileSession.planning.savingsGoals,
                month,
                accounts,
                settings,
                platformTodayIsoDate(),
                state.privacyModeEnabled,
                editors::editGoal,
            )
        }
    }
}

/** A Home module that still needs setting up, with a button to set it up. */
@Composable
private fun HomeSetupCard(
    module: HomeModule,
    editors: PlanningEditors,
    navigation: AppNavigation,
) {
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
}

/** The first few uncategorized transactions waiting for review. */
@Composable
private fun ReviewInboxCard(
    module: HomeModule,
    state: AppState,
    navigation: AppNavigation,
    items: List<TransactionItem>,
    money: @Composable (Long) -> Unit,
) {
    SectionCard(
        stringResource(module.title()),
        "${items.size} to review",
        "Review",
        onClick = { navigation.open(AppRoute.REVIEW_TRANSACTIONS) },
    ) {
        items.take(3).forEachIndexed { index, item ->
            if (index > 0) DividerLine()
            ListRow(
                item.payee,
                listOf(item.dateLabel, item.account).filter(String::isNotBlank).joinToString(" · "),
                leading = activityTransactionLeading(item),
                trailingContent = { money(item.amountMinorUnits) },
                onClick = {
                    openTransaction(state, navigation, item, isLive = true)
                },
            )
        }
    }
}

/** The latest few transactions. */
@Composable
private fun RecentActivityCard(
    module: HomeModule,
    state: AppState,
    navigation: AppNavigation,
    transactions: List<TransactionItem>,
    money: @Composable (Long) -> Unit,
) {
    SectionCard(stringResource(module.title()), null, "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
        transactions.take(4).forEachIndexed { index, item ->
            if (index > 0) DividerLine()
            ListRow(item.payee, item.dateLabel, trailingContent = {
                money(item.amountMinorUnits)
            }, onClick = { openTransaction(state, navigation, item, isLive = true) })
        }
    }
}

/** Where a module's skippable setup lives when it isn't a sheet on Home. */
internal fun HomeModule.setupRoute(): AppRoute = if (this == HomeModule.DEBT_OVERVIEW) AppRoute.ACCOUNTS else AppRoute.BUDGET

/** The budget card's one figure: to budget for an envelope budget, or this month's savings for a tracking one. */
internal fun homeBudgetFigure(
    isTrackingBudget: Boolean,
    month: LiveBudgetMonthSummary?,
): Pair<String, Long> =
    if (isTrackingBudget) {
        val figures = trackingMonthFigures(month?.groups.orEmpty())
        (if (figures.savedMinor < 0) "Overspent" else "Saved") to figures.savedMinor
    } else {
        "To budget" to (month?.toBudgetMinor ?: 0)
    }
