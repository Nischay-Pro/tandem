package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivitySearchTest {
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
        assertEquals(123_456L, parseActivitySearchAmount("1,234.56 USD"))
        assertEquals(123_456L, parseActivitySearchAmount("1.234,56 EUR"))
        assertEquals(5_000L, parseActivitySearchAmount("50,00 CAD"))
        assertEquals(75L, parseActivitySearchAmount(".75"))
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
        cleared: String? = null,
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
            dateLabel = date,
            amountMinorUnits = amount,
            amountText = formatSignedCurrency(amount),
            clearedState = cleared,
        )
}
