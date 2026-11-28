package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Counts two-finger taps: both fingers down together, barely moving, lifted within [tapMillis]. Two of them no more
 * than [gapMillis] apart make a double tap.
 */
internal class TwoFingerTaps(
    private val tapMillis: Long,
    private val gapMillis: Long,
) {
    private var lastTapEnd: Long? = null

    /** Whether this gesture finishes a two-finger double tap. */
    fun onGesture(
        fingers: Int,
        moved: Boolean,
        startMillis: Long,
        endMillis: Long,
    ): Boolean {
        val tap = fingers == 2 && !moved && endMillis - startMillis <= tapMillis
        val previous = lastTapEnd
        lastTapEnd = null
        if (!tap) return false
        if (previous != null && startMillis - previous <= gapMillis) return true
        lastTapEnd = endMillis
        return false
    }
}

/** Calls [onDoubleTap] on a two-finger double tap anywhere inside, without taking touches from what's underneath. */
internal fun Modifier.twoFingerDoubleTap(
    enabled: Boolean,
    onDoubleTap: () -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            val taps = TwoFingerTaps(TAP_MILLIS, GAP_MILLIS)
            awaitEachGesture {
                var event = awaitPointerEvent(PointerEventPass.Initial)
                val start = event.changes.first().uptimeMillis
                val origins = mutableMapOf<Long, Offset>()
                var fingers = 0
                var moved = false
                while (event.changes.any { it.pressed }) {
                    event.changes.filter { it.pressed }.forEach { change ->
                        val origin = origins.getOrPut(change.id.value) { change.position }
                        if ((change.position - origin).getDistance() > viewConfiguration.touchSlop) moved = true
                    }
                    fingers = maxOf(fingers, event.changes.count { it.pressed })
                    event = awaitPointerEvent(PointerEventPass.Initial)
                }
                if (taps.onGesture(fingers, moved, start, event.changes.first().uptimeMillis)) onDoubleTap()
            }
        }
    }

private const val TAP_MILLIS = 300L
private const val GAP_MILLIS = 400L
