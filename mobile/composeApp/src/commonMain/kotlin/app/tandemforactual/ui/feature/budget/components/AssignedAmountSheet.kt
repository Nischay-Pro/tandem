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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryAmount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.MoneyAmountInput
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sheet for changing how much is assigned to a category this month. */
@Composable
internal fun AssignedAmountSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary?,
    sheets: CategoryDetailSheetState,
    scope: CoroutineScope,
) {
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) sheets.showAssignedAmountDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            val parsedAmount = state.parseAmountMinorUnits(sheets.assignedAmountDraft)
            val canSaveAssignedAmount =
                parsedAmount != null && parsedAmount >= 0 && parsedAmount != category.assignedMinor
            SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 460.dp).padding(horizontal = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    BudgetSheetTitle(Lucide.WalletCards, "Assigned amount", mobileSession) { sheets.showAssignedAmountDialog = false }
                    Text("${category.name} · ${state.selectedMonth}", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    MoneyAmountInput(
                        label = "Amount",
                        value = sheets.assignedAmountDraft,
                        placeholder = state.formatAmount(0),
                        onValueChange = { sheets.assignedAmountDraft = it },
                        fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                    )
                    budgetActionFeedbackMessage(mobileSession)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                        ) { sheets.showAssignedAmountDialog = false }
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && canSaveAssignedAmount,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                val saved =
                                    mobileSession.budgetEdits.setBudgetCategoryAmount(
                                        state.selectedBudgetId,
                                        category.id,
                                        state.selectedMonthId,
                                        parsedAmount ?: 0L,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                if (saved) sheets.showAssignedAmountDialog = false
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Icon, title and close button shared by the category detail money sheets. */
@Composable
internal fun BudgetSheetTitle(
    icon: ImageVector,
    title: String,
    mobileSession: TandemMobileSession,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
        Text(
            title,
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
