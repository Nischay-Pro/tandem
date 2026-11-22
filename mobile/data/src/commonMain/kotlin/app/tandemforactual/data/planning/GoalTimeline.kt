package app.tandemforactual.data.planning

data class GoalInput(
    val id: String,
    val remainingMinor: Long,
)

/** How a monthly contribution is shared: one goal at a time in order, or evenly across every goal still open. */
enum class GoalSplit { PRIORITY, EVEN }

/** When a goal first gets money and the month it's reached, counted from now; null when it isn't reached in fifty years. */
data class GoalMilestone(
    val id: String,
    val startMonth: Int?,
    val reachedMonth: Int?,
)

data class GoalTimelinePlan(
    val goals: List<GoalMilestone>,
    /** The month the last goal is reached, or null when one never is. */
    val months: Int?,
)

object GoalTimeline {
    private const val MAX_MONTHS = 600

    /**
     * Puts [monthlyMinor] toward [goals] every month. With [GoalSplit.EVEN], money a finished goal no longer needs goes to
     * the others in the same month, so nothing sits idle.
     */
    fun plan(
        goals: List<GoalInput>,
        monthlyMinor: Long,
        split: GoalSplit,
    ): GoalTimelinePlan {
        val remaining = goals.associate { it.id to it.remainingMinor.coerceAtLeast(0) }.toMutableMap()
        val start = mutableMapOf<String, Int>()
        val reached = remaining.filterValues { it == 0L }.mapValues { 0 }.toMutableMap()
        var month = 0
        val monthly = monthlyMinor.coerceAtLeast(0)
        while (monthly > 0 && remaining.values.any { it > 0 } && month < MAX_MONTHS) {
            month++
            var available = monthly
            while (available > 0) {
                val open = goals.filter { remaining.getValue(it.id) > 0 }
                if (open.isEmpty()) break
                val targets = if (split == GoalSplit.PRIORITY) open.take(1) else open
                val share = available / targets.size
                var extra = available % targets.size
                targets.forEach { goal ->
                    val offer = share + if (extra > 0) 1 else 0
                    if (extra > 0) extra--
                    val pay = minOf(offer, remaining.getValue(goal.id))
                    if (pay > 0) start.getOrPut(goal.id) { month }
                    remaining[goal.id] = remaining.getValue(goal.id) - pay
                    available -= pay
                    if (remaining.getValue(goal.id) == 0L) reached.getOrPut(goal.id) { month }
                }
            }
        }
        val milestones = goals.map { GoalMilestone(it.id, start[it.id] ?: reached[it.id], reached[it.id]) }
        val done = milestones.all { it.reachedMonth != null }
        return GoalTimelinePlan(milestones, if (done) milestones.maxOfOrNull { it.reachedMonth ?: 0 } ?: 0 else null)
    }
}
