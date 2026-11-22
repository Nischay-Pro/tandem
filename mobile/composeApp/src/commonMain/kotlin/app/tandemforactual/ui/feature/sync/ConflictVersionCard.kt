@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun ConflictVersionCard(
    label: String,
    amount: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val palette = tandemPalette
    val border by animateColorAsState(
        if (selected) palette.accent else palette.border,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "conflict-version-border",
    )
    val fill by animateColorAsState(
        if (selected) palette.accentSurface else palette.surface,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "conflict-version-fill",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(TandemShapes.large))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) palette.accent else palette.muted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent))
            }
            Text(
                label.uppercase(),
                color = palette.muted,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = TandemTypography.captionLetterSpacing,
            )
        }
        Text(amount, color = palette.text, fontSize = TandemTypography.amount, fontWeight = FontWeight.Bold)
        Text(detail, color = palette.muted, fontSize = TandemTypography.compactBody)
    }
}
