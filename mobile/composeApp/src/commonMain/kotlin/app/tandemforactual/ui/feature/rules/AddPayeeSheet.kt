@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetPayee
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Task sheet for adding a new payee by name. */
@Composable
internal fun AddPayeeSheet(
    visible: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    onClose: () -> Unit,
    payees: List<Pair<String, String>>,
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
) {
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = {
            if (!mobileSession.isPerformingBudgetAction) onClose()
        },
        onDismissed = {},
    ) {
        SheetCard(
            maxWidth = 480.dp,
            header = {
                TandemModalHeader(
                    title = "Add payee",
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClose = { if (!mobileSession.isPerformingBudgetAction) onClose() },
                )
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        enabled = !mobileSession.isPerformingBudgetAction,
                        onClick = onClose,
                    )
                    TandemActionButton(
                        if (mobileSession.isPerformingBudgetAction) "Adding…" else "Add",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.weight(1f),
                        icon = Lucide.Plus,
                        enabled =
                            !state.readOnlyMode &&
                                !mobileSession.isPerformingBudgetAction &&
                                name.isNotBlank() &&
                                payees.none { it.second.equals(name.trim(), ignoreCase = true) },
                        loading = mobileSession.isPerformingBudgetAction,
                    ) {
                        scope.launch {
                            val created =
                                mobileSession.budgetEdits.createBudgetPayee(
                                    state.selectedBudgetId,
                                    name.trim(),
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (created != null) {
                                onClose()
                                onNameChange("")
                            }
                        }
                    }
                }
            },
        ) {
            TextFieldCard("Name", name, onNameChange)
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}
