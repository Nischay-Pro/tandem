package app.tandemforactual.ui.core.importing

import app.tandemforactual.ui.core.platform.decodeStatementBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementFixtureTest {
    @Test
    fun everyFixtureIsReadFromItsContentWhateverItsName() {
        val expected =
            mapOf(
                "us-checking-signed.csv" to StatementFormat.DELIMITED,
                "uk-paid-in-paid-out.csv" to StatementFormat.DELIMITED,
                "eu-semicolon.csv" to StatementFormat.DELIMITED,
                "tab-separated.tsv" to StatementFormat.DELIMITED,
                "headerless.csv" to StatementFormat.DELIMITED,
                "card-spending-positive.csv" to StatementFormat.DELIMITED,
                "debit-credit-footer.csv" to StatementFormat.DELIMITED,
                "bank.qif" to StatementFormat.QIF,
                "card.qif" to StatementFormat.QIF,
                "sgml-checking.ofx" to StatementFormat.OFX,
                "xml-card.qfx" to StatementFormat.OFX,
            )
        expected.forEach { (name, format) ->
            assertEquals(name, format, detectStatementFormat(fixture(name)))
            assertEquals(name, parse(name).transactions, parseStatementImport("download", fixture(name)).transactions)
        }
    }

    @Test
    fun signedAmountCsvWithATypeColumnAndTrailingComma() {
        val parsed = parse("us-checking-signed.csv")

        assertEquals(7, parsed.transactions.size)
        assertEquals("TRADER JOE S #552 QPS SPRINGFIELD IL", parsed.transactions.first().payeeName)
        assertEquals(
            listOf(-4_817L, -1_200L, -1_200L, 245_000L, -8_632L, -15_000L, -500L),
            parsed.transactions.map { it.amountMinor },
        )
        assertEquals("2026-10-30", parsed.transactions.last().date)
    }

    @Test
    fun preambleRowsAreSkippedAndPaidInPaidOutColumnsGiveTheSign() {
        val layout = inspectStatementCsv(fixture("uk-paid-in-paid-out.csv"))
        val parsed = parse("uk-paid-in-paid-out.csv")

        assertEquals(listOf("Date", "Transaction type", "Description", "Paid out", "Paid in", "Balance"), layout.columns.map { it.name })
        assertEquals(StatementCsvColumnMapping(dateColumn = 0, debitColumn = 3, creditColumn = 4, payeeColumn = 2), layout.suggestedMapping)
        assertEquals(listOf(-1_240L, -14_200L, 45_000L, -495L, -495L), parsed.transactions.map { it.amountMinor })
        assertEquals(listOf("2026-11-04", "2026-11-03", "2026-11-03", "2026-11-01", "2026-11-01"), parsed.transactions.map { it.date })
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun semicolonFileWithDecimalCommasAndDottedDayFirstDates() {
        val parsed = parse("eu-semicolon.csv", numberFormat = "dot-comma")

        assertEquals(listOf("2026-11-04", "2026-11-03", "2026-11-02", "2026-11-01"), parsed.transactions.map { it.date })
        assertEquals(listOf(-2_347L, -6_400L, 231_580L, -320L), parsed.transactions.map { it.amountMinor })
        assertEquals("REWE Markt GmbH", parsed.transactions.first().payeeName)
        assertEquals("Kartenzahlung girocard", parsed.transactions.first().notes)
    }

    @Test
    fun tabSeparatedReferencesAreKeptAsBankIdsOnly() {
        val parsed = parse("tab-separated.tsv")

        assertEquals(listOf("TX-88120", "TX-88121", "TX-88130", "TX-88140"), parsed.transactions.map { it.bankId })
        assertTrue(parsed.transactions.all { it.importedId.startsWith("tandem-import-") })
        assertEquals(4, parsed.transactions.map { it.importedId }.distinct().size)
    }

    @Test
    fun headerlessFileFindsDateAmountAndPayeeFromTheRows() {
        val layout = inspectStatementCsv(fixture("headerless.csv"))
        val parsed = parse("headerless.csv")

        assertEquals(listOf("Column 1", "Column 2", "Column 3", "Column 4", "Column 5"), layout.columns.map { it.name })
        assertEquals(StatementCsvColumnMapping(dateColumn = 0, amountColumn = 1, payeeColumn = 4), layout.suggestedMapping)
        assertEquals(listOf(-3_120L, -999L, 120_000L), parsed.transactions.map { it.amountMinor })
        assertEquals(listOf(1, 2, 3), parsed.transactions.map { it.rowNumber })
    }

    @Test
    fun cardExportThatShowsSpendingAsPositiveIsReadAsWritten() {
        val parsed = parse("card-spending-positive.csv")

        assertEquals(listOf(2_598L, 625L, -51_240L, 200L), parsed.transactions.map { it.amountMinor })
        assertEquals(listOf("AMZN MKTP US*2K4", "BLUE BOTTLE COFFEE", "AUTOPAY PAYMENT - THANK YOU", "FEE"), parsed.transactions.map { it.payeeName })
    }

    @Test
    fun debitCreditAmountColumnsSkipThePreambleAndTotalsFooter() {
        val parsed = parse("debit-credit-footer.csv")

        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-15"), parsed.transactions.map { it.date })
        assertEquals(listOf(-5_410L, 310_000L, -2_000L), parsed.transactions.map { it.amountMinor })
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun qifKeepsCheckNumbersAsEvidenceAndReadsQuickenDates() {
        val parsed = parse("bank.qif")

        assertEquals(listOf("2026-11-03", "2026-11-02", "2026-10-31", "2026-11-03"), parsed.transactions.map { it.date })
        assertEquals(listOf(-4_817L, 245_000L, -15_000L, -6_000L), parsed.transactions.map { it.amountMinor })
        assertEquals(listOf(null, null, "1042", null), parsed.transactions.map { it.bankId })
    }

    @Test
    fun ambiguousQifDatesFollowTheChosenOrder() {
        val dayFirst = parse("card.qif", dateOrder = StatementDateOrder.DAY_MONTH_YEAR)
        val monthFirst = parse("card.qif")

        assertEquals(listOf("2026-11-04", "2026-11-03", "2026-11-02"), dayFirst.transactions.map { it.date })
        assertEquals(listOf("2026-04-11", "2026-03-11", "2026-02-11"), monthFirst.transactions.map { it.date })
    }

    @Test
    fun sgmlOfxKeepsFitIdsOutOfImportedIds() {
        val parsed = parse("sgml-checking.ofx")

        assertEquals(6, parsed.transactions.size)
        assertEquals("AT&T WIRELESS", parsed.transactions.last().payeeName)
        assertEquals("202611010001", parsed.transactions.last().bankId)
        assertEquals(parsed.transactions[4].bankId, parsed.transactions[5].bankId)
        assertTrue(parsed.transactions.none { it.importedId == it.bankId })
        assertEquals(6, parsed.transactions.map { it.importedId }.distinct().size)
    }

    @Test
    fun xmlQfxCreditCardStatement() {
        val parsed = parse("xml-card.qfx")

        assertEquals(listOf("2026-11-04", "2026-11-03", "2026-11-02"), parsed.transactions.map { it.date })
        assertEquals(listOf(-2_598L, -625L, 51_240L), parsed.transactions.map { it.amountMinor })
        assertEquals("OAKLAND CA", parsed.transactions[1].notes)
        assertNull(parsed.transactions[0].notes)
    }

    @Test
    fun theSameStatementAlwaysGivesTheSameImportedIds() {
        listOf("us-checking-signed.csv", "bank.qif", "sgml-checking.ofx").forEach { name ->
            assertEquals(parse(name).transactions.map { it.importedId }, parse(name).transactions.map { it.importedId })
        }
    }

    @Test
    fun pdfsAndBinaryFilesAreRefusedWithAWayForward() {
        val pdf = assertThrows(StatementImportParseException::class.java) { detectStatementFormat("%PDF-1.7\n...") }
        assertTrue(pdf.message!!.contains("CSV"))
        assertThrows(StatementImportParseException::class.java) { detectStatementFormat("PK\u0003\u0004binary") }
    }

    private fun parse(
        name: String,
        dateOrder: StatementDateOrder = StatementDateOrder.MONTH_DAY_YEAR,
        numberFormat: String? = null,
    ) = parseStatementImport(name, fixture(name), dateOrder, 2, numberFormat)

    private fun fixture(name: String): String =
        decodeStatementBytes(
            checkNotNull(javaClass.classLoader?.getResourceAsStream("statements/$name")) { name }.use { it.readBytes() },
        )
}
