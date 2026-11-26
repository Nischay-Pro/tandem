package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JsMathRoundTest {
    @Test
    fun halvesRoundUpLikeJavaScript() {
        assertEquals(3.0, jsMathRound(2.5))
        assertEquals(1.0, jsMathRound(0.5))
        assertEquals(-2.0, jsMathRound(-2.5))
        assertEquals(-3.0, jsMathRound(-3.5))
        assertEquals(0.0, jsMathRound(-0.5) + 0.0)
        assertEquals(-3.0, jsMathRound(-2.6))
        assertEquals(2.0, jsMathRound(2.4))
        assertEquals(0.0, jsMathRound(0.49999999999999994))
        assertEquals(9.007199254740993E15, jsMathRound(9.007199254740993E15))
        assertEquals(Double.POSITIVE_INFINITY, jsMathRound(Double.POSITIVE_INFINITY))
    }

    @Test
    fun scheduleMidpointsRoundLikeActual() {
        fun range(
            low: Long,
            high: Long,
        ) = scheduledAmount(
            buildJsonObject {
                put("num1", low)
                put("num2", high)
            },
        )
        assertEquals(3L, range(2, 3))
        assertEquals(-3L, range(-4, -3))
        assertEquals(-2L, range(-3, -2))
    }

    @Test
    fun bankAmountsRoundHalfCentsUp() {
        assertEquals(13L, ActualBankSync.amountToInteger("0.125"))
        assertEquals(-12L, ActualBankSync.amountToInteger("-0.125"))
        assertEquals(1L, ActualBankSync.amountToInteger("0.005"))
    }
}
