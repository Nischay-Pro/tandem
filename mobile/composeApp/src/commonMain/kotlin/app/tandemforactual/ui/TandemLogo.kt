@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val LOGO_VIEWPORT = 1248f
private val tandemPurple = Color(0xFF805AD5)
private val tandemStroke = Stroke(width = 55f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)

private val firstTandemStroke =
    Path().apply {
        moveTo(150f, 360f)
        lineTo(850f, 160f)
    }

private val secondTandemStroke =
    Path().apply {
        moveTo(340f, 391.458f)
        lineTo(1040f, 191.458f)
    }

private val tandemStem =
    Path().apply {
        moveTo(620f, 226f)
        lineTo(620f, 1099.6132f)
    }

/** Tandem's supplied white mark on a purple squircle tile. */
@Composable
internal fun TandemBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "Tandem",
) {
    Canvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 25))
                .background(tandemPurple)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val side = size.minDimension
        val scaleFactor = side / LOGO_VIEWPORT * 0.9f
        val contentSide = LOGO_VIEWPORT * scaleFactor
        val left = (size.width - contentSide) / 2f
        val top = (size.height - contentSide) / 2f
        translate(left, top) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                drawPath(firstTandemStroke, Color.White, style = tandemStroke)
                drawPath(secondTandemStroke, Color.White, style = tandemStroke)
                drawPath(tandemStem, Color.White, style = tandemStroke)
            }
        }
    }
}
