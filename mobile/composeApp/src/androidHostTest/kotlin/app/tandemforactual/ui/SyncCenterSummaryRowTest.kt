package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncCenterSummaryRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun needsReviewSummaryRowOpensItsReviewActionWhenTapped() {
        var opened = false

        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SyncSummaryRow(
                    title = "Needs review",
                    subtitle = "1 change needs your attention",
                    icon = Lucide.CircleAlert,
                    color = tandemPalette.warning,
                    onClick = { opened = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Needs review").performClick()
        composeTestRule.runOnIdle { assertTrue(opened) }
    }
}
