package app.tandemforactual.ui.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Material window width classes, measured on the window rather than the device. */
internal enum class WindowWidthClass(
    val minDp: Float,
) {
    COMPACT(0f),
    MEDIUM(600f),
    EXPANDED(840f),
    LARGE(1_200f),
    EXTRA_LARGE(1_600f),
    ;

    companion object {
        fun of(widthDp: Float): WindowWidthClass = entries.last { widthDp >= it.minDp }
    }
}

/** Material window height classes, measured on the window rather than the device. */
internal enum class WindowHeightClass(
    val minDp: Float,
) {
    COMPACT(0f),
    MEDIUM(480f),
    EXPANDED(900f),
    ;

    companion object {
        fun of(heightDp: Float): WindowHeightClass = entries.last { heightDp >= it.minDp }
    }
}

/** The one description of the window every layout decision reads. */
internal data class WindowSizeInfo(
    val widthDp: Float,
    val heightDp: Float,
) {
    val widthClass = WindowWidthClass.of(widthDp)
    val heightClass = WindowHeightClass.of(heightDp)

    /** The rail needs both width and height; short windows such as landscape phones and cover screens keep the bottom bar. */
    val usesRail = widthClass >= WindowWidthClass.MEDIUM && heightClass >= WindowHeightClass.MEDIUM

    /** Short windows tighten the header and move the add action into it. */
    val compactHeight = heightClass == WindowHeightClass.COMPACT

    /** Width left for content beside the navigation. */
    val contentWidthDp = if (usesRail) widthDp - TandemLayout.railWidth.value else widthDp
}

@Composable
internal fun currentWindowSizeInfo(): WindowSizeInfo {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return remember(size, density) { WindowSizeInfo(size.width / density, size.height / density) }
}
