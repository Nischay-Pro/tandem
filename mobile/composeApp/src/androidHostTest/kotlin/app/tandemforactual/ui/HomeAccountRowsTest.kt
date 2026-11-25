package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.feature.home.homeAccountRows
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAccountRowsTest {
    private val accounts =
        (1..6).map { LiveAccountSummary("a$it", "Account $it", it * 100L, "checking", offBudget = false) } +
            LiveAccountSummary("loan", "Loan", -5_000, "loan", offBudget = true)

    @Test
    fun listsEveryOnBudgetAccountWithTheirFullTotal() {
        val (shown, total) = homeAccountRows(HomeModule.ACCOUNTS, accounts)
        assertEquals(6, shown.size)
        assertEquals(2_100, total)
    }

    @Test
    fun netWorthShowsOnlyTheTotalOfEveryAccount() {
        assertEquals(emptyList<LiveAccountSummary>() to -2_900L, homeAccountRows(HomeModule.NET_WORTH, accounts))
    }
}
