@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import app.tandemforactual.data.store.BulkTransactionEdit
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.createBudgetPayee
import app.tandemforactual.ui.core.session.transactions.applyBulkEdit
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.rows.SectionCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Undo2
import kotlinx.coroutines.launch

/** The text a payee was cleaned from, when it differs from the payee, with a way to use that text as the payee instead. */
internal fun originalPayeeText(
    importedPayee: String?,
    payeeName: String,
): String? = importedPayee?.trim()?.takeIf { it.isNotEmpty() && !it.equals(payeeName.trim(), ignoreCase = true) }

@Composable
internal fun OriginalPayeeText(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val transactionId = state.selectedTransactionRecordId
    val record = mobileSession.transactionRecords.firstOrNull { it.recordId == transactionId } ?: return
    val original = originalPayeeText(record.recordBody()?.stringValue("importedPayee"), state.selectedTransactionPayeeName) ?: return
    val scope = rememberCoroutineScope()
    SectionCard("Payee text", detail = "Came in as “$original”") {
        TandemActionButton(
            "Use this as the payee",
            TandemActionButtonTone.NEUTRAL,
            icon = Lucide.Undo2,
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
        ) {
            scope.launch {
                val budgetId = state.selectedBudgetId
                val password = state.encryptedPassword.takeIf { it.isNotBlank() }
                val payeeId =
                    mobileSession.payeeRecords.firstOrNull { it.recordName()?.trim().equals(original, ignoreCase = true) }?.recordId
                        ?: mobileSession.budgetEdits.createBudgetPayee(budgetId, original, password)
                        ?: return@launch
                if (mobileSession.transactions.applyBulkEdit(budgetId, listOf(transactionId), BulkTransactionEdit.SetPayee(payeeId))) {
                    state.selectedTransactionPayeeName = original
                }
            }
        }
    }
}
