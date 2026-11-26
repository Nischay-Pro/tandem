package app.tandemforactual.ui.design.icons

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
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
        MerchantArtworkCache.put(first, firstBitmap)
        MerchantArtworkCache.put(second, secondBitmap)
        var resource by mutableStateOf(first)
        var painter: Painter? = null
        composeTestRule.setContent { painter = rememberMerchantArtwork(resource) }
        composeTestRule.waitForIdle()
        assertEquals(BitmapPainter(firstBitmap), painter)

        resource = second
        composeTestRule.waitForIdle()

        assertEquals(BitmapPainter(secondBitmap), painter)
    }
}
