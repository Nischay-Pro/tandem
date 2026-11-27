@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.tools_add_short
import app.tandemforactual.ui.generated.resources.tools_added_short
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.jetbrains.compose.resources.stringResource

/** A large tappable card; a check badge for multi-select, a filled ring for single choices. */
@Composable
internal fun ChoiceCard(
    title: String,
    detail: String?,
    selected: Boolean,
    multiSelect: Boolean,
    icon: ImageVector? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val background by animateColorAsState(if (selected) palette.accentSurface else palette.surface, motion, label = "choice-bg")
    val border by animateColorAsState(if (selected) palette.accent else palette.border, motion, label = "choice-border")
    val selection =
        if (multiSelect) {
            Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .then(selection)
            .padding(horizontal = TandemSpacing.medium, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(
                        40.dp,
                    ).clip(
                        RoundedCornerShape(TandemShapes.control),
                    ).background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) palette.accent else palette.muted,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Text(title, color = palette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Text(
                        badge,
                        Modifier
                            .clip(
                                CircleShape,
                            ).background(palette.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = palette.accent,
                        fontSize = TandemTypography.micro,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (detail != null) Text(detail, color = palette.muted, fontSize = TandemTypography.compactBody)
        }
        SelectionMark(selected, multiSelect)
    }
}

/** The check or radio mark at the end of a choice card. */
@Composable
internal fun SelectionMark(
    selected: Boolean,
    multiSelect: Boolean,
) {
    val palette = tandemPalette
    val scale by animateFloatAsState(
        if (selected) 1f else 0f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        label = "choice-mark",
    )
    val shape = if (multiSelect) RoundedCornerShape(TandemShapes.small) else CircleShape
    Box(
        Modifier.size(24.dp).clip(shape).border(1.5.dp, if (selected) palette.accent else palette.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(shape)
                .background(palette.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.accentContent) }
    }
}

/** The add/added pill on a suggested tool. */
@Composable
internal fun AddChip(added: Boolean) {
    val palette = tandemPalette
    val motion = tandemMotionSpec<androidx.compose.ui.graphics.Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val background by animateColorAsState(if (added) palette.accent else palette.raised, motion, label = "add-chip-bg")
    val content by animateColorAsState(if (added) palette.accentContent else palette.text, motion, label = "add-chip-content")
    Row(
        Modifier.clip(CircleShape).background(background).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (added) Lucide.Check else Lucide.Plus, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
        Text(
            stringResource(if (added) Res.string.tools_added_short else Res.string.tools_add_short),
            color = content,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
