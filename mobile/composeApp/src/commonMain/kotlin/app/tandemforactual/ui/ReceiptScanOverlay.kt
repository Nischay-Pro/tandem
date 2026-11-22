@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ReceiptText

/** Covers the form while a receipt is read: the photo with a light sweeping down it, like a scanner bar. */
@Composable
internal fun ReceiptScanOverlay(
    visible: Boolean,
    preview: ImageBitmap?,
) {
    var shown by remember { mutableStateOf(preview) }
    if (preview != null) shown = preview
    val reduceMotion = tandemReduceMotion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)),
        exit = fadeOut(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)),
    ) {
        val palette = tandemPalette
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background.copy(alpha = 0.94f))
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Reading receipt"
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(TandemSpacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
            ) {
                val sweep = rememberInfiniteTransition(label = "receipt-sweep")
                val progress by sweep.animateFloat(
                    0f,
                    1f,
                    infiniteRepeatable(tween(if (reduceMotion) 2_400 else 1_600, easing = LinearEasing), RepeatMode.Reverse),
                    label = "receipt-sweep-progress",
                )
                val shape = RoundedCornerShape(TandemShapes.large)
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS), initialScale = 0.92f),
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .aspectRatio(shown?.let { it.width.toFloat() / it.height }?.coerceIn(0.4f, 1.2f) ?: 0.62f)
                            .clip(shape)
                            .background(palette.surface)
                            .border(1.dp, palette.border, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val image = shown
                        if (image != null) {
                            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Lucide.ReceiptText, null, Modifier.fillMaxHeight(0.3f).aspectRatio(1f), tint = palette.muted)
                        }
                        val accent = palette.accent
                        Canvas(Modifier.fillMaxSize()) {
                            val y = size.height * progress
                            val glow = size.height * 0.18f
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.28f)), startY = y - glow, endY = y),
                                topLeft = Offset(0f, (y - glow).coerceAtLeast(0f)),
                                size =
                                    androidx.compose.ui.geometry
                                        .Size(size.width, glow.coerceAtMost(y)),
                            )
                            drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                        }
                    }
                }
                Text("Reading receipt…", color = palette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
                Text("Finding the total, store and date on this device", color = palette.muted, fontSize = TandemTypography.compactBody)
            }
        }
    }
}
