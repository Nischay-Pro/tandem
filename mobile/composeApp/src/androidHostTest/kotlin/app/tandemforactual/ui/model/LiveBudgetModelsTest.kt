package app.tandemforactual.ui.model

import app.tandemforactual.data.database.BudgetRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveBudgetModelsTest {
    @Test
    fun mapsLiveAccountBalanceAndOffBudgetState() {
        val accounts =
            liveAccountSummaries(
                listOf(
                    record(
                        type = "account",
                        id = "checking",
                        name = "Checking",
                        json = """{"id":"checking","balance_current":12500,"type":"checking","offbudget":false}""",
                    ),
                    record(
                        type = "account",
                        id = "loan",
                        name = "Auto loan",
                        json = """{"id":"loan","balance_current":-80000,"type":"loan","offbudget":true}""",
                    ),
                ),
            )

        assertEquals(
            listOf(
                LiveAccountSummary("checking", "Checking", 12_500, "checking", false),
                LiveAccountSummary("loan", "Auto loan", -80_000, "loan", true),
            ),
            accounts,
        )
    }

    @Test
    fun mapsAccountGroupsAndClosedStateAndClassifiesKnownAccountNames() {
        val accounts =
            liveAccountSummaries(
                listOf(
                    record(
                        type = "account",
                        id = "checking",
                        name = "Chase Checking",
                        json = """{"id":"checking","account_group_id":"daily","balance_current":12000}""",
                    ),
                    record(
                        type = "account",
                        id = "visa",
                        name = "Visa Signature",
                        json = """{"id":"visa","balance_current":-2000}""",
                    ),
                    record(
                        type = "account",
                        id = "closed",
                        name = "Old Savings",
                        json = """{"id":"closed","closed":true,"balance_current":0}""",
                    ),
                ),
                listOf(record(type = "account-group", id = "daily", name = "Everyday")),
            )

        assertEquals("Everyday", accounts.first().accountGroupName)
        assertEquals("daily", accounts.first().accountGroupId)
        assertEquals("Cash", liveAccountGroupLabel(accounts[0]))
        assertEquals("Credit", liveAccountGroupLabel(accounts[1]))
        assertEquals("Closed", liveAccountGroupLabel(accounts[2]))
        assertEquals(true, accounts[2].closed)
    }

    @Test
    fun mapsVisibleBudgetGroupsAndCategoriesWithRecordNameFallbacks() {
        val month =
            record(
                type = "budget-month",
                id = "2026-11",
                json =
                    """{"month":"2026-11","toBudget":5000,"incomeAvailable":9000,"totalBudgeted":4000,"totalSpent":1800,"categoryGroups":[{"id":"essentials","categories":[{"id":"rent","budgeted":2500,"activity":-2500,"balance":0},{"id":"hidden","budgeted":100,"activity":0,"hidden":true},{"id":"deleted","budgeted":100,"activity":0,"deleted":true}]},{"id":"hidden-group","hidden":true,"categories":[{"id":"ignored","budgeted":100}]}]}""",
            )
        val categories =
            listOf(
                record(type = "category", id = "rent", name = "Rent"),
                record(type = "category", id = "hidden", name = "Hidden"),
                record(type = "category", id = "deleted", name = "Deleted"),
            )
        val groups = listOf(record(type = "category-group", id = "essentials", name = "Essentials"))

        val summary = liveBudgetMonthSummary(month, categories, groups)

        assertEquals(5_000, summary?.toBudgetMinor)
        assertEquals(9_000, summary?.incomeAvailableMinor)
        assertEquals(4_000, summary?.totalBudgetedMinor)
        assertEquals(1_800, summary?.totalSpentMinor)
        assertEquals(1, summary?.groups?.size)
        assertEquals("Essentials", summary?.groups?.single()?.name)
        assertEquals(
            listOf(LiveCategorySummary("rent", "Rent", 2_500, -2_500, 0)),
            summary?.groups?.single()?.categories,
        )
        assertNull(liveBudgetMonthSummary(null, categories, groups))
    }

    @Test
    fun prefersLocalRecordJsonAndIgnoresInvalidBodies() {
        val local =
            record(
                type = "account",
                id = "checking",
                json = """{"id":"checking","balance_current":100}""",
            ).copy(localJson = """{"id":"checking","balance_current":250}""")
        val invalid = record(type = "account", id = "invalid", json = "[]")

        assertEquals(250, local.recordBody()?.longValue("balance_current"))
        assertNull(invalid.recordBody())
    }
}

private fun record(
    type: String,
    id: String,
    name: String? = null,
    json: String? = null,
) = BudgetRecordEntity(
    budgetId = "budget",
    recordType = type,
    recordId = id,
    remoteJson = json,
    remoteRevision = "revision",
    localJson = null,
    localDeleted = false,
    remoteDeleted = false,
    syncGeneration = "generation",
    recordDate = null,
    accountId = null,
    categoryId = null,
    payeeId = null,
    amountMinor = null,
    budgetMonth = null,
    displayName = name,
)
