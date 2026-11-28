package app.tandemforactual.ui.app.shell

import app.tandemforactual.data.store.findRecord
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.CategoryEntry
import app.tandemforactual.ui.core.session.transactions.balanceAfterSave
import app.tandemforactual.ui.core.session.transactions.categoryChange
import app.tandemforactual.ui.core.session.transactions.categorySaveFeedback
import app.tandemforactual.ui.core.session.transactions.mainCategory
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.CancellationException

/** What the form will save into categories; transfers and off-budget accounts touch none. */
internal fun AppState.formCategoryEntries(mobileSession: TandemMobileSession): List<CategoryEntry> {
    if (transactionType == "Transfer" || mobileSession.isOffBudget(newTransactionAccountId)) return emptyList()
    val month = transactionDate.take(7)
    val income = transactionType == "Income"
    transactionSplit?.let { split ->
        return split.signedLines({ parseAmountMinorUnits(it) }, income).map { (amount, category) -> CategoryEntry(category, month, amount) }
    }
    val amount = parseAmountMinorUnits(transactionAmount)?.let { kotlin.math.abs(it) } ?: return emptyList()
    val category = newTransactionCategoryId.takeIf(String::isNotBlank) ?: return emptyList()
    return listOf(CategoryEntry(category, month, if (income) amount else -amount))
}

/** What the transaction being edited had in categories when it was opened. */
internal fun AppState.savedCategoryEntries(mobileSession: TandemMobileSession): List<CategoryEntry> {
    val amount = selectedTransactionAmountMinor ?: return emptyList()
    if (selectedTransactionIsTransfer || mobileSession.isOffBudget(selectedTransactionAccountId)) return emptyList()
    val month = selectedTransactionDate.take(7)
    selectedTransactionSplit?.let { split ->
        return split.signedLines({ parseAmountMinorUnits(it) }, amount > 0).map { (line, category) -> CategoryEntry(category, month, line) }
    }
    val category = selectedTransactionCategoryId.takeIf(String::isNotBlank) ?: return emptyList()
    return listOf(CategoryEntry(category, month, amount))
}

private fun TandemMobileSession.isOffBudget(accountId: String): Boolean =
    accountRecords.firstOrNull { it.recordId == accountId }?.recordBody().isOffBudgetAccount()

/** The category a save is reported against, with its month as it was before the save. */
internal class CategorySaveNotice(
    private val budgetId: String,
    private val main: CategoryEntry,
    private val otherCategories: Int,
    private val changeMinor: Long,
    private val beforeJson: String?,
    private val account: Pair<String, Long>?,
) {
    /** Shows the category's balance for the month the transaction is in, once the save went through. */
    suspend fun show(
        state: AppState,
        mobileSession: TandemMobileSession,
    ) {
        if (!state.balanceAfterSaveEnabled) return
        val afterJson = mobileSession.budgetMonthJson(budgetId, main.month)
        val (balance, estimated) = balanceAfterSave(main.categoryId, beforeJson, afterJson, changeMinor) ?: return
        val name = mobileSession.categoryRecords.firstOrNull { it.recordId == main.categoryId }?.recordName() ?: return
        val feedback =
            categorySaveFeedback(
                categoryName = name,
                otherCategories = otherCategories,
                balanceMinor = balance,
                estimated = estimated,
                hideAmounts = state.privacyModeEnabled,
                monthPhrase = monthPhrase(main.month),
                format = { state.formatCurrency(it) },
                account = account,
            )
        mobileSession.transactions.showSaveNotice(
            budgetId,
            feedback.title,
            feedback.text,
            if (feedback.over) InlineMessageKind.WARNING else InlineMessageKind.SUCCESS,
            feedback.detail,
        )
    }

    companion object {
        /** Reads the month before saving, so the balance afterwards can be told apart from one still waiting to sync. */
        suspend fun prepare(
            budgetId: String,
            mobileSession: TandemMobileSession,
            before: List<CategoryEntry>,
            after: List<CategoryEntry>,
            accountChange: Pair<String, Long>? = null,
        ): CategorySaveNotice? {
            val (main, others) = mainCategory(after) ?: return null
            val record = mobileSession.categoryRecords.firstOrNull { it.recordId == main.categoryId } ?: return null
            if (record.recordBody()?.booleanValue("is_income") == true) return null
            val change = categoryChange(main.categoryId, main.month, before, after)
            val account =
                accountChange?.let { (accountId, amount) ->
                    val record = mobileSession.accountRecords.firstOrNull { it.recordId == accountId }
                    val balance = record?.recordBody()?.longValue("balance_current") ?: return@let null
                    (record.recordName() ?: return@let null) to balance + amount
                }
            return CategorySaveNotice(budgetId, main, others, change, mobileSession.budgetMonthJson(budgetId, main.month), account)
        }
    }
}

private suspend fun TandemMobileSession.budgetMonthJson(
    budgetId: String,
    month: String,
): String? =
    try {
        repository?.findRecord(budgetId, "budget-month", month)?.let { it.localJson ?: it.remoteJson }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

private fun monthPhrase(month: String): String {
    if (month == platformTodayIsoDate().take(7)) return "this month"
    val year = month.take(4).toIntOrNull() ?: return "this month"
    val number = month.drop(5).toIntOrNull() ?: return "this month"
    return "in ${formatMonth(year, number)}"
}
