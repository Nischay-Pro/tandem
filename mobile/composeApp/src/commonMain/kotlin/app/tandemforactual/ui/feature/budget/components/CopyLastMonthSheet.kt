@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.copyBudgetMonth
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** Confirmation sheet that replaces this month's assignments with last month's. */
@Composable
internal fun CopyLastMonthSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) onDismiss() },
        onDismissed = {},
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Copy last month",
                    leadingIcon = Lucide.CalendarDays,
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClose = { if (!mobileSession.isPerformingBudgetAction) onDismiss() },
                )
                Text(
                    "Replace ${state.selectedMonth} assignments with last month’s amounts?",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.body,
                )
                budgetActionFeedbackMessage(mobileSession)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                    ) { onDismiss() }
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Copying…" else "Copy",
                        TandemActionButtonTone.DESTRUCTIVE,
                        modifier = Modifier.weight(1f),
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val copied =
                                mobileSession.budgetEdits.copyBudgetMonth(
                                    state.selectedBudgetId,
                                    state.selectedMonthId,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (copied) onDismiss()
                        }
                    }
                }
            }
        }
    }
}
