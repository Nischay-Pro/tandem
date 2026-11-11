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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemShapes
import app.tandemforactual.ui.TandemSpacing
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemPalette
import app.tandemforactual.ui.tandemReduceMotion
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

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

internal sealed interface FeedbackMessageOwner {
    data class Field(
        val name: String,
    ) : FeedbackMessageOwner

    data class Screen(
        val name: String,
    ) : FeedbackMessageOwner

    data class Action(
        val name: String,
    ) : FeedbackMessageOwner
}

internal data class FeedbackMessage(
    val text: String,
    val kind: InlineMessageKind = InlineMessageKind.WARNING,
    val owner: FeedbackMessageOwner? = null,
    val detail: String? = null,
    val title: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)

internal enum class FeedbackPresentation {
    FIELD,
    BANNER,
    EMPTY_STATE,
}

internal fun inlineMessageLiveRegion(kind: InlineMessageKind): LiveRegionMode =
    if (kind == InlineMessageKind.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite

@Composable
internal fun InlineMessage(
    text: String,
    kind: InlineMessageKind = InlineMessageKind.WARNING,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    owner: FeedbackMessageOwner? = null,
) {
    FeedbackMessageView(
        message =
            FeedbackMessage(
                text = text,
                kind = kind,
                owner = owner,
                actionLabel = actionLabel,
                onAction = onAction,
                onDismiss = onDismiss,
            ),
        presentation = FeedbackPresentation.BANNER,
        modifier = modifier,
    )
}

@Composable
internal fun FieldErrorMessage(
    text: String?,
    owner: FeedbackMessageOwner? = null,
    modifier: Modifier = Modifier,
) {
    if (!text.isNullOrBlank()) {
        FeedbackMessageView(
            FeedbackMessage(text = text, kind = InlineMessageKind.ERROR, owner = owner),
            presentation = FeedbackPresentation.FIELD,
            modifier = modifier,
        )
    }
}

@Composable
internal fun FeedbackEmptyState(
    message: FeedbackMessage,
    modifier: Modifier = Modifier,
) {
    FeedbackMessageView(message, FeedbackPresentation.EMPTY_STATE, modifier)
}

@Composable
internal fun FeedbackMessageView(
    message: FeedbackMessage,
    presentation: FeedbackPresentation,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val color =
        when (message.kind) {
            InlineMessageKind.ERROR -> palette.negative
            InlineMessageKind.WARNING -> palette.warning
            InlineMessageKind.INFO -> palette.muted
            InlineMessageKind.SUCCESS -> palette.positive
        }
    val icon =
        when (message.kind) {
            InlineMessageKind.ERROR,
            InlineMessageKind.WARNING,
            -> Lucide.CircleAlert
            InlineMessageKind.INFO -> Lucide.Info
            InlineMessageKind.SUCCESS -> Lucide.Check
        }
    val kindDescription = message.kind.accessibilityLabel()
    val textModifier = Modifier.semantics { liveRegion = inlineMessageLiveRegion(message.kind) }
    when (presentation) {
        FeedbackPresentation.FIELD -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = kindDescription, modifier = Modifier.size(16.dp), tint = color)
                Text(
                    message.text,
                    modifier = textModifier.weight(1f),
                    color = color,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }

        FeedbackPresentation.BANNER -> {
            val shape = RoundedCornerShape(TandemShapes.medium)
            Row(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(color.copy(alpha = 0.08f))
                        .border(1.dp, color.copy(alpha = 0.24f), shape)
                        .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = kindDescription, modifier = Modifier.size(18.dp), tint = color)
                Column(
                    modifier = Modifier.weight(1f, fill = message.actionLabel == null || message.onAction == null),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                ) {
                    message.title?.let {
                        Text(it, color = palette.text, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        message.text,
                        modifier = textModifier,
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        lineHeight = TandemTypography.bodyLineHeight,
                    )
                    message.detail?.let { Text(it, color = palette.muted, fontSize = TandemTypography.caption) }
                }
                if (message.actionLabel != null && message.onAction != null) {
                    Text(
                        message.actionLabel,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(TandemShapes.small))
                                .clickable(role = Role.Button, onClick = message.onAction)
                                .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.small),
                        color = palette.accent,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                message.onDismiss?.let { dismiss ->
                    TandemIconButton(
                        icon = Lucide.X,
                        contentDescription = "Dismiss message",
                        sizeDp = 44,
                        iconSizeDp = 18,
                        tint = palette.muted,
                        onClick = dismiss,
                    )
                }
            }
        }

        FeedbackPresentation.EMPTY_STATE -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = kindDescription, modifier = Modifier.size(20.dp), tint = color)
                    Text(
                        message.title ?: message.text,
                        modifier = textModifier.weight(1f),
                        color = palette.text,
                        fontSize = TandemTypography.action,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (message.title != null) BodyText(message.text)
                message.detail?.let { BodyText(it) }
                if (message.actionLabel != null && message.onAction != null) {
                    SecondaryButton(message.actionLabel, onClick = message.onAction)
                }
            }
        }
    }
}

private fun InlineMessageKind.accessibilityLabel(): String =
    when (this) {
        InlineMessageKind.ERROR -> "Error"
        InlineMessageKind.WARNING -> "Warning"
        InlineMessageKind.INFO -> "Information"
        InlineMessageKind.SUCCESS -> "Success"
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
