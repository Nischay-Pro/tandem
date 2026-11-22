@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.GoalInput
import app.tandemforactual.data.planning.GoalSplit
import app.tandemforactual.data.planning.GoalTimeline
import app.tandemforactual.data.planning.GoalTimelinePlan
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowAction
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.insights.monthFromNow
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank

/** When each savings goal is reached for a monthly amount, filling them one at a time or all at once. */
@Composable
internal fun GoalTimelineScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccounts: List<LiveAccountSummary>,
    month: LiveBudgetMonthSummary?,
) {
    val palette = tandemPalette
    val editors = rememberPlanningEditors(state, mobileSession, month)
    val goals = mobileSession.planning.savingsGoals
    val today = platformTodayIsoDate()
    if (goals.isEmpty()) {
        PageColumn {
            EmptyState(
                "Add a savings goal",
                "Add two or more goals to see when each one is reached.",
                actionLabel = "Add goal",
                onAction = { editors.editGoal(null) },
            )
        }
        return
    }
    val suggested =
        remember(goals, month, today) {
            goals.sumOf { goal ->
                val saved = goal.saved(month, liveAccounts) ?: 0
                goal.targetDate?.let { PlanningMath.monthlyToTarget(goal.targetMinor, saved, PlanningMath.monthsBetween(today, it)) } ?: 0
            }
        }
    var amountText by rememberSaveable { mutableStateOf(if (suggested > 0) formatAmount(suggested, state.budgetDisplaySettings) else "") }
    var split by rememberSaveable { mutableStateOf(GoalSplit.PRIORITY) }
    var order by rememberSaveable {
        mutableStateOf(goals.sortedWith(compareBy<SavingsGoal> { it.targetDate ?: "9999" }.thenBy { it.name.lowercase() }).map { it.id })
    }
    val ordered = order.mapNotNull { id -> goals.firstOrNull { it.id == id } } + goals.filter { it.id !in order }
    val monthly = parseAmountMinorUnits(amountText, state.budgetDisplaySettings)?.coerceAtLeast(0) ?: 0
    val inputs = ordered.map { GoalInput(it.id, it.targetMinor - (it.saved(month, liveAccounts) ?: 0)) }
    val plan = remember(inputs, monthly, split) { GoalTimeline.plan(inputs, monthly, split) }
    val horizon = plan.goals.maxOfOrNull { it.reachedMonth ?: 0 }?.coerceAtLeast(1) ?: 1
    PageColumn(verticalSpacing = TandemSpacing.large) {
        GoalTimelineSummary(state, goals.size, plan, monthly, today, inputs)
        TextFieldCard("Each month", amountText, { amountText = it }, goalAmountOptions(state))
        FilterPills(listOf(GoalSplit.PRIORITY to "One at a time", GoalSplit.EVEN to "Split evenly"), split, { split = it })
        GoalTimelineCard(plan, ordered, split, today, horizon)
        GoalPriorityList(
            state = state,
            ordered = ordered,
            plan = plan,
            inputs = inputs,
            split = split,
            today = today,
            saved = { it.saved(month, liveAccounts) },
            onEdit = { editors.editGoal(it) },
            onMoveUp = { index -> order = ordered.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) } },
        )
        Text(
            "Progress comes from each goal’s linked category or account. Nothing here changes your budget.",
            color = palette.muted,
            fontSize = TandemTypography.label,
        )
    }
}

/** When every goal is reached for the chosen amount, and how much is left to save. */
@Composable
private fun GoalTimelineSummary(
    state: AppState,
    goalCount: Int,
    plan: GoalTimelinePlan,
    monthly: Long,
    today: String,
    inputs: List<GoalInput>,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            if (goalCount ==
                1
            ) {
                "Goal reached by"
            } else {
                "Every goal reached by"
            },
            color = palette.muted,
            fontSize = TandemTypography.body,
        )
        Text(
            when {
                plan.months == 0 -> "Already there"
                plan.months == null && monthly == 0L -> "Add a monthly amount"
                plan.months == null -> "Not within 50 years"
                else -> monthFromNow(today, plan.months ?: 0)
            },
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
        Text(
            "${state.formatCurrency(inputs.sumOf { it.remainingMinor.coerceAtLeast(0) })} to go across $goalCount " +
                if (goalCount == 1) "goal" else "goals",
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
    }
}

