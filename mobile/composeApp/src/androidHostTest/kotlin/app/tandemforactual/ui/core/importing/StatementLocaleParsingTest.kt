package app.tandemforactual.ui.core.importing

import app.tandemforactual.ui.core.platform.decodeStatementBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatementLocaleParsingTest {
    @Test
    fun readsQuickenApostropheYears() {
        val parsed =
            parseStatementImport(
                "quicken.qif",
                "!Type:Bank\nD1/ 5'26\nT-4.00\nPCafe\n^\nD12/31'25\nT-5.00\nPMarket\n^\nD 2/ 3'2026\nT-6.00\nPBakery\n^\n",
            )

        assertEquals(listOf("2026-01-05", "2025-12-31", "2026-02-03"), parsed.transactions.map { it.date })
    }

    @Test
    fun oneDayFirstDateDecidesTheOrderForTheWholeFile() {
        val parsed =
            parseStatementImport(
                "day-first.csv",
                "Date,Payee,Amount\n05/04/2026,Cafe,1.00\n13/04/2026,Market,2.00\n",
                dateOrder = StatementDateOrder.MONTH_DAY_YEAR,
            )

        assertEquals(listOf("2026-04-05", "2026-04-13"), parsed.transactions.map { it.date })
    }

    @Test
    fun oneMonthFirstDateDecidesTheOrderForTheWholeFile() {
        val parsed =
            parseStatementImport(
                "month-first.qif",
                "!Type:Bank\nD04/05/2026\nT-1.00\nPCafe\n^\nD04/13/2026\nT-2.00\nPMarket\n^\n",
                dateOrder = StatementDateOrder.DAY_MONTH_YEAR,
            )

        assertEquals(listOf("2026-04-05", "2026-04-13"), parsed.transactions.map { it.date })
    }

    @Test
    fun ambiguousFilesUseTheChosenOrder() {
        val dayFirst =
            parseStatementImport("a.csv", "Date,Payee,Amount\n04/05/2026,Cafe,1.00\n", dateOrder = StatementDateOrder.DAY_MONTH_YEAR)
        val monthFirst =
            parseStatementImport("a.csv", "Date,Payee,Amount\n04/05/2026,Cafe,1.00\n", dateOrder = StatementDateOrder.MONTH_DAY_YEAR)

        assertEquals("2026-05-04", dayFirst.transactions.single().date)
        assertEquals("2026-04-05", monthFirst.transactions.single().date)
    }

    @Test
    fun filesThatMixDayFirstAndMonthFirstDatesAreRejected() {
        val error =
            assertFailsWith<StatementImportParseException> {
                parseStatementImport("mixed.csv", "Date,Payee,Amount\n13/04/2026,Cafe,1.00\n04/14/2026,Market,2.00\n")
            }

        assertEquals("This statement mixes day-first and month-first dates. Check the dates in the file.", error.message)
        assertEquals(
            StatementDateOrder.DAY_MONTH_YEAR,
            statementDateOrderFor(listOf("2026-04-13", "Apr 13, 2026"), StatementDateOrder.DAY_MONTH_YEAR),
        )
    }

    @Test
    fun readsSwissApostropheGroupingAndMoreCurrencyMarks() {
        assertEquals(123_450L, parseMoney("1'234.50", 2, "apostrophe-dot"))
        assertEquals(123_450L, parseMoney("CHF 1’234.50", 2, "apostrophe-dot"))
        assertEquals(-123_456_789L, parseMoney("-1'234'567.89", 2, "apostrophe-dot"))
        assertEquals(123_456L, parseMoney("R$ 1.234,56", 2, "dot-comma"))
        assertEquals(123_450L, parseMoney("1 234,50 kr", 2, "space-comma"))
        assertEquals(-4_200L, parseMoney("-42,00 zł", 2, "space-comma"))
        assertEquals(1_000L, parseMoney("Fr. 10.00", 2, "apostrophe-dot"))
        assertFailsWith<StatementImportParseException> { parseMoney("1'23.50", 2, "apostrophe-dot") }
        assertFailsWith<StatementImportParseException> { parseMoney("12'34", 2, "apostrophe-dot") }
    }

    @Test
    fun aThreeDigitGroupIsThousandsOnTwoDecimalBudgetsUnlessItUsesTheBudgetDecimalMark() {
        assertEquals(123_400L, parseMoney("1,234", 2, "comma-dot"))
        assertEquals(123_400L, parseMoney("1.234", 2, "dot-comma"))
        assertEquals(123_400L, parseMoney("1,234", 2, "apostrophe-dot"))
        assertEquals(-123_400L, parseMoney("(1,234)", 2, "comma-dot"))
        assertFailsWith<StatementImportParseException> { parseMoney("1.234", 2, "comma-dot") }
        assertFailsWith<StatementImportParseException> { parseMoney("1,234", 2, "dot-comma") }
        assertFailsWith<StatementImportParseException> { parseMoney("1,234", 2, null) }
    }

    @Test
    fun zeroDecimalCurrenciesAcceptStatementsThatPrintZeroCents() {
        assertEquals(150_000L, parseMoney("1500.00", 0, "comma-dot"))
        assertEquals(150_000L, parseMoney("1,500.00", 0, "comma-dot"))
        assertEquals(150_000L, parseMoney("1.500,0", 0, "dot-comma"))
        assertEquals(150_000L, parseMoney("¥1,500", 0, "comma-dot"))
        assertFailsWith<StatementImportParseException> { parseMoney("1500.50", 0, "comma-dot") }
        assertFailsWith<StatementImportParseException> { parseMoney("1500.000", 0, "comma-dot") }
    }

    @Test
    fun decodesOfxEntitiesInPayeesAndMemos() {
        val parsed =
            parseStatementImport(
                "entities.ofx",
                "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261104<TRNAMT>-18.50<FITID>e-1<NAME>AT&amp;T" +
                    "<MEMO>Caf&#233; &lt;3 &#x1F600; &bogus;</STMTTRN></BANKTRANLIST></OFX>",
            )

        assertEquals("AT&T", parsed.transactions.single().payeeName)
        assertEquals("Café <3 😀 &bogus;", parsed.transactions.single().notes)
        assertEquals("&#0; &#xD800;", decodeOfxEntities("&#0; &#xD800;"))
    }

    @Test
    fun decodesUtf16Statements() {
        val text = "Date,Payee,Amount\n11/01/2026,Café,-4.50\n"
        val littleEndianWithMark = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val bigEndianWithMark = text.toByteArray(Charsets.UTF_16)
        val littleEndianWithoutMark = text.toByteArray(Charsets.UTF_16LE)

        assertEquals(text, decodeStatementBytes(littleEndianWithMark))
        assertEquals(text, decodeStatementBytes(bigEndianWithMark))
        assertEquals(text, decodeStatementBytes(littleEndianWithoutMark))
        assertEquals(text, decodeStatementBytes(text.toByteArray(Charsets.UTF_16BE)))
        val parsed = parseStatementImport("utf16.csv", decodeStatementBytes(littleEndianWithMark))
        assertEquals("Café", parsed.transactions.single().payeeName)
        assertEquals(-450L, parsed.transactions.single().amountMinor)
    }
}
