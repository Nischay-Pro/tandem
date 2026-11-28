@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.planning.TransactionTemplate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlin.math.abs
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

/** The new transaction as a template, or null while it has no payee or amount, or is a transfer or split. */
internal fun templateFromForm(
    state: AppState,
    id: String,
): TransactionTemplate? {
    if (state.transactionType == "Transfer" || state.transactionSplit != null) return null
    val amount = state.parseAmountMinorUnits(state.transactionAmount)?.let { abs(it) }?.takeIf { it > 0 } ?: return null
    val payee = state.transactionPayee.trim().takeIf { it.isNotEmpty() } ?: return null
    val categoryId = state.newTransactionCategoryId.ifBlank { null }
    return TransactionTemplate(
        id,
        state.newTransactionPayeeId.ifBlank { null },
        payee,
        amount,
        state.transactionType == "Income",
        categoryId,
        state.transactionCategory.takeIf { categoryId != null },
        state.newTransactionAccountId.ifBlank { null },
    )
}

/** Fills the new transaction from [template], keeping the current account when the template's is closed or gone. */
internal fun AppState.applyTemplate(
    template: TransactionTemplate,
    accounts: List<BudgetRecordEntity>,
) {
    transactionType = if (template.income) "Income" else "Spending"
    transactionAmount = formatAmount(template.amountMinor)
    transactionPayee = template.payeeName
    newTransactionPayeeId = template.payeeId.orEmpty()
    newTransactionCategoryId = template.categoryId.orEmpty()
    transactionCategory = template.categoryName.orEmpty()
    val account =
        accounts.firstOrNull { record ->
            record.recordId == template.accountId && !record.localDeleted && record.recordBody()?.booleanValue("closed") != true
        }
    account?.recordName()?.let { name ->
        newTransactionAccountId = account.recordId
        transactionAccount = name
    }
}

/** Saved one-tap entries above the form: tap one to fill the form, long-press to remove it, or save this one. */
@Composable
internal fun TransactionTemplatesRow(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val templates = mobileSession.planning.transactionTemplates
    val current = templateFromForm(state, Uuid.random().toString())
    if (templates.isEmpty() && current == null) return
    val scope = rememberCoroutineScope()
    val budgetId = state.selectedBudgetId
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        templates.forEach { template ->
            TemplateChip(
                "${template.payeeName} · ${state.formatAmount(template.amountMinor)}",
                onLongClick = { scope.launch { mobileSession.planning.deleteTransactionTemplate(budgetId, template.id) } },
            ) { state.applyTemplate(template, mobileSession.accountRecords) }
        }
        if (current != null && templates.none { it.payeeName == current.payeeName && it.amountMinor == current.amountMinor }) {
            TemplateChip("Save as template") { scope.launch { mobileSession.planning.saveTransactionTemplate(budgetId, current) } }
        }
    }
}

@Composable
private fun TemplateChip(
    label: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Text(
        label,
        Modifier
            .clip(CircleShape)
            .background(palette.raised)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "Remove template".takeIf { onLongClick != null },
                onLongClick = onLongClick,
                onClick = onClick,
            ).padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        color = palette.text,
        fontSize = TandemTypography.compactBody,
    )
}
