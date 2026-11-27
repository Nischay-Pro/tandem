package app.tandemforactual.ui.design.inputs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val HOVER_ALPHA = 0.07f

/** A hand pointer and a soft tint behind the content while a mouse or trackpad pointer is over a clickable surface. */
internal fun Modifier.pointerHighlight(
    color: Color,
    shape: Shape = RectangleShape,
    reduceMotion: Boolean = false,
    enabled: Boolean = true,
): Modifier = if (enabled) pointerHoverIcon(PointerIcon.Hand).then(HoverHighlightElement(color, shape, reduceMotion)) else this

private data class HoverHighlightElement(
    val color: Color,
    val shape: Shape,
    val reduceMotion: Boolean,
) : ModifierNodeElement<HoverHighlightNode>() {
    override fun create() = HoverHighlightNode(color, shape, reduceMotion)

    override fun update(node: HoverHighlightNode) {
        node.color = color
        node.shape = shape
        node.reduceMotion = reduceMotion
        node.invalidateDraw()
    }
}

private class HoverHighlightNode(
    var color: Color,
    var shape: Shape,
    var reduceMotion: Boolean,
) : Modifier.Node(),
    PointerInputModifierNode,
    DrawModifierNode {
    private var progress = Animatable(0f)

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main) return
        when (pointerEvent.type) {
            PointerEventType.Enter -> if (pointerEvent.changes.any { it.type != PointerType.Touch }) fadeTo(1f)
            PointerEventType.Exit -> fadeTo(0f)
        }
    }

    override fun onCancelPointerInput() = fadeTo(0f)

    override fun onDetach() {
        progress = Animatable(0f)
    }

    private fun fadeTo(target: Float) {
        if (!isAttached || progress.targetValue == target) return
        val animatable = progress
        coroutineScope.launch {
            if (reduceMotion) animatable.snapTo(target) else animatable.animateTo(target, tween(TandemMotion.COLOR_MILLIS))
        }
    }

    override fun ContentDrawScope.draw() {
        val amount = progress.value
        if (amount > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), color, alpha = HOVER_ALPHA * amount)
        drawContent()
    }
}

/** Calls [onClick] at the pointer for a right-click or secondary tap, before any click or long-press sees the press. */
internal fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = then(SecondaryClickElement(onClick))

private data class SecondaryClickElement(
    val onClick: (Offset) -> Unit,
) : ModifierNodeElement<SecondaryClickNode>() {
    override fun create() = SecondaryClickNode(onClick)

    override fun update(node: SecondaryClickNode) {
        node.onClick = onClick
    }
}

private class SecondaryClickNode(
    var onClick: (Offset) -> Unit,
) : Modifier.Node(),
    PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Initial) return
        if (pointerEvent.type != PointerEventType.Press || !pointerEvent.buttons.isSecondaryPressed) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        pointerEvent.changes.forEach { it.consume() }
        onClick(change.position)
    }

    override fun onCancelPointerInput() = Unit
}

/** Lets a vertical mouse wheel scroll [state] sideways, while it still has room to move that way. */
internal fun Modifier.wheelScrollsHorizontally(
    state: ScrollState,
    reduceMotion: Boolean = false,
): Modifier = then(WheelScrollElement(state, reduceMotion))

private data class WheelScrollElement(
    val state: ScrollState,
    val reduceMotion: Boolean,
) : ModifierNodeElement<WheelScrollNode>() {
    override fun create() = WheelScrollNode(state, reduceMotion)

    override fun update(node: WheelScrollNode) {
        node.state = state
        node.reduceMotion = reduceMotion
    }
}

private class WheelScrollNode(
    var state: ScrollState,
    var reduceMotion: Boolean,
) : Modifier.Node(),
    PointerInputModifierNode {
    private var target = 0
    private var job: Job? = null

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Initial || pointerEvent.type != PointerEventType.Scroll) return
        val delta = pointerEvent.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
        if (abs(delta.y) <= abs(delta.x)) return
        val step = with(requireDensity()) { WHEEL_STEP.toPx() }
        val from = if (job?.isActive == true) target else state.value
        val to = (from + delta.y * step).roundToInt().coerceIn(0, state.maxValue)
        if (to == from) return
        pointerEvent.changes.forEach { it.consume() }
        target = to
        job?.cancel()
        job =
            coroutineScope.launch {
                if (reduceMotion) state.scrollTo(to) else state.animateScrollTo(to, tween(TandemMotion.COLOR_MILLIS))
            }
    }

    override fun onCancelPointerInput() = Unit
}

private val WHEEL_STEP = 64.dp
