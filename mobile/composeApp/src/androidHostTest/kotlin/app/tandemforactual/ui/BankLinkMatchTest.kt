package app.tandemforactual.ui

import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.feature.banking.isBestMatch
import app.tandemforactual.ui.feature.banking.linkMatchScore
import app.tandemforactual.ui.feature.banking.rankLinkCandidates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankLinkMatchTest {
    private fun account(name: String) = LiveAccountSummary(name, name, null, AccountKind.OTHER, offBudget = false)

    private fun bank(
        name: String,
        institution: String? = null,
    ) = BankProviderAccount(id = name, name = name, institution = institution, bankId = "b", balance = null)

    private val budget =
        listOf("Everyday checking", "High interest savings", "Sapphire card", "Amex Gold", "Cash wallet", "Visa 4242").map(::account)

    private fun best(
        name: String,
        institution: String? = null,
    ) = rankLinkCandidates(bank(name, institution), budget).first()

    @Test
    fun suggestsTheBudgetAccountABankOrCardAccountBelongsTo() {
        assertEquals("Sapphire card", best("Chase Sapphire Preferred", "Chase").account.name)
        assertEquals("Visa 4242", best("CREDIT CARD ...4242", "Chase").account.name)
        assertEquals("Everyday checking", best("TOTAL CHECKING", "Chase").account.name)
        assertEquals("Everyday checking", best("Chequing", "RBC").account.name)
        assertEquals("High interest savings", best("Savings Acct", "Ally Bank").account.name)
        assertEquals("Amex Gold", best("Gold Card", "American Express").account.name)
    }

    @Test
    fun flagsOnlyAClearBestMatch() {
        val ranked = rankLinkCandidates(bank("Brokerage 9", "Fidelity"), budget)
        assertFalse(isBestMatch(ranked, ranked.first()))
        val clear = rankLinkCandidates(bank("Sapphire", "Chase"), budget)
        assertTrue(isBestMatch(clear, clear.first()))
        assertFalse(isBestMatch(clear, clear[1]))
        assertEquals(budget.size, ranked.size)
    }

    @Test
    fun scoresTyposAndIgnoresFillerWords() {
        assertTrue(linkMatchScore("Sapphrie Preferred", null, "Sapphire") > 0.5)
        assertEquals(0.0, linkMatchScore("Checking", null, "The account"))
        assertTrue(linkMatchScore("a", null, "a") > 0.9)
        assertEquals(0.0, linkMatchScore("x", null, "y"))
    }
}
