package app.tandemforactual.ui.feature.activity

import kotlin.test.Test
import kotlin.test.assertEquals

class RunningBalanceTest {
    @Test
    fun eachRowShowsTheBalanceRightAfterIt() {
        assertEquals(listOf(1_000L, 1_200L, 700L), runningBalances(listOf(-200, 500, -300), 1_000))
        assertEquals(emptyList(), runningBalances(emptyList(), 1_000))
    }
}
