package app.tandemforactual.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.sync.SyncCountTile
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncCenterSummaryRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun needsReviewCountTileOpensItsReviewSectionWhenTapped() {
        var opened = false

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SyncCountTile(
                    label = "Needs review",
                    count = 1,
                    icon = Lucide.CircleAlert,
                    color = tandemPalette.warning,
                    modifier = Modifier,
                    onClick = { opened = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Needs review").performClick()
        composeTestRule.runOnIdle { assertTrue(opened) }
    }
}
