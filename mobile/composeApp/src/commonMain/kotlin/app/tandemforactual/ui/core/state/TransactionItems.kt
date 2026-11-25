@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.design.templates.financeIcon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.DrawableResource

internal fun buildLiveTransactionItems(
    records: List<BudgetRecordEntity>,
    accountRecords: List<BudgetRecordEntity>,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    displaySettings: BudgetDisplaySettings?,
    merchantIconMatcher: MerchantIconMatcherPack?,
    todayIso: String = platformTodayIsoDate(),
): List<TransactionItem> {
    val accounts = accountRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payees = payeeRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val categories = categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
    val payeeIconCache = mutableMapOf<String, DrawableResource?>()
    return records.mapNotNull { record ->
        val amount = record.amountMinor ?: return@mapNotNull null
        val payeeName = record.payeeId?.let(payees::get)
        val pendingSplit = !record.isParent && record.categoryId == null && record.localJson.hasSplitLines()
        val categoryName =
            when {
                pendingSplit -> "Split"
                else -> record.categoryId?.let(categories::get) ?: record.categoryId ?: "Uncategorized"
            }
        val displayedPayee = payeeName ?: record.payeeId ?: "No payee"
        TransactionItem(
            payee = displayedPayee,
            category = categoryName,
            dateLabel = formatTransactionListDate(record.recordDate, todayIso),
            amountMinorUnits = amount,
            icon = displayedPayee.take(1).uppercase(),
            categoryIcon = financeIcon(categoryName),
            merchantIcon = payeeIconCache.getOrPut(displayedPayee) { merchantIconMatcher?.iconFor(displayedPayee) },
            account = record.accountId?.let(accounts::get) ?: record.accountId ?: "No account",
            accountId = record.accountId,
            recordId = record.recordId,
            notes = record.notes.orEmpty(),
            displaySettings = displaySettings,
            dateIso = record.recordDate.orEmpty(),
            payeeId = record.payeeId,
            categoryId = record.categoryId,
            cleared = record.cleared,
            reconciled = record.reconciled,
            approved = record.approved,
            matched = record.matched,
            isTransfer = record.transfer == true,
            isSplitParent = record.isParent,
            isPendingSplit = pendingSplit,
        )
    }
}

internal data class SelectorItem(
    val id: String,
    val label: String,
)

internal data class SelectorGroup(
    val title: String,
    val items: List<SelectorItem>,
)

/** Whether a transaction saved on this device but not yet sent is a split, so it reads "Split" until Actual has its lines. */
internal fun String?.hasSplitLines(): Boolean =
    this != null &&
        runCatching {
            (Json.parseToJsonElement(this).jsonObject["subtransactions"] as? JsonArray)?.isNotEmpty() == true
        }.getOrDefault(false)
