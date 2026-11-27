package app.tandemforactual.data.store

import kotlin.test.Test
import kotlin.test.assertEquals

class PayeeCleanupTest {
    private fun cleaned(raw: String) = cleanPayeeName(raw, emptySet())

    @Test
    fun bankTextIsReadByItsShapeNotByBank() {
        assertEquals(CleanedPayee("CORNER MARKET", listOf("12/11", "14:32", "CARD 1234")), cleaned("CORNER MARKET 12/11 14:32 CARD 1234"))
        assertEquals(CleanedPayee("TESCO STORES LONDON", listOf("#4521")), cleaned("TESCO STORES #4521 LONDON"))
        assertEquals(CleanedPayee("UBER TRIP", listOf("2026-11-03")), cleaned("UBER *TRIP 2026-11-03"))
        assertEquals(CleanedPayee("PAYPAL", listOf("REF:AB12CD34")), cleaned("REF:AB12CD34 PAYPAL"))
        assertEquals(CleanedPayee("STARBUCKS", listOf("XXXX1234")), cleaned("XXXX1234 STARBUCKS"))
        assertEquals(CleanedPayee("SPOTIFY", listOf("USD 9.99", "@ 1.08")), cleaned("SPOTIFY USD 9.99 @ 1.08"))
        assertEquals(CleanedPayee("SHELL", listOf("3 NOV")), cleaned("SHELL 3 NOV"))
        assertEquals(CleanedPayee("7-ELEVEN", listOf("556677")), cleaned("7-ELEVEN 556677"))
        assertEquals(CleanedPayee("", emptyList()), cleaned("   --- *** "))
    }

    @Test
    fun webAddressesKeepTheirNameAndLoseTheirDressing() {
        assertEquals(CleanedPayee("NETFLIX", listOf("WWW.NETFLIX.COM")), cleaned("WWW.NETFLIX.COM"))
        assertEquals(
            CleanedPayee("AMAZON AMZN", listOf("AMAZON.COM", "AMZN.CO.UK", "MK12AB34")),
            cleaned("AMAZON.COM*MK12AB34 AMZN.CO.UK"),
        )
    }

    @Test
    fun boilerplateIsLearnedFromWordsManyUnrelatedPayeesShare() {
        val names =
            listOf("POS CORNER MARKET", "POS SHELL", "POS TESCO", "POS NETFLIX", "BOOTS LONDON", "TESCO LONDON", "PRET LONDON", "SOLO")
        val boilerplate = learnPayeeBoilerplate(names)
        assertEquals(setOf("london", "pos"), boilerplate)
        assertEquals(CleanedPayee("TESCO", listOf("POS", "LONDON")), cleanPayeeName("POS TESCO LONDON", boilerplate))
        assertEquals(CleanedPayee("POS", emptyList()), cleanPayeeName("POS", setOf("pos")))
        assertEquals("cornermarket", payeeMatchKey("Corner-Market!"))
    }

    @Test
    fun duplicatesGroupUnderTheMostUsedPayeeWithATidyName() {
        val payees =
            listOf(
                PayeeUsage("a", "POS TESCO 12/11", 3),
                PayeeUsage("b", "POS TESCO #4521", 10),
                PayeeUsage("c", "Tesco", 1),
                PayeeUsage("d", "POS NETFLIX", 4),
                PayeeUsage("e", "POS SHELL 1234", 2),
                PayeeUsage("f", "Shell", 2),
                PayeeUsage("g", "Corner Market", 5),
                PayeeUsage("h", "POS", 1),
                PayeeUsage("i", "***", 1),
                PayeeUsage("j", "--", 1),
            )

        val groups = payeeDuplicateGroups(payees)

        assertEquals(listOf("Tesco", "Shell"), groups.map { it.name })
        assertEquals("b", groups[0].keepId)
        assertEquals(listOf("b", "a", "c"), groups[0].members.map { it.id })
        assertEquals(listOf("12/11", "POS", "#4521"), groups[0].stripped)
        assertEquals("f", groups[1].keepId)
        assertEquals(listOf("f", "e"), groups[1].members.map { it.id })
        assertEquals(listOf(true, true), groups.map { it.safe })
    }

    @Test
    fun namesThatDifferBeyondBankDetailAreNotSafeToMergeUnseen() {
        val groups = payeeDuplicateGroups(listOf(PayeeUsage("x", "Corner-Market", 2), PayeeUsage("y", "CORNER MARKET", 1)))
        assertEquals(listOf("x", "y"), groups.single().members.map { it.id })
        assertEquals(false, groups.single().safe)
    }
}
