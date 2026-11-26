@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.feedback

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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun BodyText(text: String) {
    Text(
        amountAnnotatedString(text),
        color = tandemPalette.muted,
        fontSize = TandemTypography.body,
        lineHeight = TandemTypography.bodyLineHeight,
    )
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
internal fun EmptyStateCard(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SurfaceCard(modifier) {
        EmptyState(title, message, Modifier.fillMaxWidth().padding(TandemSpacing.large), actionLabel, onAction)
    }
}

@Composable
internal fun LabelText(text: String) {
    Text(
        amountAnnotatedString(text),
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
