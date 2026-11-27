@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.renameBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

/** Task sheet for renaming the selected account. */
@Composable
internal fun RenameAccountSheet(
    visible: Boolean,
    account: LiveAccountSummary?,
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) onClose() },
        onDismissed = {},
    ) {
        if (account != null) {
            SheetCard(
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rename account",
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
                            onClick = onClose,
                        )
                    }
                },
                actions = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                            onClick = onClose,
                        )
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled = value.isNotBlank() && !mobileSession.isPerformingBudgetAction,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                if (mobileSession.budgetEdits.renameBudgetAccount(
                                        state.selectedBudgetId,
                                        account.id,
                                        value,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                ) {
                                    onClose()
                                }
                            }
                        }
                    }
                },
            ) {
                TextFieldCard("Name", value, onValueChange, TextFieldOptions(placeholder = "Account name"))
                budgetActionFeedbackMessage(mobileSession)
            }
        }
    }
}
