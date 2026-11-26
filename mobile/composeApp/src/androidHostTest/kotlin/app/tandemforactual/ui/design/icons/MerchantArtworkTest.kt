package app.tandemforactual.ui.design.icons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MerchantArtworkTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun logoFollowsTheRowWhenItsMerchantChanges() {
        val (first, second) = Res.allDrawableResources.values.take(2)
        val firstBitmap = ImageBitmap(1, 1)
        val secondBitmap = ImageBitmap(2, 2)
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
