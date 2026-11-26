@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlin.math.abs

/**
 * A single-series line with a soft fill resting on zero, drawn left to right when it appears. Touching or dragging
 * sideways reports the nearest point through [onScrub], and lifting reports null, so the caller can show that point's value.
 */
@Composable
internal fun AreaChart(
    values: List<Long>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    selected: Int? = null,
    onScrub: (Int?) -> Unit = {},
) {
    val palette = tandemPalette
    val reveal = remember(values) { Animatable(0f) }
    val reduceMotion = tandemReduceMotion
    LaunchedEffect(values) { reveal.animateTo(1f, tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS * 3)) }
    val scrub = rememberUpdatedState(onScrub)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .areaChartScrubbing(values.size, scrub),
    ) {
        if (values.isEmpty()) return@Canvas
        drawAreaChart(values, reveal.value, selected, palette)
    }
}

/** Scrubs on press and horizontal drags, and lets a vertical drag go to the scrolling page instead. */
private fun Modifier.areaChartScrubbing(
    count: Int,
    scrub: State<(Int?) -> Unit>,
): Modifier =
    pointerInput(count) {
        awaitEachGesture {
            val down = awaitFirstDown()
            scrub.value(areaChartIndexAt(down.position.x, size.width.toFloat(), count))
            var horizontal = false
            var travel = Offset.Zero
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                if (!horizontal) {
                    travel += change.positionChange()
                    val slop = viewConfiguration.touchSlop
                    if (abs(travel.y) > slop && abs(travel.y) > abs(travel.x)) break
                    horizontal = abs(travel.x) > slop
                }
                if (horizontal) change.consume()
                scrub.value(areaChartIndexAt(change.position.x, size.width.toFloat(), count))
            }
            scrub.value(null)
        }
    }

private fun DrawScope.drawAreaChart(
    values: List<Long>,
    revealFraction: Float,
    selected: Int?,
    palette: TandemPalette,
) {
    val range = areaChartRange(values)
    val top = 8.dp.toPx()
    val bottom = size.height - 4.dp.toPx()
    val zeroY = areaChartY(0L, range, top, bottom)
    val hasNegatives = range.first < 0L

    fun point(i: Int) = Offset(areaChartX(i, values.size, size.width), areaChartY(values[i], range, top, bottom))
    val line = Path().apply { values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
    val fill =
        Path().apply {
            moveTo(point(0).x, zeroY)
            values.indices.forEach { i -> point(i).let { lineTo(it.x, it.y) } }
            lineTo(point(values.lastIndex).x, zeroY)
            close()
        }
    val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (hasNegatives) drawLine(palette.border, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
    clipRect(right = size.width * revealFraction) {
        clipRect(bottom = if (hasNegatives) zeroY else size.height) {
            drawPath(fill, Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.28f), palette.accent.copy(alpha = 0f)), top, zeroY))
            drawPath(line, palette.accent, style = stroke)
        }
        if (hasNegatives) {
            clipRect(top = zeroY) {
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(palette.negative.copy(alpha = 0f), palette.negative.copy(alpha = 0.28f)), zeroY, bottom),
                )
                drawPath(line, palette.negative, style = stroke)
            }
        }
    }
    selected?.takeIf { it in values.indices }?.let { i ->
        val p = point(i)
        drawLine(
            palette.muted,
            Offset(p.x, 0f),
            Offset(p.x, size.height),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
        drawCircle(palette.background, 6.dp.toPx(), p)
        drawCircle(if (values[i] < 0L) palette.negative else palette.accent, 4.dp.toPx(), p)
    }
}
