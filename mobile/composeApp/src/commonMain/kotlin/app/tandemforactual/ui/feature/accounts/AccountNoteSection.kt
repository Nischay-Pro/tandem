@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.noteOverridesFor
import app.tandemforactual.ui.core.session.budget.setBudgetAccountNote
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.ui.NoteEditor
import app.tandemforactual.ui.core.ui.NoteSection
import app.tandemforactual.ui.core.ui.noteWithPendingEdit

/** The account's note, which Actual keeps as `account-<id>`. */
@Composable
internal fun AccountNoteSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    account: LiveAccountSummary,
) {
    val budgetId = state.selectedBudgetId
    val note =
        noteWithPendingEdit(
            mobileSession.budgetQueue.noteOverridesFor(budgetId, BudgetReorderKind.SET_ACCOUNT_NOTE),
            mobileSession.accountRecords.firstOrNull { it.recordId == account.id },
            account.id,
        )
    val editor =
        NoteEditor("Account note", account.name, "Who uses it, what it’s for", state.readOnlyMode) { draft ->
            mobileSession.budgetEdits.setBudgetAccountNote(budgetId, account.id, draft, state.encryptedPassword.takeIf { it.isNotBlank() })
        }
    NoteSection(note, editor, mobileSession, "account-note:$budgetId:${account.id}")
}
