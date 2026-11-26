@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.holdBudgetForNextMonth
import app.tandemforactual.ui.core.session.budget.resetBudgetHold
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.BudgetAssignmentPresentation
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.TransactionAmountField
import app.tandemforactual.ui.design.templates.TransactionAmountKeypad
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.holdAmountAllowed
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Month figures shown in the hold-for-next-month sheet. */
internal class HoldSheetFigures(
    val readyToAssignMinor: Long,
    val assignmentPresentation: BudgetAssignmentPresentation,
    val heldForNextMonthMinor: Long,
    val heldPending: Boolean,
)

/** Sheet with a calculator keypad for holding money for next month. */
@Composable
internal fun HoldForNextMonthSheet(
    hold: HoldSheetState,
    state: AppState,
    mobileSession: TandemMobileSession,
    figures: HoldSheetFigures,
    scope: CoroutineScope,
) {
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(hold.keypadVisible) {
        if (hold.keypadVisible) {
            softwareKeyboard?.hide()
        }
    }

    fun closeHoldDialog() {
        hold.keypadVisible = false
        softwareKeyboard?.hide()
        hold.showDialog = false
    }
    TandemTaskSheet(
        visible = hold.showDialog,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) closeHoldDialog() },
        onDismissed = { hold.mounted = false },
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                HoldSheetHeader(mobileSession, ::closeHoldDialog)
                HoldSheetSummary(state, figures)
                HoldAmountEntry(hold, state, onHideKeypad = {
                    hold.keypadVisible = false
                    softwareKeyboard?.hide()
                })
                budgetActionFeedbackMessage(mobileSession)
                HoldSheetActions(hold, state, mobileSession, figures, scope, ::closeHoldDialog)
            }
        }
    }
}

@Composable
private fun HoldSheetHeader(
    mobileSession: TandemMobileSession,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
        Text(
            "Hold for next month",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            enabled = !mobileSession.isPerformingBudgetAction,
        ) { onClose() }
    }
}

@Composable
private fun HoldSheetSummary(
    state: AppState,
    figures: HoldSheetFigures,
) {
    val palette = tandemPalette
    val readyToAssignMinor = figures.readyToAssignMinor
    val heldForNextMonthMinor = figures.heldForNextMonthMinor
    ListRow(
        if (readyToAssignMinor < 0) "Overbudgeted" else figures.assignmentPresentation.label,
        trailing = state.formatCurrency(figures.assignmentPresentation.amountMinor),
        leading = RowLeading.Icon(Lucide.WalletCards),
    )
    ListRow(
        "Held",
        leading = RowLeading.Icon(Lucide.CalendarDays),
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                if (figures.heldPending) PendingBudgetSyncIcon()
                Text(
                    state.formatCurrency(heldForNextMonthMinor),
                    color = palette.muted,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
    if (readyToAssignMinor < 0) {
        InfoBubble(
            if (heldForNextMonthMinor > 0) {
                "This month is overbudgeted by ${state.formatCurrency(-readyToAssignMinor)}. Release the hold to cover it."
            } else {
                "This month is overbudgeted. Move money back before holding it for next month."
            },
        )
    }
}

@Composable
private fun HoldAmountEntry(
    hold: HoldSheetState,
    state: AppState,
    onHideKeypad: () -> Unit,
) {
    TransactionAmountField(
        amount = state.formatAmount(hold.amountMinor),
        amountMinorUnits = hold.amountMinor,
        transactionType = "Neutral",
        showCursor = hold.keypadVisible,
        onShowKeypad = { hold.keypadVisible = true },
        amountColorOverride = tandemPalette.accent,
    )
    AnimatedReveal(visible = hold.message != null) {
        InlineMessage(hold.message.orEmpty(), InlineMessageKind.ERROR)
    }
    if (hold.keypadVisible) {
        TransactionAmountKeypad(
            onDigit = { hold.enterDigit(state, it) },
            onDelete = { hold.deleteDigit() },
            onOperator = { hold.chooseOperator(state, it) },
            onEquals = { hold.calculate(state) },
            onDone = onHideKeypad,
        )
    }
}

@Composable
private fun HoldSheetActions(
    hold: HoldSheetState,
    state: AppState,
    mobileSession: TandemMobileSession,
    figures: HoldSheetFigures,
    scope: CoroutineScope,
    closeHoldDialog: () -> Unit,
) {
    val heldForNextMonthMinor = figures.heldForNextMonthMinor
    val parsedHoldAmount = hold.amountMinor
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        TandemActionButton(
            if (heldForNextMonthMinor > 0) "Release all" else "Close",
            TandemActionButtonTone.NEUTRAL,
            Modifier.weight(1f),
            enabled =
                !mobileSession.isPerformingBudgetAction &&
                    (heldForNextMonthMinor == 0L || !state.readOnlyMode),
            loading = mobileSession.isPerformingBudgetAction && heldForNextMonthMinor > 0,
        ) {
            if (heldForNextMonthMinor > 0) {
                scope.launch {
                    val reset =
                        mobileSession.budgetEdits.resetBudgetHold(
                            state.selectedBudgetId,
                            state.selectedMonthId,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    if (reset) closeHoldDialog()
                }
            } else {
                closeHoldDialog()
            }
        }
        TandemActionButton(
            when {
                mobileSession.isPerformingBudgetAction -> "Holding…"
                else -> "Hold amount"
            },
            TandemActionButtonTone.PRIMARY,
            Modifier.weight(1f),
            icon = Lucide.Check,
            enabled =
                !state.readOnlyMode &&
                    !mobileSession.isPerformingBudgetAction &&
                    holdAmountAllowed(parsedHoldAmount, figures.readyToAssignMinor),
            loading = mobileSession.isPerformingBudgetAction,
        ) {
            scope.launch {
                val held =
                    mobileSession.budgetEdits.holdBudgetForNextMonth(
                        state.selectedBudgetId,
                        state.selectedMonthId,
                        parsedHoldAmount,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                if (held) closeHoldDialog()
            }
        }
    }
}
