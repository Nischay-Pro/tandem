package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.sync.ConflictCompareTable
import app.tandemforactual.ui.feature.sync.ConflictFieldComparison
import app.tandemforactual.ui.feature.sync.ConflictVersionCard
import app.tandemforactual.ui.feature.sync.SyncPageHeading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConflictChoiceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val comparisons =
        listOf(
            ConflictFieldComparison("Amount", "-$42.17", "-$45.20"),
            ConflictFieldComparison("Category", "Groceries", "Dining"),
            ConflictFieldComparison("Payee", "Whole Foods", "Whole Foods"),
        )

    @Test
    fun versionCardsBehaveLikeOneChoiceAndTheTableListsEveryField() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                var keepThisDevice by remember { mutableStateOf(true) }
                Column(
                    Modifier.fillMaxSize().background(tandemPalette.background).padding(TandemSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    SyncPageHeading("Conflict 1 of 2", "Choose what to keep.", "Whole Foods · Nov 17")
                    ConflictVersionCard("This device", "-$42.17", "Category · Groceries", keepThisDevice, true) {
                        keepThisDevice = true
                    }
                    ConflictVersionCard("Actual", "-$45.20", "Category · Dining", !keepThisDevice, true) {
                        keepThisDevice = false
                    }
                    ConflictCompareTable(comparisons, "Actual")
                }
            }
        }

        composeTestRule.onAllNodesWithText("-$42.17").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("THIS DEVICE").assertCountEquals(2)
        composeTestRule.onNodeWithText("Dining").assertExists()
        composeTestRule.onAllNodesWithText("-$45.20").assertCountEquals(2)
        composeTestRule.onNodeWithText("Category · Dining", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText("Category · Groceries", useUnmergedTree = true).assertExists()

        val output = File("build/reports/conflict-choice.png")
        output.parentFile.mkdirs()
        output.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun selectingAVersionMovesTheSelection() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                var keepThisDevice by remember { mutableStateOf(true) }
                Column {
                    ConflictVersionCard("This device", "-$42.17", "Mine", keepThisDevice, true) { keepThisDevice = true }
                    ConflictVersionCard("Actual", "-$45.20", "Theirs", !keepThisDevice, true) { keepThisDevice = false }
                }
            }
        }

        composeTestRule.onNodeWithText("Mine").assertIsSelected()
        composeTestRule.onNodeWithText("Theirs").assertIsNotSelected()
        composeTestRule.onNodeWithText("Theirs").performClick()
        composeTestRule.onNodeWithText("Theirs").assertIsSelected()
        composeTestRule.onNodeWithText("Mine").assertIsNotSelected()
    }
}
