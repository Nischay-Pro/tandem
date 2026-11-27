package app.tandemforactual.ui.app

import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo

/** The vertical fold that splits the window into two screens, if the device reports one. */
fun WindowLayoutInfo.separatingVerticalHinge(): WindowHinge? =
    displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.bounds
        ?.let { WindowHinge(it.left.toFloat(), it.right.toFloat()) }
