@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.GripVertical
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

@Composable
internal fun TandemActionButton(
    label: String,
    tone: TandemActionButtonTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.medium)
    val containerColor =
        when (tone) {
            TandemActionButtonTone.PRIMARY -> if (enabled) palette.accent else palette.raised
            TandemActionButtonTone.NEUTRAL -> palette.surface
            TandemActionButtonTone.DESTRUCTIVE -> palette.negative.copy(alpha = 0.12f)
        }
    val contentColor =
        when {
            !enabled -> palette.muted
            tone == TandemActionButtonTone.PRIMARY -> palette.accentContent
            tone == TandemActionButtonTone.DESTRUCTIVE -> palette.negative
            else -> palette.text
        }
    val outlineColor =
        when (tone) {
            TandemActionButtonTone.PRIMARY -> containerColor
            TandemActionButtonTone.NEUTRAL -> palette.border
            TandemActionButtonTone.DESTRUCTIVE -> palette.negative.copy(alpha = 0.32f)
        }
    Row(
        modifier =
            modifier
                .heightIn(min = 50.dp)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, outlineColor, shape)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
                .padding(horizontal = TandemSpacing.small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            TandemLoadingIndicator(Modifier.size(16.dp), contentColor, 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = contentColor)
        }
        Text(
            label,
            modifier = if (loading || icon != null) Modifier.padding(start = TandemSpacing.extraSmall) else Modifier,
            color = contentColor,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}

@Composable
internal fun TandemIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClickLabel: String = contentDescription,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
    iconSizeDp: Int = 22,
    enabled: Boolean = true,
    tint: Color? = null,
    containerColor: Color? = null,
    hoveredContainerColor: Color? = null,
    shape: Shape = CircleShape,
    borderColor: Color? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val modalClose = icon == Lucide.X && contentDescription == "Close"
    val closeHoverColor = if (palette.surface.luminance() < 0.5f) palette.border else palette.surface
    val resolvedContainerColor = containerColor ?: palette.raised.takeIf { modalClose }
    val resolvedHoveredContainerColor = hoveredContainerColor ?: closeHoverColor.takeIf { modalClose }
    val resolvedBorderColor = borderColor ?: palette.border.copy(alpha = 0.56f).takeIf { modalClose }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundTarget =
        when {
            (hovered || pressed) && resolvedHoveredContainerColor != null -> resolvedHoveredContainerColor
            else -> resolvedContainerColor
        }
    val animatedContainerColor by
        animateColorAsState(
            targetValue = backgroundTarget ?: Color.Transparent,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "icon-button-background",
        )
    Box(
        modifier =
            modifier
                .size(sizeDp.dp)
                .clip(shape)
                .then(
                    if (resolvedContainerColor != null || resolvedHoveredContainerColor != null) {
                        Modifier.background(animatedContainerColor)
                    } else {
                        Modifier
                    },
                ).hoverable(interactionSource)
                .then(resolvedBorderColor?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .clickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSizeDp.dp), tint = tint ?: palette.text)
    }
}

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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TandemModalCloseButton(enabled = enabled, onClick = onClose)
    }
}

@Composable
internal fun ReorderDragHandle(
    itemName: String = "item",
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val stepPixels = with(density) { 52.dp.toPx() }
    val latestMoveUp by rememberUpdatedState(onMoveUp)
    val latestMoveDown by rememberUpdatedState(onMoveDown)
    val latestOnDragStarted by rememberUpdatedState(onDragStarted)
    val latestOnDragBy by rememberUpdatedState(onDragBy)
    val latestOnDragStopped by rememberUpdatedState(onDragStopped)
    val latestCanMoveUp by rememberUpdatedState(canMoveUp)
    val latestCanMoveDown by rememberUpdatedState(canMoveDown)
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            val dragHandler = latestOnDragBy
            if (dragHandler != null) {
                dragHandler(delta)
            } else {
                dragDistance += delta
                if (dragDistance >= stepPixels && latestCanMoveDown) {
                    latestMoveDown()
                    dragDistance -= stepPixels
                }
                if (dragDistance <= -stepPixels && latestCanMoveUp) {
                    latestMoveUp()
                    dragDistance += stepPixels
                }
                if (!latestCanMoveDown && dragDistance > 0f || !latestCanMoveUp && dragDistance < 0f) {
                    dragDistance = 0f
                }
            }
        }
    val actionList =
        buildList {
            if (enabled && canMoveUp) {
                add(
                    CustomAccessibilityAction("Move $itemName up") {
                        latestMoveUp()
                        true
                    },
                )
            }
            if (enabled && canMoveDown) {
                add(
                    CustomAccessibilityAction("Move $itemName down") {
                        latestMoveDown()
                        true
                    },
                )
            }
        }
    val reorderSemantics =
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Drag to reorder $itemName"
            customActions = actionList
        }
    val handleModifier =
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(TandemShapes.medium))
            .then(reorderSemantics)
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                enabled = enabled && (canMoveUp || canMoveDown),
                onDragStarted = {
                    dragDistance = 0f
                    latestOnDragStarted?.invoke()
                },
                onDragStopped = {
                    dragDistance = 0f
                    latestOnDragStopped?.invoke()
                },
            )
    androidx.compose.foundation.layout.Box(
        modifier = handleModifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.GripVertical, contentDescription = null, modifier = Modifier.size(21.dp), tint = tandemPalette.muted)
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
            targetValue = if (enabled) palette.border else palette.raised,
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
                ).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
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
