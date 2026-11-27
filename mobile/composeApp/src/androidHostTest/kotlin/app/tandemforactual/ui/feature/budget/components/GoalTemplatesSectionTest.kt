package app.tandemforactual.ui.feature.budget.components

import app.tandemforactual.data.actual.GoalTemplateOutcome
import app.tandemforactual.data.actual.GoalTemplateValues
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTemplatesSectionTest {
    private fun outcome(
        applied: Int,
        errors: List<String> = emptyList(),
    ) = GoalTemplateOutcome(applied, errors, List(applied) { GoalTemplateValues("c$it", 100, 100, false) })

    @Test
    fun theSummarySaysWhatApplyingWouldDo() {
        assertEquals(
            "3 categories with templates · 2 still empty",
            goalTemplateSummary(GoalTemplatesPreview(outcome(2), outcome(3), emptyList())),
        )
        assertEquals(
            "1 category with templates. All already have amounts.",
            goalTemplateSummary(GoalTemplatesPreview(outcome(0), outcome(1), emptyList())),
        )
        assertEquals(
            "No category notes have #template or #goal lines yet.",
            goalTemplateSummary(GoalTemplatesPreview(outcome(0), outcome(0), emptyList())),
        )
    }

    @Test
    fun problemsComeFirstAndOnlyOnce() {
        val preview =
            GoalTemplatesPreview(outcome(0, listOf("Rent: x")), outcome(0, listOf("Rent: x")), listOf("Rent: x", "Food: #template ?"))
        assertEquals("Fix these notes before applying templates.", goalTemplateSummary(preview))
        assertEquals(listOf("Rent: x", "Food: #template ?"), goalTemplateMessages(preview))
    }
}
