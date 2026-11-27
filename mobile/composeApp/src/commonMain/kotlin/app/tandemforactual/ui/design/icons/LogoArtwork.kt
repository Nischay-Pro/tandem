@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** Whether a logo is mostly dark or mostly light ink, so its tile can keep it visible in either theme. */
internal enum class LogoTone {
    DARK,
    LIGHT,
    BALANCED,
}

internal data class LogoArtwork(
    val image: ImageBitmap,
    val tone: LogoTone,
)

internal fun ImageBitmap.logoArtwork(): LogoArtwork = LogoArtwork(this, runCatching { logoTone() }.getOrDefault(LogoTone.BALANCED))

/** Dark or light when most of the ink would sit below about 2:1 on a tile of the same tone; full-bleed logos bring their own. */
internal fun ImageBitmap.logoTone(): LogoTone {
    val pixels = toPixelMap()
    var ink = 0f
    var dark = 0f
    var light = 0f
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixels[x, y]
            if (color.alpha < 0.05f) continue
            val luminance = color.copy(alpha = 1f).luminance()
            ink += color.alpha
            if (luminance < 0.08f) dark += color.alpha
            if (luminance > 0.55f) light += color.alpha
        }
    }
    return when {
        ink == 0f || ink > width * height * 0.9f -> LogoTone.BALANCED
        dark > ink / 2f -> LogoTone.DARK
        light > ink / 2f -> LogoTone.LIGHT
        else -> LogoTone.BALANCED
    }
}

/** Draws a decoded logo and keeps its [LogoTone] alongside it. */
internal class LogoPainter(
    val artwork: LogoArtwork,
) : Painter() {
    private val bitmap = BitmapPainter(artwork.image)
    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size get() = bitmap.intrinsicSize

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        with(bitmap) { draw(size, alpha, colorFilter) }
    }

    override fun equals(other: Any?): Boolean = other is LogoPainter && other.artwork == artwork

    override fun hashCode(): Int = artwork.hashCode()
}

/** The tile behind [logo]: [base], or a soft plate in the opposite tone when the logo would vanish on [base]. */
@Composable
internal fun logoTileColor(
    logo: LogoPainter?,
    base: Color,
    muted: Boolean = false,
): Color {
    val palette = tandemPalette
    val darkTheme = palette.background.luminance() < 0.5f
    val needsPlate =
        when (logo?.artwork?.tone) {
            LogoTone.DARK -> darkTheme
            LogoTone.LIGHT -> !darkTheme
            else -> false
        }
    val target = if (needsPlate) lerp(base, palette.text, if (muted) 0.5f else 0.88f) else base
    return animateColorAsState(target, tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS), label = "logo-tile").value
}
