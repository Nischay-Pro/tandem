package app.tandemforactual.ui.feature.budget

import app.tandemforactual.data.actual.GoalTemplate
import app.tandemforactual.data.actual.GoalTemplateLimit
import app.tandemforactual.data.actual.GoalTemplateStatus
import app.tandemforactual.ui.feature.budget.components.goalTemplateStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalTemplateDraftTest {
    @Test
    fun everyKindWritesActualsSyntax() {
        val lines =
            listOf(
                GoalTemplateDraft(amount = "50", cap = "200", priority = "2"),
                GoalTemplateDraft(GoalTemplateKind.BY_DATE, amount = "1500", month = "2027-06", repeatYearly = true),
                GoalTemplateDraft(GoalTemplateKind.PERCENT, percent = "12.5", source = "Salary", previous = true),
                GoalTemplateDraft(GoalTemplateKind.AVERAGE, months = 6, adjustPercent = "-10"),
                GoalTemplateDraft(GoalTemplateKind.SCHEDULE, schedule = "Rent", full = true),
                GoalTemplateDraft(GoalTemplateKind.CAP, cap = "20", period = "weekly", weekStart = "2027-01-04", hold = true),
                GoalTemplateDraft(GoalTemplateKind.REMAINDER, weight = "3"),
                GoalTemplateDraft(GoalTemplateKind.GOAL, amount = "1000"),
            ).map { it.line() }
        assertEquals(
            listOf(
                "#template-2 50 up to 200",
                "#template 1500 by 2027-06 repeat every year",
                "#template 12.5% of previous Salary",
                "#template average 6 months [decrease 10%]",
                "#template schedule full Rent",
                "#template up to 20 per week starting 2027-01-04 hold",
                "#template remainder 3",
                "#goal 1000",
            ),
            lines,
        )
    }

    @Test
    fun incompleteDraftsHaveNoLine() {
        assertNull(GoalTemplateDraft(amount = "").line())
        assertNull(GoalTemplateDraft(GoalTemplateKind.BY_DATE, amount = "10", month = "June").line())
        assertNull(GoalTemplateDraft(GoalTemplateKind.CAP, cap = "20", period = "weekly").line())
        assertNull(GoalTemplateDraft(GoalTemplateKind.SCHEDULE).line())
        assertNull(GoalTemplateDraft(amount = "5", priority = "-1").line())
    }

    @Test
    fun templatesReadAsPlainWords() {
        val money: (Double) -> String = { "$" + it.toString().removeSuffix(".0") }
        assertEquals(
            "$50 a month, up to $200 a week, keeping any extra",
            describeGoalTemplate(
                GoalTemplate("simple", monthly = 50.0, limit = GoalTemplateLimit(200.0, true, "weekly", "2027-01-04")),
                money,
            ),
        )
        assertEquals(
            "Top up to $5 a day · priority 2",
            describeGoalTemplate(GoalTemplate("simple", priority = 2, limit = GoalTemplateLimit(5.0, period = "daily")), money),
        )
        assertEquals(
            "Save $300 by 2026-12, spending from 2026-09 · repeats every year",
            describeGoalTemplate(GoalTemplate("spend", amount = 300.0, month = "2026-12", from = "2026-09", annual = true), money),
        )
        assertEquals(
            "Average of the last 3 months plus 10%",
            describeGoalTemplate(GoalTemplate("average", numMonths = 3, adjustment = 10.0, adjustmentType = "percent"), money),
        )
        assertEquals(
            "Cover “Phone” minus $5",
            describeGoalTemplate(GoalTemplate("schedule", name = "Phone", adjustment = -5.0, adjustmentType = "fixed"), money),
        )
        assertEquals(
            "$10 every 2 weeks from 2027-01-01",
            describeGoalTemplate(GoalTemplate("periodic", amount = 10.0, period = "week", periodCount = 2, starting = "2027-01-01"), money),
        )
        assertEquals(
            "Can’t read “#template ?”",
            describeGoalTemplate(GoalTemplate("error", directive = "error", priority = null, line = "#template ?"), money),
        )
    }

    @Test
    fun statusSaysOnTrackOrHowMuchIsMissing() {
        val money: (Long) -> String = { "$" + (it / 100) }
        val month = GoalTemplateStatus(listOf(GoalTemplate("simple", monthly = 100.0)), 10_000, 10_000, false, emptyList())
        assertEquals(true to "On track · Template $100 this month", goalTemplateStatusText(month, 10_000, 0, money))
        assertEquals(false to "Underfunded by $40 · Template $100 this month", goalTemplateStatusText(month, 6_000, 0, money))
        val goal = GoalTemplateStatus(listOf(GoalTemplate("goal", directive = "goal", amount = 500.0)), 0, 50_000, true, emptyList())
        assertEquals(false to "Underfunded by $200 · Goal $500", goalTemplateStatusText(goal, 0, 30_000, money))
        assertEquals(false to "Rent: x", goalTemplateStatusText(month.copy(problems = listOf("Rent: x")), 0, 0, money))
        assertNull(goalTemplateStatusText(month.copy(goal = null), 0, 0, money))
    }
}
