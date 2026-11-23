@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.design.buttons.TandemBackButton
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalCloseButton
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.X

@Composable
internal fun AppHeader(
    route: AppRoute,
    state: AppState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSync: () -> Unit,
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onBudget: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
    actualConnected: Boolean,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    activitySelectionTotalMinor: Long?,
    conflictTitle: String?,
    compactLandscape: Boolean = false,
    onAddTransaction: (() -> Unit)? = null,
    addTransactionEnabled: Boolean = true,
    syncCaption: String? = null,
    onRefreshBudgets: (() -> Unit)? = null,
    refreshingBudgets: Boolean = false,
    onOpenMenu: (() -> Unit)? = null,
) {
    val headerFontScale = LocalDensity.current.fontScale
    val budgetHeaderSlotHeight = maxOf(88.dp, 88.dp * headerFontScale)
    val headerMinHeight =
        if (route == AppRoute.BUDGET || route == AppRoute.BUDGET_CHOOSER) {
            if (compactLandscape && headerFontScale < 1.5f) 64.dp else budgetHeaderSlotHeight
        } else {
            64.dp
        }
    val headerTitle = appHeaderTitle(route, state, liveAccounts, liveBudgetMonth, activitySelectionTotalMinor)
    if (route == AppRoute.BUDGET) {
        BudgetAppHeader(
            route,
            state,
            headerTitle,
            headerMinHeight,
            syncStatus,
            pendingWriteStatuses,
            onSync,
            onBudget,
            syncCaption,
            onAddTransaction,
            addTransactionEnabled,
            onOpenMenu,
        )
    } else {
        StandardAppHeader(
            route,
            state,
            headerTitle,
            headerMinHeight,
            showBack,
            onBack,
            onOpenMenu,
            onBudget,
        ) {
            AppHeaderActions(
                route,
                state,
                syncStatus,
                pendingWriteStatuses,
                onSync,
                onBack,
                onBudget,
                onOpenRoute,
                actualConnected,
                onRefreshBudgets,
                refreshingBudgets,
            )
        }
    }
}

