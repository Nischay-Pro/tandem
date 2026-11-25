package app.tandemforactual.ui

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.app.routes.categoryPickerModel
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.TransactionItem
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryPickerModelTest {
    private fun record(
        type: String,
        id: String,
        name: String,
        json: String = "{}",
    ) = BudgetRecordEntity(
        budgetId = "b",
        recordType = type,
        recordId = id,
        remoteJson = json,
        remoteRevision = "r",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "g",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
        displayName = name,
    )

    private val categories =
        listOf(
            record("category", "food", "Food"),
            record("category", "rent", "Rent"),
            record("category", "old", "Old", """{"hidden":true}"""),
            record("category", "pay", "Paycheck"),
        )
    private val groups =
        listOf(record("category-group", "bills", "Bills"), record("category-group", "in", "Income", """{"is_income":true}"""))
    private val month =
        LiveBudgetMonthSummary(
            toBudgetMinor = 0,
            incomeAvailableMinor = null,
            totalBudgetedMinor = null,
            totalSpentMinor = null,
            groups =
                listOf(
                    LiveCategoryGroupSummary(
                        "bills",
                        "Bills",
                        listOf(LiveCategorySummary("rent", "Rent", 0, 0, 0), LiveCategorySummary("food", "Food", 0, 0, 0)),
                    ),
                    LiveCategoryGroupSummary("in", "Income", listOf(LiveCategorySummary("pay", "Paycheck", 0, 0, 0)), isIncome = true),
                ),
        )
    private val recent = listOf(TransactionItem("Shop", "Food", "", -1, "S", categoryId = "food"))

    @Test
    fun listsTheMonthsSpendingCategoriesWithRecentOnesFirst() {
        val model = categoryPickerModel(true, false, month, categories, groups, recent)
        assertEquals(listOf("", "food", "rent"), model.values.map { it.id })
        assertEquals(listOf("Recent", "Bills"), model.groups.map { it.title })
        assertEquals(listOf(SelectorItem("food", "Food")), model.groups.first().items)
        assertEquals(listOf("Food", "Rent"), model.groups[1].items.map { it.label })
        assertEquals(listOf(SelectorItem("bills", "Bills")), model.categoryGroups)
    }

    @Test
    fun fallsBackToStoredCategoriesAndGroupsWithoutAMonth() {
        val model = categoryPickerModel(true, true, null, categories, groups, emptyList())
        assertEquals(listOf("", "food", "rent", "pay"), model.values.map { it.id })
        assertEquals(listOf(SelectorItem("in", "Income")), model.categoryGroups)
        assertEquals(6, categoryPickerModel(false, false, null, categories, groups, recent).values.size)
    }
}
