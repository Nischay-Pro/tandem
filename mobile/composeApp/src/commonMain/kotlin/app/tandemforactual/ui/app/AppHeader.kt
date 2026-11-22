@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemBackButton
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalCloseButton
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.reports.ReportKind
import app.tandemforactual.ui.feature.reports.reportMonthLabel
import app.tandemforactual.ui.feature.reports.shiftReportMonthId
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ChartNoAxesColumn
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Plus
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
    val palette = tandemPalette
    val menuButton: @Composable () -> Unit = {
        if (onOpenMenu != null && route.isTab()) {
            TandemIconButton(Lucide.Menu, "Menu", containerColor = palette.surface, onClick = onOpenMenu)
            Spacer(Modifier.width(TandemSpacing.medium))
        }
    }
    var headerMenuExpanded by rememberSaveable(route) { mutableStateOf(false) }
    val headerFontScale = LocalDensity.current.fontScale
    val budgetHeaderSlotHeight = maxOf(88.dp, 88.dp * headerFontScale)
    val headerMinHeight =
        if (route == AppRoute.BUDGET || route == AppRoute.BUDGET_CHOOSER) {
            if (compactLandscape && headerFontScale < 1.5f) 64.dp else budgetHeaderSlotHeight
        } else {
            64.dp
        }
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
    val headerTitle =
        when (route) {
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
            else -> route.title
        }
    if (route == AppRoute.BUDGET) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = headerMinHeight)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    menuButton()
                    Column(Modifier.weight(1f)) {
                        Text(
                            headerTitle,
                            color = palette.text,
                            fontSize = TandemTypography.periodTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        syncCaption?.let { caption ->
                            Text(
                                caption,
                                color = if (syncStatus == MainCloudStatus.ERROR) palette.negative else palette.muted,
                                fontSize = TandemTypography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onAddTransaction != null) {
                        TandemIconButton(
                            Lucide.Plus,
                            if (addTransactionEnabled) "Add transaction" else "Read-only mode is on",
                            enabled = addTransactionEnabled,
                            iconSizeDp = 20,
                            onClick = onAddTransaction,
                        )
                    }
                    MainCloudStatusButton(syncStatus, pendingWriteStatuses, onSync)
                    Box {
                        TandemIconButton(
                            Lucide.EllipsisVertical,
                            "Budget options",
                            onClick = { headerMenuExpanded = true },
                        )
                        BudgetHeaderMenu(
                            expanded = headerMenuExpanded,
                            state = state,
                            onDismiss = { headerMenuExpanded = false },
                            onSwitchBudget = {
                                headerMenuExpanded = false
                                onBudget()
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Choose month",
                            onClick = { state.isBudgetMonthPickerOpen = true },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                    ) {
                        Text(
                            state.selectedMonth,
                            color = palette.text,
                            fontSize = TandemTypography.action,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(Lucide.ChevronDown, contentDescription = "Choose month", modifier = Modifier.size(20.dp), tint = palette.muted)
                    }
                    Spacer(Modifier.weight(1f))
                    TandemIconButton(
                        Lucide.ChevronLeft,
                        "Previous month",
                        sizeDp = 36,
                        iconSizeDp = 20,
                        onClick = { state.shiftBudgetMonth(-1) },
                    )
                    TandemIconButton(
                        Lucide.ChevronRight,
                        "Next month",
                        sizeDp = 36,
                        iconSizeDp = 20,
                        onClick = { state.shiftBudgetMonth(1) },
                    )
                }
            }
        }
    } else {
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
            menuButton()
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
                AppRoute.ACCOUNTS -> {
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
    }
}

@Composable
private fun BudgetHeaderMenu(
    expanded: Boolean,
    state: AppState,
    onDismiss: () -> Unit,
    onSwitchBudget: () -> Unit,
) {
    TandemHeaderMenu(expanded = expanded, onDismiss = onDismiss) {
        if (state.selectedBudgetEncrypted && state.encryptedPassword.isNotBlank()) {
            HeaderMenuAction(Lucide.Lock, "Lock budget", onClick = {
                onDismiss()
                state.encryptedPassword = ""
            })
        }
        HeaderMenuAction(Lucide.ArrowLeftRight, "Switch budget", onClick = onSwitchBudget)
        HeaderMenuDivider()
        HeaderMenuLabel("Category amounts")
        HeaderMenuChoice(Lucide.Hash, "Numbers", selected = !state.budgetShowProgressBars, onClick = {
            state.budgetShowProgressBars = false
            onDismiss()
        })
        HeaderMenuChoice(Lucide.ChartNoAxesColumn, "Progress bars", selected = state.budgetShowProgressBars, onClick = {
            state.budgetShowProgressBars = true
            onDismiss()
        })
        HeaderMenuDivider()
        HeaderMenuLabel("Privacy")
        HeaderMenuChoice(Lucide.EyeOff, "Hide amounts", selected = state.privacyModeEnabled, onClick = {
            state.privacyModeEnabled = !state.privacyModeEnabled
            onDismiss()
        })
    }
}

@Composable
private fun AccountHeaderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onManageBanks: () -> Unit,
    onSwitchBudget: () -> Unit,
    onOpenSync: () -> Unit,
) {
    TandemHeaderMenu(expanded = expanded, onDismiss = onDismiss) {
        HeaderMenuLabel("Account actions")
        HeaderMenuAction(Lucide.Plus, "Add account", onClick = onAddAccount)
        HeaderMenuAction(Lucide.Landmark, "Manage bank connections", onClick = onManageBanks)
        HeaderMenuDivider()
        HeaderMenuAction(Lucide.ArrowLeftRight, "Switch budget", onClick = onSwitchBudget)
        HeaderMenuAction(Lucide.CloudSync, "Sync Center", onClick = onOpenSync)
    }
}

@Composable
private fun HeaderMenuLabel(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HeaderMenuDivider() {
    androidx.compose.material.Divider(
        modifier = Modifier.padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = tandemPalette.border,
        thickness = 1.dp,
    )
}

@Composable
private fun HeaderMenuChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) palette.accent else palette.muted)
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (selected) palette.accent else palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
        )
        if (selected) Icon(Lucide.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp), tint = palette.accent)
    }
}

private fun AppState.shiftBudgetMonth(offset: Int) {
    val monthId = shiftReportMonthId(selectedMonthId, offset) ?: return
    selectedMonthId = monthId
    selectedMonth = reportMonthLabel(monthId)
}
