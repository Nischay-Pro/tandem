package app.tandemforactual.android

import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import app.tandemforactual.ui.app.WindowHinge
import app.tandemforactual.ui.design.theme.TabletopFold

/** The fold or hinge that splits the window into side-by-side halves, in window pixels; a tabletop fold keeps one layout. */
internal fun WindowLayoutInfo.separatingVerticalHinge(): WindowHinge? =
    displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.bounds
        ?.let { WindowHinge(it.left.toFloat(), it.right.toFloat()) }

/** A half-open horizontal fold, so sheets and dialogs can sit above it like a laptop screen. */
internal fun WindowLayoutInfo.tabletopFold(windowHeight: Float): TabletopFold? =
    displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
        ?.bounds
        ?.let { TabletopFold(it.top.toFloat(), it.bottom.toFloat(), windowHeight) }
