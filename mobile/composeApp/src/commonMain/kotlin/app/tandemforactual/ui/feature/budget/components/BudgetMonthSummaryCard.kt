@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.rememberTodayIsoDate
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.BudgetAssignmentKind
import app.tandemforactual.ui.core.state.BudgetAssignmentPresentation
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isBudgetSectionCollapsed
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit
import app.tandemforactual.ui.feature.budget.stillToSpend
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.WalletCards

/** Collapsible month summary: amount to assign, held money and what can still be spent. */
@Composable
internal fun BudgetMonthSummaryCard(
    state: AppState,
    summaryKey: String,
    groups: List<LiveCategoryGroupSummary>,
    assignmentPresentation: BudgetAssignmentPresentation,
    heldForNextMonthMinor: Long,
    heldPending: Boolean,
    isLiveBudget: Boolean,
    billsDueMinor: Long,
    sectionSpacing: Dp,
    onOpenHold: () -> Unit,
    onAutoAssign: () -> Unit = {},
) {
    val palette = tandemPalette
    val displayedAssignmentMinor = assignmentPresentation.amountMinor
    val readyToAssignColor =
        when (assignmentPresentation.kind) {
            BudgetAssignmentKind.OVERBUDGETED -> palette.negative
            BudgetAssignmentKind.TO_BUDGET -> palette.positive
            BudgetAssignmentKind.ALL_ASSIGNED -> palette.muted
        }
    val summaryExpanded = !state.isBudgetSectionCollapsed(summaryKey)
    Column {
        CollapsibleSectionCard(
            "${state.selectedMonth} summary",
            "${categoryCountLabel(groups.sumOf { it.categories.size })} · ${assignmentPresentation.label}",
            actionContent = {
                PrivacyAmountText(
                    state.formatCurrency(displayedAssignmentMinor),
                    privacyModeEnabled = state.privacyModeEnabled,
                    fractionDigits = state.currencyFractionDigitsForDisplay,
                    color = readyToAssignColor,
                    fontSize = TandemTypography.label,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            },
            actionColor = readyToAssignColor,
            expanded = summaryExpanded,
            onToggleExpanded = { state.toggleBudgetSection(summaryKey) },
        ) {}
        AnimatedVisibility(
            visible = summaryExpanded,
            enter = tandemRevealEnter(tandemReduceMotion),
            exit = tandemRevealExit(tandemReduceMotion),
        ) {
            SurfaceCard(Modifier.padding(top = sectionSpacing)) {
                Column {
                    BudgetAssignmentRow(state, assignmentPresentation, readyToAssignColor, isLiveBudget)
                    if (isLiveBudget && assignmentPresentation.kind == BudgetAssignmentKind.TO_BUDGET && !state.readOnlyMode) {
                        DividerLine()
                        ListRow(
                            "Auto-assign",
                            "Share out what’s left to budget",
                            leading = RowLeading.Icon(Lucide.Sparkles),
                            onClick = onAutoAssign,
                        )
                    }
                    if (isLiveBudget) {
                        DividerLine()
                        BudgetHoldRow(state, heldForNextMonthMinor, heldPending, onOpenHold)
                    }
                    BudgetStillToSpendRow(state, groups, isLiveBudget, billsDueMinor)
                }
            }
        }
    }
}

@Composable
private fun BudgetAssignmentRow(
    state: AppState,
    assignmentPresentation: BudgetAssignmentPresentation,
    readyToAssignColor: Color,
    isLiveBudget: Boolean,
) {
    ListRow(
        assignmentPresentation.label,
        if (isLiveBudget) state.selectedMonth else "On track",
        leading = RowLeading.Icon(Lucide.WalletCards),
        options = ListRowOptions(trailingColor = readyToAssignColor),
        trailingContent = {
            PrivacyAmountText(
                state.formatCurrency(assignmentPresentation.amountMinor),
                privacyModeEnabled = state.privacyModeEnabled,
                fractionDigits = state.currencyFractionDigitsForDisplay,
                color = readyToAssignColor,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

@Composable
private fun BudgetHoldRow(
    state: AppState,
    heldForNextMonthMinor: Long,
    heldPending: Boolean,
    onOpenHold: () -> Unit,
) {
    ListRow(
        "Hold for next month",
        leading = RowLeading.Icon(Lucide.CalendarDays),
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                if (heldPending) PendingBudgetSyncIcon()
                PrivacyAmountText(
                    state.formatCurrency(heldForNextMonthMinor),
                    privacyModeEnabled = state.privacyModeEnabled,
                    fractionDigits = state.currencyFractionDigitsForDisplay,
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        onClick = onOpenHold,
    )
}

@Composable
private fun BudgetStillToSpendRow(
    state: AppState,
    groups: List<LiveCategoryGroupSummary>,
    isLiveBudget: Boolean,
    billsDueMinor: Long,
) {
    val today = rememberTodayIsoDate()
    val left =
        if (isLiveBudget && state.selectedMonthId == today.take(7)) {
            stillToSpend(
                groups,
                today.drop(8).take(2).toIntOrNull() ?: 0,
                daysInMonth(state.selectedMonthId) ?: 0,
            )
        } else {
            null
        }
    if (left != null) {
        DividerLine()
        ListRow(
            "You can still spend",
            if (state.privacyModeEnabled) {
                null
            } else {
                listOfNotNull(
                    "About ${state.formatCurrency(left.perDayMinor)} a day",
                    billsDueMinor.takeIf { it > 0 }?.let { "${state.formatCurrency(it)} of bills still due" },
                ).joinToString(" · ")
            },
            leading = RowLeading.Icon(Lucide.Gauge),
            trailingContent = {
                PrivacyAmountText(
                    state.formatCurrency(left.totalMinor),
                    privacyModeEnabled = state.privacyModeEnabled,
                    fractionDigits = state.currencyFractionDigitsForDisplay,
                    color = tandemPalette.text,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}
