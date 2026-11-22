package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.withFrameNanos
import app.tandemforactual.ui.app.isReportDetail
import app.tandemforactual.ui.app.isTransactionForm
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import kotlinx.coroutines.launch

/** The [AppNavigation] handed to screens, backed by this navigator's back stack. */
internal fun ShellNavigator.appNavigation(): AppNavigation {
    val backStack = shell.backStack
    return AppNavigation(
        open = this::openRoute,
        openSchedule = { scheduleId ->
            if (beginRouteTransition()) {
                coroutineScope.launch {
                    withFrameNanos { }
                    state.pendingScheduleToOpenId = scheduleId
                    if (backStack.lastOrNull()?.route != AppRoute.SCHEDULES) {
                        shell.routeTransitionDirection = 1f
                        backStack.add(newRouteEntry(AppRoute.SCHEDULES))
                    }
                }
                true
            } else {
                false
            }
        },
        reset = { next ->
            state.pendingScheduleToOpenId = null
            shell.routeTransitionDirection = 0f
            shell.savedTabBackStacks = ""
            backStack.clear()
            backStack.add(newRouteEntry(next))
        },
        replaceTop = { next ->
            if (next != AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
            shell.routeTransitionDirection = 1f
            if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
            backStack.add(newRouteEntry(next))
        },
        newTransaction = this::newTransaction,
        selectTab = this::selectTab,
        back = this::handleBack,
        backToList = {
            if (backStack.lastOrNull()?.route?.isTransactionForm() == true && backStack.size > 2) {
                shell.transactionSheetPopCount = 2
                shell.transactionSheetClosing = true
            } else if (backStack.size > 2) {
                shell.routeTransitionDirection = -1f
                backStack.removeAt(backStack.lastIndex)
                backStack.removeAt(backStack.lastIndex)
            } else {
                handleBack()
            }
        },
        selectTabRoot = this::selectTabRoot,
    )
}

private fun ShellNavigator.openRoute(next: AppRoute) {
    val backStack = shell.backStack
    if (next != AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
    if (beginRouteTransition()) {
        coroutineScope.launch {
            // Finish the opening pointer dispatch before swapping in interactive content.
            withFrameNanos { }
            if (next == AppRoute.NEW_TRANSACTION) {
                newTransaction(null)
            } else if (backStack.lastOrNull()?.route != next) {
                if (next.isTransactionForm()) {
                    shell.transactionSheetClosing = false
                    shell.transactionSheetPopCount = 1
                }
                if (next.isReportDetail() && backStack.lastOrNull()?.route != AppRoute.REPORTS) {
                    selectTab(AppRoute.REPORTS)
                }
                shell.routeTransitionDirection = 1f
                backStack.add(newRouteEntry(next))
            }
        }
    }
}
