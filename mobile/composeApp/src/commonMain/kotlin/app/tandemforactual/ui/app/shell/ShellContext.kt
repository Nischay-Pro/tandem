package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.UriHandler
import app.tandemforactual.ui.app.isTransactionForm
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState

/** Everything one composition of the app shell shares, rebuilt on every composition. */
internal class ShellContext(
    val env: ShellEnvironment,
    val state: AppState,
    val shell: AppShellState,
    val budgetScoped: BudgetScopedShellState,
    val mobileSession: TandemMobileSession,
    val navigator: ShellNavigator,
    val saveableStateHolder: SaveableStateHolder,
    val uriHandler: UriHandler,
    haptics: HapticFeedback,
) {
    val coroutineScope = navigator.coroutineScope
    val reduceMotion = navigator.reduceMotion
    val navigation = navigator.appNavigation()
    val signInActions = SignInActions(env, state, shell, mobileSession, navigation, coroutineScope, uriHandler)
    val budgetActions = BudgetActions(state, mobileSession, navigation, coroutineScope, haptics)

    val backStack = shell.backStack
    val routeEntry = backStack.last()
    val route = routeEntry.route
    private val transactionSheetIndex = backStack.indexOfLast { it.route.isTransactionForm() }
    val transactionSheetActive = transactionSheetIndex >= 0
    val transactionSheetBaseEntry = backStack.getOrNull(transactionSheetIndex - 1)
    val backgroundRouteEntry =
        if (transactionSheetActive && transactionSheetBaseEntry != null) transactionSheetBaseEntry else routeEntry
}
