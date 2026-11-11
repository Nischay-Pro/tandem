package app.tandemforactual.ui

import app.tandemforactual.api.client.StatementImportSummary
import app.tandemforactual.data.store.BudgetDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StatementImportTest {
    @Test
    fun previewSkipsStatementRowsBefore1995AndAllowsFarFutureDates() {
        val parsed =
            parseStatementImport(
                filename = "date-bounds.csv",
                contents =
                    "Date,Payee,Amount\n" +
                        "2036-11-10,At the limit,1.00\n" +
                        "2036-11-11,Future,2.00\n" +
                        "2062-03-04,Far future,3.00\n" +
                        "1994-12-31,Too old,3.00\n",
            )

        assertEquals(
            listOf("2036-11-10", "2036-11-11", "2062-03-04"),
            parsed.transactions.map { it.date },
        )
        assertEquals(1, parsed.skippedRows)
        assertEquals(
            "Choose a date on or after ${displayTransactionDate("1995-01-01")}.",
            parsed.firstSkippedReason,
        )
        assertEquals(
            parsed.transactions.map { it.date },
            parsed.toStatementImportItems().map { it.date },
        )
    }

    @Test
    fun statementPreviewAndImportItemsKeepTheSameMinorUnitsAcrossCurrencyScales() {
        val cases =
            listOf(
                Triple(
                    parseStatementImport(
                        "yen.csv",
                        "Date,Payee,Amount\n11/01/2026,Market,1500\n",
                        currencyFractionDigits = 0,
                        numberFormat = "comma-dot",
                    ),
                    displaySettings("JPY", "¥", 0),
                    listOf(1_500L),
                ),
                Triple(
                    parseStatementImport(
                        "dollar.csv",
                        "Date,Payee,Amount\n11/01/2026,Market,12.34\n",
                        currencyFractionDigits = 2,
                        numberFormat = "comma-dot",
                    ),
                    displaySettings("USD", "$", 2),
                    listOf(1_234L),
                ),
                Triple(
                    parseStatementImport(
                        "dinar.csv",
                        "Date,Payee,Amount\n11/01/2026,Market,12.345\n",
                        currencyFractionDigits = 3,
                        numberFormat = "comma-dot",
                    ),
                    displaySettings("KWD", "KD", 3),
                    listOf(12_345L),
                ),
            )

        cases.forEach { (parsed, settings, expectedAmounts) ->
            val preview = parsed.transactions.map { row -> formatCurrency(row.amountMinor, settings) }
            val importItems = parsed.toStatementImportItems()

            assertEquals(expectedAmounts, parsed.transactions.map { it.amountMinor })
            assertEquals(parsed.transactions.map { it.amountMinor }, importItems.map { it.amountMinor })
            assertEquals(preview, importItems.map { formatCurrency(it.amountMinor, settings) })
        }
    }

    @Test
    fun parsesZeroDecimalCurrencyAndUsesTheSameAmountInThePreview() {
        val parsed =
            parseStatementImport(
                "yen.csv",
                "Date,Payee,Amount\n" +
                    "11/01/2026,Market,1500\n" +
                    "11/02/2026,Refund,\"-2,500\"\n",
                currencyFractionDigits = 0,
                numberFormat = "comma-dot",
            )

        assertEquals(listOf(1_500L, -2_500L), parsed.transactions.map { it.amountMinor })
        val yenSettings =
            BudgetDisplaySettings(
                currencyCode = "JPY",
                currencySymbol = "¥",
                currencyFractionDigits = 0,
                numberFormat = "comma-dot",
                hideFraction = false,
                currencySymbolPosition = "before",
                currencySpaceBetweenAmountAndSymbol = false,
            )
        assertEquals("¥1,500", formatCurrency(parsed.transactions.first().amountMinor, yenSettings))
    }

    @Test
    fun parsesThreeDecimalAmountsInCsvQifAndOfx() {
        val csv =
            parseStatementImport(
                "three-decimal.csv",
                "Date,Payee,Amount\n" +
                    "11/01/2026,Cafe,1.234\n" +
                    "11/02/2026,Market,-23.5\n",
                currencyFractionDigits = 3,
                numberFormat = "comma-dot",
            )
        assertEquals(listOf(1_234L, -23_500L), csv.transactions.map { it.amountMinor })
        val threeDecimalSettings =
            BudgetDisplaySettings(
                currencyCode = "KWD",
                currencySymbol = "KD",
                currencyFractionDigits = 3,
                numberFormat = "comma-dot",
                hideFraction = false,
                currencySymbolPosition = "before",
                currencySpaceBetweenAmountAndSymbol = false,
            )
        assertEquals("KD1.234", formatCurrency(csv.transactions.first().amountMinor, threeDecimalSettings))

        assertThrows(StatementImportParseException::class.java) {
            parseStatementImport(
                "grouped.csv",
                "Date,Payee,Amount\n11/01/2026,Market,\"1,234\"\n",
                currencyFractionDigits = 3,
                numberFormat = "comma-dot",
            )
        }

        val european =
            parseStatementImport(
                "three-decimal-europe.csv",
                "Date;Payee;Amount\n11/01/2026;Market;1,234\n11/02/2026;Cafe;-2,5\n",
                currencyFractionDigits = 3,
                numberFormat = "dot-comma",
            )
        assertEquals(listOf(1_234L, -2_500L), european.transactions.map { it.amountMinor })

        val qif =
            parseStatementImport(
                "three-decimal.qif",
                "!Type:Bank\nD11/03/2026\nT-12.345\nPShop\n^\n",
                currencyFractionDigits = 3,
                numberFormat = "comma-dot",
            )
        assertEquals(-12_345L, qif.transactions.single().amountMinor)

        val ofx =
            parseStatementImport(
                "three-decimal.ofx",
                """<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261104<TRNAMT>6.789<FITID>ofx-3<NAME>Shop</STMTTRN></BANKTRANLIST></OFX>""",
                currencyFractionDigits = 3,
                numberFormat = "comma-dot",
            )
        assertEquals(6_789L, ofx.transactions.single().amountMinor)
    }

    @Test
    fun parsesCsvWithQuotedPayeesIdsAndDebitCreditColumns() {
        val parsed =
            parseStatementImport(
                "bank.csv",
                "Date,Payee,Debit,Credit,FitId,Memo\r\n" +
                    "11/01/2026,\"Market, Main St.\",12.34,,row-1,Groceries\r\n" +
                    "11/02/2026,Employer,,\"2,500.00\",row-2,Paycheck\r\n",
            )

        assertEquals(2, parsed.transactions.size)
        assertEquals(0, parsed.skippedRows)
        assertEquals("2026-11-01", parsed.transactions[0].date)
        assertEquals(-1_234L, parsed.transactions[0].amountMinor)
        assertEquals("Market, Main St.", parsed.transactions[0].payeeName)
        assertEquals("row-1", parsed.transactions[0].importedId)
        assertEquals("2026-11-02", parsed.transactions[1].date)
        assertEquals(250_000L, parsed.transactions[1].amountMinor)
    }

    @Test
    fun handlesInternationalDatesAndEuropeanDecimalFormatting() {
        val parsed =
            parseStatementImport(
                "bank.csv",
                "Date;Description;Amount\n" +
                    "31/10/2026;Pet clinic;−1.234,56\n",
                dateOrder = StatementDateOrder.DAY_MONTH_YEAR,
            )

        assertEquals("2026-10-31", parsed.transactions.single().date)
        assertEquals(-123_456L, parsed.transactions.single().amountMinor)

        val withCurrencyCode =
            parseStatementImport(
                "bank.csv",
                "Date,Description,Amount\n" +
                    "10/31/2026,Household purchase,\"CAD -1,234.56\"\n",
            )
        assertEquals(-123_456L, withCurrencyCode.transactions.single().amountMinor)
    }

    @Test
    fun readsDottedMonthNameAndYearFirstDatesAndReportsTheExactBadDate() {
        val parsed =
            parseStatementImport(
                "dates.csv",
                "Date,Payee,Amount\n" +
                    "31.10.2026,Market,1.00\n" +
                    "03 Nov 2026,Transit,2.00\n" +
                    "\"Nov 3, 2026\",Cafe,3.00\n" +
                    "2026/11/04,Pharmacy,4.00\n",
                dateOrder = StatementDateOrder.DAY_MONTH_YEAR,
            )

        assertEquals(listOf("2026-10-31", "2026-11-03", "2026-11-03", "2026-11-04"), parsed.transactions.map { it.date })

        val error =
            assertThrows(StatementImportParseException::class.java) {
                parseStatementImport("bad-date.csv", "Date,Payee,Amount\nnot-a-date,Market,1.00\n")
            }
        assertEquals("Row 2: Couldn’t read the date “not-a-date”. Check the selected date format.", error.message)
    }

    @Test
    fun repeatedReferenceValuesFallBackToDistinctStableImportIds() {
        val statement =
            "Date,Payee,Amount,Reference\n" +
                "11/01/2026,Market,12.00,statement_1\n" +
                "11/01/2026,Market,12.00,statement_1\n"
        val first = parseStatementImport("references.csv", statement).transactions.map { it.importedId }
        val retry = parseStatementImport("references.csv", statement).transactions.map { it.importedId }

        assertEquals(2, first.distinct().size)
        assertNotEquals("statement_1", first[0])
        assertEquals(first, retry)
    }

    @Test
    fun parsesDebitSuffixesAndSkipsAmbiguousThreeDigitDecimals() {
        val parsed =
            parseStatementImport(
                "amounts.csv",
                "Date,Payee,Amount\n" +
                    "11/01/2026,Market,\"1,234.56-\"\n" +
                    "11/02/2026,Pet Clinic,12.34 DR\n" +
                    "11/03/2026,Refund,12.34 CR\n" +
                    "11/04/2026,Unclear,12.345\n",
            )

        assertEquals(listOf(-123_456L, -1_234L, 1_234L), parsed.transactions.map { it.amountMinor })
        assertEquals(1, parsed.skippedRows)
        assertEquals("Row 5: Couldn’t read the amount “12.345”. Check its decimal places.", parsed.firstSkippedReason)
    }

    @Test
    fun reportsTheFirstBadStatementRowAndHowToCorrectItsAmount() {
        val parsed =
            parseStatementImport(
                "amounts.csv",
                "Date,Payee,Amount\n" +
                    "11/01/2026,Market,\"1,2x\"\n" +
                    "11/02/2026,Cafe,12.34\n",
            )

        assertEquals(1, parsed.skippedRows)
        assertEquals("Row 2: Couldn’t read the amount “1,2x”. Use a number such as 12.34.", parsed.firstSkippedReason)
    }

    @Test
    fun hidesUnexpectedParserDetailsBehindRowSpecificGuidance() {
        assertEquals(
            "Row 12: Couldn’t read this transaction. Check its date and amount.",
            statementImportRowFailure(12, IllegalArgumentException("database stack trace: private detail")),
        )
    }

    @Test
    fun decodesWindows1252AndFormatsImportCountsNaturally() {
        assertEquals("Café", decodeStatementBytes(byteArrayOf(67, 97, 102, 0xE9.toByte())))
        assertEquals("Café", decodeStatementBytes("Café".encodeToByteArray()))
        assertEquals("Imported 1 transaction. 1 row needs review in Actual.", formatStatementImportSummary(StatementImportSummary(1, 0, 1)))
        assertEquals("Imported 2 transactions.", formatStatementImportSummary(StatementImportSummary(2, 0, 0)))
    }

    @Test
    fun parsesQifAndOfxTransactions() {
        val qif =
            parseStatementImport(
                "account.qif",
                "!Type:Bank\nD11/03/2026\nT-42.18\nPCorner Market\nMGroceries\n^\n",
            )
        assertEquals("2026-11-03", qif.transactions.single().date)
        assertEquals(-4_218L, qif.transactions.single().amountMinor)
        assertEquals("Corner Market", qif.transactions.single().payeeName)

        val ofx =
            parseStatementImport(
                "account.ofx",
                """<OFX><BANKTRANLIST><STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261104<TRNAMT>-18.50<FITID>ofx-1<NAME>Pet Clinic<MEMO>Medication</STMTTRN></BANKTRANLIST></OFX>""",
            )
        assertEquals("2026-11-04", ofx.transactions.single().date)
        assertEquals(-1_850L, ofx.transactions.single().amountMinor)
        assertEquals("ofx-1", ofx.transactions.single().importedId)
        assertEquals("Medication", ofx.transactions.single().notes)
    }

    @Test
    fun generatedIdsStayStableAcrossOverlappingStatementsAndIgnoreRowOrder() {
        val first =
            parseStatementImport(
                "first.csv",
                "Date,Amount,Payee\n" +
                    "11/01/2026,-12.00,Target\n" +
                    "11/02/2026,-5.00,Market\n" +
                    "11/02/2026,-5.00,Market\n",
            )
        val overlapping =
            parseStatementImport(
                "overlap.csv",
                "Date,Amount,Payee\n" +
                    "11/03/2026,-8.00,Coffee Shop\n" +
                    "11/02/2026,-5.00,Market\n" +
                    "11/01/2026,-12.00,Target\n" +
                    "11/02/2026,-5.00,Market\n",
            )

        val firstIds = first.transactions.associate { it.payeeName to it.importedId }
        val overlappingIds = overlapping.transactions.associate { it.payeeName to it.importedId }
        assertEquals(firstIds["Target"], overlappingIds["Target"])
        assertEquals(firstIds["Market"], overlappingIds["Market"])
        assertEquals(
            3,
            first.transactions
                .map { it.importedId }
                .distinct()
                .size,
        )
        assertEquals(
            4,
            overlapping.transactions
                .map { it.importedId }
                .distinct()
                .size,
        )
        assertEquals(
            first.transactions.filter { it.payeeName == "Market" }.map { it.importedId },
            overlapping.transactions.filter { it.payeeName == "Market" }.map { it.importedId },
        )
    }

    @Test
    fun rejectsUnknownFormatsAndAmbiguousCsvStructure() {
        assertThrows(StatementImportParseException::class.java) {
            parseStatementImport("statement.txt", "not a statement")
        }
        assertThrows(StatementImportParseException::class.java) {
            parseStatementImport("statement.csv", "Merchant,Amount\nMarket,12.00")
        }
    }

    private fun displaySettings(
        currencyCode: String,
        currencySymbol: String,
        currencyFractionDigits: Int,
    ) = BudgetDisplaySettings(
        currencyCode = currencyCode,
        currencySymbol = currencySymbol,
        currencyFractionDigits = currencyFractionDigits,
        numberFormat = "comma-dot",
        hideFraction = false,
        currencySymbolPosition = "before",
        currencySpaceBetweenAmountAndSymbol = false,
    )
}
