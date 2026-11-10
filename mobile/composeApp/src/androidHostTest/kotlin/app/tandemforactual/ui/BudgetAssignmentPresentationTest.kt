package app.tandemforactual.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetAssignmentPresentationTest {
    @Test
    fun positiveAmountUsesToBudgetLabelAndMagnitude() {
        assertEquals(
            BudgetAssignmentPresentation("To Budget", 12_400, BudgetAssignmentKind.TO_BUDGET),
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

    @Test
    fun categoryCountsUseTheCorrectSingularAndPluralForms() {
        assertEquals("0 categories", categoryCountLabel(0))
        assertEquals("1 category", categoryCountLabel(1))
        assertEquals("2 categories", categoryCountLabel(2))
    }

    @Test
    fun budgetHeaderUsesWordsThatMatchTheSyncState() {
        assertEquals("Sync off", mainCloudStatusLabel(MainCloudStatus.OFF))
        assertEquals("Synced", mainCloudStatusLabel(MainCloudStatus.SYNCED))
        assertEquals("Syncing", mainCloudStatusLabel(MainCloudStatus.SYNCING))
        assertEquals("Sync needs attention", mainCloudStatusLabel(MainCloudStatus.ERROR))
    }
}