/** Header row for every route other than Budget: back and menu buttons, title, and route actions. */
@Composable
private fun StandardAppHeader(
    route: AppRoute,
    state: AppState,
    headerTitle: String,
    headerMinHeight: Dp,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onBudget: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = headerMinHeight)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack && !route.isTransactionForm()) {
            TandemBackButton(onClick = onBack)
            Spacer(Modifier.width(6.dp))
        }
        HeaderMenuButton(route, onOpenMenu)
        Column(
            modifier =
                Modifier.weight(1f).clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = route.isTab() && route != AppRoute.ACCOUNTS,
                    role = Role.Button,
                    onClickLabel = "Choose budget",
                    onClick = onBudget,
                ),
        ) {
            if (headerTitle.isNotBlank()) {
                Text(
                    text = headerTitle,
                    color = palette.text,
                    fontSize = TandemTypography.periodTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = tandemAlloFamily(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Menu button shown on tab routes when a menu handler is provided. */
@Composable
internal fun HeaderMenuButton(
    route: AppRoute,
    onOpenMenu: (() -> Unit)?,
) {
    if (onOpenMenu != null && route.isTab()) {
        TandemIconButton(Lucide.Menu, "Menu", containerColor = tandemPalette.surface, onClick = onOpenMenu)
        Spacer(Modifier.width(TandemSpacing.medium))
    }
}

private fun appHeaderTitle(
    route: AppRoute,
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    activitySelectionTotalMinor: Long?,
): String {
    val headerCategoryGroups = liveBudgetMonth?.groups.orEmpty()
    val headerGroup =
        headerCategoryGroups.firstOrNull { it.id == state.selectedCategoryGroupId }
            ?: headerCategoryGroups.firstOrNull()
    val headerGroupName = headerGroup?.name
    val headerCategoryName =
        headerCategoryGroups
            .asSequence()
            .flatMap { it.categories.asSequence() }
            .firstOrNull { it.id == state.selectedCategoryId }
            ?.name
    return when (route) {
        AppRoute.NEW_TRANSACTION -> "Add transaction"
        AppRoute.BUDGET -> state.selectedBudget.ifBlank { "Choose a budget" }
        AppRoute.CATEGORY_GROUP ->
            headerGroupName ?: "Category group"
        AppRoute.CATEGORY_DETAIL ->
            headerCategoryName ?: "Category"
        AppRoute.ACCOUNT_DETAIL ->
            liveAccounts.firstOrNull { it.id == state.selectedAccountId }?.name ?: "Account"
        AppRoute.TRANSACTION_DETAIL -> state.transactionPayee.ifBlank { route.title }
        AppRoute.ACTIVITY,
        AppRoute.ACTIVITY_SEARCH,
        -> activitySelectionTotalMinor?.let(state::formatCurrency) ?: route.title
        AppRoute.CONFLICT_REVIEW -> route.title
        AppRoute.REPORT_DETAIL ->
            ReportKind.entries
                .firstOrNull { it.name == state.selectedReportKindName }
                ?.title ?: route.title
        AppRoute.CUSTOM_REPORT -> state.selectedCustomReportName.ifBlank { "New report" }
        AppRoute.SHARED_BILL_EDIT -> if (state.selectedSharedBillId.isBlank()) "Add shared bill" else route.title
        else -> route.title
    }
}

/** Route-specific actions at the end of the standard header row. */
@Composable
private fun AppHeaderActions(
    route: AppRoute,
    state: AppState,
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onSync: () -> Unit,
    onBack: () -> Unit,
    onBudget: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
    actualConnected: Boolean,
    onRefreshBudgets: (() -> Unit)?,
    refreshingBudgets: Boolean,
) {
    val palette = tandemPalette
    when (route) {
        AppRoute.CHOOSE_PAYEE -> {
            if (state.selectedBudgetId.isNotBlank() && state.transactionType != "Transfer") {
                TandemIconButton(
                    Lucide.Tags,
                    "Manage payees",
                    onClick = { onOpenRoute(AppRoute.MANAGE_PAYEES) },
                )
            }
        }
        AppRoute.ACCOUNTS -> AccountsHeaderActions(route, state, onBudget, onOpenRoute)
        AppRoute.SWITCH_BUDGET -> {
            if (onRefreshBudgets != null) {
                if (refreshingBudgets) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        TandemLoadingIndicator(Modifier.size(20.dp), palette.accent, 2.dp)
                    }
                } else {
                    TandemIconButton(Lucide.RefreshCw, "Refresh budgets", onClick = onRefreshBudgets)
                }
            }
        }
        AppRoute.SYNC_CENTER -> {
            TandemIconButton(
                Lucide.Settings,
                "Sync settings",
                onClick = { onOpenRoute(AppRoute.SYNC_PREFERENCES) },
            )
        }
        AppRoute.BUDGET_CHOOSER -> {
            Text(
                when {
                    actualConnected -> "Actual budgets"
                    state.selectedBudgetId.isNotBlank() -> "Offline copy"
                    else -> "Connect to Actual"
                },
                color = palette.muted,
                fontSize = TandemTypography.micro,
                fontWeight = FontWeight.Bold,
            )
        }
        else -> {
            if (route == AppRoute.HOME) {
                AdvancedModeSwitch(state)
                Spacer(Modifier.width(TandemSpacing.small))
            }
            if (route.isTab()) MainCloudStatusButton(syncStatus, pendingWriteStatuses, onSync)
            if (route.isTransactionForm()) TandemModalCloseButton(onClick = onBack)
        }
    }
}

/** Search toggle and overflow menu on the accounts header. */
@Composable
private fun AccountsHeaderActions(
    route: AppRoute,
    state: AppState,
    onBudget: () -> Unit,
    onOpenRoute: (AppRoute) -> Unit,
) {
    var headerMenuExpanded by rememberSaveable(route) { mutableStateOf(false) }
    TandemIconButton(
        if (state.accountsSearchExpanded) Lucide.X else Lucide.Search,
        if (state.accountsSearchExpanded) "Close account search" else "Search accounts",
        onClick = { state.accountsSearchExpanded = !state.accountsSearchExpanded },
    )
    Box {
        TandemIconButton(
            Lucide.EllipsisVertical,
            "Account options",
            onClick = { headerMenuExpanded = true },
        )
        AccountHeaderMenu(
            expanded = headerMenuExpanded,
            onDismiss = { headerMenuExpanded = false },
            onAddAccount = {
                headerMenuExpanded = false
                state.accountsRequestedAction = "add"
            },
            onManageBanks = {
                headerMenuExpanded = false
                state.accountsRequestedAction = "banks"
            },
            onSwitchBudget = {
                headerMenuExpanded = false
                onBudget()
            },
            onOpenSync = {
                headerMenuExpanded = false
                onOpenRoute(AppRoute.SYNC_CENTER)
            },
        )
    }
}
