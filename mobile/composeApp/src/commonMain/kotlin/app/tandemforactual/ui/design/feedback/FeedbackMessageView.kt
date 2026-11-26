@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.feedback

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

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
        FeedbackPresentation.FIELD -> FieldFeedback(message, icon, color, kindDescription, textModifier, modifier)
        FeedbackPresentation.BANNER -> BannerFeedback(message, icon, color, kindDescription, textModifier, modifier)
    }
}

/** Compact inline message shown under a form field. */
@Composable
private fun FieldFeedback(
    message: FeedbackMessage,
    icon: ImageVector,
    color: Color,
    kindDescription: String,
    textModifier: Modifier,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = kindDescription, modifier = Modifier.size(16.dp), tint = color)
        Text(
            amountAnnotatedString(message.text),
            modifier = textModifier.weight(1f),
            color = color,
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.bodyLineHeight,
        )
    }
}

/** Tinted banner with optional title, detail, action and dismiss button. */
@Composable
private fun BannerFeedback(
    message: FeedbackMessage,
    icon: ImageVector,
    color: Color,
    kindDescription: String,
    textModifier: Modifier,
    modifier: Modifier,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.large)
    val tone = if (message.kind == InlineMessageKind.INFO) palette.accent else color
    val hasAction = message.actionLabel != null && message.onAction != null
    val simple = message.title == null && message.detail == null && !hasAction
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tone.copy(alpha = 0.08f))
                .border(1.dp, tone.copy(alpha = 0.3f), shape)
                .padding(
                    start = TandemSpacing.medium,
                    end = if (message.onDismiss != null) TandemSpacing.extraSmall else TandemSpacing.medium,
                    top = TandemSpacing.medium,
                    bottom = TandemSpacing.medium,
                ),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = if (simple) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = kindDescription, modifier = Modifier.size(18.dp), tint = tone)
        }
        BannerFeedbackText(message, tone, hasAction, simple, textModifier)
        message.onDismiss?.let { dismiss ->
            TandemIconButton(
                icon = Lucide.X,
                contentDescription = "Dismiss message",
                sizeDp = 36,
                iconSizeDp = 16,
                tint = palette.muted,
                onClick = dismiss,
            )
        }
    }
}

/** Title, message, detail and action column of a feedback banner. */
@Composable
private fun RowScope.BannerFeedbackText(
    message: FeedbackMessage,
    tone: Color,
    hasAction: Boolean,
    simple: Boolean,
    textModifier: Modifier,
) {
    val palette = tandemPalette
    Column(
        modifier = Modifier.weight(1f).padding(top = if (simple) 0.dp else TandemSpacing.xxSmall),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        message.title?.let {
            Text(it, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
        }
        Text(
            amountAnnotatedString(message.text),
            modifier = textModifier,
            color = if (message.title != null) palette.muted else palette.text,
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.bodyLineHeight,
        )
        message.detail?.let {
            Text(amountAnnotatedString(it), color = palette.muted, fontSize = TandemTypography.caption)
        }
        if (hasAction) {
            val actionInteractions = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clickable(actionInteractions, indication = null, role = Role.Button, onClick = message.onAction!!),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    message.actionLabel!!,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(TandemShapes.capsule))
                            .background(tone.copy(alpha = 0.16f))
                            .indication(actionInteractions, LocalIndication.current)
                            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
                    color = tone,
                    fontSize = TandemTypography.compactBody,
                    fontWeight = FontWeight.SemiBold,
                )
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
