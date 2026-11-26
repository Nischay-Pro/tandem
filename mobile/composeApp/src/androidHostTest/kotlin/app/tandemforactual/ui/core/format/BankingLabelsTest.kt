package app.tandemforactual.ui.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class BankingLabelsTest {
    @Test
    fun anUnreadableTransactionDoesNotAskToReconnect() {
        val expected =
            "Last sync failed because a transaction from the bank couldn’t be read. " +
                "Tandem skipped this account’s sync so nothing wrong was saved. " +
                "Try again later; if it keeps happening, sync it from Actual’s web app."
        assertEquals(expected, bankFailureMessage("unreadable_transaction", browser = false))
        assertEquals(expected, bankFailureMessage("unreadable_transaction", browser = true))
    }

    @Test
    fun otherFailuresKeepTheirReconnectAdvice() {
        assertEquals(
            "Last sync failed because the bank needs you to sign in again. Sign in at the bank again to reconnect it.",
            bankFailureMessage("item_login_required", browser = true),
        )
        assertEquals(
            "Last sync failed because the bank no longer shares this account. " +
                "Reconnect it in Actual’s web app, or link a different account.",
            bankFailureMessage("account_missing", browser = false),
        )
        assertEquals(
            "Last sync failed because some code. Sign in at the bank again to reconnect it.",
            bankFailureMessage("SOME_CODE", true),
        )
    }
}
