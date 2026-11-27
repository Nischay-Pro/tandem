package app.tandemforactual.ui
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.PlanningKeyring
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.ui.formatRateBasisPoints
import app.tandemforactual.ui.core.ui.parseRateBasisPoints
import app.tandemforactual.ui.core.ui.saved
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlanningTest {
    @Test
    fun ratesParseExactly() {
        assertEquals(1_999, parseRateBasisPoints("19.99"))
        assertEquals(1_990, parseRateBasisPoints(" 19.9 % "))
        assertEquals(500, parseRateBasisPoints("5"))
        assertEquals(10_000, parseRateBasisPoints("100"))
        listOf("", "abc", "19.999", "-1", "101", "1.2.3").forEach { assertNull(parseRateBasisPoints(it), it) }
        assertEquals("19.99%", formatRateBasisPoints(1_999))
        assertEquals("0.05%", formatRateBasisPoints(5))
    }

    @Test
    fun keyringKeepsOneKeyPerBudget() {
        var stored: String? = null
        val store =
            object : DeviceCredentialStore {
                override fun read() = stored

                override fun write(value: String) {
                    stored = value
                }

                override fun clear() {
                    stored = null
                }
            }
        val keyring = PlanningKeyring(store)
        val sealed = keyring.cipherFor("budget-1").seal("goal".encodeToByteArray(), "id")
        assertContentEquals(
            "goal".encodeToByteArray(),
            PlanningKeyring(store).cipherFor("budget-1").open(sealed.first, sealed.second, "id"),
        )
        keyring.cipherFor("budget-2")
        keyring.forget("budget-1")
        keyring.forget("missing")
        assertFalse(stored.orEmpty().contains("budget-1"))
        val memory = PlanningKeyring(null)
        val inMemory = memory.cipherFor("b").seal("x".encodeToByteArray(), "i")
        assertContentEquals("x".encodeToByteArray(), memory.cipherFor("b").open(inMemory.first, inMemory.second, "i"))
    }

    @Test
    fun goalProgressComesFromLinkedBalances() {
        val month =
            LiveBudgetMonthSummary(
                0,
                0,
                0,
                0,
                listOf(LiveCategoryGroupSummary("g", "Savings", listOf(LiveCategorySummary("cat", "Holiday", 0, 0, 42_000)))),
            )
        val accounts = listOf(LiveAccountSummary("acct", "Savings", 90_000, AccountKind.OTHER, false))
        val goal = SavingsGoal("1", "Holiday", 100_000, "USD")
        assertEquals(42_000, goal.copy(categoryId = "cat").saved(month, accounts))
        assertEquals(90_000, goal.copy(accountId = "acct").saved(null, accounts))
        assertNull(goal.saved(month, accounts))
    }
}
