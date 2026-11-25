package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.feature.accounts.activeAccountSort
import app.tandemforactual.ui.feature.accounts.actualAccountOrder
import app.tandemforactual.ui.feature.accounts.orderAccountsForList
import app.tandemforactual.ui.feature.accounts.sortAccountsForBudget
import app.tandemforactual.ui.feature.accounts.sortAccountsForList
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

    @Test
    fun savesAnOrderSortedAcrossActualsSectionsNotTandemsTypeGroups() {
        val budget =
            listOf(
                account("Zeta checking", 500, "checking"),
                account("Amex", -50, "credit"),
                account("Brokerage", 9_000, "investment").copy(offBudget = true),
                account("Alpha savings", 100, "savings"),
                account("Old card", 0, "credit").copy(closed = true),
            )
        val saved = sortAccountsForBudget(budget, AccountSort.NAME_ASCENDING)
        assertEquals(listOf("Alpha savings", "Amex", "Zeta checking", "Brokerage", "Old card"), saved.map { it.name })
        assertEquals(AccountSort.NAME_ASCENDING, activeAccountSort(sortAccountsWithinGroups(saved, AccountSort.NAME_ASCENDING)))
    }

    @Test
    fun sortsFromActualsOwnOrderNotTheGroupedList() {
        val budget =
            listOf(
                account("Visa", 0, "credit").copy(sortOrder = 1.0),
                account("Checking", 0, "checking").copy(sortOrder = 2.0),
                account("Amex", 0, "credit").copy(sortOrder = 3.0),
                account("Brokerage", 0, "investment").copy(sortOrder = 2.0),
                account("New", 0, "savings"),
            )
        assertEquals(listOf("Visa", "Brokerage", "Checking", "Amex", "New"), actualAccountOrder(budget))
    }

    @Test
    fun listsEachActualSectionTogetherSoASavedSortReadsSorted() {
        val saved =
            listOf(
                account("Amex Cash", 0, "other").copy(sortOrder = 1.0),
                account("Brokerage", 0, "other").copy(sortOrder = 2.0, offBudget = true),
                account("Amex Plus", 0, "other").copy(sortOrder = 3.0),
                account("Honda loan", 0, "other").copy(sortOrder = 4.0, offBudget = true),
                account("Amex Rewards", 0, "other").copy(sortOrder = 5.0),
            )
        val listed = sortAccountsForList(saved)
        assertEquals(listOf("Amex Cash", "Amex Plus", "Amex Rewards", "Brokerage", "Honda loan"), listed.map { it.name })
        assertEquals(AccountSort.NAME_ASCENDING, activeAccountSort(listed))
        assertEquals(listed.map { it.name }, orderAccountsForList(saved, saved.map { it.id }).map { it.name })
    }
}
