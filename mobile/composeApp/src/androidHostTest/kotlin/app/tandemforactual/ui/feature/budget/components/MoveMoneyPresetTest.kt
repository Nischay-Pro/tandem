package app.tandemforactual.ui.feature.budget.components

import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.MoveMoneyRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveMoneyPresetTest {
    private fun category(
        id: String,
        balance: Long,
    ) = LiveCategorySummary(id, id, 0, 0, balance)

    private val car = category("car", -13_600)
    private val categories = listOf(category("food", 4_000), car, category("fun", 22_000), category("gifts", -500))

    @Test
    fun coveringOverspendingDrawsOnTheCategoryWithTheMostLeft() {
        assertEquals(MoveMoneyPreset("fun", "car", 13_600), moveMoneyPreset(MoveMoneyRequest(13_600), car, categories))
    }

    @Test
    fun coveringWithNothingLeftAnywhereLeavesTheSourceToChoose() {
        assertEquals(MoveMoneyPreset("", "car", 13_600), moveMoneyPreset(MoveMoneyRequest(13_600), car, listOf(car)))
    }

    @Test
    fun aPlainMoveStartsFromTheCategoryWithNoAmount() {
        assertEquals(MoveMoneyPreset("car", "food", null), moveMoneyPreset(MoveMoneyRequest(0), car, categories))
    }
}
