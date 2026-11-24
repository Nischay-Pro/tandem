@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.ui.app.AppMenu
import app.tandemforactual.ui.app.appMenuItems
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue

/** The full-screen app menu with its counts, shortcuts and budget links. */
@Composable
internal fun ShellMenu(chrome: ShellChromeState) {
    val ctx = chrome.ctx
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val navigation = ctx.navigation
    val payeeRecords = mobileSession.payeeRecords
    val payeeCount = remember(payeeRecords) { payeeRecords.count { it.recordBody()?.stringValue("transfer_acct") == null } }
    val openFromMenu: (AppRoute) -> Unit = { target ->
        state.menuOpen = false
        if (target in navigationRoutes(state.interfaceMode) ||
            target == AppRoute.SETTINGS
        ) {
            navigation.selectTab(target)
        } else {
            navigation.open(target)
        }
    }
    AppMenu(
        visible = state.menuOpen && state.selectedBudgetId.isNotBlank(),
        state = state,
        syncStatus = chrome.mainCloudStatus,
        syncCaption = chrome.syncCaption(),
        items =
            appMenuItems(
                advanced = state.interfaceMode == InterfaceMode.ADVANCED,
                ruleCount = mobileSession.ruleRecords.size,
                scheduleCount = ctx.shell.liveSchedules.size,
                payeeCount = payeeCount,
                conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" },
                reimbursementsOwedMinor =
                    mobileSession.planning.reimbursements
                        .filterNot { it.received }
                        .sumOf { it.amountMinor },
                formatMoney = state::formatCurrency,
                readOnly = state.readOnlyMode,
                open = openFromMenu,
                onAddTransaction = {
                    state.menuOpen = false
                    navigation.newTransaction(null)
                },
                onScanReceipt = {
                    state.menuOpen = false
                    state.receiptScanRequested = true
                    navigation.newTransaction(null)
                },
                onManageBanks = {
                    state.accountsRequestedAction = "banks"
                    openFromMenu(AppRoute.ACCOUNTS)
                },
                onFeedback = { ctx.uriHandler.openUri("https://github.com/Nischay-Pro/tandem/issues") },
            ),
        onClose = { state.menuOpen = false },
        onBudgetSettings = { openFromMenu(AppRoute.SETTINGS) },
        onSwitchBudget = {
            state.menuOpen = false
            navigation.open(AppRoute.SWITCH_BUDGET)
        },
        onSyncCenter = { openFromMenu(AppRoute.SYNC_CENTER) },
        onAbout = { openFromMenu(AppRoute.ABOUT) },
        onLicenses = { openFromMenu(AppRoute.MERCHANT_ICON_LICENSES) },
    )
}
