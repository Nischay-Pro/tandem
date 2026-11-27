@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatShortMonth
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** Year stepper and month grid for choosing the budget month. */
@Composable
internal fun MonthPickerDialog(
    visible: Boolean,
    state: AppState,
    onDismiss: () -> Unit,
) {
    var selectedYear by remember(state.selectedBudgetId, state.selectedMonthId) {
        mutableStateOf(state.selectedMonthId.substringBefore('-').toIntOrNull() ?: 2026)
    }
    val months = (1..12).map { month -> month to formatShortMonth(selectedYear, month) }
    TandemChoiceDialog(visible, onDismiss) {
        SheetCard(
            maxWidth = 480.dp,
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader(
                    title = "Choose month",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismiss,
                )
            },
        ) {
            PeriodStepper(
                title = selectedYear.toString(),
                previousLabel = "Previous year",
                nextLabel = "Next year",
                onPrevious = { selectedYear -= 1 },
                onNext = { selectedYear += 1 },
            )
            months.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    row.forEach { (monthNumber, monthLabel) ->
                        MonthTile(state, selectedYear, monthNumber, monthLabel, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MonthTile(
    state: AppState,
    selectedYear: Int,
    monthNumber: Int,
    monthLabel: String,
    onDismiss: () -> Unit,
) {
    val full = formatMonth(selectedYear, monthNumber)
    val chosen = state.selectedMonth == full
    SelectableTile(
        selected = chosen,
        contentDescription = full,
        onSelect = {
            state.selectedMonth = full
            state.selectedMonthId = "$selectedYear-${monthNumber.toString().padStart(2, '0')}"
            onDismiss()
        },
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        selectedBackground = tandemPalette.accentSurface,
        unselectedBackground = tandemPalette.surface,
        selectedBorder = tandemPalette.accent,
        unselectedBorder = tandemPalette.border,
    ) {
        Text(
            monthLabel,
            color = if (chosen) tandemPalette.accent else tandemPalette.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = TandemTypography.body,
        )
    }
}
