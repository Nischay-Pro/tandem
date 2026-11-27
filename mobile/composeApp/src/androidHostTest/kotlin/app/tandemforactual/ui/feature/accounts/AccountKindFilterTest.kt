package app.tandemforactual.ui.feature.accounts

import app.tandemforactual.data.planning.AccountKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountKindFilterTest {
    private fun item(
        name: String,
        kind: AccountKind,
    ) = AccountListItemUi(name, name, kind, "Last synced today", "$1.00", offBudget = false)

    private val accounts =
        listOf(
            item("Everyday checking", AccountKind.BANK),
            item("Savings", AccountKind.SAVINGS),
            item("Wallet", AccountKind.CASH),
            item("Credit card", AccountKind.CREDIT_CARD),
            item("Car", AccountKind.LOAN),
            item("House", AccountKind.MORTGAGE),
            item("Brokerage", AccountKind.INVESTMENT),
            item("Joint", AccountKind.OTHER),
        )

    private fun shown(filter: String) = accounts.filter { accountMatchesFilter(it, "", filter, "") }.map { it.name }

    @Test
    fun eachFilterShowsItsKindsAndEveryKindHasAFilter() {
        assertEquals(accounts.map { it.name }, shown("All"))
        assertEquals(listOf("Everyday checking", "Savings", "Wallet"), shown("Bank"))
        assertEquals(listOf("Credit card"), shown("Credit"))
        assertEquals(listOf("Car", "House"), shown("Loans"))
        assertEquals(listOf("Brokerage", "Joint"), shown("Other"))
        assertEquals(accounts.size, ACCOUNT_FILTERS.drop(1).sumOf { shown(it).size })
    }

    @Test
    fun searchMatchesTheKindLabel() {
        assertTrue(accountMatchesFilter(item("Joint", AccountKind.MORTGAGE), "", "All", "mortgage"))
        assertFalse(accountMatchesFilter(item("Joint", AccountKind.MORTGAGE), "", "All", "credit"))
        assertTrue(accountMatchesFilter(item("Joint", AccountKind.MORTGAGE), "Mortgages", "Loans", "mort"))
    }

    @Test
    fun pickerMarksGuessesAndAsksWhenThereIsNone() {
        assertNull(accountKindPickerHint(AccountKind.CREDIT_CARD, suggested = false))
        assertEquals("Suggested from the name", accountKindPickerHint(AccountKind.CREDIT_CARD, suggested = true))
        assertEquals("Choose what this account is", accountKindPickerHint(AccountKind.OTHER, suggested = true))
    }
}
