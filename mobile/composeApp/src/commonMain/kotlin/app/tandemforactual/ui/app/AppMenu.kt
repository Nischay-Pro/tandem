@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.app.routes.accountsMenu
import app.tandemforactual.ui.app.routes.bankingMenu
import app.tandemforactual.ui.app.routes.checkupMenu
import app.tandemforactual.ui.app.routes.currencyMenu
import app.tandemforactual.ui.app.routes.exportMenu
import app.tandemforactual.ui.app.routes.homeMenu
import app.tandemforactual.ui.app.routes.householdMenu
import app.tandemforactual.ui.app.routes.insightsMenu
import app.tandemforactual.ui.app.routes.planningMenu
import app.tandemforactual.ui.app.routes.reimbursementsMenu
import app.tandemforactual.ui.app.routes.reportsMenu
import app.tandemforactual.ui.app.routes.residenceMenu
import app.tandemforactual.ui.app.routes.retirementMenu
import app.tandemforactual.ui.app.routes.rulesMenu
import app.tandemforactual.ui.app.routes.schedulesMenu
import app.tandemforactual.ui.app.routes.sharedBillsMenu
import app.tandemforactual.ui.app.routes.syncMenu
import app.tandemforactual.ui.core.state.AppRoute
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ScanLine

/** One destination in the menu, with an optional short hint on the right such as a count. */
internal data class AppMenuItem(
    val icon: ImageVector,
    val label: String,
    val hint: String? = null,
    val hintIsAlert: Boolean = false,
    val external: Boolean = false,
    val onClick: () -> Unit,
)

/** What menu entries may read when deciding whether and how to show themselves. */
internal class MenuContext(
    val advanced: Boolean,
    val ruleCount: Int,
    val scheduleCount: Int,
    val payeeCount: Int,
    val conflictCount: Int,
    val reimbursementsOwedMinor: Long,
    val formatMoney: (Long) -> String,
    val readOnly: Boolean,
    val open: (AppRoute) -> Unit,
    val onAddTransaction: () -> Unit,
    val onScanReceipt: () -> Unit,
    val onManageBanks: () -> Unit,
    val onFeedback: () -> Unit,
)

/** One menu entry; [order] keeps the menu in a stable order across features. Returns null when hidden. */
internal class MenuEntry(
    val order: Int,
    val build: (MenuContext) -> AppMenuItem?,
)

private val shellMenu: List<MenuEntry> =
    listOf(
        MenuEntry(
            10,
        ) { ctx -> with(ctx) { if (!readOnly) AppMenuItem(Lucide.Plus, "Add transaction", onClick = onAddTransaction) else null } },
        MenuEntry(20) { ctx ->
            with(ctx) {
                if (!readOnly) AppMenuItem(Lucide.ScanLine, "Scan a receipt", "Fills in the details", onClick = onScanReceipt) else null
            }
        },
        MenuEntry(290) { ctx -> with(ctx) { AppMenuItem(Lucide.MessageSquare, "Share feedback", external = true, onClick = onFeedback) } },
    )

internal val menuEntries: List<MenuEntry> =
    (
        shellMenu +
            accountsMenu +
            bankingMenu +
            checkupMenu +
            currencyMenu +
            exportMenu +
            homeMenu +
            householdMenu +
            insightsMenu +
            planningMenu +
            reimbursementsMenu +
            sharedBillsMenu +
            reportsMenu +
            retirementMenu +
            residenceMenu +
            rulesMenu +
            schedulesMenu +
            syncMenu +
            emptyList()
    ).sortedBy { it.order }

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
): List<AppMenuItem> {
    val context =
        MenuContext(
            advanced,
            ruleCount,
            scheduleCount,
            payeeCount,
            conflictCount,
            reimbursementsOwedMinor,
            formatMoney,
            readOnly,
            open,
            onAddTransaction,
            onScanReceipt,
            onManageBanks,
            onFeedback,
        )
    return menuEntries.mapNotNull { it.build(context) }
}
