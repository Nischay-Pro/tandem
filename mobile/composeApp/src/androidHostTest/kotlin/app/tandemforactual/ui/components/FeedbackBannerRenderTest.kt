package app.tandemforactual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import java.io.File
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackBannerRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyBannerKindRenders() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column(
                    Modifier.fillMaxSize().background(tandemPalette.background).padding(TandemSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    InlineMessage("Reconciled 12 transactions. Balance matches $1,176.18.", InlineMessageKind.SUCCESS)
                    InlineMessage("Reading statement…", InlineMessageKind.INFO)
                    InlineMessage(
                        "Couldn’t move Clothing. 3 changes are still waiting.",
                        InlineMessageKind.WARNING,
                        actionLabel = "Try again",
                        onAction = {},
                    )
                    InlineMessage(
                        "Tandem couldn’t reach the server. Your edits are saved on this device.",
                        InlineMessageKind.ERROR,
                        onDismiss = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Try again").assertExists()

        val output = File("build/reports/feedback-banners.png")
        output.parentFile.mkdirs()
        output.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
