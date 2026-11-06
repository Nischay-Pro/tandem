package app.tandemforactual.ui

import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivitySearchTest {
    @Test
    fun buildsSearchRowsDirectlyFromSlimTransactionProjections() {
        val indexed =
            buildSearchActivityRecords(
                records =
                    listOf(
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
    fun serverAddressWarningAllowsHttpsAndPrivateLanAddresses() {
        assertFalse(serverAddressMayBeInsecure("https://budget.example.com"))
        assertFalse(serverAddressMayBeInsecure("http://192.168.1.40:5006"))
        assertFalse(serverAddressMayBeInsecure("http://10.0.0.12"))
        assertTrue(serverAddressMayBeInsecure("http://1.1.1.1"))
        assertTrue(serverAddressMayBeInsecure("http://budget.example.com"))
        assertTrue(serverAddressMayBeInsecure("www.example.com"))
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
