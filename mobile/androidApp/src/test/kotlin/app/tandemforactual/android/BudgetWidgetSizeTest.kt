package app.tandemforactual.android

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetWidgetSizeTest {
    @Test
    fun narrowWidgetsShowOnlyWhatIsReadyToAssign() {
        assertEquals(BudgetWidgetSize.SMALL, BudgetWidgetSize.of(180f, 110f))
        assertEquals(BudgetWidgetSize.SMALL, BudgetWidgetSize.of(249f, 300f))
        assertEquals(BudgetWidgetSize.SMALL, BudgetWidgetSize.of(120f, 80f))
    }

    @Test
    fun wideWidgetsAddSpendingAndTallOnesStackIt() {
        assertEquals(BudgetWidgetSize.MEDIUM, BudgetWidgetSize.of(250f, 110f))
        assertEquals(BudgetWidgetSize.MEDIUM, BudgetWidgetSize.of(400f, 199f))
        assertEquals(BudgetWidgetSize.LARGE, BudgetWidgetSize.of(250f, 200f))
        assertEquals(BudgetWidgetSize.LARGE, BudgetWidgetSize.of(352f, 339f))
    }
}
