package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalTimelineTest {
    @Test
    fun fillsGoalsOneAtATimeInPriorityOrder() {
        val plan = GoalTimeline.plan(listOf(GoalInput("a", 300), GoalInput("b", 200)), 100, GoalSplit.PRIORITY)
        assertEquals(listOf(GoalMilestone("a", 1, 3), GoalMilestone("b", 4, 5)), plan.goals)
        assertEquals(5, plan.months)
    }

    @Test
    fun splitsEvenlyAndPassesOnWhatAFinishedGoalDoesntNeed() {
        val plan = GoalTimeline.plan(listOf(GoalInput("a", 300), GoalInput("b", 30)), 100, GoalSplit.EVEN)
        assertEquals(listOf(GoalMilestone("a", 1, 4), GoalMilestone("b", 1, 1)), plan.goals)
        assertEquals(4, plan.months)
        val odd = GoalTimeline.plan(listOf(GoalInput("a", 51), GoalInput("b", 50)), 101, GoalSplit.EVEN)
        assertEquals(1, odd.months)
    }

    @Test
    fun reportsGoalsThatAreNeverReached() {
        val none = GoalTimeline.plan(listOf(GoalInput("done", -5), GoalInput("open", 100)), 0, GoalSplit.EVEN)
        assertEquals(listOf(GoalMilestone("done", 0, 0), GoalMilestone("open", null, null)), none.goals)
        assertNull(none.months)
        assertNull(GoalTimeline.plan(listOf(GoalInput("big", 1_000_000)), 1, GoalSplit.PRIORITY).months)
        assertEquals(0, GoalTimeline.plan(emptyList(), 100, GoalSplit.PRIORITY).months)
    }
}
