package app.tandemforactual.ui

import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.core.importing.StatementApplySummary
import app.tandemforactual.ui.core.importing.statementApplyLabel
import app.tandemforactual.ui.core.session.ConflictResolutionFeedback
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.feature.accounts.accountBalanceLabel
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
    fun applyButtonCountsOnlyRowsThatChangeActual() {
        assertEquals("Apply 1 change", statementApplyLabel(StatementApplySummary(1, 0, 3, 2), busy = false))
        assertEquals("Apply 3 changes", statementApplyLabel(StatementApplySummary(1, 2, 0, 0), busy = false))
        assertEquals("Nothing to apply", statementApplyLabel(StatementApplySummary(0, 0, 4, 1), busy = false))
        assertEquals("Applying…", statementApplyLabel(StatementApplySummary(1, 0, 0, 0), busy = true))
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
