package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.model.LiveAccountSummary
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTimeZoneCopyDefault
import platform.CoreFoundation.CFTimeZoneCreateWithName
import platform.CoreFoundation.CFTimeZoneSetDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosLocaleFormattingTest {
    @Test
    fun cachedIosFormattersPreserveBudgetCurrencyAndNumberRules() {
        val settings =
            BudgetDisplaySettings(
                currencyCode = "USD",
                currencySymbol = "$",
                currencyFractionDigits = 2,
                numberFormat = "comma-dot",
                hideFraction = false,
                currencySymbolPosition = "before",
                currencySpaceBetweenAmountAndSymbol = false,
            )

        repeat(20) {
            assertEquals("$1,234.56", platformFormatCurrency(123_456, settings))
            assertEquals("1,234.56", platformFormatDecimal(123_456, settings))
            assertEquals(123_456L, platformParseDecimalMinorUnits("1,234.56", settings))
        }
    }

    @Test
    fun amountParserRejectsSwappedMarksAndMalformedGroupingForEveryBudgetFormat() {
        val formats =
            listOf(
                "comma-dot" to ("12,50" to "12,34.56"),
                "dot-comma" to ("12.50" to "12.34,56"),
                "space-comma" to ("12.50" to "12\u202f34,56"),
                "apostrophe-dot" to ("12,50" to "12’34.56"),
                "comma-dot-in" to ("12,50" to "123,45,678.89"),
            )

        formats.forEach { (numberFormat, invalidInputs) ->
            val settings = settings(numberFormat)
            val formatted = platformFormatDecimal(123_456_789L, settings)
            assertEquals(123_456_789L, platformParseDecimalMinorUnits(formatted, settings), numberFormat)
            assertNull(platformParseDecimalMinorUnits(invalidInputs.first, settings), "$numberFormat decimal mark")
            assertNull(platformParseDecimalMinorUnits(invalidInputs.second, settings), "$numberFormat grouping")
            if (numberFormat == "comma-dot-in") {
                assertEquals(123_456_789L, platformParseDecimalMinorUnits("1,234,567.89", settings), "western grouping")
            }
        }
    }

    @Test
    fun transactionAmountsRejectKeyboardDecimalMarksThatConflictWithBudgetFormat() {
        val dotBudget = settings("comma-dot")
        val commaBudget = settings("dot-comma")

        assertEquals(1_250L, platformParseDecimalMinorUnits("12.50", dotBudget))
        assertNull(platformParseDecimalMinorUnits("12,50", dotBudget))
        assertNull(platformParseDecimalMinorUnits("1,5", dotBudget))
        assertEquals(
            "Use a period for decimals in this budget, such as 12.00.",
            transactionAmountInputError("1,5", dotBudget),
        )

        assertEquals(1_250L, platformParseDecimalMinorUnits("12,50", commaBudget))
        assertNull(platformParseDecimalMinorUnits("12.50", commaBudget))
        assertEquals(
            "Use a comma for decimals in this budget, such as 12,00.",
            transactionAmountInputError("12.50", commaBudget),
        )
    }

    @Test
    fun amountParserUsesZeroTwoAndThreeDigitCurrenciesOnIos() {
        val yen = settings("comma-dot", fractionDigits = 0).copy(currencyCode = "JPY", currencySymbol = "¥")
        val usd = settings("comma-dot")
        val threeDigitUs = settings("comma-dot", fractionDigits = 3)
        val threeDigitGerman = settings("dot-comma", fractionDigits = 3)

        assertEquals(1_500L, platformParseDecimalMinorUnits("1,500", yen))
        assertNull(platformParseDecimalMinorUnits("1.50", yen))
        assertEquals(1_250L, platformParseDecimalMinorUnits("12.50", usd))
        assertNull(platformParseDecimalMinorUnits("12,50", usd))
        assertEquals(12_345L, platformParseDecimalMinorUnits("12.345", threeDigitUs))
        assertNull(platformParseDecimalMinorUnits("12,345", threeDigitUs))
        assertEquals(12_345L, platformParseDecimalMinorUnits("12,345", threeDigitGerman))
        assertNull(platformParseDecimalMinorUnits("12.345", threeDigitGerman))
    }

    @Test
    fun amountParserHandlesAllNumberFormatsAtZeroTwoAndThreeFractionDigits() {
        val formats =
            listOf(
                Triple("comma-dot", '.', ','),
                Triple("dot-comma", ',', '.'),
                Triple("space-comma", ',', '\u202f'),
                Triple("apostrophe-dot", '.', '\u2019'),
                Triple("comma-dot-in", '.', ','),
            )

        formats.forEach { (numberFormat, decimalMark, groupingMark) ->
            listOf(0, 2, 3).forEach { fractionDigits ->
                val displaySettings = settings(numberFormat, fractionDigits)
                val groupedInteger = "1${groupingMark}234${groupingMark}567"
                val malformedGrouping = "1234${groupingMark}567"
                val expectedGroupedMinorUnits = "1234567${"0".repeat(fractionDigits)}".toLong()

                assertEquals(
                    expectedGroupedMinorUnits,
                    platformParseDecimalMinorUnits(groupedInteger, displaySettings),
                    "$numberFormat/$fractionDigits grouped integer",
                )
                assertNull(
                    platformParseDecimalMinorUnits(malformedGrouping, displaySettings),
                    "$numberFormat/$fractionDigits malformed grouping",
                )
                assertEquals(
                    123_456_789L,
                    platformParseDecimalMinorUnits(platformFormatDecimal(123_456_789L, displaySettings), displaySettings),
                    "$numberFormat/$fractionDigits formatted round trip",
                )

                if (fractionDigits == 0) {
                    assertNull(
                        platformParseDecimalMinorUnits("12${decimalMark}34", displaySettings),
                        "$numberFormat rejects decimal mark for whole currency",
                    )
                } else {
                    val fractionalDigits = "123".take(fractionDigits)
                    val validAmount = "12${decimalMark}$fractionalDigits"
                    val expectedMinorUnits = "12$fractionalDigits".toLong()
                    val groupingMarkAsDecimal =
                        if (fractionDigits == 3 && groupingMark in setOf('.', ',')) {
                            "12${groupingMark}$fractionalDigits"
                        } else {
                            "12${groupingMark}${fractionalDigits.take(2)}"
                        }
                    assertEquals(
                        expectedMinorUnits,
                        platformParseDecimalMinorUnits(validAmount, displaySettings),
                        "$numberFormat/$fractionDigits typed decimal",
                    )
                    assertEquals(
                        "1234567$fractionalDigits".toLong(),
                        platformParseDecimalMinorUnits(
                            "1${groupingMark}234${groupingMark}567${decimalMark}$fractionalDigits",
                            displaySettings,
                        ),
                        "$numberFormat/$fractionDigits typed grouping and decimal",
                    )
                    assertNull(
                        platformParseDecimalMinorUnits(groupingMarkAsDecimal, displaySettings),
                        "$numberFormat/$fractionDigits grouping mark is not a decimal",
                    )
                    assertNull(
                        platformParseDecimalMinorUnits("12${decimalMark}${fractionalDigits}0", displaySettings),
                        "$numberFormat/$fractionDigits excess fractional digit",
                    )
                    assertNull(
                        platformParseDecimalMinorUnits("1234${groupingMark}567${decimalMark}$fractionalDigits", displaySettings),
                        "$numberFormat/$fractionDigits malformed grouped decimal",
                    )
                }
            }
        }
    }

    @Test
    fun invalidAmountGuidanceAndConfirmationUseTheIosBudgetFormat() {
        val german = settings("dot-comma")
        val yen =
            german.copy(
                currencyCode = "JPY",
                currencySymbol = "¥",
                currencyFractionDigits = 0,
            )

        assertEquals("Use a comma for decimals in this budget, such as 12,00.", transactionAmountInputError("12.50", german))
        assertEquals("This budget doesn’t use decimals. Enter a whole amount, such as 12.", transactionAmountInputError("12.50", yen))
        assertTrue(transactionAmountNeedsFormatConfirmation("1234,56", 123_456, german))
        assertFalse(transactionAmountNeedsFormatConfirmation("1.234,56", 123_456, german))
        assertEquals("1.234,56", platformFormatDecimal(123_456, german))
    }

    @Test
    fun iosDateAndReportFormattersReturnStableNonemptyValues() {
        val month = platformFormatMonth(2026, 11, abbreviated = false)
        assertFalse(month.isBlank())
        assertEquals(month, platformFormatMonth(2026, 11, abbreviated = false))
        assertEquals(platformFormatDate(2026, 11, 7, includeYear = true), platformFormatDate(2026, 11, 7, includeYear = true))
        assertFalse(platformFormatDateAtEpochMilliseconds(1_794_087_360_000).isBlank())
        assertFalse(platformFormatReportPercentage(0.125).isBlank())
        assertFalse(platformFormatReportCurrency(123_456, "USD", 2).isBlank())
    }

    @Test
    fun transactionDetailsAndListsShareTheSystemDateFormatter() {
        val isoDate = "2026-11-07"
        val localizedDate = formatLiveDate(isoDate)

        assertEquals(localizedDate, displayTransactionDate(isoDate))
        assertEquals("Today", formatTransactionListDate(isoDate, isoDate))
        assertFalse(localizedDate.isBlank())
    }

    @Test
    fun cachedEpochDateFormatterTracksTheCurrentTimeZoneAcrossUtcMidnight() {
        val previousTimeZone = CFTimeZoneCopyDefault()
        val instant = 1_767_227_400_000L // 2026-01-01T00:30:00Z
        try {
            setDefaultTimeZone("America/Los_Angeles")
            val westCoastDate = platformFormatDateAtEpochMilliseconds(instant)

            setDefaultTimeZone("Pacific/Kiritimati")
            val eastDate = platformFormatDateAtEpochMilliseconds(instant)

            assertNotEquals(westCoastDate, eastDate)
        } finally {
            CFTimeZoneSetDefault(previousTimeZone)
            CFRelease(previousTimeZone)
        }
    }

    @Test
    fun cachedIsoDateFormatterTracksTheLocalDayAcrossUtcMidnight() {
        val previousTimeZone = CFTimeZoneCopyDefault()
        val instant = 1_767_227_400_000L // 2026-01-01T00:30:00Z
        try {
            setDefaultTimeZone("America/Los_Angeles")
            assertEquals("2025-12-31", platformIsoDateAtEpochMilliseconds(instant))

            setDefaultTimeZone("Pacific/Kiritimati")
            assertEquals("2026-01-01", platformIsoDateAtEpochMilliseconds(instant))
        } finally {
            CFTimeZoneSetDefault(previousTimeZone)
            CFRelease(previousTimeZone)
        }
    }

    private fun setDefaultTimeZone(identifier: String) {
        val name = CFStringCreateWithCString(null, identifier, kCFStringEncodingUTF8)!!
        val timeZone = CFTimeZoneCreateWithName(null, name, true)!!
        CFTimeZoneSetDefault(timeZone)
        CFRelease(timeZone)
        CFRelease(name)
    }

    @Test
    fun accountCardKeepsItsSyncSubtitleOnIos() {
        val checking = LiveAccountSummary("checking", "Checking", 12_300, "checking", offBudget = false)
        val closed = LiveAccountSummary("closed", "Closed", 0, "savings", offBudget = false, closed = true)
        val offBudget = LiveAccountSummary("tracking", "Tracking", 4_500, "investment", offBudget = true)

        assertTrue(accountListSubtitle(checking, 1_794_087_360_000)?.startsWith("Last synced ") == true)
        assertEquals("Not synced yet", accountListSubtitle(checking, null))
        assertEquals(
            "Off budget · Last synced ${formatDateAtEpochMilliseconds(1_794_087_360_000)}",
            accountListSubtitle(offBudget, 1_794_087_360_000),
        )
        assertNull(accountListSubtitle(closed, 1_794_087_360_000))
    }

    private fun settings(
        numberFormat: String,
        fractionDigits: Int = 2,
    ) = BudgetDisplaySettings(
        currencyCode = "USD",
        currencySymbol = "$",
        currencyFractionDigits = fractionDigits,
        numberFormat = numberFormat,
        hideFraction = false,
        currencySymbolPosition = "before",
        currencySpaceBetweenAmountAndSymbol = false,
    )
}
