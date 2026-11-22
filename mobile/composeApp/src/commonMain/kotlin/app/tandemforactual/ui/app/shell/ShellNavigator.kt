package app.tandemforactual.ui.app.shell

import app.tandemforactual.ui.app.isTransactionForm
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.BackRequestDisposition
import app.tandemforactual.ui.core.state.backRequestDisposition
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.restorePendingBudgetSelectionOrigin
import app.tandemforactual.ui.core.state.selectAppTabNavigation
import app.tandemforactual.ui.core.state.selectAppTabRootNavigation
import app.tandemforactual.ui.core.state.transactionFormHasUnsavedChanges
import app.tandemforactual.ui.design.theme.TandemMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Back stack operations of the app shell; rebuilt on every composition like the lambdas it replaces. */
internal class ShellNavigator(
    val shell: AppShellState,
    val state: AppState,
    val coroutineScope: CoroutineScope,
    val reduceMotion: Boolean,
) {
    private val backStack get() = shell.backStack

    fun newRouteEntry(nextRoute: AppRoute): AppRouteEntry {
        val entryId = shell.nextRouteEntryId
        shell.nextRouteEntryId += 1
        return AppRouteEntry(nextRoute, entryId)
    }

    fun beginRouteTransition(): Boolean =
        if (shell.routeTransitionInputBlocked) {
            false
        } else {
            shell.routeTransitionInputBlocked = true
            shell.routeTransitionInputJob?.cancel()
            shell.routeTransitionInputJob =
                coroutineScope.launch {
                    delay(TandemMotion.ROUTE_MILLIS.toLong() + 80L)
                    shell.routeTransitionInputBlocked = false
                    shell.routeTransitionInputJob = null
                }
            true
        }

    fun newTransaction(requestedAccountId: String?) {
        if (!state.readOnlyMode && state.selectedBudgetId.isNotBlank()) {
            shell.transactionSheetClosing = false
            shell.transactionSheetPopCount = 1
            val eligibleAccounts = shell.liveAccounts.filterNot { it.offBudget }
            val defaultAccount =
                if (state.selectedBudgetId.isNotBlank()) {
                    eligibleAccounts.firstOrNull { it.id == requestedAccountId }
                        ?: eligibleAccounts.firstOrNull { it.id == state.lastTransactionAccountId }
                        ?: eligibleAccounts.firstOrNull()
                } else {
                    null
                }
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = defaultAccount?.name ?: "Checking"
            state.newTransactionAccountId = defaultAccount?.id.orEmpty()
            state.newTransactionInitialAccountId = state.newTransactionAccountId
            state.newTransactionInitialAccountName = state.transactionAccount
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = platformTodayIsoDate()
            state.newTransactionInitialDate = state.transactionDate
            state.transactionType = "Spending"
            state.transactionNotes = ""
            state.selectedTransactionRecordId = ""
            state.selectedTransactionAccountId = ""
            state.selectedTransactionPayeeId = ""
            state.selectedTransactionCategoryId = ""
            state.selectedTransactionDate = ""
            state.selectedTransactionAmountMinor = null
            state.selectedTransactionIsLive = false
            state.selectedTransactionIsTransfer = false
            backStack.add(newRouteEntry(AppRoute.NEW_TRANSACTION))
        }
    }

    fun popRoute() {
        if (backStack.size > 1) {
            shell.routeTransitionDirection = -1f
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun selectTab(selectedTab: AppRoute) {
        state.pendingScheduleToOpenId = null
        val tabSelection = selectAppTabNavigation(backStack.toList(), shell.savedTabBackStacks, selectedTab, ::newRouteEntry)
        if (tabSelection.backStack != backStack.toList()) {
            shell.routeTransitionDirection = 0f
            backStack.clear()
            backStack.addAll(tabSelection.backStack)
        }
        shell.savedTabBackStacks = tabSelection.savedTabBackStacks
    }

    fun selectTabRoot(selectedTab: AppRoute) {
        state.pendingScheduleToOpenId = null
        val tabSelection = selectAppTabRootNavigation(backStack.toList(), shell.savedTabBackStacks, selectedTab, ::newRouteEntry)
        if (tabSelection.backStack != backStack.toList()) {
            shell.routeTransitionDirection = 0f
            backStack.clear()
            backStack.addAll(tabSelection.backStack)
        }
        shell.savedTabBackStacks = tabSelection.savedTabBackStacks
    }

    fun performBack() {
        when (backRequestDisposition(shell.routeTransitionInputBlocked, shell.backButtonTransitionRunning)) {
            BackRequestDisposition.QUEUE_AFTER_BACK_TRANSITION -> {
                shell.queuedBackPresses += 1
            }
            BackRequestDisposition.DEFER_UNTIL_ROUTE_TRANSITION_FINISHES -> {
                shell.pendingBackAfterRouteTransition = true
            }
            BackRequestDisposition.START -> {
                if (beginRouteTransition()) {
                    val backAnimationWasRunning = shell.backGestureCancelJob != null
                    shell.backGestureCancelJob?.cancel()
                    shell.backGestureCancelJob = null
                    if (shell.backGestureCommitPending || backAnimationWasRunning) {
                        shell.backGestureCommitPending = false
                        shell.backGestureProgress = 1f
                        shell.skipNextRouteTransition = true
                        popRoute()
                    } else if (backStack.size > 1) {
                        // Button back runs the same route pair and progress transform as predictive back.
                        startBackButtonTransition()
                    }
                }
            }
        }
    }

    fun handleBack() {
        val currentRoute = backStack.lastOrNull()?.route
        if (currentRoute == AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
        if (transactionFormHasUnsavedChanges(state, currentRoute)) {
            shell.backGestureCancelJob?.cancel()
            shell.backGestureCancelJob = null
            shell.backGestureCommitPending = false
            shell.backButtonTransitionRunning = false
            shell.backGestureProgress = 0f
            shell.backGestureOutgoingEntry = null
            shell.backPreviewRoute = null
            shell.queuedBackPresses = 0
            shell.showDiscardTransactionDialog = true
        } else if (backStack.any { it.route.isTransactionForm() }) {
            val formIndex = backStack.indexOfLast { it.route.isTransactionForm() }
            if (backStack.lastIndex > formIndex) {
                shell.routeTransitionDirection = -1f
                backStack.removeAt(backStack.lastIndex)
            } else {
                shell.transactionSheetPopCount = 1
                shell.transactionSheetClosing = true
            }
        } else {
            val returningToUnlock = backStack.getOrNull(backStack.lastIndex - 1)?.route == AppRoute.BUDGET_UNLOCK
            if ((currentRoute == AppRoute.FIRST_SYNC && !returningToUnlock) || currentRoute == AppRoute.BUDGET_UNLOCK) {
                state.restorePendingBudgetSelectionOrigin()
            }
            performBack()
        }
    }

    fun handleCurrentRouteBack() {
        val payeesOverTransactionSheet =
            backStack.lastOrNull()?.route == AppRoute.MANAGE_PAYEES &&
                (
                    backStack.size == 1 ||
                        backStack[backStack.lastIndex - 1].route.let { it == AppRoute.CHOOSE_PAYEE || it.isTransactionForm() }
                )
        if (payeesOverTransactionSheet) {
            shell.routeTransitionInputJob?.cancel()
            shell.routeTransitionInputJob = null
            shell.routeTransitionInputBlocked = false
            shell.pendingBackAfterRouteTransition = false
            shell.routeTransitionDirection = -1f
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                backStack[backStack.lastIndex] = newRouteEntry(AppRoute.CHOOSE_PAYEE)
            }
        } else {
            handleBack()
        }
    }
}
