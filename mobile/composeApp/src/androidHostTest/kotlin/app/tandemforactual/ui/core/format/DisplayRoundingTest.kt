package app.tandemforactual.ui.core.format

import app.tandemforactual.data.store.BudgetDisplaySettings
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayRoundingTest {
    private val usd =
        BudgetDisplaySettings(
            currencyCode = "USD",
            currencySymbol = "$",
            currencyFractionDigits = 2,
            numberFormat = "comma-dot",
            hideFraction = false,
            currencySymbolPosition = "before",
            currencySpaceBetweenAmountAndSymbol = false,
        )
    private val hidden = usd.copy(hideFraction = true)
    private val yen = usd.copy(currencyCode = "JPY", currencySymbol = "¥", currencyFractionDigits = 0)

    @Test
    fun roundsHalfAwayFromZeroLikeActual() {
        assertEquals(300L, roundForDisplay(250, 0))
        assertEquals(-300L, roundForDisplay(-250, 0))
        assertEquals(200L, roundForDisplay(249, 0))
        assertEquals(-200L, roundForDisplay(-249, 0))
        assertEquals(0L, roundForDisplay(-49, 0))
        assertEquals(-100L, roundForDisplay(-50, 0))
        assertEquals(130L, roundForDisplay(125, 1))
        assertEquals(-130L, roundForDisplay(-125, 1))
        assertEquals(1_234L, roundForDisplay(1_234, 2))
        assertEquals(1_234L, roundForDisplay(1_234, 3))
    }

    @Test
    fun hiddenFractionsRoundHalfAwayFromZeroAndNeverShowNegativeZero() {
        withLocale(Locale.US) {
            assertEquals("$3", platformFormatCurrency(250, hidden))
            assertEquals("-$3", platformFormatCurrency(-250, hidden))
            assertEquals("$0", platformFormatCurrency(-40, hidden))
            assertEquals("-$1", platformFormatCurrency(-50, hidden))
            assertEquals("$0.00", platformFormatCurrency(0, usd))
            assertEquals("¥0", platformFormatCurrency(-49, yen))
            assertEquals("-¥1", platformFormatCurrency(-50, yen))
            assertEquals("1,501", platformFormatDecimal(150_050, yen))
            assertEquals("0", platformFormatDecimal(-40, yen))
        }
    }

    @Test
    fun signedAmountsOnlyShowAPlusWhenTheShownAmountIsNotZero() {
        withLocale(Locale.US) {
            assertEquals("$0", formatSignedCurrency(40, hidden))
            assertEquals("+$3", formatSignedCurrency(250, hidden))
            assertEquals("+$0.40", formatSignedCurrency(40, usd))
            assertEquals("$0.00", formatSignedCurrency(0, usd))
            assertEquals("¥0", formatSignedCurrency(49, yen))
        }
    }

    @Test
    fun reportAndPhoneCurrencyRoundTheSameWay() {
        withLocale(Locale.US) {
            assertEquals("$3", platformFormatReportCurrency(250, "USD", 0))
            assertEquals("-$3", platformFormatReportCurrency(-250, "USD", 0))
            assertFalse(platformFormatReportCurrency(-40, "USD", 0).contains("-"))
        }
        withLocale(Locale.JAPAN) {
            val phoneYen = platformFormatCurrency(-40, null)
            assertFalse(phoneYen.contains("-"), phoneYen)
            assertTrue(platformFormatCurrency(250, null).contains("3"))
        }
    }

    @Test
    fun previousDayFollowsTheCalendar() {
        assertEquals("2026-11-25", previousIsoDate("2026-11-26"))
        assertEquals("2026-10-31", previousIsoDate("2026-11-01"))
        assertEquals("2024-02-29", previousIsoDate("2024-03-01"))
        assertEquals("2023-02-28", previousIsoDate("2023-03-01"))
        assertEquals("2025-12-31", previousIsoDate("2026-01-01"))
        assertNull(previousIsoDate("not a date"))
    }

    @Test
    fun yesterdayIsTheCalendarDayBeforeEvenAcrossADaylightSavingChange() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val now = Instant.parse("2026-03-09T04:30:00Z").toEpochMilli()
            val dayBefore = Instant.parse("2026-03-08T15:00:00Z").toEpochMilli()
            val twoDaysBefore = Instant.parse("2026-03-07T15:00:00Z").toEpochMilli()
            assertTrue(formatMomentAtEpochMilliseconds(dayBefore, now).startsWith("yesterday at "))
            assertFalse(formatMomentAtEpochMilliseconds(twoDaysBefore, now).startsWith("yesterday"))
            assertTrue(formatMomentAtEpochMilliseconds(now - 60_000L, now).startsWith("today at "))
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    private inline fun withLocale(
        locale: Locale,
        block: () -> Unit,
    ) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
