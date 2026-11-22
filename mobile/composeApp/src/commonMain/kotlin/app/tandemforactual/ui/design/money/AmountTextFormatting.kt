@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.money

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

internal fun amountAnnotatedString(
    value: String,
    fractionDigits: Int = 2,
): AnnotatedString {
    if (fractionDigits <= 0 || value.isEmpty()) return AnnotatedString(value)

    val fractionRanges =
        buildList {
            value.forEachIndexed { separatorIndex, separator ->
                if (separator !in AMOUNT_DECIMAL_SEPARATORS || separatorIndex == 0) return@forEachIndexed
                if (!value[separatorIndex - 1].isDigit()) return@forEachIndexed
                val firstFractionDigit = separatorIndex + 1
                val lastFractionDigit = firstFractionDigit + fractionDigits - 1
                if (lastFractionDigit >= value.length) return@forEachIndexed
                if (value.substring(firstFractionDigit, lastFractionDigit + 1).any { !it.isDigit() }) return@forEachIndexed
                if (lastFractionDigit + 1 < value.length && value[lastFractionDigit + 1].isDigit()) return@forEachIndexed
                if (lastFractionDigit + 1 < value.length && value[lastFractionDigit + 1] in AMOUNT_DECIMAL_SEPARATORS) return@forEachIndexed
                add(firstFractionDigit..lastFractionDigit)
            }
        }
    if (fractionRanges.isEmpty()) return AnnotatedString(value)

    return buildAnnotatedString {
        var nextIndex = 0
        fractionRanges.forEach { range ->
            append(value.substring(nextIndex, range.first))
            withStyle(SpanStyle(fontSize = 0.75.em)) {
                append(value.substring(range.first, range.last + 1))
            }
            nextIndex = range.last + 1
        }
        append(value.substring(nextIndex))
    }
}

private val AMOUNT_DECIMAL_SEPARATORS = setOf('.', ',', '\u066b', '\uff0e')
