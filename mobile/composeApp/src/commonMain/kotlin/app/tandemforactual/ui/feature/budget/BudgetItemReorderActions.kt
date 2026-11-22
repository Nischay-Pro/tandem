@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.ReorderDragHandle
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide

@Composable
internal fun BudgetItemReorderActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    toggleEnabled: Boolean = enabled,
    dragEnabled: Boolean = enabled,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    itemName: String = "item",
    isHidden: Boolean = false,
    onToggleHidden: (() -> Unit)? = null,
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        onToggleHidden?.let { toggleHidden ->
            TandemIconButton(
                if (isHidden) Lucide.Eye else Lucide.EyeOff,
                if (isHidden) "Show $itemName" else "Hide $itemName",
                sizeDp = 44,
                iconSizeDp = 18,
                enabled = toggleEnabled,
                tint = if (isHidden) tandemPalette.positive else tandemPalette.muted,
                onClick = toggleHidden,
            )
        }
        ReorderDragHandle(
            itemName = itemName,
            enabled = dragEnabled && (canMoveUp || canMoveDown),
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDragStarted = onDragStarted,
            onDragBy = onDragBy,
            onDragStopped = onDragStopped,
        )
    }
}
