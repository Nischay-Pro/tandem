package app.tandemforactual.ui.feature.onboarding

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyBudgetTest {
    @Test
    fun theEmptyBudgetIsActualsNewBudgetFile() {
        val bytes = File("src/commonMain/composeResources/$EMPTY_BUDGET").readBytes()
        assertEquals("SQLite format 3", bytes.copyOfRange(0, 15).decodeToString())
        val text = bytes.decodeToString()
        listOf("__migrations__", "category_groups", "payees", "Starting Balances").forEach { assertTrue(it in text, it) }
    }
}
