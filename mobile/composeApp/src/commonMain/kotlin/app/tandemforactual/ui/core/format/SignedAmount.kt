package app.tandemforactual.ui.core.format

import app.tandemforactual.ui.core.format.formatAmount
import kotlin.math.absoluteValue

internal fun signedAmount(
    text: String,
    parseAmount: (String) -> Long?,
): Long? {
    val trimmed = text.trim()
    val negative = trimmed.startsWith("-") || trimmed.startsWith("−")
    val magnitude = parseAmount(trimmed.trimStart('-', '−').trim()) ?: return null
    return if (negative) -magnitude.absoluteValue else magnitude
}
