@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity

import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavedSearchFilter
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatTransactionListDate
import app.tandemforactual.ui.design.templates.financeIcon
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    todayIso: String = platformTodayIsoDate(),
): TransactionItem =
    TransactionItem(
        payee = payee,
        category = category,
        dateLabel = formatTransactionListDate(dateIso, todayIso),
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

internal fun SavedActivitySearch.toSavedSearch(id: String): SavedSearch =
    SavedSearch(id, name.take(80), query.take(300), filters.take(20).map { SavedSearchFilter(it.field.name, it.value, it.label) })

/** The search's filters, skipping any whose field this version of Tandem doesn't know. */
internal fun SavedSearch.activityFilters(): List<ActivitySearchFilter> =
    filters.mapNotNull { filter ->
        ActivitySearchField.entries.firstOrNull { it.name == filter.field }?.let { ActivitySearchFilter(it, filter.value, filter.label) }
    }

internal fun SavedSearch.summary(): String =
    listOfNotNull(
        query.trim().takeIf { it.isNotEmpty() }?.let { "“$it”" },
        filters.size.takeIf { it > 0 }?.let { if (it == 1) "1 filter" else "$it filters" },
    ).joinToString(" · ").ifEmpty { "All transactions" }

@OptIn(ExperimentalUuidApi::class)
internal fun newSavedSearchId(): String = Uuid.random().toString()
