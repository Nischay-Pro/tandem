package app.tandemforactual.ui.feature.budget.grid

import kotlin.math.roundToLong

private const val OPERATORS = "+-*/()×÷−"

/**
 * An amount typed as a sum such as `120+35`, `3*40` or `(100-20)/2`, in minor units. Each number is read by [parse], so
 * it follows the budget's decimal and grouping separators; a plain number is just parsed. Null when it doesn't add up.
 */
internal fun evaluateAmount(
    text: String,
    parse: (String) -> Long?,
): Long? {
    val input = text.trim().removePrefix("=")
    if (input.isEmpty()) return null
    if (input.drop(1).none { it in OPERATORS }) return parse(input)
    val unit = parse("1")?.takeIf { it > 0 } ?: return null
    val tokens = tokenize(input, parse) ?: return null
    val parser = ExpressionParser(tokens, unit.toDouble())
    val value = parser.sum() ?: return null
    return if (parser.finished && value.isFinite()) value.roundToLong() else null
}

private sealed interface Token {
    data class Number(
        val minor: Double,
    ) : Token

    data class Symbol(
        val char: Char,
    ) : Token
}

private fun tokenize(
    input: String,
    parse: (String) -> Long?,
): List<Token>? {
    val tokens = mutableListOf<Token>()
    val number = StringBuilder()
    fun flush(): Boolean {
        val digits = number.toString().trim()
        number.clear()
        if (digits.isEmpty()) return true
        tokens += Token.Number(parse(digits)?.toDouble() ?: return false)
        return true
    }
    input.forEach { char ->
        if (char in OPERATORS) {
            if (!flush()) return null
            tokens += Token.Symbol(normalize(char))
        } else {
            number.append(char)
        }
    }
    return if (flush()) tokens else null
}

private fun normalize(char: Char): Char =
    when (char) {
        '×' -> '*'
        '÷' -> '/'
        '−' -> '-'
        else -> char
    }

/** Sums of products of signed numbers or bracketed sums, in minor units where one whole unit is [unit]. */
private class ExpressionParser(
    private val tokens: List<Token>,
    private val unit: Double,
) {
    private var index = 0

    val finished: Boolean get() = index == tokens.size

    fun sum(): Double? {
        var value = product() ?: return null
        while (true) {
            value =
                when (peek()) {
                    '+' -> value + (next().let { product() } ?: return null)
                    '-' -> value - (next().let { product() } ?: return null)
                    else -> return value
                }
        }
    }

    private fun product(): Double? {
        var value = factor() ?: return null
        while (true) {
            value =
                when (peek()) {
                    '*' -> value * (next().let { factor() } ?: return null) / unit
                    '/' -> {
                        next()
                        val divisor = factor()?.takeIf { it != 0.0 } ?: return null
                        value / divisor * unit
                    }
                    else -> return value
                }
        }
    }

    private fun factor(): Double? {
        val token = tokens.getOrNull(index) ?: return null
        index++
        return when {
            token is Token.Number -> token.minor
            token == Token.Symbol('-') -> factor()?.let { -it }
            token == Token.Symbol('+') -> factor()
            token == Token.Symbol('(') -> sum()?.takeIf { peek() == ')' }?.also { next() }
            else -> null
        }
    }

    private fun peek(): Char? = (tokens.getOrNull(index) as? Token.Symbol)?.char

    private fun next() {
        index++
    }
}
