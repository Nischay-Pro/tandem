package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class FloatingEditorSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneEditorFillsTheWindow() {
        renderEditor()
        composeTestRule.onNodeWithTag(EDITOR_TAG).assertWidthIsEqualTo(411.dp)
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp")
    fun unfoldedEditorFloatsAsACentredDialog() {
        renderEditor()
        composeTestRule
            .onNodeWithTag(EDITOR_TAG)
            .assertWidthIsEqualTo(TandemLayout.editorDialogWidth)
            .assertHeightIsEqualTo(TandemLayout.editorDialogMaxHeight)
    }

    @Test
    @Config(qualifiers = "w1200dp-h800dp")
    fun landscapeTabletEditorKeepsTheDialogWidthAndFitsTheHeight() {
        renderEditor()
        composeTestRule.onNodeWithTag(EDITOR_TAG).assertWidthIsEqualTo(TandemLayout.editorDialogWidth)
        assertTrue(composeTestRule.onNodeWithTag(EDITOR_TAG).getBoundsInRoot().height < TandemLayout.editorDialogMaxHeight)
    }

    @Test
    fun dialogsMoveBelowATabletopFoldOnlyWhenTheyFit() {
        assertEquals(700, belowFoldOffset(boxHeight = 1_000, contentHeight = 200, spaceBelowFold = 400))
        assertEquals(600, belowFoldOffset(boxHeight = 1_000, contentHeight = 400, spaceBelowFold = 400))
        assertNull(belowFoldOffset(boxHeight = 1_000, contentHeight = 401, spaceBelowFold = 400))
        assertNull(belowFoldOffset(boxHeight = 1_000, contentHeight = 200, spaceBelowFold = 0))
        assertNull(belowFoldOffset(boxHeight = 300, contentHeight = 200, spaceBelowFold = 400))
    }

    private fun renderEditor() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                TandemTaskSheet(visible = true, onDismissRequest = {}, onDismissed = {}, fullScreen = true) {
                    Box(Modifier.fillMaxSize().testTag(EDITOR_TAG))
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val EDITOR_TAG = "editor"
    }
}
