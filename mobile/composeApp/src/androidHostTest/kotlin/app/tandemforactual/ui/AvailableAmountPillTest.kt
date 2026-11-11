package app.tandemforactual.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AvailableAmountPill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AvailableAmountPillTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun overdrawnPillAnnouncesTheStatusAndSignedAmount() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AvailableAmountPill("-$12.00", isOverdrawn = true)
            }
        }

        composeTestRule.onNodeWithContentDescription("Overspent, -$12.00 available").assertExists()
    }

    @Test
    fun nonOverdrawnPillDoesNotAnnounceAnOverspentStatus() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AvailableAmountPill("$12.00", isOverdrawn = false)
            }
        }

        composeTestRule.onNodeWithContentDescription("$12.00 available").assertExists()
    }

    @Test
    fun longAmountsGrowAtTwoTimesTextScaleInsteadOfBeingClipped() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                    Box(Modifier.width(96.dp)) {
                        AvailableAmountPill("-€123,456,789.00", isOverdrawn = true)
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Overspent, -€123,456,789.00 available")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
    }
}
