@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.residence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.ui.core.format.formatShortMonth
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** A month typed as a year and picked from the twelve months, kept as yyyy-MM once the year is complete. */
@Composable
internal fun MonthField(
    label: String,
    year: String,
    month: Int,
    onYear: (String) -> Unit,
    onMonth: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(label, color = tandemPalette.muted, fontSize = TandemTypography.label)
        TextFieldCard(
            "Year",
            year,
            { onYear(it.filter(Char::isDigit).take(4)) },
            TextFieldOptions(placeholder = "e.g. 2021", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
        )
        PillGrid((1..12).map { it to monthName(it) }, month, onMonth, columns = 6)
    }
}

internal fun monthName(month: Int): String = formatShortMonth(2000, month).substringBefore(' ')

internal fun monthId(
    year: String,
    month: Int,
): String? = year.takeIf { it.length == 4 && it.toInt() in 1900..2100 }?.let { "$it-${month.toString().padStart(2, '0')}" }

internal fun monthText(monthId: String): String = formatShortMonth(monthId.take(4).toInt(), monthId.drop(5).toInt())

/** "Jan 2018 to Jun 2023" or "Since Jul 2023". */
internal fun spanText(period: JurisdictionPeriod): String =
    period.through?.let { "${monthText(period.from)} to ${monthText(it)}" } ?: "Since ${monthText(period.from)}"
