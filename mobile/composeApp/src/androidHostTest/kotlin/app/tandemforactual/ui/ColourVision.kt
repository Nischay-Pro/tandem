package app.tandemforactual.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** Full-severity colour vision deficiency simulation (Machado, Oliveira and Fernandes 2009). */
internal enum class ColourVision(
    private val matrix: List<List<Double>>?,
) {
    TYPICAL(null),
    DEUTERANOPIA(
        listOf(
            listOf(0.367322, 0.860646, -0.227968),
            listOf(0.280085, 0.672501, 0.047413),
            listOf(-0.011820, 0.042940, 0.968881),
        ),
    ),
    PROTANOPIA(
        listOf(
            listOf(0.152286, 1.052583, -0.204868),
            listOf(0.114503, 0.786281, 0.099216),
            listOf(-0.003882, -0.048116, 1.051998),
        ),
    ),
    ;

    fun simulate(color: Color): List<Double> {
        val linear = listOf(color.red, color.green, color.blue).map { toLinear(it.toDouble()) }
        val m = matrix ?: return linear
        return m.map { row -> (row[0] * linear[0] + row[1] * linear[1] + row[2] * linear[2]).coerceIn(0.0, 1.0) }
    }
}

/** CIE76 colour difference in CIELAB (D65) between two colours as seen with [vision]. */
internal fun deltaE(
    first: Color,
    second: Color,
    vision: ColourVision = ColourVision.TYPICAL,
): Double {
    val a = lab(vision.simulate(first))
    val b = lab(vision.simulate(second))
    return sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))
}

private fun toLinear(channel: Double): Double = if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

private fun lab(linear: List<Double>): List<Double> {
    val (r, g, b) = linear
    val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
    return listOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
}
