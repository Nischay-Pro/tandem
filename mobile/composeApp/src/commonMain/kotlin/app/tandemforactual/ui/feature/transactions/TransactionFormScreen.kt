@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.feature.transactions.components.EditTransactionForm
import app.tandemforactual.ui.feature.transactions.components.NewTransactionForm
import app.tandemforactual.ui.feature.transactions.components.rememberTransactionFormPickerState

@Composable
internal fun TransactionFormScreen(
    route: AppRoute,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: (String?) -> Unit,
) {
    if (state.readOnlyMode) {
        PageColumn {
            Heading("Read-only mode")
            BodyText("Turn it off in Device protection to edit this transaction.")
        }
        return
    }
    val isNew = route == AppRoute.NEW_TRANSACTION
    val pickers = rememberTransactionFormPickerState()
    if (state.selectedBudgetId.isNotBlank()) {
        if (isNew) {
            NewTransactionForm(state, navigation, mobileSession, liveAccounts, pickers, onCreateTransaction)
        } else {
            EditTransactionForm(state, navigation, mobileSession, liveAccounts, pickers, onSaveTransaction, onDeleteTransaction)
        }
        return
    }
    PageColumn {
        EmptyStateCard(
            "Choose a budget",
            "Open an Actual budget before adding or editing a transaction.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
    }
}