/** One bar per goal showing when it starts getting money and when it is reached. */
@Composable
private fun GoalTimelineCard(
    plan: GoalTimelinePlan,
    ordered: List<SavingsGoal>,
    split: GoalSplit,
    today: String,
    horizon: Int,
) {
    val palette = tandemPalette
    SectionCard(
        "Timeline",
        if (split == GoalSplit.PRIORITY) {
            "Each goal is filled in order before the next starts"
        } else {
            "Shared evenly until each is reached"
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(TandemSpacing.large)
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS)),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            plan.goals.forEach { milestone ->
                val goal = ordered.first { it.id == milestone.id }
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Row {
                        Text(
                            goal.name,
                            Modifier.weight(1f).padding(end = TandemSpacing.small),
                            color = palette.text,
                            fontSize = TandemTypography.compactBody,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            milestone.reachedMonth?.let { if (it == 0) "Reached" else monthFromNow(today, it) } ?: "Not yet",
                            color = palette.muted,
                            fontSize = TandemTypography.caption,
                        )
                    }
                    TimelineBar(milestone.startMonth, milestone.reachedMonth, horizon)
                }
            }
            Row {
                Text("Now", Modifier.weight(1f), color = palette.muted, fontSize = TandemTypography.caption)
                Text(monthFromNow(today, horizon), color = palette.muted, fontSize = TandemTypography.caption)
            }
        }
    }
}

/** The goals in funding order, each with what is left and whether it lands on time. */
@Composable
private fun GoalPriorityList(
    state: AppState,
    ordered: List<SavingsGoal>,
    plan: GoalTimelinePlan,
    inputs: List<GoalInput>,
    split: GoalSplit,
    today: String,
    saved: (SavingsGoal) -> Long?,
    onEdit: (SavingsGoal?) -> Unit,
    onMoveUp: (Int) -> Unit,
) {
    SectionCard(
        "Your goals",
        if (split == GoalSplit.PRIORITY) "Move a goal up to fund it sooner" else "Tap a goal to edit it",
        "Add goal",
        onClick = { onEdit(null) },
    ) {
        ordered.forEachIndexed { index, goal ->
            if (index > 0) DividerLine()
            val milestone = plan.goals.first { it.id == goal.id }
            val remaining = inputs.first { it.id == goal.id }.remainingMinor.coerceAtLeast(0)
            val target = goal.targetDate
            val reached = milestone.reachedMonth
            val lateness =
                when {
                    target == null || reached == null -> null
                    reached <= PlanningMath.monthsBetween(today, target) -> "on time for ${formatLiveDate(target)}"
                    else -> "after its ${formatLiveDate(target)} target"
                }
            ListRow(
                if (split == GoalSplit.PRIORITY) "${index + 1}. ${goal.name}" else goal.name,
                listOfNotNull(
                    if (remaining == 0L) "Fully saved" else "${state.formatCurrency(remaining)} to go",
                    if (saved(goal) == null) "not linked to a balance" else null,
                    lateness,
                ).joinToString(" · "),
                leading = RowLeading.Icon(Lucide.PiggyBank),
                onClick = { onEdit(goal) },
                options =
                    ListRowOptions(
                        showChevron = false,
                        trailingAction =
                            if (split == GoalSplit.PRIORITY && index > 0) {
                                ListRowAction(Lucide.ArrowUp, "Move ${goal.name} up") { onMoveUp(index) }
                            } else {
                                null
                            },
                    ),
            )
        }
    }
}

@Composable
private fun TimelineBar(
    startMonth: Int?,
    reachedMonth: Int?,
    horizon: Int,
) {
    val palette = tandemPalette
    val start = ((startMonth ?: horizon) - 1).coerceAtLeast(0).toFloat() / horizon
    val end = (reachedMonth ?: horizon).toFloat() / horizon
    val reveal by animateFloatAsState(end, tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS), label = "goal-timeline")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(palette.raised),
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (start > 0f) Spacer(Modifier.weight(start))
            val span = (reveal - start).coerceAtLeast(0.02f)
            Box(
                Modifier
                    .weight(span)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (reachedMonth == null) palette.warning else palette.accent),
            )
            val rest = 1f - start - span
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
    }
}

private fun goalAmountOptions(state: AppState) =
    TextFieldOptions(
        placeholder = "0",
        currencySymbol = state.budgetDisplaySettings?.currencySymbol ?: "$",
        currencySymbolAfter = state.budgetDisplaySettings?.currencySymbolPosition == "after",
        currencySymbolSpace = state.budgetDisplaySettings?.currencySpaceBetweenAmountAndSymbol == true,
        amountFractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
    )
