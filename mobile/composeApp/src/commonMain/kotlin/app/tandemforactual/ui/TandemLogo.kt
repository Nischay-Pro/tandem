@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val LOGO_VIEWPORT = 1248f

private val firstTandemStroke =
    Path().apply {
        moveTo(154f, 326f)
        lineTo(847f, 116f)
        lineTo(847f, 170f)
        lineTo(154f, 380f)
        close()
    }

private val secondTandemStroke =
    Path().apply {
        moveTo(329f, 346f)
        lineTo(1094f, 108f)
        lineTo(1094f, 162f)
        lineTo(329f, 400f)
        close()
    }

private val tandemStem =
    Path().apply {
        moveTo(594f, 215f)
        lineTo(654f, 215f)
        lineTo(654f, 1130f)
        lineTo(594f, 1130f)
        close()
    }

/** White transparent Tandem mark for use on the app's dark launch background. */
@Composable
internal fun TandemBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "Tandem",
) {
    val markColor = if (tandemPalette.text.luminance() > 0.5f) Color.White else Color(0xFF805AD5)
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        val side = size.minDimension
        val scaleFactor = side / LOGO_VIEWPORT * 0.72f
        val contentSide = LOGO_VIEWPORT * scaleFactor
        val left = (size.width - contentSide) / 2f
        val top = (size.height - contentSide) / 2f
        translate(left, top) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                drawPath(firstTandemStroke, markColor)
                drawPath(secondTandemStroke, markColor)
                drawPath(tandemStem, markColor)
            }
        }
    }
}
