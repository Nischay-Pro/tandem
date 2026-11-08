@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.store.BudgetDisplaySettings

internal fun TransactionItem.toActivitySearchRecord(): ActivitySearchRecord =
    ActivitySearchRecord(
        payee = payee,
        payeeId = payeeId,
        account = account,
        accountId = accountId,
        category = category,
        categoryId = categoryId,
        notes = notes,
        dateIso = dateIso,
        amountMinorUnits = amountMinorUnits,
        cleared = cleared,
        reconciled = reconciled,
        approved = approved,
        matched = matched,
        transfer = isTransfer,
        recordId = recordId,
    )

internal fun buildSearchActivityRecords(
    records: List<SearchableBudgetTransaction>,
    accounts: Map<String, String>,
    payees: Map<String, String>,
    categories: Map<String, String>,
): List<ActivitySearchRecord> =
    records.filterNot(SearchableBudgetTransaction::isParent).mapNotNull { record ->
        val amount = record.amountMinor ?: return@mapNotNull null
        val payee = record.payeeId?.let(payees::get) ?: record.payeeId ?: "No payee"
        val category = record.categoryId?.let(categories::get) ?: record.categoryId ?: "Uncategorized"
        ActivitySearchRecord(
            payee = payee,
            payeeId = record.payeeId,
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            accountId = record.accountId,
            category = category,
            categoryId = record.categoryId,
            notes = record.notes.orEmpty(),
            dateIso = record.recordDate.orEmpty(),
            amountMinorUnits = amount,
            cleared = record.cleared,
            reconciled = record.reconciled,
            approved = record.approved,
            matched = record.matched,
            transfer = record.transfer == true,
            recordId = record.recordId,
        )
    }

internal fun ActivitySearchRecord.toTransactionItem(
    displaySettings: BudgetDisplaySettings?,
    merchantIconMatcher: MerchantIconMatcherPack?,
): TransactionItem =
    TransactionItem(
        payee = payee,
        category = category,
        dateLabel = formatLiveDate(dateIso),
        amountMinorUnits = amountMinorUnits,
        icon = payee.take(1).uppercase(),
        categoryIcon = financeIcon(category),
        merchantIcon = merchantIconMatcher?.iconFor(payee),
        account = account,
        accountId = accountId,
        recordId = recordId,
        notes = notes,
        displaySettings = displaySettings,
        dateIso = dateIso,
        payeeId = payeeId,
        categoryId = categoryId,
        cleared = cleared,
        reconciled = reconciled,
        approved = approved,
        matched = matched,
        isTransfer = transfer,
    )
