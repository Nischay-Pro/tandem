package app.tandemforactual.ui.model

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.BudgetReorderKind
import app.tandemforactual.ui.BudgetReorderQueueState
import app.tandemforactual.ui.QueuedBudgetReorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun usesActualAccountGroupsTypesAndClosedState() {
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
                        json = """{"id":"visa","type":"credit","balance_current":-2000}""",
                    ),
                    record(
                        type = "account",
                        id = "loan",
                        name = "Auto loan",
                        json = """{"id":"loan","type":"mortgage","balance_current":-80000,"offbudget":true}""",
                    ),
                    record(
                        type = "account",
                        id = "brokerage",
                        name = "Brokerage investments",
                        json = """{"id":"brokerage","type":"investment","balance_current":30000,"offbudget":true}""",
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
        assertEquals("Other", liveAccountGroupLabel(accounts[0]))
        assertEquals("Credit", liveAccountGroupLabel(accounts[1]))
        assertEquals("Mortgage", liveAccountGroupLabel(accounts[2]))
        assertEquals("Investment", liveAccountGroupLabel(accounts[3]))
        assertEquals("Closed", liveAccountGroupLabel(accounts[4]))
        assertEquals(true, accounts[4].closed)
    }

    @Test
    fun groupsAccountsByActualTypeWithoutReadingNamesOrRepeatingBudgetScope() {
        val accounts =
            liveAccountSummaries(
                listOf(
                    record(
                        type = "account",
                        id = "elmira",
                        name = "Elmira Savings",
                        json = """{"id":"elmira","type":"checking","offbudget":false}""",
                    ),
                    record(
                        type = "account",
                        id = "credit-union",
                        name = "Credit Union Checking",
                        json = """{"id":"credit-union","type":"checking","offbudget":false}""",
                    ),
                    record(
                        type = "account",
                        id = "discard",
                        name = "Discard Fund",
                        json = """{"id":"discard","type":"savings","offbudget":false}""",
                    ),
                    record(
                        type = "account",
                        id = "debt-free",
                        name = "Debt-free Savings",
                        json = """{"id":"debt-free","type":"savings","offbudget":true}""",
                    ),
                ),
            )

        assertEquals("Checking", liveAccountGroupLabel(accounts[0]))
        assertEquals(liveAccountGroupLabel(accounts[0]), liveAccountGroupLabel(accounts[1]))
        assertEquals("Savings", liveAccountGroupLabel(accounts[2]))
        assertEquals(liveAccountGroupLabel(accounts[2]), liveAccountGroupLabel(accounts[3]))
        assertFalse(accounts[2].offBudget)
        assertTrue(accounts[3].offBudget)
    }

    @Test
    fun mapsVisibleBudgetGroupsAndCategoriesWithRecordNameFallbacks() {
        val month =
            record(
                type = "budget-month",
                id = "2026-11",
                json =
                    """{"month":"2026-11","toBudget":5000,"forNextMonth":1200,"incomeAvailable":9000,"totalBudgeted":4000,"totalSpent":1800,"categoryGroups":[{"id":"essentials","categories":[{"id":"rent","budgeted":2500,"activity":-2500,"balance":0,"carryover":true},{"id":"hidden","budgeted":100,"activity":0,"hidden":true},{"id":"deleted","budgeted":100,"activity":0,"deleted":true}]},{"id":"hidden-group","hidden":true,"categories":[{"id":"ignored","budgeted":100}]}]}""",
            )
        val categories =
            listOf(
                record(type = "category", id = "rent", name = "Rent"),
                record(
                    type = "category",
                    id = "home-maintenance",
                    name = "Home Maintenance",
                    json = """{"id":"home-maintenance","group":"essentials"}""",
                ),
                record(type = "category", id = "hidden", name = "Hidden"),
                record(type = "category", id = "deleted", name = "Deleted"),
            )
        val groups = listOf(record(type = "category-group", id = "essentials", name = "Essentials"))

        val summary = liveBudgetMonthSummary(month, categories, groups)

        assertEquals(5_000, summary?.toBudgetMinor)
        assertEquals(9_000, summary?.incomeAvailableMinor)
        assertEquals(4_000, summary?.totalBudgetedMinor)
        assertEquals(1_800, summary?.totalSpentMinor)
        assertEquals(1_200, summary?.heldForNextMonthMinor)
        assertEquals(1, summary?.groups?.size)
        assertEquals("Essentials", summary?.groups?.single()?.name)
        assertEquals(
            listOf(
                LiveCategorySummary("rent", "Rent", 2_500, -2_500, 0, carryover = true),
                LiveCategorySummary("home-maintenance", "Home Maintenance", 0, 0, 0),
            ),
            summary?.groups?.single()?.categories,
        )
        assertNull(liveBudgetMonthSummary(null, categories, groups))
    }

    @Test
    fun hidesQueuedCategoryCreateWhenItHasAlsoBeenQueuedForDeletion() {
        val month =
            record(
                type = "budget-month",
                id = "2026-11",
                json = """{"month":"2026-11","categoryGroups":[{"id":"home","categories":[]}]}""",
            )
        val pendingCategoryId = "local-category-1"
        val queue =
            BudgetReorderQueueState()
                .enqueue(
                    QueuedBudgetReorder(
                        budgetId = "budget",
                        scopeId = "home",
                        kind = BudgetReorderKind.CREATE_CATEGORY,
                        itemId = pendingCategoryId,
                        targetId = null,
                        desiredOrder = listOf(pendingCategoryId),
                        encryptionPassword = null,
                        name = "Offline test",
                        parentGroupId = "home",
                    ),
                ).enqueue(
                    QueuedBudgetReorder(
                        budgetId = "budget",
                        scopeId = "category-$pendingCategoryId",
                        kind = BudgetReorderKind.DELETE_CATEGORY,
                        itemId = pendingCategoryId,
                        targetId = null,
                        desiredOrder = emptyList(),
                        encryptionPassword = null,
                    ),
                )

        val summary =
            liveBudgetMonthSummary(
                monthRecord = month,
                categoryRecords = emptyList(),
                categoryGroupRecords = listOf(record(type = "category-group", id = "home", name = "Home")),
                deletedCategoryIds = queue.deletedCategoryIdsFor("budget"),
                pendingCategories = queue.pendingCategoryCreatesFor("budget"),
            )

        assertEquals(emptyList(), summary?.groups?.single()?.categories)
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

    @Test
    fun mapsUpcomingSchedulesAndOmitsCompletedOrUndatedRows() {
        val schedules =
            listOf(
                record(
                    type = "schedule",
                    id = "rent",
                    json =
                        """
                        {"id":"rent","name":"Rent","payee":"landlord","account":"checking",
                         "amount":150000,"next_date":"2026-11-15","completed":false,
                         "date":{"frequency":"monthly","start":"2026-11-15"}}
                        """.trimIndent(),
                ),
                record(
                    type = "schedule",
                    id = "variable",
                    json =
                        """
                        {"id":"variable","payee":"utility","account":"checking",
                         "amount":{"num1":5000,"num2":12000},"next_date":"2026-11-18",
                         "date":"2026-11-18"}
                        """.trimIndent(),
                ),
                record(
                    type = "schedule",
                    id = "completed",
                    json = """{"id":"completed","next_date":"2026-11-01","completed":true}""",
                ),
                record(type = "schedule", id = "undated", json = """{"id":"undated"}"""),
            )

        val upcoming =
            liveScheduleSummaries(
                schedules,
                listOf(
                    record(type = "payee", id = "landlord", name = "North Apartments"),
                    record(type = "payee", id = "utility", name = "Electric Company"),
                ),
                listOf(record(type = "account", id = "checking", name = "Household Checking")),
            )

        assertEquals(listOf("rent", "variable"), upcoming.map { it.id })
        assertEquals("Rent", upcoming.first().name)
        assertEquals("Household Checking", upcoming.first().accountName)
        assertEquals(150_000, upcoming.first().amountMinor)
        assertEquals(5_000L to 12_000L, upcoming.last().amountRangeMinor)
        assertTrue(upcoming.first().canSkip)
        assertFalse(upcoming.last().canSkip)
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
