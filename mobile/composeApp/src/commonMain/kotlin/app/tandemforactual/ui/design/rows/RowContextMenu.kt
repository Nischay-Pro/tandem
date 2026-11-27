@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.inputs.onSecondaryClick
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlin.math.roundToInt

/** One choice in a row's right-click menu. */
internal class RowMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Wraps a row so a right-click or secondary tap opens [actions] at the pointer, the same choices long-press and swipe give. */
@Composable
internal fun RowContextMenu(
    actions: List<RowMenuAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var anchor by remember { mutableStateOf<Offset?>(null) }
    Box(if (actions.isEmpty()) modifier else modifier.onSecondaryClick { anchor = it }) {
        content()
        val position = anchor
        if (position != null && actions.isNotEmpty()) {
            Box(Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }) {
                RowMenu(actions, onDismiss = { anchor = null })
            }
        }
    }
}

@Composable
private fun RowMenu(
    actions: List<RowMenuAction>,
    onDismiss: () -> Unit,
) {
    val palette = tandemPalette
    val colors = MaterialTheme.colors.copy(surface = palette.surface, background = palette.surface, onSurface = palette.text)
    val shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(TandemShapes.large))
    MaterialTheme(colors = colors, shapes = shapes) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)) {
            Column(Modifier.padding(vertical = TandemSpacing.extraSmall)) {
                actions.forEach { action ->
                    RowMenuItem(action) {
                        onDismiss()
                        action.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun RowMenuItem(
    action: RowMenuAction,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val tint = if (action.destructive) palette.negative else palette.text
    val iconTint = if (action.destructive) palette.negative else palette.muted
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = iconTint)
        Text(
            action.label,
            color = tint,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
