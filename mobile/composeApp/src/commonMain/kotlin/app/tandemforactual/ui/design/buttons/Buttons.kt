@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.AnimatedStatusText
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

@Composable
internal fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    TandemButton(TandemButtonStyle.PRIMARY, label, enabled, modifier, loading, onClick)
}

@Composable
internal fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TandemButton(TandemButtonStyle.SECONDARY, label, enabled, modifier, loading = false, onClick = onClick)
}

internal enum class TandemActionButtonTone {
    PRIMARY,
    NEUTRAL,
    DESTRUCTIVE,
}

/** Android and Apple both ask for touch targets of at least 48dp / 44pt; the larger one covers both. */
internal val MIN_TOUCH_TARGET = 48.dp

@Composable
internal fun TandemNavigationChevronButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TandemIconButton(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        sizeDp = 44,
        iconSizeDp = 22,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun TandemBackButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TandemNavigationChevronButton(
        icon = Lucide.ChevronLeft,
        contentDescription = "Back",
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun TandemModalCloseButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val hoverColor = if (palette.surface.luminance() < 0.5f) palette.border else palette.surface
    TandemIconButton(
        icon = Lucide.X,
        contentDescription = "Close",
        modifier = modifier,
        sizeDp = 40,
        iconSizeDp = 18,
        enabled = enabled,
        containerColor = palette.raised,
        hoveredContainerColor = hoverColor,
        borderColor = palette.border.copy(alpha = 0.56f),
        onClick = onClick,
    )
}

@Composable
internal fun TandemModalHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleSize: TextUnit = TandemTypography.sectionTitle,
    leadingIcon: ImageVector? = null,
) {
    val palette = tandemPalette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.accent)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            fontFamily = tandemAlloFamily(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TandemModalCloseButton(enabled = enabled, onClick = onClose)
    }
}

@Composable
internal fun PeriodStepper(
    title: String,
    previousLabel: String,
    nextLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TandemNavigationChevronButton(Lucide.ChevronLeft, previousLabel, onClick = onPrevious)
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.periodTitle,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        TandemNavigationChevronButton(Lucide.ChevronRight, nextLabel, onClick = onNext)
    }
}

@Composable
internal fun SelectableTile(
    selected: Boolean,
    contentDescription: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TandemShapes.medium),
    selectedBackground: Color = tandemPalette.accentSurface,
    unselectedBackground: Color = tandemPalette.surface,
    selectedBorder: Color? = null,
    unselectedBorder: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val background by
        animateColorAsState(
            targetValue = if (selected) selectedBackground else unselectedBackground,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "selectable-tile-background",
        )
    val border = if (selected) selectedBorder else unselectedBorder
    Box(
        modifier =
            modifier
                .animateContentSize(animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .clip(shape)
                .background(background)
                .then(border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
                .semantics { this.contentDescription = contentDescription }
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private enum class TandemButtonStyle {
    PRIMARY,
    SECONDARY,
}

@Composable
private fun TandemButton(
    style: TandemButtonStyle,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val primary = style == TandemButtonStyle.PRIMARY
    val shape = RoundedCornerShape(TandemShapes.medium)
    val backgroundColor by
        animateColorAsState(
            targetValue = if (primary && enabled) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "tandem-button-background",
        )
    val borderColor by
        animateColorAsState(
            targetValue = if (enabled) palette.outline else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "tandem-button-border",
        )
    val textColor by
        animateColorAsState(
            targetValue =
                when {
                    !enabled -> palette.muted
                    primary -> palette.accentContent
                    else -> palette.text
                },
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "tandem-button-text",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .then(
                    if (primary) {
                        Modifier.background(backgroundColor)
                    } else {
                        Modifier.background(palette.surface).border(1.dp, borderColor, shape)
                    },
                ).clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (primary) {
            Row(
                modifier = Modifier.animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS)),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedReveal(visible = loading, animateHeight = false) {
                    TandemLoadingIndicator(Modifier.size(17.dp), textColor, 2.dp)
                }
                AnimatedStatusText(
                    label,
                    textColor,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
        } else {
            Text(
                label,
                color = textColor,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

@Composable
internal fun SegmentedSelector(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(tandemPalette.raised)
            .padding(TandemSpacing.xxSmall),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            SelectableTile(
                selected = selected,
                contentDescription = label,
                onSelect = { onSelect(index) },
                modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
                shape = RoundedCornerShape(TandemShapes.pill),
                selectedBackground = tandemPalette.accentSurface,
                unselectedBackground = tandemPalette.raised,
            ) {
                Text(
                    label,
                    color = if (selected) tandemPalette.accent else tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}
