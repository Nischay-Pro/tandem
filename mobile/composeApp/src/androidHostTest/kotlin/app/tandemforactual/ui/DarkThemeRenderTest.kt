package app.tandemforactual.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.icons.MerchantArtworkCache
import app.tandemforactual.ui.design.icons.logoArtwork
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.reports.CategorySpendingPoint
import app.tandemforactual.ui.feature.reports.MonthlyMoneyPoint
import app.tandemforactual.ui.feature.reports.ReportChartSelection
import app.tandemforactual.ui.feature.reports.ReportDataBundle
import app.tandemforactual.ui.feature.reports.drawReport
import java.io.File
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkThemeRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(InternalResourceApi::class)
    private val blackLogo = DrawableResource("drawable:test_black_logo", emptySet())

    @OptIn(InternalResourceApi::class)
    private val redLogo = DrawableResource("drawable:test_red_logo", emptySet())

    private fun render(
        name: String,
        mode: ThemeMode,
        content: @Composable () -> Unit,
    ): ImageBitmap {
        composeTestRule.setContent {
            TandemTheme(mode, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column(Modifier.fillMaxWidth().background(tandemPalette.surface)) { content() }
            }
        }
        composeTestRule.waitForIdle()
        val image = composeTestRule.onRoot().captureToImage()
        File("build/reports/dark-theme-$name.png").also { it.parentFile.mkdirs() }.outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    private fun glyph(color: Int): ImageBitmap =
        Bitmap
            .createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            .apply {
                eraseColor(android.graphics.Color.TRANSPARENT)
                (16 until 56).forEach { x -> (16 until 56).forEach { y -> setPixel(x, y, color) } }
            }.asImageBitmap()

    private fun tileLuminance(
        image: ImageBitmap,
        row: Int,
    ): Float {
        val density = 3
        val pixel = image.asAndroidBitmap().getPixel((16 + 3) * density, (row * 64 + 32) * density)
        return Color(pixel).luminance()
    }

    private fun assertLogoPlates(mode: ThemeMode) {
        MerchantArtworkCache.put(blackLogo, glyph(android.graphics.Color.BLACK).logoArtwork())
        MerchantArtworkCache.put(redLogo, glyph(android.graphics.Color.rgb(0xE5, 0x39, 0x35)).logoArtwork())
        val image =
            render("logos-$mode", mode) {
                ListRow("Black logo", leading = RowLeading.Merchant(blackLogo))
                ListRow("Red logo", leading = RowLeading.Merchant(redLogo))
            }
        assertTrue(tileLuminance(image, 0) > 0.5f, "a black logo sits on a light plate in $mode")
        assertTrue(tileLuminance(image, 1) < 0.1f, "a colourful logo keeps the dark tile in $mode")
    }

    private fun renderCharts(mode: ThemeMode) {
        val monthly = (1..6).map { MonthlyMoneyPoint("2026-0$it", "M$it", 300_00L + it * 40_00L, 250_00L + it * 30_00L) }
        val categories = (1..8).map { CategorySpendingPoint("c$it", "Category $it", 100_00L - it * 5_00L) }
        val data = ReportDataBundle(categories, monthly, monthly, emptyList(), emptyList(), null)
        render("charts-$mode", mode) {
            val palette = tandemPalette
            listOf(null, ReportChartSelection(2)).forEach { selection ->
                Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                    drawReport(ReportKind.INCOME_AND_SPENDING, data, monthly, emptyList(), selection, null, false, palette)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                drawReport(ReportKind.SPENDING_BY_CATEGORY, data, monthly, categories, null, null, false, palette)
            }
        }
    }

    @Test
    fun darkLogosGetALightPlateInDarkMode() = assertLogoPlates(ThemeMode.DARK)

    @Test
    fun darkLogosGetALightPlateInPureBlack() = assertLogoPlates(ThemeMode.PURE_BLACK)

    @Test
    fun chartsInLightMode() = renderCharts(ThemeMode.LIGHT)

    @Test
    fun chartsInDarkMode() = renderCharts(ThemeMode.DARK)

    @Test
    fun chartsInPureBlack() = renderCharts(ThemeMode.PURE_BLACK)
}
