@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/**
 * A single-series line with a soft fill, drawn left to right when it appears. Touching or dragging reports the nearest
 * point through [onScrub], and lifting reports null, so the caller can show that point's value.
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
            .areaChartScrubbing(values, scrub),
    ) {
        if (values.isEmpty()) return@Canvas
        drawAreaChart(values, reveal.value, selected, palette)
    }
}

private fun Modifier.areaChartScrubbing(
    values: List<Long>,
    scrub: State<(Int?) -> Unit>,
): Modifier =
    this
        .pointerInput(values) {
            fun index(x: Float) =
                if (values.size <
                    2
                ) {
                    0
                } else {
                    ((x / size.width) * (values.size - 1)).toInt().coerceIn(0, values.lastIndex)
                }
            detectDragGestures(
                onDragStart = { scrub.value(index(it.x)) },
                onDragEnd = { scrub.value(null) },
                onDragCancel = { scrub.value(null) },
            ) { change, _ -> scrub.value(index(change.position.x)) }
        }.pointerInput(values) {
            detectTapGestures(onPress = { offset ->
                scrub.value(
                    if (values.size <
                        2
                    ) {
                        0
                    } else {
                        ((offset.x / size.width) * (values.size - 1)).toInt().coerceIn(0, values.lastIndex)
                    },
                )
                tryAwaitRelease()
                scrub.value(null)
            })
        }

private fun DrawScope.drawAreaChart(
    values: List<Long>,
    revealFraction: Float,
    selected: Int?,
    palette: TandemPalette,
) {
    val max = values.max().coerceAtLeast(1)
    val min = values.min().coerceAtMost(0)
    val span = (max - min).toFloat().coerceAtLeast(1f)
    val top = 8.dp.toPx()
    val usable = size.height - top - 4.dp.toPx()

    fun point(i: Int) =
        Offset(
            if (values.size < 2) size.width / 2 else size.width * i / (values.size - 1),
            top + usable * (1 - (values[i] - min) / span),
        )
    val line = Path().apply { values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
    val fill =
        Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, size.height)
            lineTo(point(0).x, size.height)
            close()
        }
    clipRect(right = size.width * revealFraction) {
        drawPath(fill, Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.28f), palette.accent.copy(alpha = 0f))))
        drawPath(line, palette.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
        drawCircle(palette.accent, 4.dp.toPx(), p)
    }
}
