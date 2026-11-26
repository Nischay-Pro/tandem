@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.BudgetSwitchSplash
import app.tandemforactual.ui.app.UNDO_NOTICE_MILLIS
import app.tandemforactual.ui.app.isLandscapeViewport
import app.tandemforactual.ui.app.usesRailNavigation
import app.tandemforactual.ui.core.session.ForgetBankIconsWhenOff
import app.tandemforactual.ui.core.session.planning.FetchExchangeRatesWhenOn
import app.tandemforactual.ui.core.session.transactions.undoTransactionDeletes
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.restoreTransactionFormBaseline
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.feature.auth.AppLockGate
import app.tandemforactual.ui.feature.onboarding.nextSetupRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The layers drawn above the scaffold: undo notice, discard dialog, menu, budget switch cover and app lock. */
@Composable
internal fun ShellOverlays(
    chrome: ShellChromeState,
    isAppInForeground: Boolean,
) {
    val ctx = chrome.ctx
    val state = ctx.state
    val shell = ctx.shell
    ForgetBankIconsWhenOff(state.bankIconsEnabled)
    FetchExchangeRatesWhenOn(state, ctx.mobileSession)
    UndoNotice(ctx)
    if (shell.showDiscardTransactionDialog) DiscardTransactionDialog(ctx)
    ShellMenu(chrome)
    BudgetSwitchSplash(
        state.selectedBudget.takeIf { state.modeSwitchTarget == null && state.budgetSwitchSplashId != null },
        state.modeSwitchTarget,
    )
    AppLockGate(
        state = state,
        mobileSession = ctx.mobileSession,
        route = ctx.backStack.lastOrNull()?.route ?: AppRoute.BUDGET,
        authenticator = ctx.env.deviceAuthenticator,
        lockState = shell.appLockState,
        appInForeground = isAppInForeground,
        onChooseBudget = { ctx.navigation.open(AppRoute.SWITCH_BUDGET) },
        vault = ctx.env.encryptedBudgetVault,
        backTargetRoute = shell.backPreviewRoute?.route,
        backProgress = shell.backGestureProgress,
        onUnlocked = {
            if (state.pendingSetupAfterUnlock) {
                state.pendingSetupAfterUnlock = false
                shell.routeTransitionDirection = 0f
                shell.savedTabBackStacks = ""
                ctx.backStack.clear()
                ctx.backStack.add(ctx.navigator.newRouteEntry(AppRoute.BUDGET_CHOOSER))
                ctx.backStack.add(ctx.navigator.newRouteEntry(nextSetupRoute(state, ctx.env.notificationPermissionEnabled)))
            }
        },
    )
}

@Composable
private fun UndoNotice(ctx: ShellContext) {
    val mobileSession = ctx.mobileSession
    val undoNotice = mobileSession.transactions.undoNotice
    val undoAboveTabBar =
        !usesRailNavigation(
            with(LocalDensity.current) {
                LocalWindowInfo.current.containerSize.width
                    .toDp()
                    .value
            },
            isLandscapeViewport(LocalWindowInfo.current.containerSize.width, LocalWindowInfo.current.containerSize.height),
        )
    LaunchedEffect(undoNotice?.id) {
        val shown = undoNotice ?: return@LaunchedEffect
        delay(UNDO_NOTICE_MILLIS)
        mobileSession.transactions.dismissUndoNotice(shown.id)
    }
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = if (undoAboveTabBar) 104.dp else 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = undoNotice != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val notice = undoNotice ?: return@AnimatedVisibility
            SurfaceCard(Modifier.widthIn(max = 560.dp)) {
                InlineMessage(
                    notice.message,
                    InlineMessageKind.INFO,
                    actionLabel = "Undo".takeIf { notice.transactionIds.isNotEmpty() || notice.undo != null },
                    onAction = {
                        ctx.coroutineScope.launch {
                            val undo = notice.undo
                            if (undo == null) {
                                mobileSession.transactions.undoTransactionDeletes(notice)
                            } else {
                                mobileSession.transactions.dismissUndoNotice(notice.id)
                                undo()
                            }
                        }
                    },
                    onDismiss = { mobileSession.transactions.dismissUndoNotice(notice.id) },
                )
            }
        }
    }
}

@Composable
private fun DiscardTransactionDialog(ctx: ShellContext) {
    val state = ctx.state
    val shell = ctx.shell
    val formRoute = ctx.backStack.lastOrNull()?.route
    val isNewTransaction = formRoute == AppRoute.NEW_TRANSACTION
    val payee = state.transactionPayee.trim().takeIf { it.isNotBlank() }
    val amount =
        state.parseAmountMinorUnits(state.transactionAmount)?.let { state.formatCurrency(kotlin.math.abs(it)) }
            ?: "the current amount"
    val transactionSummary = payee?.let { "$amount at $it" } ?: amount
    DestructiveConfirmationDialog(
        title = if (isNewTransaction) "Discard this transaction?" else "Discard changes?",
        message =
            if (isNewTransaction) {
                "Discard $transactionSummary? The transaction and its notes will be lost."
            } else {
                "Discard your changes to ${payee ?: "this transaction"} ($amount)? The current transaction will stay as it is."
            },
        confirmLabel = "Discard",
        dismissLabel = "Keep editing",
        onDismissRequest = { shell.showDiscardTransactionDialog = false },
        onConfirm = {
            restoreTransactionFormBaseline(state, formRoute)
            shell.showDiscardTransactionDialog = false
            shell.transactionSheetPopCount = 1
            shell.transactionSheetClosing = true
        },
    )
}
