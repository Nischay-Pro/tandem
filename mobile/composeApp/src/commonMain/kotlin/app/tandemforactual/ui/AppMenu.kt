@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandemforactual.ui.components.TandemIconButton
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.ChartSpline
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.PanelsTopLeft
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScanLine
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.TrendingDown
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wand
import com.composables.icons.lucide.X

/** One destination in the menu, with an optional short hint on the right such as a count. */
internal data class AppMenuItem(
    val icon: ImageVector,
    val label: String,
    val hint: String? = null,
    val hintIsAlert: Boolean = false,
    val external: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Full-screen menu behind the header's menu button: the budget and its sync state at the top, then everything else
 * Tandem can do, so tools that don't earn a tab are one tap away.
 */
@Composable
internal fun AppMenu(
    visible: Boolean,
    state: AppState,
    syncStatus: MainCloudStatus,
    syncCaption: String?,
    items: List<AppMenuItem>,
    onClose: () -> Unit,
    onBudgetSettings: () -> Unit,
    onSwitchBudget: () -> Unit,
    onSyncCenter: () -> Unit,
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
) {
    val reduceMotion = tandemReduceMotion
    TandemBackHandler(enabled = visible, onBack = onClose, onBackProgress = { _, _ -> })
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                slideInHorizontally(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) { -it / 6 },
        exit =
            fadeOut(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                slideOutHorizontally(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) { -it / 8 },
    ) {
        val palette = tandemPalette
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TandemIconButton(Lucide.X, "Close menu", containerColor = palette.surface, onClick = onClose)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(palette.surface)
                            .clickable(role = Role.Button, onClick = onSwitchBudget)
                            .padding(horizontal = TandemSpacing.medium, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.ArrowLeftRight, null, Modifier.size(16.dp), tint = palette.text)
                        Text(
                            "Switch budget",
                            Modifier.padding(start = TandemSpacing.extraSmall),
                            color = palette.text,
                            fontSize = TandemTypography.compactBody,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = TandemSpacing.large),
                ) {
                    BudgetIdentity(state.selectedBudget.ifBlank { "Budget" }, onBudgetSettings)
                    SyncStrip(syncStatus, syncCaption, onSyncCenter)
                    Spacer(Modifier.height(TandemSpacing.large))
                    Text(
                        "MORE YOU CAN DO",
                        Modifier.semantics { heading() }.padding(vertical = TandemSpacing.small),
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    val advanced = (state.modeSwitchTarget ?: state.interfaceMode) == InterfaceMode.ADVANCED
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(TandemShapes.control))
                            .toggleable(value = advanced, role = Role.Switch) {
                                state.switchInterfaceMode(if (it) InterfaceMode.ADVANCED else InterfaceMode.MINIMAL)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.PanelsTopLeft, null, Modifier.size(22.dp), tint = palette.text)
                        Column(Modifier.weight(1f).padding(start = TandemSpacing.medium)) {
                            Text("Advanced", color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
                            Text(
                                "More tabs, tools and detail",
                                color = palette.muted,
                                fontSize = TandemTypography.label,
                            )
                        }
                        AdvancedModeSwitch(state, labeled = false)
                    }
                    items.forEach { MenuRow(it) }
                    MenuFooter(onAbout, onLicenses)
                }
            }
        }
    }
}

@Composable
private fun BudgetIdentity(
    name: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.large))
            .clickable(role = Role.Button, onClickLabel = "Budget and settings", onClick = onClick)
            .padding(vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(palette.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().take(1).uppercase(),
                color = palette.text,
                fontSize = TandemTypography.sectionTitle,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.weight(1f).padding(start = TandemSpacing.medium)) {
            Text(
                name,
                color = palette.text,
                fontSize = TandemTypography.pageTitle,
                fontWeight = FontWeight.SemiBold,
                fontFamily = tandemAlloFamily(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Budget & settings", color = palette.muted, fontSize = TandemTypography.body)
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = palette.muted)
    }
}

@Composable
private fun SyncStrip(
    status: MainCloudStatus,
    caption: String?,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val alert = status == MainCloudStatus.ERROR
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(palette.surface)
            .border(1.dp, if (alert) palette.negative.copy(alpha = 0.4f) else palette.border, RoundedCornerShape(TandemShapes.large))
            .clickable(role = Role.Button, onClickLabel = "Open Sync Center", onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (status) {
                MainCloudStatus.ERROR -> Lucide.CloudAlert
                MainCloudStatus.SYNCING -> Lucide.CloudSync
                else -> Lucide.CloudCheck
            },
            null,
            Modifier.size(20.dp),
            tint = if (alert) palette.negative else palette.accent,
        )
        Text(
            caption ?: "Synced with Actual",
            Modifier.weight(1f).padding(start = TandemSpacing.small),
            color = if (alert) palette.negative else palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Sync Center", color = palette.muted, fontSize = TandemTypography.compactBody)
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = palette.muted)
    }
}

