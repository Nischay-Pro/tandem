package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WatchSummaryTest {
    private val format = { minor: Long, _: BudgetDisplaySettings? -> "E$minor" }
    private val open = mapOf("selectedBudgetId" to "budget", "selectedBudget" to "Home")

    @Test
    fun privacyDecidesWhatTheWatchMayShow() {
        assertEquals(WatchState.NONE, watchState(emptyMap(), opened = true))
        assertEquals(WatchState.READY, watchState(open, opened = false))
        assertEquals(WatchState.HIDDEN, watchState(open + ("privacyModeEnabled" to "true"), opened = false))
        val encrypted = open + ("selectedBudgetEncrypted" to "true")
        assertEquals(WatchState.LOCKED, watchState(encrypted, opened = false))
        assertEquals(WatchState.READY, watchState(encrypted, opened = true))
        val appLock = open + ("biometricLockEnabled" to "true")
        assertEquals(WatchState.LOCKED, watchState(appLock, opened = false))
        assertEquals(WatchState.READY, watchState(appLock, opened = true))
        assertEquals(WatchState.READY, watchState(appLock + WatchSettings(summaryAfterUnlock = false).toPreferences(), opened = false))
        assertEquals(WatchState.LOCKED, watchState(encrypted + WatchSettings(summaryAfterUnlock = false).toPreferences(), opened = false))
    }

    @Test
    fun quickAddIsOfferedOnlyWhenThePhoneWouldTakeIt() {
        assertTrue(watchQuickAddOpen(open, WatchState.READY))
        assertTrue(watchQuickAddOpen(open + ("biometricLockEnabled" to "true"), WatchState.LOCKED))
        assertTrue(watchQuickAddOpen(open + ("selectedBudgetEncrypted" to "true"), WatchState.LOCKED))
        assertFalse(watchQuickAddOpen(open + ("readOnlyMode" to "true"), WatchState.READY))
        assertFalse(watchQuickAddOpen(open + WatchSettings(quickAddEnabled = false).toPreferences(), WatchState.READY))
        assertFalse(watchQuickAddOpen(emptyMap(), WatchState.NONE))
        assertNull(watchQuickAddRefusal(open + ("selectedBudgetEncrypted" to "true")))
    }

    @Test
    fun settingsKeepAtMostSixPinsInTheOrderPicked() {
        var settings = WatchSettings()
        (1..8).forEach { settings = settings.pinning("c$it", true) }
        assertEquals((1..6).map { "c$it" }, settings.pinnedCategoryIds)
        settings = settings.pinning("c2", false).copy(perDayFirst = false)
        assertEquals(listOf("c1", "c3", "c4", "c5", "c6"), settings.pinnedCategoryIds)
        assertEquals(settings, WatchSettings.from(settings.toPreferences()))
        assertEquals(WatchSettings(), WatchSettings.from(emptyMap()))
    }

    @Test
    fun aFullSummaryStaysWithinTheWatchLimit() {
        val longName = "A category with a very long name indeed"
        val summary =
            WatchSummary(
                state = WatchState.READY,
                generatedAtEpochMs = NOW,
                budgetName = "Household budget for the whole family",
                syncedAtEpochMs = NOW,
                leftToSpend = "€12,345.67",
                toBudget = "€1,234.56",
                categories =
                    (1..MAX_WATCH_CATEGORIES).map { index ->
                        WatchCategory(
                            id = "3f1c2d3e-4b5a-4c6d-8e7f-90a1b2c3d4e$index",
                            name = "$longName $index",
                            left = "€1,234.56",
                            spentPercent = 45,
                            budgeted = "€2,000.00",
                            spent = "€765.44",
                            transactions = (1..3).map { WatchTransaction("A payee with a long name $it", "−€123.45", "2026-11-2$it") },
                        )
                    },
                bills = (1..3).map { WatchBill("Electricity and gas $it", "2026-12-0$it", "€150.00") },
                quickAdd =
                    WatchQuickAdd(
                        open = true,
                        accounts = (1..4).map { WatchChoice("account-id-number-$it-0000-0000", "Everyday checking $it") },
                        payees = (1..5).map { "Corner Café number $it" },
                        recentCategories = (1..3).map { WatchChoice("category-id-$it-0000-0000", "Recent category $it") },
                    ),
            )

        val json = encodeWatchSummary(summary)

        assertTrue(json.encodeToByteArray().size <= WATCH_SUMMARY_MAX_BYTES, "${json.encodeToByteArray().size} bytes")
        val decoded = Json.parseToJsonElement(json).jsonObject
        assertEquals(MAX_WATCH_CATEGORIES, decoded.getValue("c").jsonArray.size)
        assertEquals(3, decoded.getValue("u").jsonArray.size)
        assertEquals("€12,345.67", decoded.getValue("l").jsonPrimitive.content)
    }

    @Test
    fun aSmallSummaryKeepsEveryDetail() {
        val summary =
            WatchSummary(
                state = WatchState.READY,
                generatedAtEpochMs = NOW,
                budgetName = "Home",
                categories =
                    listOf(WatchCategory("food", "Food", "E5", transactions = List(3) { WatchTransaction("Shop", "E-1", "2026-11-26") })),
            )

        val category = (Json.parseToJsonElement(encodeWatchSummary(summary)).jsonObject.getValue("c") as JsonArray)[0].jsonObject

        assertEquals(3, category.getValue("x").jsonArray.size)
    }

    @Test
    fun summaryHoldsOnlyWhatItsStateAllows() =
        withBudget { repository ->
            val none = decode(watchSummaryJson(repository, emptyMap(), TODAY, NOW, opened = true, format))
            assertEquals("none", none.text("s"))
            assertNull(none["b"])

            val locked = decode(watchSummaryJson(repository, open + ("biometricLockEnabled" to "true"), TODAY, NOW, false, format))
            assertEquals("locked", locked.text("s"))
            assertEquals("Home", locked.text("b"))
            assertNull(locked["c"])
            assertNull(locked["l"])

            val hidden = decode(watchSummaryJson(repository, open + ("privacyModeEnabled" to "true"), TODAY, NOW, false, format))
            assertEquals("hidden", hidden.text("s"))
            assertNull(hidden["l"])
            assertNull(hidden["pd"])
            val hiddenFood = hidden.getValue("c").jsonArray[0].jsonObject
            assertEquals("Food", hiddenFood.text("n"))
            assertNull(hiddenFood["l"])
            assertNull(hiddenFood["x"])
            assertNull(hidden.getValue("u").jsonArray[0].jsonObject["a"])
        }

    @Test
    fun readySummaryFollowsPinsAndShowsRecentDetail() =
        withBudget { repository ->
            val pins = WatchSettings(pinnedCategoryIds = listOf("rent", "food")).toPreferences()
            val pinned = open + pins + ("lastTransactionAccountId" to "savings")

            val ready = decode(watchSummaryJson(repository, pinned, TODAY, NOW, opened = false, format))

            assertEquals("ready", ready.text("s"))
            assertEquals("E32000", ready.text("l"))
            assertEquals("E6400", ready.text("pd"))
            assertEquals("5", ready.text("dl"))
            assertEquals("true", ready.text("pf"))
            val monthFirst = pinned + WatchSettings(perDayFirst = false).toPreferences()
            val monthly = decode(watchSummaryJson(repository, monthFirst, TODAY, NOW, opened = false, format))
            assertEquals("false", monthly.text("pf"))
            assertEquals("E-50", ready.text("r"))
            assertEquals("true", ready.getValue("ro").jsonPrimitive.content)
            val categories = ready.getValue("c").jsonArray.map { it.jsonObject }
            assertEquals(listOf("rent", "food"), categories.map { it.text("i") })
            val food = categories[1]
            assertEquals("E-1240", food.text("l"))
            assertEquals("true", food.getValue("o").jsonPrimitive.content)
            assertEquals("112", food.text("p"))
            assertEquals(listOf("Corner Café"), food.getValue("x").jsonArray.map { it.jsonObject.text("n") })
            assertEquals(
                "Water",
                ready
                    .getValue("u")
                    .jsonArray[0]
                    .jsonObject
                    .text("n"),
            )
            val quickAdd = ready.getValue("q").jsonObject
            assertEquals(listOf("savings", "checking"), quickAdd.getValue("a").jsonArray.map { it.jsonObject.text("i") })
            assertEquals(listOf("Corner Café"), quickAdd.getValue("p").jsonArray.map { it.jsonPrimitive.content })
        }

    @Test
    fun theDailyAllowanceSplitsWhatIsLeftOverTheRestOfTheMonth() {
        assertEquals(DailyAllowance(6_400, 5), dailyAllowance(32_000, "2026-11-26"))
        assertEquals(DailyAllowance(1_000, 1), dailyAllowance(1_000, "2026-12-31"))
        assertEquals(DailyAllowance(1_034, 29), dailyAllowance(30_000, "2028-02-01"))
        assertEquals(DailyAllowance(-500, 3), dailyAllowance(-500, "2026-11-28"))
        assertEquals(DailyAllowance(0, 28), dailyAllowance(0, "2027-02-01"))
    }

    @Test
    fun everyRefusalNamesItsReason() {
        assertNull(watchQuickAddRefusal(open))
        assertEquals("No budget is open. Open a budget in Tandem first.", watchQuickAddRefusal(emptyMap()))
        assertTrue(watchQuickAddRefusal(open + ("readOnlyMode" to "true")).orEmpty().startsWith("Lock editing"))
        val off = open + WatchSettings(quickAddEnabled = false).toPreferences()
        assertTrue(watchQuickAddRefusal(off).orEmpty().startsWith("Quick add is off"))
    }

    @Test
    fun trimmingFallsBackStepByStepUntilItFits() {
        val summary =
            WatchSummary(
                state = WatchState.READY,
                generatedAtEpochMs = NOW,
                budgetId = "budget",
                budgetName = "Home",
                syncedAtEpochMs = NOW,
                leftToSpend = "E1",
                leftToSpendNegative = true,
                toBudget = "E2",
                categories = (1..4).map { WatchCategory("c$it", "Category $it", "E$it", spentPercent = 10, budgeted = "E9", spent = "E8") },
                bills = (1..3).map { WatchBill("Bill $it", "2026-12-0$it") },
                quickAdd =
                    WatchQuickAdd(
                        open = true,
                        accounts = listOf(WatchChoice("a", "Account")),
                        payees = listOf("Shop"),
                        recentCategories = listOf(WatchChoice("r", "Recent")),
                    ),
            )

        val tiny = decode(encodeWatchSummary(summary, maxBytes = 10))
        assertNull(tiny["c"])
        assertNull(tiny["u"])
        assertEquals("Home", tiny.text("b"))
        assertEquals(
            listOf("a"),
            tiny
                .getValue("q")
                .jsonObject
                .getValue("a")
                .jsonArray
                .map { it.jsonObject.text("i") },
        )

        val full = decode(encodeWatchSummary(summary))
        assertEquals("true", full.text("lo"))
        assertEquals(
            "E9",
            full
                .getValue("c")
                .jsonArray[0]
                .jsonObject
                .text("bu"),
        )
        assertNull(full.getValue("u").jsonArray[0].jsonObject["a"])
        assertEquals(
            "r",
            full
                .getValue("q")
                .jsonObject
                .getValue("k")
                .jsonArray[0]
                .jsonObject
                .text("i"),
        )
    }

    @Test
    fun readOnlyBudgetsStillShowNumbersButOfferNoQuickAdd() =
        withBudget { repository ->
            val ready = decode(watchSummaryJson(repository, open + ("readOnlyMode" to "true"), TODAY, NOW, opened = true, format))

            assertEquals("ready", ready.text("s"))
            assertEquals("false", ready.getValue("q").jsonObject.text("on"))
            assertEquals(listOf("food", "rent"), ready.getValue("c").jsonArray.map { it.jsonObject.text("i") })
        }

    @Test
    fun aMonthNotOnTheDeviceLeavesNumbersOut() =
        withBudget { repository ->
            val later = decode(watchSummaryJson(repository, open, "2027-03-01", NOW, opened = true, format))

            assertEquals("ready", later.text("s"))
            assertNull(later["l"])
            assertNull(later["c"])
            assertTrue(watchCategoryChoices(repository, "budget", "2027-03-01").isEmpty())
        }

    @Test
    fun pinChoicesAreThisMonthsExpenseCategoriesWithTheirGroup() =
        withBudget { repository ->
            val choices = watchCategoryChoices(repository, "budget", TODAY)

            assertEquals(listOf("food", "rent"), choices.map { it.id })
            assertEquals("Living", choices.first().group)
            assertEquals(11240L, choices.first().spentMinor)
        }

    @Test
    fun spentPercentCountsSpendingWithNothingBudgetedAsAll() {
        assertEquals(50, watchSpentPercent(200, 100))
        assertEquals(0, watchSpentPercent(200, -100))
        assertEquals(100, watchSpentPercent(0, 5))
        assertEquals(0, watchSpentPercent(0, 0))
        assertEquals(999, watchSpentPercent(1, 100_000))
    }

    private fun decode(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.content

    private fun withBudget(block: suspend (LocalBudgetRepository) -> Unit) =
        runBlocking {
            val database =
                openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(database) { NOW }
                repository.applyFullSnapshot("budget", "generation", "1", null, records())
                block(repository)
            } finally {
                database.close()
            }
        }

    private fun records() =
        listOf(
            record("budget-month", "2026-11", MONTH),
            record("account", "checking", """{"id":"checking","name":"Checking","offbudget":false,"closed":false}"""),
            record("account", "savings", """{"id":"savings","name":"Savings","offbudget":false,"closed":false}"""),
            record("account", "old", """{"id":"old","name":"Old","offbudget":false,"closed":true}"""),
            record("payee", "cafe", """{"id":"cafe","name":"Corner Café"}"""),
            record("schedule", "water", """{"id":"water","name":"Water","next_date":"2026-12-02","amount":-4000,"completed":false}"""),
            RemoteBudgetRecord(
                "budget",
                "transaction",
                "t1",
                "r-t1",
                """{"id":"t1","account":"checking","amount":-1240,"date":"2026-11-20","payee":"cafe","category":"food"}""",
                "generation",
                recordDate = "2026-11-20",
                accountId = "checking",
                categoryId = "food",
                payeeId = "cafe",
                amountMinor = -1240,
                budgetMonth = "2026-11",
            ),
        )

    private fun record(
        type: String,
        id: String,
        json: String,
    ) = RemoteBudgetRecord("budget", type, id, "r-$id", json, "generation")

    private companion object {
        const val TODAY = "2026-11-26"
        const val NOW = 1_795_000_000_000L
        const val MONTH =
            """{"toBudget":-50,"totalBalance":32000,"categoryGroups":[{"id":"living","name":"Living","categories":[""" +
                """{"id":"food","name":"Food","budgeted":10000,"spent":-11240,"balance":-1240},""" +
                """{"id":"rent","name":"Rent","budgeted":90000,"spent":-90000,"balance":0}]},""" +
                """{"id":"income","name":"Income","is_income":true,"categories":[{"id":"pay","name":"Pay","balance":0}]}]}"""
    }
}
