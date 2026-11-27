@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import app.tandemforactual.ui.design.theme.LocalTabletopFold
import kotlin.math.roundToInt

/**
 * Where content [contentHeight] tall goes in a box [boxHeight] tall so it's centred in the [spaceBelowFold] above the
 * box's bottom edge, or null when it doesn't fit below the fold.
 */
internal fun belowFoldOffset(
    boxHeight: Int,
    contentHeight: Int,
    spaceBelowFold: Int,
): Int? =
    if (spaceBelowFold <= 0 || spaceBelowFold > boxHeight || contentHeight > spaceBelowFold) {
        null
    } else {
        boxHeight - spaceBelowFold + (spaceBelowFold - contentHeight) / 2
    }

/**
 * Centres [content] in a window-filling dialog box. In tabletop posture it sits in the lower half when it fits, so the
 * controls are on the flat half and the page above stays readable on the upright one.
 */
@Composable
internal fun TabletopAwareBox(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val fold = LocalTabletopFold.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var bottomInset by remember { mutableIntStateOf(0) }
    Layout(
        content = content,
        modifier =
            modifier.onGloballyPositioned {
                bottomInset = windowHeight - (it.positionInWindow().y.roundToInt() + it.size.height)
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { placeable ->
                val centred = (constraints.maxHeight - placeable.height) / 2
                val belowFold =
                    fold?.let { belowFoldOffset(constraints.maxHeight, placeable.height, it.belowFold.roundToInt() - bottomInset) }
                placeable.place((constraints.maxWidth - placeable.width) / 2, belowFold ?: centred)
            }
        }
    }
}