@Composable
private fun MenuRow(item: AppMenuItem) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, Modifier.size(22.dp), tint = palette.text)
        Text(
            item.label,
            Modifier.weight(1f).padding(start = TandemSpacing.medium),
            color = palette.text,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.hint?.let {
            Text(
                it,
                color = if (item.hintIsAlert) palette.negative else palette.muted,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (item.external) Icon(Lucide.ExternalLink, null, Modifier.padding(start = TandemSpacing.small).size(18.dp), tint = palette.muted)
    }
}

@Composable
private fun ColumnScope.MenuFooter(
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
) {
    val palette = tandemPalette
    Column(
        Modifier.fillMaxWidth().padding(top = TandemSpacing.extraLarge, bottom = TandemSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        TandemBrandMark(Modifier.size(40.dp))
        listOf("About Tandem" to onAbout, "Licenses" to onLicenses).forEach { (label, onClick) ->
            Text(
                label,
                Modifier.clip(RoundedCornerShape(TandemShapes.control)).clickable(role = Role.Button, onClick = onClick).padding(4.dp),
                color = palette.muted,
                fontSize = TandemTypography.compactBody,
                textDecoration = TextDecoration.Underline,
            )
        }
        Text("Version ${platformAppVersionName()}", color = palette.muted, fontSize = TandemTypography.caption)
    }
}

/** The menu's tools for the open budget, with counts where they help decide where to go next. */
internal fun appMenuItems(
    advanced: Boolean,
    ruleCount: Int,
    scheduleCount: Int,
    payeeCount: Int,
    conflictCount: Int,
    reimbursementsOwedMinor: Long,
    formatMoney: (Long) -> String,
    readOnly: Boolean,
    open: (AppRoute) -> Unit,
    onAddTransaction: () -> Unit,
    onScanReceipt: () -> Unit,
    onManageBanks: () -> Unit,
    onFeedback: () -> Unit,
): List<AppMenuItem> =
    buildList {
        if (!readOnly) add(AppMenuItem(Lucide.Plus, "Add transaction", onClick = onAddTransaction))
        if (!readOnly) add(AppMenuItem(Lucide.ScanLine, "Scan a receipt", "Fills in the details", onClick = onScanReceipt))
        if (conflictCount > 0) {
            add(
                AppMenuItem(
                    Lucide.GitMerge,
                    "Review conflicts",
                    "$conflictCount to review",
                    hintIsAlert = true,
                ) { open(AppRoute.CONFLICT_INBOX) },
            )
        }
        add(AppMenuItem(Lucide.CloudSync, "Sync Center", "Sync, bank links and fixes") { open(AppRoute.SYNC_CENTER) })
        if (!advanced) add(AppMenuItem(Lucide.WalletCards, "Accounts") { open(AppRoute.ACCOUNTS) })
        add(AppMenuItem(Lucide.Landmark, "Bank connections", "Link and sync") { open(AppRoute.BANK_CONNECTIONS) })
        add(AppMenuItem(Lucide.FileUp, "Import a statement", "CSV, OFX, QIF") { open(AppRoute.ACCOUNTS) })
        add(
            AppMenuItem(
                Lucide.CalendarClock,
                "Schedules",
                scheduleCount.takeIf { it > 0 }?.let { "$it upcoming" },
            ) { open(AppRoute.SCHEDULES) },
        )
        add(AppMenuItem(Lucide.Wand, "Rules", ruleCount.takeIf { it > 0 }?.let { "$it rules" }) { open(AppRoute.BUDGET_RULES) })
        add(AppMenuItem(Lucide.Store, "Payees", payeeCount.takeIf { it > 0 }?.let { "$it payees" }) { open(AppRoute.MANAGE_PAYEES) })
        if (!advanced) add(AppMenuItem(Lucide.ChartColumn, "Reports") { open(AppRoute.REPORTS) })
        add(AppMenuItem(Lucide.CalendarCheck, "Monthly review", "How this month went") { open(AppRoute.MONTHLY_REVIEW) })
        add(AppMenuItem(Lucide.TrendingUp, "Spending trends", "Compared with past months") { open(AppRoute.SPENDING_TRENDS) })
        add(AppMenuItem(Lucide.FlaskConical, "What if", "Try income and spending changes") { open(AppRoute.WHAT_IF) })
        add(AppMenuItem(Lucide.ChartSpline, "Cash flow", "Your balance ahead") { open(AppRoute.CASH_FLOW) })
        add(AppMenuItem(Lucide.Stethoscope, "Budget checkup", "Find things to fix") { open(AppRoute.DATA_HEALTH) })
        add(
            AppMenuItem(Lucide.HandCoins, "Reimbursements", reimbursementsOwedMinor.takeIf { it > 0 }?.let { "${formatMoney(it)} owed" }) {
                open(AppRoute.REIMBURSEMENTS)
            },
        )
        add(AppMenuItem(Lucide.PiggyBank, "Retirement & savings", "401(k), IRA, HSA and more") { open(AppRoute.RETIREMENT) })
        add(AppMenuItem(Lucide.TrendingDown, "Debt payoff", "Snowball or avalanche") { open(AppRoute.DEBT_PAYOFF) })
        add(AppMenuItem(Lucide.FileDown, "Export & backup", "CSV and planning backup") { open(AppRoute.EXPORT) })
        add(AppMenuItem(Lucide.History, "Change history", "Undo any edit") { open(AppRoute.CHANGE_HISTORY) })
        add(AppMenuItem(Lucide.WalletCards, "Closed accounts") { open(AppRoute.CLOSED_ACCOUNTS) })
        add(AppMenuItem(Lucide.Sparkles, "Personalize Home", "Order and sections") { open(AppRoute.HOME_LAYOUT) })
        add(AppMenuItem(Lucide.MessageSquare, "Share feedback", external = true, onClick = onFeedback))
    }
