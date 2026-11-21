@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.home_more
import app.tandemforactual.ui.generated.resources.home_set_up
import app.tandemforactual.ui.generated.resources.nav_settings
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
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
) {
    val data = remember(accounts, month, schedules, transactions) { homeData(accounts, month, schedules, transactions) }
    val plan = planHome(state.experience ?: ExperienceSettings(), state.interfaceMode, data)
    val settings = mobileSession.budgetDisplaySettings
    val money: @Composable (Long) -> Unit = { amount ->
        PrivacyAmountText(formatCurrency(amount, settings), state.privacyModeEnabled, color = tandemPalette.muted)
    }
    PageLazyColumn {
        item(key = "home-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    budgetDisplayNameForHeader(state.selectedBudget),
                    Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.sectionTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                AdvancedModeSwitch(state)
                if (AppRoute.SETTINGS !in navigationRoutes(state.interfaceMode)) {
                    TandemIconButton(Lucide.Settings, stringResource(Res.string.nav_settings)) { navigation.selectTab(AppRoute.SETTINGS) }
                }
            }
        }
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
            ) { HomeEntryCard(entry, state, navigation, accounts, month, schedules, transactions, settings, money) }
        }
        if (plan.more.isNotEmpty()) {
            item(key = "home-more-title") {
                Text(
                    stringResource(Res.string.home_more),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.label,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            plan.more.forEach { entry ->
                item(
                    key = "home-more-${entry.module.name}",
                ) { HomeEntryCard(entry, state, navigation, accounts, month, schedules, transactions, settings, money) }
            }
        }
    }
}

@Composable
private fun HomeEntryCard(
    entry: HomeEntry,
    state: AppState,
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
        SurfaceCard {
            Text(
                stringResource(module.title()),
                color = tandemPalette.text,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(module.detail()), color = tandemPalette.muted, fontSize = TandemTypography.caption)
            SecondaryButton(stringResource(Res.string.home_set_up), Modifier.fillMaxWidth()) { navigation.open(module.setupRoute()) }
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
                        "Left ${formatCurrency(
                            category.balanceMinor,
                            settings,
                        )}".takeUnless { state.privacyModeEnabled },
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
                    month?.totalBudgetedMinor?.let { "Budgeted" to it },
                    month?.totalSpentMinor?.let { "Spent" to it },
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
        HomeModule.ACCOUNTS, HomeModule.DEBT_OVERVIEW, HomeModule.NET_WORTH -> {
            val shown =
                when (module) {
                    HomeModule.DEBT_OVERVIEW -> open.filter { (it.balanceMinor ?: 0) < 0 }
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
        HomeModule.SAVINGS_GOALS -> Unit
    }
}

/** Where a module's skippable setup lives. */
internal fun HomeModule.setupRoute(): AppRoute =
    when (this) {
        HomeModule.DEBT_OVERVIEW -> AppRoute.ACCOUNTS
        else -> AppRoute.BUDGET
    }
