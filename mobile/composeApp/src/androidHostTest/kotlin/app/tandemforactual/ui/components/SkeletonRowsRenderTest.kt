package app.tandemforactual.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.theme.AccentChoice
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
@Config(sdk = [28], qualifiers = "w411dp-h600dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkeletonRowsRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun skeletonRowsRenderInLightTheme() = render(ThemeMode.LIGHT)

    @Test
    fun skeletonRowsRenderInDarkTheme() = render(ThemeMode.DARK)

    private fun render(theme: ThemeMode) {
        composeTestRule.setContent {
            TandemTheme(theme, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column(Modifier.fillMaxSize().background(tandemPalette.background)) { SkeletonRows() }
            }
        }
        composeTestRule.onNodeWithContentDescription("Loading").assertExists()
        val output = File("build/reports/skeleton-rows-${theme.name.lowercase()}.png")
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
