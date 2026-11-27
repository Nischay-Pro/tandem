package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.app.isTransactionForm
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.core.platform.rememberFileDropTarget
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock

/**
 * Files dropped on the app: a statement starts the import for the account on screen, or opens Accounts to choose one,
 * and a receipt photo opens a new transaction filled from it.
 */
@Composable
internal fun rememberShellFileDrop(ctx: ShellContext): Modifier {
    val state = ctx.state
    val ready = state.selectedBudgetId.isNotBlank() && !state.readOnlyMode && !needsEncryptedBudgetUnlock(state, ctx.route)
    val canAddTransactions = ready && ctx.shell.liveAccounts.any { !it.offBudget }
    return rememberFileDropTarget(
        onStatement =
            if (ready) {
                { file ->
                    state.pendingStatementFile = file
                    val route = ctx.route
                    if (route != AppRoute.ACCOUNT_DETAIL && route != AppRoute.ACCOUNTS) {
                        state.menuOpen = false
                        val isTab = AppRoute.ACCOUNTS in navigationRoutes(state.interfaceMode)
                        if (isTab) ctx.navigation.selectTab(AppRoute.ACCOUNTS) else ctx.navigation.open(AppRoute.ACCOUNTS)
                    }
                }
            } else {
                null
            },
        onImage =
            if (canAddTransactions) {
                { photo ->
                    state.pendingReceiptPhoto = photo
                    state.menuOpen = false
                    if (!ctx.route.isTransactionForm()) ctx.navigation.newTransaction(null)
                }
            } else {
                null
            },
    )
}

/** Receipt photos dropped on the new transaction sheet, which sits in its own window above the app. */
@Composable
internal fun rememberTransactionSheetFileDrop(ctx: ShellContext): Modifier =
    rememberFileDropTarget(
        onStatement = null,
        onImage = if (ctx.route == AppRoute.NEW_TRANSACTION) ({ photo -> ctx.state.pendingReceiptPhoto = photo }) else null,
    )
