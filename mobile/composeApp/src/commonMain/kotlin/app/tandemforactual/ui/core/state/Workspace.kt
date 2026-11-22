package app.tandemforactual.ui.core.state

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Columns2
import com.composables.icons.lucide.Columns3
import com.composables.icons.lucide.Columns4
import com.composables.icons.lucide.Grid2x2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Square

/** How many screens sit side by side on a wide window, and the room each arrangement needs. */
internal enum class WorkspaceLayout(
    val paneCount: Int,
    val minWidthDp: Float,
    val minHeightDp: Float = 0f,
    val label: String,
) {
    SINGLE(1, 0f, label = "One screen"),
    TWO(2, 760f, label = "Two side by side"),
    THREE(3, 1_080f, label = "Three side by side"),
    FOUR(4, 1_360f, label = "Four side by side"),
    GRID(4, 760f, 700f, "Four in a grid"),
    ;

    fun fits(
        widthDp: Float,
        heightDp: Float,
    ) = widthDp >= minWidthDp && heightDp >= minHeightDp

    val icon: ImageVector
        get() =
            when (this) {
                SINGLE -> Lucide.Square
                TWO -> Lucide.Columns2
                THREE -> Lucide.Columns3
                FOUR -> Lucide.Columns4
                GRID -> Lucide.Grid2x2
            }
}

/** The chosen arrangement and which tab each pane shows, left to right then top to bottom. */
internal data class Workspace(
    val layout: WorkspaceLayout = WorkspaceLayout.SINGLE,
    val panes: List<AppRoute> = emptyList(),
) {
    /** What fits this window: the chosen arrangement, or the largest smaller one that does. */
    fun effective(
        widthDp: Float,
        heightDp: Float,
    ): WorkspaceLayout =
        if (layout.fits(widthDp, heightDp)) {
            layout
        } else {
            listOf(WorkspaceLayout.THREE, WorkspaceLayout.TWO)
                .firstOrNull { it.paneCount < layout.paneCount && it.fits(widthDp, heightDp) }
                ?: WorkspaceLayout.SINGLE
        }

    /** The tabs to show for [count] panes, starting with [focused] and filling in from [available] without repeats. */
    fun panesFor(
        count: Int,
        focused: AppRoute,
        available: List<AppRoute>,
    ): List<AppRoute> {
        val kept = panes.filter { it in available }.distinct()
        val ordered = (if (focused in kept) kept else listOf(focused) + kept) + available
        return ordered.distinct().take(count)
    }

    /** Puts [route] in the pane at [index], swapping with the pane that already shows it. */
    fun place(
        route: AppRoute,
        index: Int,
        current: List<AppRoute>,
    ): Workspace {
        val next = current.toMutableList()
        val existing = next.indexOf(route)
        if (existing >= 0) next[existing] = next[index]
        next[index] = route
        return copy(panes = next)
    }

    fun encode(): String = "${layout.name}|${panes.joinToString(",") { it.name }}"

    companion object {
        fun decode(value: String?): Workspace {
            val parts = value?.split('|') ?: return Workspace()
            val layout = WorkspaceLayout.entries.firstOrNull { it.name == parts[0] } ?: return Workspace()
            val panes =
                parts
                    .getOrNull(1)
                    .orEmpty()
                    .split(',')
                    .mapNotNull { name -> AppRoute.entries.firstOrNull { it.name == name } }
            return Workspace(layout, panes)
        }
    }
}
