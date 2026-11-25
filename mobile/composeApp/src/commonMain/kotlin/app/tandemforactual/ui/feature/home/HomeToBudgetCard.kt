@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.BudgetAssignmentKind
import app.tandemforactual.ui.core.state.presentBudgetAssignment
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** This month's money left to budget, or how far it is overbudgeted, at the top of Home; tapping opens the budget. */
@Composable
internal fun HomeToBudgetCard(
    toBudgetMinor: Long,
    settings: BudgetDisplaySettings?,
    privacyModeEnabled: Boolean,
    onOpenBudget: () -> Unit,
) {
    val palette = tandemPalette
    val assignment = presentBudgetAssignment(toBudgetMinor)
    val color =
        when (assignment.kind) {
            BudgetAssignmentKind.OVERBUDGETED -> palette.negative
            BudgetAssignmentKind.TO_BUDGET -> palette.positive
            BudgetAssignmentKind.ALL_ASSIGNED -> palette.muted
        }
    SurfaceCard(onClick = onOpenBudget, onClickLabel = "Open budget") {
        Row(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(assignment.label, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
            if (assignment.kind != BudgetAssignmentKind.ALL_ASSIGNED) {
                PrivacyAmountText(
                    formatCurrency(assignment.amountMinor, settings),
                    privacyModeEnabled,
                    color = color,
                    fontSize = TandemTypography.cardTitle,
                    fontWeight = FontWeight.Bold,
                    fractionDigits = settings?.currencyFractionDigits ?: 2,
                )
            }
        }
    }
}
