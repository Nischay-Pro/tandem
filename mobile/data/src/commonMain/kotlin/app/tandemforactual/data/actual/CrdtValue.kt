package app.tandemforactual.data.actual

/** A cell value in Actual's sync messages: `0:` for null, `N:` for numbers and `S:` for strings. */
sealed class CrdtValue {
    data object Null : CrdtValue()

    data class Number(
        val value: Double,
    ) : CrdtValue()

    data class Text(
        val value: String,
    ) : CrdtValue()

    /** A value written by a newer Actual in a format this version can't read. */
    data class Unknown(
        val raw: String,
    ) : CrdtValue()

    fun serialize(): String =
        when (this) {
            Null -> "0:"
            is Number -> "N:" + formatNumber(value)
            is Text -> "S:$value"
            is Unknown -> raw
        }

    companion object {
        fun of(value: Any?): CrdtValue =
            when (value) {
                null -> Null
                is Boolean -> Number(if (value) 1.0 else 0.0)
                is kotlin.Number -> Number(value.toDouble())
                is String -> Text(value)
                else -> throw IllegalArgumentException("unsupported value $value")
            }

        fun deserialize(value: String): CrdtValue {
            if (value.length >= 2 && value[1] == ':') {
                when (value[0]) {
                    '0' -> return Null
                    'N' -> return parseJsNumber(value.substring(2))?.let(::Number) ?: Unknown(value)
                    'S' -> return Text(value.substring(2))
                }
            }
            return Unknown(value)
        }
    }
}

/** Formats a number the way JavaScript's `String(number)` does for the values Actual stores. */
internal fun formatNumber(value: Double): String =
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e21) {
        value.toLong().toString()
    } else {
        value.toString()
    }

/** JavaScript's `parseFloat`: the longest leading decimal number, or null. */
private fun parseJsNumber(text: String): Double? {
    val match = Regex("""^\s*[+-]?(Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)""").find(text) ?: return null
    val number = match.value.trim()
    return when (number.trimStart('+', '-')) {
        "Infinity" -> if (number.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        else -> number.toDouble()
    }
}
