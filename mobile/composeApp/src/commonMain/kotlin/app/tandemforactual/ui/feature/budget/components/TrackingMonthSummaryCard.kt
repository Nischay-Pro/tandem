@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.TrackingMonthFigures
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.core.state.trackingMonthFigures
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.ProgressBar
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.TriangleAlert

/** A tracking budget's month: income against what was expected, spending against the plan, and what was saved. */
@Composable
internal fun TrackingMonthSummaryCard(
    state: AppState,
    summaryKey: String,
    groups: List<LiveCategoryGroupSummary>,
    billsDueMinor: Long,
    sectionSpacing: Dp,
) {
    val figures = remember(groups) { trackingMonthFigures(groups) }
    val savedColor = if (figures.savedMinor < 0) tandemPalette.negative else tandemPalette.positive
    val expanded = !state.isBudgetSectionCollapsed(summaryKey)
    Column {
        CollapsibleSectionCard(
            "${state.selectedMonth} summary",
            "${categoryCountLabel(groups.sumOf { it.categories.size })} · ${savedLabel(figures)}",
            actionContent = { SummaryAmount(state, figures.savedMinor, savedColor, TandemTypography.label) },
            actionColor = savedColor,
            expanded = expanded,
            onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
        ) {}
        AnimatedVisibility(
            visible = expanded,
            enter = tandemRevealEnter(tandemReduceMotion),
            exit = tandemRevealExit(tandemReduceMotion),
        ) {
            SurfaceCard(Modifier.padding(top = sectionSpacing)) {
                Column {
                    TrackingMeterRow(
                        state,
                        TrackingMeter("Income", Lucide.Banknote, figures.receivedIncomeMinor, figures.expectedIncomeMinor),
                        "received of",
                        "expected",
                        tandemPalette.positive,
                    )
                    DividerLine()
                    val over = figures.spentMinor > figures.budgetedMinor
                    TrackingMeterRow(
                        state,
                        TrackingMeter("Spending", Lucide.Receipt, figures.spentMinor, figures.budgetedMinor),
                        "spent of",
                        "budgeted",
                        if (over) tandemPalette.negative else tandemPalette.accent,
                    )
                    DividerLine()
                    TrackingSavedRow(state, figures, savedColor)
                    TrackingOverspentRow(state, figures)
                    BudgetStillToSpendRow(state, groups, isLiveBudget = true, billsDueMinor = billsDueMinor)
                }
            }
        }
    }
}

/** "Saved" or "Overspent" for the month, which is what Actual's tracking budget shows at the top. */
internal fun savedLabel(figures: TrackingMonthFigures): String = if (figures.savedMinor < 0) "Overspent" else "Saved"

/** How this month's savings compare with what the plan leaves over. */
internal fun savedAgainstPlanLabel(
    figures: TrackingMonthFigures,
    format: (Long) -> String,
): String {
    val difference = figures.savedAgainstPlanMinor
    val plan = "Plan ${format(figures.plannedSavingsMinor)}"
    return when {
        difference > 0 -> "$plan · ${format(difference)} ahead"
        difference < 0 -> "$plan · ${format(-difference)} behind"
        else -> "$plan · on plan"
    }
}

private class TrackingMeter(
    val title: String,
    val icon: ImageVector,
    val actualMinor: Long,
    val plannedMinor: Long,
)

@Composable
private fun TrackingMeterRow(
    state: AppState,
    meter: TrackingMeter,
    actualWord: String,
    plannedWord: String,
    color: Color,
) {
    val fraction =
        when {
            meter.plannedMinor > 0 -> (meter.actualMinor.toDouble() / meter.plannedMinor).toFloat()
            meter.actualMinor > 0 -> 1f
            else -> 0f
        }
    Column(Modifier.padding(bottom = TandemSpacing.medium)) {
        ListRow(
            meter.title,
            if (state.privacyModeEnabled) {
                null
            } else {
                "${state.formatCurrency(meter.actualMinor)} $actualWord ${state.formatCurrency(meter.plannedMinor)} $plannedWord"
            },
            leading = RowLeading.Icon(meter.icon),
            trailingContent = { SummaryAmount(state, meter.actualMinor, tandemPalette.text, TandemTypography.compactBody) },
        )
        ProgressBar(
            fraction,
            color,
            Modifier.padding(horizontal = TandemLayout.listRowHorizontalInset),
        )
    }
}

@Composable
private fun TrackingSavedRow(
    state: AppState,
    figures: TrackingMonthFigures,
    color: Color,
) {
    ListRow(
        savedLabel(figures),
        if (state.privacyModeEnabled) null else savedAgainstPlanLabel(figures) { state.formatCurrency(it) },
        leading = RowLeading.Icon(Lucide.PiggyBank),
        trailingContent = { SummaryAmount(state, figures.savedMinor, color, TandemTypography.compactBody) },
    )
}

@Composable
private fun TrackingOverspentRow(
    state: AppState,
    figures: TrackingMonthFigures,
) {
    if (figures.overspentCategories == 0) return
    DividerLine()
    ListRow(
        "Over budget",
        categoryCountLabel(figures.overspentCategories),
        leading = RowLeading.Icon(Lucide.TriangleAlert),
        trailingContent = { SummaryAmount(state, figures.overspentMinor, tandemPalette.negative, TandemTypography.compactBody) },
    )
}

@Composable
private fun SummaryAmount(
    state: AppState,
    amountMinor: Long,
    color: Color,
    fontSize: TextUnit,
) {
    PrivacyAmountText(
        state.formatCurrency(amountMinor),
        privacyModeEnabled = state.privacyModeEnabled,
        fractionDigits = state.currencyFractionDigitsForDisplay,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
    )
}
