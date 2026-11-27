package app.tandemforactual.ui

import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.core.session.ConflictResolutionFeedback
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.feature.accounts.accountBalanceLabel
import app.tandemforactual.ui.feature.accounts.statementImportButtonLabel
import app.tandemforactual.ui.feature.sync.conflictDoneCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HonestLabelsTest {
    @Test
    fun debtAccountsShowWhatIsOwed() {
        assertEquals("Balance owed", accountBalanceLabel(AccountKind.CREDIT_CARD, -12_000))
        assertEquals("Balance owed", accountBalanceLabel(AccountKind.MORTGAGE, null))
        assertEquals("Balance", accountBalanceLabel(AccountKind.LOAN, 500))
        assertEquals("Balance", accountBalanceLabel(AccountKind.BANK, -100))
        assertEquals("Available balance", accountBalanceLabel(AccountKind.BANK, 2_500))
        assertEquals("Available balance", accountBalanceLabel(null, null))
    }

    @Test
    fun importButtonCountsOnlyRowsActualDoesNotHave() {
        assertEquals("Import 3 transactions", statementImportButtonLabel(3, newRows = null, busy = false))
        assertEquals("Import 1 new transaction", statementImportButtonLabel(3, newRows = 1, busy = false))
        assertEquals("Import 2 new transactions", statementImportButtonLabel(3, newRows = 2, busy = false))
        assertEquals("No new transactions", statementImportButtonLabel(3, newRows = 0, busy = false))
        assertEquals("Importing…", statementImportButtonLabel(3, newRows = 2, busy = true))
    }

    @Test
    fun choiceSavedOnlyAfterAChoiceWasSaved() {
        val nothing = conflictDoneCopy(feedback = null, openConflicts = 0)
        assertEquals("Nothing to review.", nothing.title)
        assertFalse(nothing.saved)

        val waiting = conflictDoneCopy(feedback = null, openConflicts = 2)
        assertEquals("Changes to review.", waiting.title)
        assertEquals("2 changes need your review.", waiting.body)

        val gone = conflictDoneCopy(ConflictResolutionFeedback("op", "This change no longer needs review."), openConflicts = 0)
        assertEquals("Nothing to review.", gone.title)
        assertEquals("This change no longer needs review.", gone.body)

        val saved = conflictDoneCopy(ConflictResolutionFeedback("op", "Kept your edit.", saved = true), openConflicts = 1)
        assertEquals("Choice saved.", saved.title)
        assertEquals("Kept your edit. One change needs your review.", saved.body)
        assertTrue(saved.saved)
    }

    @Test
    fun budgetPasswordIsNotSavedBehindBiometricsByDefault() {
        assertFalse(AppState().rememberBudgetPasswordWithBiometrics)
    }
}
