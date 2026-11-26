package app.tandemforactual.ui

import app.tandemforactual.data.planning.ExchangeRate
import app.tandemforactual.ui.core.format.BudgetCurrencyConverter
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.feature.home.homeAccountRows
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAccountRowsTest {
    private val accounts =
        (1..6).map { LiveAccountSummary("a$it", "Account $it", it * 100L, "checking", offBudget = false) } +
            LiveAccountSummary("loan", "Loan", -5_000, "loan", offBudget = true)
    private val sameCurrency = BudgetCurrencyConverter(emptyMap(), "USD", emptyList())

    @Test
    fun listsEveryOnBudgetAccountWithTheirFullTotal() {
        val (shown, total) = homeAccountRows(HomeModule.ACCOUNTS, accounts, sameCurrency)
        assertEquals(6, shown.size)
        assertEquals(2_100, total)
    }

    @Test
    fun netWorthShowsOnlyTheTotalOfEveryAccount() {
        assertEquals(emptyList<LiveAccountSummary>() to -2_900L, homeAccountRows(HomeModule.NET_WORTH, accounts, sameCurrency))
    }

    @Test
    fun totalsConvertForeignAccountsLikeTheAccountsScreen() {
        val converter =
            BudgetCurrencyConverter(
                mapOf("a1" to "EUR", "a2" to "JPY"),
                "USD",
                listOf(ExchangeRate("EUR", "USD", "2", "2025-01-01")),
            )
        val (_, total) = homeAccountRows(HomeModule.NET_WORTH, accounts, converter)
        assertEquals(converter.total(accounts.map { it.id to it.balanceMinor }).totalMinor, total)
        assertEquals(-2_900L + 100L - 200L, total)
    }
}
