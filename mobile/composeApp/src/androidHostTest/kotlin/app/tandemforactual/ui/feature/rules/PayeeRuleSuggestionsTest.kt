package app.tandemforactual.ui.feature.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PayeeRuleSuggestionsTest {
    private val names = mapOf("gym" to "Fitness First", "shop" to "Corner Shop", "cafe" to "Bean There")

    private fun text(
        payeeId: String,
        vararg texts: String,
    ) = texts.map { ImportedText(payeeId, it) }

    @Test
    fun aPayeeTheBankNamesDifferentlyGetsARuleTriedOnHistory() {
        val history =
            text("gym", "FF CLUBS 0412 LONDON", "FF CLUBS 0512 LONDON", "Fitness First") +
                text("shop", "Corner Shop", "Corner Shop")
        val suggestion = payeeRuleSuggestions(history, names, emptySet()).single()
        assertEquals("gym", suggestion.payeeId)
        assertTrue(history.filter { it.payeeId == "gym" }.count { it.text.contains(suggestion.match, ignoreCase = true) } >= 2)
        assertEquals(2, suggestion.catches)
        assertEquals(1, suggestion.misses)
        assertEquals("Would have caught 2 past transactions and missed 1.", suggestionTally(suggestion))
    }

    @Test
    fun noRuleIsSuggestedWhenItWouldCatchAnotherPayeeOrOneAlreadyHasARule() {
        val clash = text("gym", "CARD PAYMENT", "CARD PAYMENT") + text("cafe", "CARD PAYMENT")
        assertEquals(emptyList(), payeeRuleSuggestions(clash, names, emptySet()))
        val ruled = text("gym", "FF CLUBS 0412", "FF CLUBS 0512")
        assertEquals(emptyList(), payeeRuleSuggestions(ruled, names, setOf("gym")))
        assertEquals(emptyList(), payeeRuleSuggestions(text("gym", "FF CLUBS 0412"), names, emptySet()))
    }

    @Test
    fun theSuggestionBecomesAFirstStageImportedPayeeRule() {
        val draft = PayeeRuleSuggestion("gym", "Fitness First", "FF CLUBS", 3, 0).toRuleDraft()
        val body = draft.toRequestBody { null }.getOrThrow().toString()
        assertTrue("\"stage\":\"pre\"" in body)
        assertTrue("\"field\":\"imported_payee\"" in body && "\"text\":\"FF CLUBS\"" in body)
        assertTrue("\"field\":\"payee\"" in body && "\"text\":\"gym\"" in body)
        assertEquals("Would have caught 3 past transactions and missed none.", suggestionTally(PayeeRuleSuggestion("gym", "", "x", 3, 0)))
    }
}
