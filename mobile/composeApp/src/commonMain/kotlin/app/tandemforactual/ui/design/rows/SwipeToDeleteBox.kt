@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val DELETE_THRESHOLD = 0.4f
private const val FLING_VELOCITY = 1_800f
private const val RESTORE_AFTER_MILLIS = 4_000L

/** Swipe the row left to delete it; screen readers get the same action as a custom action. */
@Composable
internal fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = tandemPalette
    val haptics = LocalHapticFeedback.current
    val settle = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val past = { value: Float -> width > 0 && -value > width * DELETE_THRESHOLD }
    val shown = if (settling) settle.value else dragOffset
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onDelete().let { true } }) },
    ) {
        if (shown < 0f) {
            Row(
                Modifier.matchParentSize().background(palette.negative).padding(end = TandemSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                Text("Delete", color = Color.White, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(shown.roundToInt(), 0) }
                .background(palette.background)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state =
                        rememberDraggableState { delta ->
                            if (!settling) {
                                val next = (dragOffset + delta).coerceIn(-width.toFloat(), 0f)
                                if (past(dragOffset) != past(next)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                dragOffset = next
                            }
                        },
                    onDragStarted = {
                        if (settling) {
                            settle.stop()
                            dragOffset = settle.value
                            settling = false
                        }
                    },
                    onDragStopped = { velocity ->
                        settle.snapTo(dragOffset)
                        settling = true
                        try {
                            if (past(dragOffset) || velocity < -FLING_VELOCITY) {
                                settle.animateTo(-width.toFloat())
                                onDelete()
                                delay(RESTORE_AFTER_MILLIS)
                            }
                            settle.animateTo(0f)
                            dragOffset = 0f
                        } finally {
                            settling = false
                        }
                    },
                ),
        ) { content() }
    }
}
