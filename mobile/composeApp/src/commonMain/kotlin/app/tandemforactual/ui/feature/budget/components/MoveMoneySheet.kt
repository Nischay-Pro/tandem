@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.moveBudgetMoney
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.MoneyAmountInput
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sheet for moving money between two categories this month. */
@Composable
internal fun MoveMoneySheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary?,
    sheets: CategoryDetailSheetState,
    moveMoneyCategories: List<Pair<String, LiveCategorySummary>>,
    scope: CoroutineScope,
) {
    var moveMoneyFromExpanded by rememberSaveable { mutableStateOf(false) }
    var moveMoneyToExpanded by rememberSaveable { mutableStateOf(false) }
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) sheets.showMoveMoneyDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            val parsedMoveAmount = state.parseAmountMinorUnits(sheets.moveMoneyAmountDraft)
            val fromLabel = moveMoneyCategories.firstOrNull { it.second.id == sheets.moveMoneyFromId }?.second?.name ?: "Choose category"
            val toLabel = moveMoneyCategories.firstOrNull { it.second.id == sheets.moveMoneyToId }?.second?.name ?: "Choose category"
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    BudgetSheetTitle(Lucide.WalletCards, "Move money", mobileSession) { sheets.showMoveMoneyDialog = false }
                    MoveMoneyCategoryPicker(
                        label = "From",
                        selectedLabel = fromLabel,
                        leadingIcon = RowLeading.Icon(Lucide.Minus),
                        expanded = moveMoneyFromExpanded,
                        onExpandedChange = { moveMoneyFromExpanded = it },
                        candidates = moveMoneyCategories,
                        onSelect = { sheets.moveMoneyFromId = it },
                    )
                    MoveMoneyCategoryPicker(
                        label = "To",
                        selectedLabel = toLabel,
                        leadingIcon = RowLeading.Icon(Lucide.Plus),
                        expanded = moveMoneyToExpanded,
                        onExpandedChange = { moveMoneyToExpanded = it },
                        candidates = moveMoneyCategories.filter { it.second.id != sheets.moveMoneyFromId },
                        onSelect = { sheets.moveMoneyToId = it },
                    )
                    MoneyAmountInput(
                        label = "Amount",
                        value = sheets.moveMoneyAmountDraft,
                        placeholder = state.formatAmount(0),
                        onValueChange = { sheets.moveMoneyAmountDraft = it },
                        fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                    budgetActionFeedbackMessage(mobileSession)
                    MoveMoneyActions(state, mobileSession, sheets, parsedMoveAmount, scope)
                }
            }
        }
    }
}

@Composable
private fun MoveMoneyCategoryPicker(
    label: String,
    selectedLabel: String,
    leadingIcon: RowLeading,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    candidates: List<Pair<String, LiveCategorySummary>>,
    onSelect: (String) -> Unit,
) {
    Box {
        ListRow(label, trailing = selectedLabel, leading = leadingIcon, onClick = { onExpandedChange(true) })
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            candidates.forEach { (_, candidate) ->
                DropdownMenuItem(onClick = {
                    onSelect(candidate.id)
                    onExpandedChange(false)
                }) {
                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}

@Composable
private fun MoveMoneyActions(
    state: AppState,
    mobileSession: TandemMobileSession,
    sheets: CategoryDetailSheetState,
    parsedMoveAmount: Long?,
    scope: CoroutineScope,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        TandemActionButton(
            "Cancel",
            TandemActionButtonTone.NEUTRAL,
            Modifier.weight(1f),
            enabled = !mobileSession.isPerformingBudgetAction,
        ) { sheets.showMoveMoneyDialog = false }
        TandemActionButton(
            if (mobileSession.isPerformingBudgetAction) "Moving…" else "Move money",
            TandemActionButtonTone.PRIMARY,
            Modifier.weight(1f),
            icon = Lucide.Check,
            enabled =
                !state.readOnlyMode &&
                    !mobileSession.isPerformingBudgetAction &&
                    parsedMoveAmount != null &&
                    parsedMoveAmount > 0 &&
                    sheets.moveMoneyFromId.isNotBlank() &&
                    sheets.moveMoneyToId.isNotBlank() &&
                    sheets.moveMoneyFromId != sheets.moveMoneyToId,
            loading = mobileSession.isPerformingBudgetAction,
        ) {
            scope.launch {
                val moved =
                    mobileSession.budgetEdits.moveBudgetMoney(
                        state.selectedBudgetId,
                        state.selectedMonthId,
                        sheets.moveMoneyFromId,
                        sheets.moveMoneyToId,
                        parsedMoveAmount ?: 0L,
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                if (moved) sheets.showMoveMoneyDialog = false
            }
        }
    }
}
