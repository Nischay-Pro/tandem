@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** English names for the countries European bank providers serve. */
internal fun countryName(code: String): String =
    mapOf(
        "GB" to "United Kingdom",
        "DE" to "Germany",
        "FR" to "France",
        "ES" to "Spain",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "BE" to "Belgium",
        "IE" to "Ireland",
        "AT" to "Austria",
        "PT" to "Portugal",
        "FI" to "Finland",
        "SE" to "Sweden",
        "DK" to "Denmark",
        "NO" to "Norway",
        "PL" to "Poland",
        "CZ" to "Czechia",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "HR" to "Croatia",
        "HU" to "Hungary",
        "RO" to "Romania",
        "BG" to "Bulgaria",
        "GR" to "Greece",
        "CY" to "Cyprus",
        "MT" to "Malta",
        "LU" to "Luxembourg",
        "EE" to "Estonia",
        "LV" to "Latvia",
        "LT" to "Lithuania",
        "IS" to "Iceland",
        "LI" to "Liechtenstein",
    )[code] ?: code
