@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.PlanningMath
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank

@Composable
internal fun SavingsGoalsCard(
    goals: List<SavingsGoal>,
    month: LiveBudgetMonthSummary?,
    accounts: List<LiveAccountSummary>,
    settings: BudgetDisplaySettings?,
    today: String,
    privacy: Boolean,
    onEdit: (SavingsGoal?) -> Unit,
) {
    val palette = tandemPalette
    SectionCard("Savings goals", "Progress toward what you’re saving for", "Add goal", onClick = { onEdit(null) }) {
        goals.forEachIndexed { index, goal ->
            if (index > 0) DividerLine()
            val saved = goal.saved(month, accounts)
            val fraction = if (saved == null || goal.targetMinor <= 0) 0f else (saved.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
            val progress by animateFloatAsState(
                fraction,
                tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS),
                label = "goal-progress",
            )
            val detail =
                when {
                    saved == null -> "Link a category or account to track progress"
                    privacy -> "${(fraction * 100).toInt()}% saved"
                    goal.targetDate != null && saved < goal.targetMinor -> {
                        val monthly =
                            PlanningMath.monthlyToTarget(
                                goal.targetMinor,
                                saved,
                                PlanningMath.monthsBetween(today, goal.targetDate.orEmpty()),
                            )
                        "${formatCurrency(
                            saved,
                            settings,
                        )} of ${formatCurrency(goal.targetMinor, settings)} · about ${formatCurrency(monthly, settings)} a month"
                    }
                    else -> "${formatCurrency(saved, settings)} of ${formatCurrency(goal.targetMinor, settings)}"
                }
            Column(
                Modifier.fillMaxWidth().padding(bottom = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ListRow(
                    goal.name,
                    detail,
                    leading = RowLeading.Icon(Lucide.PiggyBank),
                    onClick = { onEdit(goal) },
                    options = ListRowOptions(dense = true),
                )
                if (saved != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(palette.raised),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(CircleShape)
                                    .background(palette.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}
