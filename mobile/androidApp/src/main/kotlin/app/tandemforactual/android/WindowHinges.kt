package app.tandemforactual.android

import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import app.tandemforactual.ui.app.WindowHinge

/** The fold or hinge that splits the window into side-by-side halves, in window pixels; a tabletop fold keeps one layout. */
internal fun WindowLayoutInfo.separatingVerticalHinge(): WindowHinge? =
    displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.bounds
        ?.let { WindowHinge(it.left.toFloat(), it.right.toFloat()) }
