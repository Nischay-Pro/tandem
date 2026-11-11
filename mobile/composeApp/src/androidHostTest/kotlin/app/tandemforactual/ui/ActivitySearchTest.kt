package app.tandemforactual.ui

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActivitySearchTest {
    @Test
    fun activityRowsUseOneQuietLineForNotesOrCategoryAndAccount() {
        val transaction =
            TransactionItem(
                payee = "Corner Market",
                category = "Groceries",
                dateLabel = "May 14, 2026",
                amountMinorUnits = -4_218,
                icon = "C",
                account = "Checking",
                notes = "  Weekly groceries  ",
            )

        assertEquals("Weekly groceries", activityTransactionSubtitle(transaction, showAccount = true))
        assertEquals("Groceries · Checking", activityTransactionSubtitle(transaction.copy(notes = ""), showAccount = true))
        assertEquals("Groceries", activityTransactionSubtitle(transaction.copy(notes = ""), showAccount = false))
    }

    @Test
    fun activityDateHeaderAppearsOnlyAtAdjacentDateBoundaries() {
        assertTrue(shouldShowActivityDateHeader("Nov 5, 2026", null))
        assertFalse(shouldShowActivityDateHeader("Nov 5, 2026", "Nov 5, 2026"))
        assertTrue(shouldShowActivityDateHeader("Nov 6, 2026", "Nov 5, 2026"))
    }

    @Test
    fun searchResultRowsUseTheSharedRelativeDateLabels() {
        val record =
            ActivitySearchRecord(
                payee = "Corner Market",
                payeeId = null,
                account = "Checking",
                accountId = null,
                category = "Groceries",
                categoryId = null,
                notes = "",
                dateIso = "2026-05-14",
                amountMinorUnits = -4_218,
            )

        assertEquals("Today", record.toTransactionItem(null, null, todayIso = "2026-05-14").dateLabel)
        assertEquals(
            "Yesterday",
            record.copy(dateIso = "2026-05-13").toTransactionItem(null, null, todayIso = "2026-05-14").dateLabel,
        )
    }

    @Test
    fun savedSearchesRoundTripWithBudgetScopeAndFilters() {
        val saved =
            SavedActivitySearch(
                budgetId = "budget-a",
                name = "Home supplies",
                query = "hardware",
                filters =
                    listOf(
                        ActivitySearchFilter(ActivitySearchField.TAG, "home", "Tag: #home"),
                        ActivitySearchFilter(ActivitySearchField.DATE_RANGE, "2026-05-01..2026-05-31", "Date: May 2026"),
                    ),
            )

        assertEquals(listOf(saved), decodeSavedActivitySearches(encodeSavedActivitySearches(listOf(saved))))
        assertEquals(emptyList(), decodeSavedActivitySearches("not json"))
    }

    @Test
    fun savedSearchLimitIsTwentyPerBudget() {
        val searches =
            listOf("budget-a", "budget-b").flatMap { budgetId ->
                (0..20).map { index ->
                    SavedActivitySearch(
                        budgetId = budgetId,
                        name = "Search $index",
                        query = "query $index",
                        filters = emptyList(),
                    )
                }
            }

        val restored = decodeSavedActivitySearches(encodeSavedActivitySearches(searches))

        assertEquals(20, restored.count { it.budgetId == "budget-a" })
        assertEquals(20, restored.count { it.budgetId == "budget-b" })
        assertEquals("Search 20", restored.last { it.budgetId == "budget-a" }.name)
        assertFalse(restored.any { it.name == "Search 0" })
        assertEquals(setOf("budget-a", "budget-b"), restored.map { it.budgetId }.toSet())
    }

    @Test
    fun actualHashTagsCanBeFilteredAndCombinedWithOtherFilters() {
        val tagged = record(notes = "hardware run #home #DIY #home")
        val escaped = record(notes = "literal ##not-a-tag")
        val tagFilter = ActivitySearchFilter(ActivitySearchField.TAG, "home", "Tag: #home")
        val categoryFilter = ActivitySearchFilter(ActivitySearchField.CATEGORY, "category-groceries", "Category: Groceries")
        val dateFilter = ActivitySearchFilter(ActivitySearchField.DATE_RANGE, "2026-05-01..2026-05-31", "Date: May 2026")

        assertEquals(listOf("home", "DIY"), parseActivitySearchTags(tagged.notes))
        assertEquals(emptyList(), parseActivitySearchTags(escaped.notes))
        assertTrue(matchesActivitySearch(tagged, "tag:home", emptyList()))
        assertFalse(matchesActivitySearch(tagged, "tag:travel", emptyList()))
        assertTrue(matchesActivitySearch(tagged, "", listOf(tagFilter)))
        assertFalse(matchesActivitySearch(escaped, "", listOf(tagFilter)))
        assertTrue(matchesActivitySearch(tagged, "", listOf(tagFilter, categoryFilter, dateFilter)))
        assertTrue(
            activitySearchSuggestions("tag:di", listOf(tagged)).any {
                it.field == ActivitySearchField.TAG && it.value == "DIY"
            },
        )
    }

    @Test
    fun buildsSearchRowsDirectlyFromSlimTransactionProjections() {
        val indexed =
            buildSearchActivityRecords(
                records =
                    listOf(
                        SearchableBudgetTransaction(
                            recordId = "split-parent",
                            recordDate = "2026-05-14",
                            accountId = "account-1",
                            categoryId = null,
                            payeeId = "payee-1",
                            amountMinor = -1_170,
                            notes = "House supplies",
                            cleared = null,
                            reconciled = null,
                            approved = null,
                            matched = null,
                            transfer = false,
                            isParent = true,
                        ),
                        SearchableBudgetTransaction(
                            recordId = "transaction-1",
                            recordDate = "2026-05-14",
                            accountId = "account-1",
                            categoryId = "category-1",
                            payeeId = "payee-1",
                            amountMinor = -1_170,
                            notes = "House supplies",
                            cleared = true,
                            reconciled = false,
                            approved = true,
                            matched = false,
                            transfer = false,
                        ),
                    ),
                accounts = mapOf("account-1" to "Checking"),
                payees = mapOf("payee-1" to "Home Store"),
                categories = mapOf("category-1" to "Home Maintenance"),
            )

        assertEquals(1, indexed.size)
        assertEquals("transaction-1", indexed.single().recordId)
        assertEquals("Home Store", indexed.single().payee)
        assertEquals("Checking", indexed.single().account)
        assertEquals("Home Maintenance", indexed.single().category)
        assertEquals("House supplies", indexed.single().notes)
        assertEquals(-1_170L, indexed.single().amountMinorUnits)
        assertEquals(true, indexed.single().cleared)
        assertEquals(false, indexed.single().reconciled)
        assertEquals(true, indexed.single().approved)
        assertEquals(false, indexed.single().matched)
    }

    @Test
    fun generalSearchOffersRecentMonthFiltersAndKeepsOnlyOneDateFilter() {
        val records =
            listOf(
                record(date = "2026-05-14"),
                record(date = "2025-12-14"),
                record(date = "2026-04-14"),
                record(date = "2024-08-14"),
            )
        val monthFilters = recentActivitySearchMonthFilters(records)
        val category = ActivitySearchFilter(ActivitySearchField.CATEGORY, "rent", "Category: Rent")
        val may = monthFilters.first { it.value == "2026-05" }
        val april = monthFilters.first { it.value == "2026-04" }
        val filters = toggleActivitySearchFilter(listOf(category, may), april)

        assertEquals(listOf("2026-05", "2026-04", "2025-12"), monthFilters.map { it.value })
        assertEquals(listOf(category, april), filters)
        assertEquals(listOf(category), toggleActivitySearchFilter(filters, april))
        assertFalse(isActivitySearchMonthId("2026-13"))
    }

    @Test
    fun generalSearchMatchesPartialIsoDatesButDoesNotFuzzyMatchTypos() {
        val november = record(date = "2026-11-06")

        assertTrue(matchesActivitySearch(november, "2026-11", emptyList()))
        assertTrue(matchesActivitySearch(november, "11/6/26", emptyList()))
        assertFalse(matchesActivitySearch(november, "Noveber 6", emptyList()))
        assertEquals(listOf("2026-11-06"), parseActivitySearchDatePrefixes("11/6/26"))
        assertTrue(parseActivitySearchDatePrefixes("11/31/26").isEmpty())
        assertTrue(
            activitySearchSuggestions("Nov", listOf(november)).any {
                it.field == ActivitySearchField.DATE_MONTH && it.value == "2026-11"
            },
        )
        assertTrue(
            activitySearchSuggestions("11/6/26", listOf(november)).any {
                it.field == ActivitySearchField.DATE_ON && it.value == "2026-11-06"
            },
        )
    }

    @Test
    fun generalSearchAcceptsDatePrefixesMonthNamesAndCommonDateFormats() {
        val november = record(date = "2026-11-06")
        val january = record(date = "2026-01-12")
        val januaryThirteenth = record(date = "2026-01-13")

        listOf("Date: November 2026", "November 2026", "Nov 2026", "11/2026", "2026-11")
            .forEach { query -> assertTrue(matchesActivitySearch(november, query, emptyList()), query) }
        listOf(
            "01/12/2026",
            "2026-01-12",
            "2026/01/12",
            "1-12-2026",
            "January 12, 2026",
            "Jan. 12, 2026",
            "12 January 2026",
        ).forEach { query -> assertTrue(matchesActivitySearch(january, query, emptyList()), query) }
        assertTrue(matchesActivitySearch(januaryThirteenth, "13/01/2026", emptyList()))
        assertTrue(matchesActivitySearch(january, "12.01.2026", emptyList()))
        assertFalse(matchesActivitySearch(november, "Date: Noveber 2026", emptyList()))

        val monthSuggestion = activitySearchSuggestions("Date: November 2026", listOf(november)).single()
        assertEquals(ActivitySearchField.DATE_MONTH, monthSuggestion.field)
        assertEquals("2026-11", monthSuggestion.value)
        assertTrue(monthSuggestion.label.contains("November 2026"))
    }

    @Test
    fun generalSearchSupportsInclusiveDateRangesAndCommonRangeSeparators() {
        val januaryFirst = record(date = "2026-01-01")
        val januaryThirtyFirst = record(date = "2026-01-31")
        val februaryFirst = record(date = "2026-02-01")
        val queries =
            listOf(
                "Date: 1/1/2026 to 1/31/2026",
                "between 01/01/2026 and 01/31/2026",
                "2026-01-01..2026-01-31",
                "Jan 1 - Jan 31, 2026",
            )

        queries.forEach { query ->
            assertTrue(matchesActivitySearch(januaryFirst, query, emptyList()), query)
            assertTrue(matchesActivitySearch(januaryThirtyFirst, query, emptyList()), query)
            assertFalse(matchesActivitySearch(februaryFirst, query, emptyList()), query)
        }
        assertTrue(matchesActivitySearch(februaryFirst, "between January 2026 and February 2026", emptyList()))
    }

    @Test
    fun dateQuickFilterShowsMinusWhenItsMonthIsCoveredByAnActiveDateRange() {
        val november = recentActivitySearchMonthFilters(listOf(record(date = "2026-11-06"))).single()
        val dateRange =
            ActivitySearchFilter(
                ActivitySearchField.DATE_RANGE,
                "2026-11-01..2026-11-30",
                "Date: November 2026",
            )

        assertTrue(isActivitySearchFilterActive(listOf(dateRange), november))
        assertFalse(isActivitySearchFilterActive(emptyList(), november))
        assertEquals(emptyList(), toggleActivitySearchFilter(listOf(dateRange), november))
    }

    @Test
    fun activitySearchSortsByDateAmountAndPayee() {
        val records =
            listOf(
                record(payee = "Target", date = "2026-01-01", amount = -4_000),
                record(payee = "Costco", date = "2026-01-03", amount = -12_000),
                record(payee = "Walmart", date = "2026-01-02", amount = 20_000),
            )
        val indices = records.indices.toList()

        assertEquals(listOf(1, 2, 0), sortActivitySearchIndices(indices, records, ActivitySortOrder.NEWEST_FIRST))
        assertEquals(listOf(0, 2, 1), sortActivitySearchIndices(indices, records, ActivitySortOrder.OLDEST_FIRST))
        assertEquals(listOf(2, 1, 0), sortActivitySearchIndices(indices, records, ActivitySortOrder.AMOUNT_HIGH_TO_LOW))
        assertEquals(listOf(0, 1, 2), sortActivitySearchIndices(indices, records, ActivitySortOrder.AMOUNT_LOW_TO_HIGH))
        assertEquals(listOf(1, 0, 2), sortActivitySearchIndices(indices, records, ActivitySortOrder.PAYEE_A_TO_Z))
    }

    @Test
    fun activityListReusesItsSortedRowsAfterReturningFromAnotherScreen() {
        val transactions =
            listOf(
                TransactionItem("Target", "", "Jan 1", -4_000, "", dateIso = "2026-01-01"),
                TransactionItem("Costco", "", "Jan 3", -12_000, "", dateIso = "2026-01-03"),
                TransactionItem("Walmart", "", "Jan 2", 20_000, "", dateIso = "2026-01-02"),
            )
        val cache = ActivityTransactionSortCache()
        val sorted = sortActivityTransactions(transactions, ActivitySortOrder.OLDEST_FIRST)

        assertEquals(listOf("Target", "Walmart", "Costco"), sorted.map { it.payee })
        assertNull(cache.get("budget-1", transactions, ActivitySortOrder.OLDEST_FIRST))
        cache.put("budget-1", transactions, ActivitySortOrder.OLDEST_FIRST, sorted)
        assertSame(sorted, cache.get("budget-1", transactions, ActivitySortOrder.OLDEST_FIRST))
        assertNull(cache.get("budget-2", transactions, ActivitySortOrder.OLDEST_FIRST))
    }

    @Test
    fun categoryAndMonthFiltersCombineForCategoryActivity() {
        val groceriesMay = record(category = "Groceries", categoryId = "cat-grocery", date = "2026-05-14")
        val groceriesApril = record(category = "Groceries", categoryId = "cat-grocery", date = "2026-04-14")
        val rentMay = record(category = "Rent", categoryId = "cat-rent", date = "2026-05-01")
        val filters =
            listOf(
                ActivitySearchFilter(ActivitySearchField.CATEGORY, "cat-grocery", "Category: Groceries"),
                ActivitySearchFilter(ActivitySearchField.DATE_MONTH, "2026-05", "Date: May 2026"),
            )

        assertTrue(matchesActivitySearch(groceriesMay, "", filters))
        assertFalse(matchesActivitySearch(groceriesApril, "", filters))
        assertFalse(matchesActivitySearch(rentMay, "", filters))
    }

    @Test
    fun searchDetectsTextDateAndAmountRefinements() {
        val record = record(payee = "Target", date = "2026-05-14", amount = -5_000)

        val textSuggestions = activitySearchSuggestions("Tar", listOf(record))
        assertTrue(textSuggestions.any { it.field == ActivitySearchField.ANY_CONTAINS && it.label == "Anything contains: Tar" })
        assertTrue(textSuggestions.any { it.field == ActivitySearchField.PAYEE_CONTAINS })
        assertTrue(activitySearchSuggestions("2026-05", listOf(record)).any { it.field == ActivitySearchField.DATE_MONTH })
        assertTrue(activitySearchSuggestions("May", listOf(record)).any { it.field == ActivitySearchField.DATE_MONTH })
        assertTrue(activitySearchSuggestions("50", listOf(record)).any { it.field == ActivitySearchField.OUTFLOW_IS })
        assertTrue(activitySearchSuggestions("50", listOf(record)).any { it.field == ActivitySearchField.INFLOW_AT_LEAST })
    }

    @Test
    fun amountSearchSupportsCurrencyAndGroupingSeparators() {
        assertEquals(5_000L, parseActivitySearchAmount("$50.00"))
        assertEquals(123_456L, parseActivitySearchAmount("1,234.56 USD", "USD"))
        assertEquals(123_456L, parseActivitySearchAmount("1.234,56 EUR", "EUR"))
        assertEquals(5_000L, parseActivitySearchAmount("50,00 CAD", "CAD"))
        assertEquals(5_000L, parseActivitySearchAmount("USD 50", "USD"))
        assertEquals(75L, parseActivitySearchAmount(".75"))
    }

    @Test
    fun wordsThatContainThreeLettersAreNotTreatedAsCurrencyCodes() {
        assertNull(parseActivitySearchAmount("Gas 20", "USD"))
        assertNull(parseActivitySearchAmount("mar 2026", "USD"))
    }

    @Test
    fun suggestionsUseTheAppTermsForNotesSpendingAndIncome() {
        val labels = activitySearchSuggestions("12", listOf(record())).map(ActivitySearchFilter::label)

        assertTrue("Notes contain: 12" in labels)
        assertTrue(labels.any { it.startsWith("Spending is:") })
        assertTrue(labels.any { it.startsWith("Income at least:") })
        assertTrue(labels.none { it.contains("Memo") || it.contains("Outflow") || it.contains("Inflow") })
    }

    @Test
    fun preparedSearchNormalizesQueryAndParsesFilterAmountsOnce() {
        val transaction = record(payee = "Panera Bread", amount = -5_000)
        val outflow = ActivitySearchFilter(ActivitySearchField.OUTFLOW_IS, "5000", "Outflow is $50.00")
        val prepared = prepareActivitySearch("  PANERA  ", listOf(outflow))

        assertEquals("panera", prepared.normalizedQuery)
        assertEquals(5_000L, prepared.filters.single().expectedAmount)
        assertTrue(matchesActivitySearch(transaction, prepared))
    }

    @Test
    fun amountBoundsSeparateInflowsAndOutflows() {
        val expense = record(amount = -12_000)
        val income = record(amount = 12_000)
        val atLeastTen = ActivitySearchFilter(ActivitySearchField.OUTFLOW_AT_LEAST, "1000", "Outflow ≥ $10.00")
        val exactIncome = ActivitySearchFilter(ActivitySearchField.INFLOW_IS, "12000", "Inflow is $120.00")

        assertTrue(matchesActivitySearch(expense, "", listOf(atLeastTen)))
        assertFalse(matchesActivitySearch(income, "", listOf(atLeastTen)))
        assertTrue(matchesActivitySearch(income, "", listOf(exactIncome)))
    }

    @Test
    fun clearedAndReconciledFiltersUseActualsSeparateFlags() {
        val clearedOnly = record(cleared = true, reconciled = false)
        val uncleared = record(cleared = false, reconciled = false)
        val reconciled = record(cleared = true, reconciled = true)

        assertTrue(matchesActivitySearch(clearedOnly, "", listOf(filter(ActivitySearchField.CLEARED))))
        assertTrue(matchesActivitySearch(reconciled, "", listOf(filter(ActivitySearchField.CLEARED))))
        assertFalse(matchesActivitySearch(uncleared, "", listOf(filter(ActivitySearchField.CLEARED))))
        assertTrue(matchesActivitySearch(uncleared, "", listOf(filter(ActivitySearchField.UNCLEARED))))
        assertTrue(matchesActivitySearch(reconciled, "", listOf(filter(ActivitySearchField.RECONCILED))))
        assertFalse(matchesActivitySearch(clearedOnly, "", listOf(filter(ActivitySearchField.RECONCILED))))
        assertTrue(matchesActivitySearch(clearedOnly, "", listOf(filter(ActivitySearchField.UNRECONCILED))))
        assertFalse(matchesActivitySearch(reconciled, "", listOf(filter(ActivitySearchField.UNRECONCILED))))
    }

    @Test
    fun serverAddressWarningMatchesTheCleartextHostPolicy() {
        assertFalse(serverAddressMayBeInsecure("https://budget.example.com"))
        assertFalse(serverAddressMayBeInsecure("www.example.com"))
        assertFalse(serverAddressMayBeInsecure("http://192.168.1.40:5006"))
        assertFalse(serverAddressMayBeInsecure("http://10.0.0.12"))
        assertFalse(serverAddressMayBeInsecure("http://[fd12::1]:19753"))
        assertFalse(serverAddressMayBeInsecure("http://localhost:19753"))
        assertTrue(serverAddressMayBeInsecure("http://1.1.1.1"))
        assertTrue(serverAddressMayBeInsecure("http://budget.example.com"))
        assertTrue(serverAddressMayBeInsecure("http://tandem.home"))
        assertTrue(serverAddressMayBeInsecure("http://tandem.lan"))
        assertTrue(serverAddressMayBeInsecure("http://actual.local"))
        assertTrue(serverAddressMayBeInsecure("http://fe80"))
    }

    @Test
    fun signInTransportWarningCoversEveryHttpServerAndKeepsHttpUsable() {
        val warning = "HTTP does not encrypt passwords or session tokens. Use it only on trusted local networks."

        assertEquals(warning, signInHttpTransportWarning("http://192.168.1.40:5006"))
        assertEquals(warning, signInHttpTransportWarning("http://10.0.0.12"))
        assertEquals(warning, signInHttpTransportWarning("http://[fd12::1]:19753"))
        assertEquals(warning, signInHttpTransportWarning("http://localhost:19753"))
        assertEquals(warning, signInHttpTransportWarning("http://budget.example.com"))
        assertEquals(warning, signInHttpTransportWarning("http://tandem.home"))
        assertEquals(warning, signInHttpTransportWarning(" HTTP://1.1.1.1:5006 "))
        assertEquals(null, signInHttpTransportWarning("https://budget.example.com"))
        assertEquals(null, signInHttpTransportWarning(""))
        assertTrue(serverAddressIsValid("http://192.168.1.40:5006"))
    }

    @Test
    fun serverAddressRequiresAUsableHttpAddress() {
        assertFalse(serverAddressIsValid(""))
        assertFalse(serverAddressIsValid("https://"))
        assertFalse(serverAddressIsValid("www.example.com"))
        assertFalse(serverAddressIsValid("https://bad host.example"))
        assertFalse(serverAddressIsValid("https://example.com:70000"))
        assertTrue(serverAddressIsValid("https://tandem.example.com:19753"))
        assertTrue(serverAddressIsValid("http://192.168.1.40:5006"))
        assertTrue(serverAddressIsValid("http://[fd12::1]:19753"))
        assertEquals("", cleanServerAddress("https://budget.example.com"))
        assertEquals("https://my-tandem.home", cleanServerAddress(" https://my-tandem.home "))
    }

    private fun record(
        payee: String = "Market",
        payeeId: String? = null,
        account: String = "Checking",
        accountId: String? = "account-checking",
        category: String = "Groceries",
        categoryId: String? = "category-groceries",
        notes: String = "",
        date: String = "2026-05-14",
        amount: Long = -4_200,
        cleared: Boolean? = null,
        reconciled: Boolean? = null,
    ): ActivitySearchRecord =
        ActivitySearchRecord(
            payee = payee,
            payeeId = payeeId,
            account = account,
            accountId = accountId,
            category = category,
            categoryId = categoryId,
            notes = notes,
            dateIso = date,
            amountMinorUnits = amount,
            cleared = cleared,
            reconciled = reconciled,
        )

    private fun filter(field: ActivitySearchField) = ActivitySearchFilter(field, "", field.name)
}
