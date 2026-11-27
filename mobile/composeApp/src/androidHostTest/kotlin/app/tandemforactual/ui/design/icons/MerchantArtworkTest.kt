package app.tandemforactual.ui.design.icons

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MerchantArtworkTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(InternalResourceApi::class)
    @Test
    fun logoFollowsTheRowWhenItsMerchantChanges() {
        val first = DrawableResource("drawable:test_first", emptySet())
        val second = DrawableResource("drawable:test_second", emptySet())
        val firstBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        val secondBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        val firstArtwork = LogoArtwork(firstBitmap, LogoTone.BALANCED)
        val secondArtwork = LogoArtwork(secondBitmap, LogoTone.DARK)
        MerchantArtworkCache.put(first, firstArtwork)
        MerchantArtworkCache.put(second, secondArtwork)
        var resource by mutableStateOf(first)
        var painter: Painter? = null
        composeTestRule.setContent { painter = rememberMerchantArtwork(resource) }
        composeTestRule.waitForIdle()
        assertEquals(LogoPainter(firstArtwork), painter)

        resource = second
        composeTestRule.waitForIdle()

        assertEquals(LogoPainter(secondArtwork), painter)
    }

    @Test
    fun darkAndLightLogosAreToldApartFromColourfulAndFullBleedOnes() {
        assertEquals(LogoTone.DARK, glyph(Color.BLACK).asImageBitmap().logoTone())
        assertEquals(LogoTone.LIGHT, glyph(Color.WHITE).asImageBitmap().logoTone())
        assertEquals(LogoTone.BALANCED, glyph(Color.rgb(0xE5, 0x39, 0x35)).asImageBitmap().logoTone())
        val fullBleed = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        assertEquals(LogoTone.BALANCED, fullBleed.asImageBitmap().logoTone())
    }

    private fun glyph(color: Int): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            (2 until 6).forEach { x -> (2 until 6).forEach { y -> setPixel(x, y, color) } }
        }
}
