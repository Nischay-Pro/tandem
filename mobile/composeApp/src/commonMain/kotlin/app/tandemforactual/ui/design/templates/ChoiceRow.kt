@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val selectionColor by
        animateColorAsState(
            targetValue = if (selected) palette.accent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "choice-selection-color",
        )
    val selectionProgress by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "choice-selection-progress",
        )
    SelectableTile(
        selected = selected,
        contentDescription = title,
        onSelect = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TandemShapes.large),
        selectedBackground = palette.surface,
        unselectedBackground = palette.surface,
        selectedBorder = palette.border,
        unselectedBorder = palette.border,
    ) {
        SettingRowLayout(
            title = title,
            subtitle = subtitle,
            enabled = true,
            semiboldTitle = true,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, selectionColor, CircleShape)
                    .padding(TandemSpacing.extraSmall),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = selectionProgress
                            scaleX = 0.75f + 0.25f * selectionProgress
                            scaleY = 0.75f + 0.25f * selectionProgress
                        }.clip(CircleShape)
                        .background(palette.accent),
                )
            }
        }
    }
}
