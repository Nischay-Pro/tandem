package app.tandemforactual.ui.core.importing

import app.tandemforactual.api.client.StatementLedgerTransaction
import app.tandemforactual.ui.core.platform.decodeStatementBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementMatchingTest {
    @Test
    fun aRowMatchesOnItsOwnOnlyWithTheExactSignedAmountInsideTheWindow() {
        val review =
            reviewStatement(
                listOf(row("2026-11-04", -1_240, "TESCO STORES 3297")),
                listOf(
                    ledger("near", "2026-11-06", -1_240, "Groceries shop"),
                    ledger("positive", "2026-11-04", 1_240, "Tesco"),
                    ledger("far", "2026-11-20", -1_240, "Tesco"),
                ),
            )

        val only = review.rows.single()
        assertEquals(StatementRowStatus.MATCHED, only.status)
        assertEquals("near", only.candidates.single().transaction.id)
        assertEquals(2, only.candidates.single().days)
        assertEquals(StatementRowAction.Match("near"), only.defaultAction())
    }

    @Test
    fun matchingTextNeverMakesACandidate() {
        val review =
            reviewStatement(
                listOf(row("2026-11-04", -1_240, "TESCO STORES")),
                listOf(ledger("t", "2026-11-04", -1_241, "Tesco Stores")),
            )

        assertEquals(StatementRowStatus.NEW, review.rows.single().status)
        assertEquals(StatementNoMatchReason.NoSameAmount, review.rows.single().reason)
        assertEquals(StatementRowAction.Create, review.rows.single().defaultAction())
    }

    @Test
    fun textOnlyRanksCandidatesTheAmountFoundAndTheUserChooses() {
        val review =
            reviewStatement(
                listOf(row("2026-11-04", -5_000, "CITY POWER CO")),
                listOf(
                    ledger("phone", "2026-11-04", -5_000, "Mobile phone"),
                    ledger("power", "2026-11-06", -5_000, "City Power"),
                ),
            )

        val only = review.rows.single()
        assertEquals(StatementRowStatus.CHOOSE, only.status)
        assertEquals(listOf("power", "phone"), only.candidates.map { it.transaction.id })
        assertEquals(StatementRowAction.Later, only.defaultAction())
    }

    @Test
    fun shortAndGenericWordsAreNotEvidence() {
        assertEquals(emptySet<String>(), meaningfulWords("FEE"))
        assertEquals(emptySet<String>(), meaningfulWords("PAYMENT - THANK YOU 0042"))
        assertEquals(setOf("tesco", "stores"), meaningfulWords("POS TESCO STORES 3297"))
        val review =
            reviewStatement(
                listOf(row("2026-11-04", -500, "FEE")),
                listOf(ledger("a", "2026-11-05", -500, "Fee refund"), ledger("b", "2026-11-04", -500, "Bank fee")),
            )

        assertTrue(review.rows.single().candidates.all { it.sharedWords == 0 })
        assertEquals(listOf("b", "a"), review.rows.single().candidates.map { it.transaction.id })
    }

    @Test
    fun sameDayRowsFromOneMerchantBecomeAGroupToPair() {
        val parsed = fixture("us-checking-signed.csv")
        val ledger =
            listOf(
                ledger("uber-1", "2026-11-03", -1_200, "Uber"),
                ledger("uber-2", "2026-11-03", -1_200, "Uber"),
                ledger("joes", "2026-11-03", -4_817, "Trader Joe's"),
            )
        val review = reviewStatement(parsed.transactions, ledger)
        val ubers = review.rows.filter { it.row.payeeName.startsWith("UBER") }

        assertEquals(listOf(StatementRowStatus.GROUP, StatementRowStatus.GROUP), ubers.map { it.status })
        assertEquals(ubers[0].group, ubers[1].group)
        assertEquals(StatementRowStatus.MATCHED, review.rows.first().status)
        assertTrue(ubers.all { it.defaultAction() == StatementRowAction.Later })
    }

    @Test
    fun nearIdenticalRowsAreLikelyDuplicatesAndASharedBankIdProvesIt() {
        val uk = reviewStatement(fixture("uk-paid-in-paid-out.csv").transactions, emptyList())
        val pret = uk.rows.filter { it.row.payeeName == "PRET A MANGER" }
        assertEquals(listOf(StatementRowStatus.NEW, StatementRowStatus.DUPLICATE), pret.map { it.status })
        assertFalse(pret[1].provenDuplicate)
        assertEquals(StatementRowAction.Later, pret[1].defaultAction())
        assertEquals("Looks the same as row 8. Import it only if you made both.", uk.explanation(pret[1], null))

        val ofx = reviewStatement(fixture("sgml-checking.ofx").transactions, emptyList())
        assertTrue(ofx.rows.last().provenDuplicate)
        assertEquals(StatementRowAction.Duplicate, ofx.rows.last().defaultAction())

        val tsv = reviewStatement(fixture("tab-separated.tsv").transactions, emptyList())
        assertTrue(tsv.rows.none { it.status == StatementRowStatus.DUPLICATE })
    }

    @Test
    fun explainsWhenTheStatementShowsSpendingAsPositive() {
        val parsed = fixture("card-spending-positive.csv")
        val ledger =
            listOf(
                ledger("amzn", "2026-11-04", -2_598, "Amazon"),
                ledger("coffee", "2026-11-03", -625, "Blue Bottle"),
                ledger("pay", "2026-11-02", 51_240, "Card payment"),
            )
        val review = reviewStatement(parsed.transactions, ledger)

        assertTrue(review.spendingLooksPositive)
        assertTrue(review.rows.first().reason is StatementNoMatchReason.OppositeSign)
        assertTrue(noMatchExplanation(review.rows.first().reason!!, null).contains("spending as positive"))

        val flipped = reviewStatement(parsed.withFlippedSigns().transactions, ledger)
        assertFalse(flipped.spendingLooksPositive)
        assertEquals(3, flipped.rows.count { it.status == StatementRowStatus.MATCHED })
    }

    @Test
    fun saysWhenTheSameAmountIsTooFarAwayOrTheAccountIsQuiet() {
        val far =
            reviewStatement(
                listOf(row("2026-11-01", -900, "Gym")),
                listOf(ledger("gym", "2026-11-12", -900, "Gym"), ledger("other", "2026-11-02", -100, "Shop")),
            )
        assertEquals(StatementNoMatchReason.TooFarApart(ledger("gym", "2026-11-12", -900, "Gym"), 11), far.rows.single().reason)

        val quiet = reviewStatement(listOf(row("2026-11-01", -900, "Gym")), emptyList())
        assertEquals(StatementNoMatchReason.NothingNearby, quiet.rows.single().reason)
    }

    @Test
    fun recognisesAStatementImportedBeforeByItsRowsOrAnOldBankId() {
        val parsed = fixture("sgml-checking.ofx").transactions
        val ledger =
            parsed.take(5).mapIndexed { index, draft ->
                ledger("t$index", draft.date, draft.amountMinor, draft.payeeName, importedId = if (index == 0) draft.bankId else draft.importedId)
            }
        val review = reviewStatement(parsed, ledger)

        assertTrue(review.repeatedStatement)
        assertEquals(5, review.alreadyImported)
        assertEquals("t0", review.rows.first().existing?.id)
        assertEquals(StatementRowAction.Duplicate, review.rows.first().defaultAction())
        assertTrue(review.applyItems(review.defaultActions()).isEmpty())
    }

    @Test
    fun anAmountAlreadyTakenByAnImportedRowIsExplained() {
        val first = row("2026-11-04", -700, "Cinema")
        val second = row("2026-11-05", -700, "Snacks")
        val review = reviewStatement(listOf(first, second), listOf(ledger("t", "2026-11-04", -700, "Cinema", importedId = first.importedId)))

        assertEquals(StatementRowStatus.IMPORTED, review.rows[0].status)
        assertTrue(review.rows[1].reason is StatementNoMatchReason.AlreadyImported)
    }

    @Test
    fun applySendsOnlyPairsAndNewRowsAndATransactionPairsOnce() {
        val rows = listOf(row("2026-11-03", -1_200, "UBER TRIP", bankId = "fit-1"), row("2026-11-03", -1_200, "UBER TRIP", bankId = "fit-2"))
        val review = reviewStatement(rows, listOf(ledger("u1", "2026-11-03", -1_200, "Uber"), ledger("u2", "2026-11-03", -1_200, "Uber")))
        var actions = review.defaultActions()
        assertTrue(review.applyItems(actions).isEmpty())

        actions = actions.choose(0, StatementRowAction.Match("u1"))
        actions = actions.choose(1, StatementRowAction.Match("u1"))
        assertEquals(StatementRowAction.Later, actions[0])
        assertEquals(1, actions.pairedRow("u1", 0))
        actions = actions.choose(0, StatementRowAction.Create)

        val items = review.applyItems(actions)
        assertEquals(listOf(null, "u1"), items.map { it.matchId })
        assertTrue(items.none { it.importedId.startsWith("fit") })
        assertEquals(StatementApplySummary(matched = 1, created = 1, skipped = 0, later = 0), review.summary(actions))
        assertEquals("Apply 2 changes", statementApplyLabel(review.summary(actions), busy = false))
        assertEquals("1 matched · 1 new", review.summary(actions).description())
    }

    @Test
    fun ledgerRangeCoversTheExplanationWindowAndDatesRoundTrip() {
        assertEquals("2026-10-01" to "2026-12-02", statementLedgerRange(listOf(row("2026-11-01", 1, "a"), row("2026-11-01", 1, "b"))))
        assertNull(statementLedgerRange(emptyList()))
        listOf("1995-01-01", "2024-02-29", "2026-12-31", "2100-03-01").forEach { assertEquals(it, isoDate(isoDayNumber(it)!!)) }
        assertEquals(1, isoDayNumber("2026-03-01")!! - isoDayNumber("2026-02-28")!!)
    }

    @Test
    fun describesDistancesAndActions() {
        assertEquals("same day", statementDayDistance(0))
        assertEquals("1 day earlier", statementDayDistance(-1))
        assertEquals("3 days later", statementDayDistance(3))
        val imported = StatementRowReview(0, row("2026-11-01", 1, "a"), StatementRowStatus.IMPORTED)
        assertEquals("Already in Actual", statementActionLabel(imported, StatementRowAction.Duplicate))
        assertEquals("Matched", statementActionLabel(imported, StatementRowAction.Match("x")))
    }

    private fun row(
        date: String,
        amount: Long,
        payee: String,
        bankId: String? = null,
    ) = StatementImportDraft(date, amount, payee, null, StatementImportIds().next(date, amount, payee + bankId.orEmpty()), bankId)

    private fun ledger(
        id: String,
        date: String,
        amount: Long,
        payee: String?,
        importedId: String? = null,
    ) = StatementLedgerTransaction(id, date, amount, payee, null, null, importedId, cleared = false, reconciled = false)

    private fun fixture(name: String): StatementParseResult =
        parseStatementImport(
            name,
            decodeStatementBytes(checkNotNull(javaClass.classLoader?.getResourceAsStream("statements/$name")).use { it.readBytes() }),
        )
}
