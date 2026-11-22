@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState

/** Loads linked bank accounts and handles actions requested from the app bar. */
@Composable
internal fun AccountsScreenEffects(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    addAccountSheet: AddAccountSheetState,
) {
    LaunchedEffect(state.selectedBudgetId) {
        if (state.selectedBudgetId.isNotBlank()) {
            runCatching {
                mobileSession.banking.loadLinkedAccounts(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    LaunchedEffect(state.accountsRequestedAction) {
        when (state.accountsRequestedAction) {
            "add" -> addAccountSheet.open(mobileSession)
            "banks" -> navigation.open(AppRoute.BANK_CONNECTIONS)
        }
        state.accountsRequestedAction = null
    }
}
