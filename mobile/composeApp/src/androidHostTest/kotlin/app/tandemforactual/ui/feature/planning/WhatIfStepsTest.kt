package app.tandemforactual.ui.feature.planning

import kotlin.test.Test
import kotlin.test.assertEquals

class WhatIfStepsTest {
    @Test
    fun scalesToTheHouseholdsOwnMonth() {
        assertEquals(
            listOf(-100_000L, -50_000L, -25_000L, 0L, 25_000L, 50_000L, 100_000L),
            WhatIfSteps.steps(500_000, 300_000, "USD"),
        )
        assertEquals(
            listOf(-2_000_000L, -1_000_000L, -500_000L, 0L, 500_000L, 1_000_000L, 2_000_000L),
            WhatIfSteps.steps(8_000_000, 6_000_000, "INR"),
        )
    }

    @Test
    fun fallsBackToATypicalMonthForTheCurrency() {
        assertEquals(
            listOf(-5_000_000L, -2_500_000L, -1_000_000L, 0L, 1_000_000L, 2_500_000L, 5_000_000L),
            WhatIfSteps.steps(0, 0, "JPY"),
            "¥50,000 steps, in the hundredths Actual stores",
        )
        assertEquals(listOf(-50_000L, -25_000L, -10_000L, 0L, 10_000L, 25_000L, 50_000L), WhatIfSteps.steps(0, 0, null))
    }

    @Test
    fun roundsToAmountsPeopleType() {
        assertEquals(250.0, WhatIfSteps.nice(240.0))
        assertEquals(5_000.0, WhatIfSteps.nice(4_000.0))
        assertEquals(1.0, WhatIfSteps.nice(0.0))
    }
}
