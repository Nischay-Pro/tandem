package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.feature.accounts.activeAccountSort
import app.tandemforactual.ui.feature.accounts.sortAccountsWithinGroups
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSortTest {
    private fun account(
        name: String,
        balance: Long?,
        type: String,
    ) = LiveAccountSummary(name, name, balance, type, offBudget = false)

    private val accounts =
        listOf(
            account("Zeta checking", 500, "checking"),
            account("Alpha checking", 100, "checking"),
            account("Visa", -2_000, "credit"),
            account("Amex", -50, "credit"),
            account("Unknown", null, "checking"),
        )

    private fun names(sort: AccountSort) = sortAccountsWithinGroups(accounts, sort).map { it.name }

    @Test
    fun sortsWithinEachGroup() {
        assertEquals(listOf("Alpha checking", "Unknown", "Zeta checking", "Amex", "Visa"), names(AccountSort.NAME_ASCENDING))
        assertEquals(listOf("Zeta checking", "Unknown", "Alpha checking", "Visa", "Amex"), names(AccountSort.NAME_DESCENDING))
        assertEquals(listOf("Alpha checking", "Zeta checking", "Unknown", "Visa", "Amex"), names(AccountSort.BALANCE_ASCENDING))
        assertEquals(listOf("Zeta checking", "Alpha checking", "Unknown", "Amex", "Visa"), names(AccountSort.BALANCE_DESCENDING))
        assertEquals(listOf(true, false, true, false), AccountSort.entries.map { it.ascending })
    }

    @Test
    fun ticksTheSortTheAccountsAreInAndCustomOnceRearranged() {
        val sorted = sortAccountsWithinGroups(accounts, AccountSort.NAME_DESCENDING)
        assertEquals(AccountSort.NAME_DESCENDING, activeAccountSort(sorted))
        assertEquals(null, activeAccountSort(listOf(sorted[1], sorted[0]) + sorted.drop(2)))
        assertEquals(null, activeAccountSort(accounts.take(1)))
    }
}
