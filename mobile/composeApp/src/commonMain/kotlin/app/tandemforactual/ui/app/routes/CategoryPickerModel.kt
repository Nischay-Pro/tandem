package app.tandemforactual.ui.app.routes

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName

/** What the category picker lists: every choice, the grouped and recent sections, and the groups a new category can join. */
internal data class CategoryPickerModel(
    val visibleMonthGroups: List<LiveCategoryGroupSummary>,
    val values: List<SelectorItem>,
    val groups: List<SelectorGroup>,
    val categoryGroups: List<SelectorItem>,
)

/**
 * Builds the category picker's lists once per change to the categories, month or recent transactions, rather than on
 * every keystroke in its search, since it reads each category's stored record.
 */
internal fun categoryPickerModel(
    isLiveBudget: Boolean,
    income: Boolean,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    categoryRecords: List<BudgetRecordEntity>,
    categoryGroupRecords: List<BudgetRecordEntity>,
    liveTransactions: List<TransactionItem>,
): CategoryPickerModel {
    if (!isLiveBudget) {
        val samples = listOf("Groceries", "Utilities", "Dining", "Rent", "Transport", "Personal").map { SelectorItem(it, it) }
        return CategoryPickerModel(emptyList(), samples, emptyList(), emptyList())
    }
    val visibleMonthGroups = liveBudgetMonth?.groups?.filter { it.isIncome == income }.orEmpty()
    val all =
        (
            storedCategoryItems(categoryRecords) +
                visibleMonthGroups.flatMap { group -> group.categories.map { SelectorItem(it.id, it.name) } }
        ).distinctBy(SelectorItem::id)
    val byId = all.associateBy(SelectorItem::id)
    val visibleIds = visibleMonthGroups.flatMap { it.categories }.mapTo(mutableSetOf()) { it.id }
    val visible = if (liveBudgetMonth == null) all else all.filter { it.id in visibleIds }
    val recent = liveTransactions.mapNotNull { transaction -> transaction.categoryId?.let(byId::get) }.distinctBy(SelectorItem::id).take(5)
    return CategoryPickerModel(
        visibleMonthGroups,
        listOf(SelectorItem("", "Uncategorized")) + visible,
        pickerGroups(recent, visibleMonthGroups, byId, visible),
        visibleMonthGroups.map { SelectorItem(it.id, it.name) }.ifEmpty { storedGroupItems(categoryGroupRecords, income) },
    )
}

private fun storedCategoryItems(records: List<BudgetRecordEntity>): List<SelectorItem> =
    records.mapNotNull { record ->
        val body = record.recordBody()
        if (body?.booleanValue("hidden") == true || body?.booleanValue("deleted") == true) {
            null
        } else {
            record.recordName()?.let { SelectorItem(record.recordId, it) }
        }
    }

private fun storedGroupItems(
    records: List<BudgetRecordEntity>,
    income: Boolean,
): List<SelectorItem> =
    records.mapNotNull { record ->
        val body = record.recordBody() ?: return@mapNotNull null
        if ((body.booleanValue("is_income") ?: false) != income || body.booleanValue("hidden") == true) return@mapNotNull null
        record.recordName()?.let { SelectorItem(record.recordId, it) }
    }

/** Recent categories first, then each group's categories by name, then any left over under Other. */
private fun pickerGroups(
    recent: List<SelectorItem>,
    monthGroups: List<LiveCategoryGroupSummary>,
    byId: Map<String, SelectorItem>,
    visible: List<SelectorItem>,
): List<SelectorGroup> =
    buildList {
        if (recent.isNotEmpty()) add(SelectorGroup("Recent", recent))
        monthGroups.forEach { group ->
            val items = group.categories.map { byId[it.id] ?: SelectorItem(it.id, it.name) }.sortedBy { it.label.lowercase() }
            if (items.isNotEmpty()) add(SelectorGroup(group.name, items))
        }
        val grouped = flatMap { it.items }.mapTo(mutableSetOf()) { it.id }
        val ungrouped = visible.filterNot { it.id in grouped }.sortedBy { it.label.lowercase() }
        if (ungrouped.isNotEmpty()) add(SelectorGroup("Other", ungrouped))
    }
