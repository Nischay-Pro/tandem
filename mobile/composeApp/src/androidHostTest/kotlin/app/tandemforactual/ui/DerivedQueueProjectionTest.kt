package app.tandemforactual.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.tandemforactual.ui.app.shell.rememberDerived
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DerivedQueueProjectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun projectionsRunOnlyWhenTheStateTheyReadChanges() {
        val queue = mutableStateOf(listOf("rename"))
        val unrelated = mutableIntStateOf(0)
        var runs = 0
        var compositions = 0
        var pending = 0
        composeTestRule.setContent {
            unrelated.intValue
            SideEffect { compositions++ }
            pending =
                rememberDerived(queue) {
                    runs++
                    queue.value.size
                }
        }
        repeat(5) { composeTestRule.runOnIdle { unrelated.intValue++ } }
        composeTestRule.waitForIdle()
        assertTrue(compositions >= 6, "compositions: $compositions")
        assertEquals(1, runs)

        composeTestRule.runOnIdle { queue.value = listOf("rename", "hide") }
        composeTestRule.waitForIdle()
        assertEquals(2, runs)
        assertEquals(2, pending)
    }
}
