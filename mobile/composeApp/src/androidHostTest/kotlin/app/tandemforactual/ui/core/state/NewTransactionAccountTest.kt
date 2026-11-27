package app.tandemforactual.ui.core.state

import app.tandemforactual.data.planning.AccountKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewTransactionAccountTest {
    private fun account(
        id: String,
        offBudget: Boolean = false,
        closed: Boolean = false,
    ) = LiveAccountSummary(id, id, 0, AccountKind.OTHER, offBudget, closed)

    private val accounts =
        listOf(
            account("old", closed = true),
            account("checking"),
            account("savings"),
            account("investments", offBudget = true),
            account("old-loan", offBudget = true, closed = true),
        )

    @Test
    fun addingFromAnAccountStartsOnThatAccountEvenOffBudget() {
        assertEquals("investments", defaultNewTransactionAccount(accounts, "investments", "savings")?.id)
        assertEquals("savings", defaultNewTransactionAccount(accounts, "savings", "checking")?.id)
    }

    @Test
    fun closedAccountsAreNeverTheStartingAccount() {
        assertEquals("savings", defaultNewTransactionAccount(accounts, "old", "savings")?.id)
        assertEquals("checking", defaultNewTransactionAccount(accounts, "old-loan", "old")?.id)
        assertEquals("checking", defaultNewTransactionAccount(accounts, null, "investments")?.id)
        assertEquals("investments", defaultNewTransactionAccount(accounts.filter { it.offBudget }, null, null)?.id)
        assertNull(defaultNewTransactionAccount(listOf(account("old", closed = true)), "old", "old"))
    }
}
