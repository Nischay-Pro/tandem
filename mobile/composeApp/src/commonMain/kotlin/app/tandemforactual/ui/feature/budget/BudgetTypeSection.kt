@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetType
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import kotlinx.coroutines.launch

/** Envelope or tracking for the open budget, switched only after saying what changes. */
@Composable
internal fun BudgetTypeSection(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val tracking = state.budgetDisplaySettings?.budgetType == "tracking"
    var confirming by remember(state.selectedBudgetId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SectionCard("Budget type", budgetTypeDescription(tracking)) {
        Column(Modifier.padding(TandemSpacing.large)) {
            SegmentedSelector(listOf("Envelope" to !tracking, "Tracking" to tracking)) { index ->
                if ((index == 1) != tracking && !state.readOnlyMode) confirming = true
            }
        }
    }
    TandemChoiceDialog(confirming, { confirming = false }) {
        SheetCard(
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader(
                    title = if (tracking) "Switch to envelope?" else "Switch to tracking?",
                    titleSize = TandemTypography.cardTitle,
                    onClose = { confirming = false },
                )
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Keep ${if (tracking) "tracking" else "envelope"}", Modifier.weight(1f)) { confirming = false }
                    PrimaryButton(
                        "Switch",
                        enabled = !mobileSession.isPerformingBudgetAction,
                        modifier = Modifier.weight(1f),
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val type = if (tracking) "envelope" else "tracking"
                            val password = state.encryptedPassword.takeIf { it.isNotBlank() }
                            if (mobileSession.budgetEdits.setBudgetType(state.selectedBudgetId, type, password)) confirming = false
                        }
                    }
                }
            },
        ) {
            BodyText(budgetTypeSwitchChanges(toTracking = !tracking))
        }
    }
}

internal fun budgetTypeDescription(tracking: Boolean): String =
    if (tracking) {
        "Each category gets a monthly plan, and you see what you spent and saved against it."
    } else {
        "All income is assigned to categories, and what you don’t spend stays in them."
    }

internal fun budgetTypeSwitchChanges(toTracking: Boolean): String =
    if (toTracking) {
        "Categories get a monthly plan instead of money. To budget, holds and moving money go away, and income " +
            "categories get an expected amount. Your envelope amounts are kept and come back if you switch again."
    } else {
        "Income is assigned to categories again, and To budget, holds and moving money come back. Your tracking " +
            "plans are kept and come back if you switch again."
    }
