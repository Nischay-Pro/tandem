package app.tandemforactual.ui.core.format

import java.util.Currency
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLocaleTest {
    @Test
    fun aChosenLanguageKeepsTheDeviceRegion() {
        assertEquals("te-GB", appLocale("te", Locale.UK).toLanguageTag())
        assertEquals("en-IN", appLocale("en", Locale.forLanguageTag("hi-IN")).toLanguageTag())
        assertEquals("EUR", Currency.getInstance(appLocale("en", Locale.GERMANY)).currencyCode)
    }

    @Test
    fun aChosenRegionOrARegionlessDeviceKeepsTheChoice() {
        assertEquals("en-US", appLocale("en-US", Locale.UK).toLanguageTag())
        assertEquals("te", appLocale("te", Locale.forLanguageTag("te")).toLanguageTag())
    }

    @Test
    fun deviceFormatPreferencesComeAlong() {
        assertEquals("te-GB-u-fw-mon", appLocale("te", Locale.forLanguageTag("en-GB-u-fw-mon")).toLanguageTag())
    }

    @Test
    fun britishEnglishDatesStayDayFirstWhenTheAppIsInEnglish() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(appLocale("en", Locale.UK))
            assertEquals("14 May 2026", platformFormatDate(2026, 5, 14, includeYear = true))
        } finally {
            Locale.setDefault(original)
        }
    }
}
