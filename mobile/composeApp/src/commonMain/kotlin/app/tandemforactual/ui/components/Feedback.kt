@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion

@Composable
internal fun BodyText(text: String) {
    Text(text, color = tandemPalette.muted, fontSize = TandemTypography.body, lineHeight = TandemTypography.bodyLineHeight)
}

internal enum class InlineMessageKind {
    ERROR,
    WARNING,
    INFO,
    SUCCESS,
}

@Composable
internal fun InlineMessage(
    text: String,
    kind: InlineMessageKind = InlineMessageKind.WARNING,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val color =
        when (kind) {
            InlineMessageKind.ERROR -> palette.negative
            InlineMessageKind.WARNING -> palette.warning
            InlineMessageKind.INFO -> palette.muted
            InlineMessageKind.SUCCESS -> palette.positive
        }
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = TandemTypography.compactBody,
        lineHeight = TandemTypography.bodyLineHeight,
    )
}

@Composable
internal fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(
            title,
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.SemiBold,
        )
        if (message != null) BodyText(message)
        if (actionLabel != null && onAction != null) {
            SecondaryButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
internal fun LabelText(text: String) {
    Text(
        text,
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        letterSpacing = TandemTypography.captionLetterSpacing,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun Badge(
    label: String,
    color: Color = tandemPalette.accent,
) {
    val motionSpec: FiniteAnimationSpec<Color> = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val animatedColor by androidx.compose.animation.animateColorAsState(color, motionSpec, label = "badge-color")
    Box(
        Modifier
            .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(animatedColor.copy(alpha = 0.13f))
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
    ) {
        AnimatedStatusText(label, color, fontSize = TandemTypography.micro, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun TandemLoadingIndicator(
    modifier: Modifier,
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
) {
    if (tandemReduceMotion) {
        Box(modifier.border(strokeWidth, color, CircleShape))
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

@Composable
internal fun AnimatedReveal(
    visible: Boolean,
    animateHeight: Boolean = true,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enter =
        if (tandemReduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.enterEasing)) +
                if (animateHeight) {
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.movementEasing),
                        clip = true,
                    )
                } else {
                    EnterTransition.None
                }
        }
    val exit =
        if (tandemReduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.exitEasing)) +
                if (animateHeight) {
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(TandemMotion.REVEAL_MILLIS, easing = TandemMotion.movementEasing),
                        clip = true,
                    )
                } else {
                    ExitTransition.None
                }
        }
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, content = content)
}
