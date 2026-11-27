@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.suggestedCategoryForPayee
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags
import kotlin.math.abs

/** Sets or clears a reviewed transaction's category through the regular edit path, so it queues offline like any edit. */
internal suspend fun TandemMobileSession.setReviewCategory(
    state: AppState,
    item: TransactionItem,
    categoryId: String?,
): Boolean =
    transactions.updateTransaction(
        budgetId = state.selectedBudgetId,
        transactionId = item.recordId ?: return false,
        amountText = formatAmount(abs(item.amountMinorUnits), state.budgetDisplaySettings),
        notes = item.notes,
        originalAmountMinor = item.amountMinorUnits,
        transactionType = if (item.amountMinorUnits > 0) "Income" else "Expense",
        displaySettings = state.budgetDisplaySettings,
        accountId = item.accountId,
        payeeId = item.payeeId,
        categoryId = categoryId,
        date = item.dateIso,
        updateDetails = true,
    )

/** The payee's default category, or the one last used with it. */
internal suspend fun TandemMobileSession.suggestedReviewCategory(
    budgetId: String,
    payeeId: String,
): String? = budgetEdits.suggestedCategoryForPayee(budgetId, payeeId)

/** Searches the budget's categories for the one a reviewed transaction belongs to. */
@Composable
internal fun ReviewCategoryPicker(
    visible: Boolean,
    categories: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember(visible) { mutableStateOf("") }
    val shown = categories.filter { it.second.contains(query.trim(), ignoreCase = true) }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SheetCard(
            scrollBody = false,
            maxWidth = 480.dp,
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader("Choose category", onClose = onDismiss, leadingIcon = Lucide.Tags)
            },
        ) {
            TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Groceries"))
            Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                shown.forEachIndexed { index, (id, name) ->
                    if (index > 0) DividerLine()
                    ListRow(name, onClick = { onPick(id) })
                }
                if (shown.isEmpty()) ListRow("No matching categories")
            }
        }
    }
}
