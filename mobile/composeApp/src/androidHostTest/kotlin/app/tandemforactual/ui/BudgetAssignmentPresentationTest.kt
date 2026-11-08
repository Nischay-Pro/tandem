package app.tandemforactual.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetAssignmentPresentationTest {
    @Test
    fun positiveAmountUsesToBudgetLabelAndMagnitude() {
        assertEquals(
            BudgetAssignmentPresentation("To budget", 12_400, BudgetAssignmentKind.TO_BUDGET),
            presentBudgetAssignment(12_400),
        )
    }

    @Test
    fun negativeAmountUsesOverbudgetedLabelAndPositiveMagnitude() {
        assertEquals(
            BudgetAssignmentPresentation("Overbudgeted", 31_645_554, BudgetAssignmentKind.OVERBUDGETED),
            presentBudgetAssignment(-31_645_554),
        )
    }

    @Test
    fun zeroAmountSaysAllMoneyIsAssigned() {
        assertEquals(
            BudgetAssignmentPresentation("All money assigned", 0, BudgetAssignmentKind.ALL_ASSIGNED),
            presentBudgetAssignment(0),
        )
    }
}
