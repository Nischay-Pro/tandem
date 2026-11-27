@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.delay

@Composable
internal fun PrivacyAmountText(
    text: String,
    privacyModeEnabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TandemTypography.body,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    fractionDigits: Int = 2,
) {
    val resolvedColor = if (color == Color.Unspecified) tandemPalette.text else color
    if (!privacyModeEnabled && maxLines == 1 && fontSize.isSp) {
        BasicText(
            amountAnnotatedString(text, fractionDigits),
            modifier,
            style =
                LocalTextStyle.current.merge(
                    TextStyle(
                        color = resolvedColor,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        textAlign = textAlign ?: TextAlign.Unspecified,
                    ),
                ),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = fontSize * AMOUNT_MIN_SCALE, maxFontSize = fontSize, stepSize = 0.5.sp),
        )
    } else if (!privacyModeEnabled) {
        Text(
            text = amountAnnotatedString(text, fractionDigits),
            modifier = modifier,
            color = resolvedColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
        )
    } else {
        MaskedAmountText(text, modifier, resolvedColor, fontSize, fontWeight, textAlign, maxLines, overflow, fractionDigits)
    }
}

/** A one-line amount shrinks to this share of its size before it's cut short, so a long figure stays whole. */
private const val AMOUNT_MIN_SCALE = 0.7f

/** Tap/hover reveal state of a masked amount. */
private class PrivacyRevealState {
    var tapReveal by mutableStateOf(false)
    var revealRequest by mutableIntStateOf(0)
    var wasHovered by mutableStateOf(false)

    fun revealBriefly() {
        tapReveal = true
        revealRequest += 1
    }
}

@Composable
private fun rememberPrivacyRevealState(isHovered: Boolean): PrivacyRevealState {
    val state = remember { PrivacyRevealState() }
    LaunchedEffect(state.revealRequest) {
        if (state.revealRequest > 0) {
            delay(PRIVACY_AMOUNT_TAP_REVEAL_MILLIS)
            state.tapReveal = false
        }
    }
    LaunchedEffect(isHovered) {
        if (isHovered) {
            state.wasHovered = true
        } else if (state.wasHovered) {
            state.wasHovered = false
            state.tapReveal = false
        }
    }
    return state
}

/** Amount shown as a mask until hovered or tapped. */
@Composable
private fun MaskedAmountText(
    text: String,
    modifier: Modifier,
    resolvedColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    textAlign: TextAlign?,
    maxLines: Int,
    overflow: TextOverflow,
    fractionDigits: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val reveal = rememberPrivacyRevealState(isHovered)
    val hidden = !isHovered && !reveal.tapReveal
    val actionLabel = if (hidden) "Reveal amount" else "Hide amount"
    Text(
        text = if (hidden) AnnotatedString(PRIVACY_AMOUNT_MASK) else amountAnnotatedString(text, fractionDigits),
        modifier =
            modifier
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = {
                        if (hidden) {
                            reveal.revealBriefly()
                        } else {
                            reveal.tapReveal = false
                        }
                    },
                ).clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = if (hidden) "Amount hidden" else text
                    stateDescription = if (hidden) "Hidden. Tap to reveal briefly." else "Visible temporarily."
                    onClick(actionLabel) {
                        if (hidden) {
                            reveal.revealBriefly()
                        } else {
                            reveal.tapReveal = false
                        }
                        true
                    }
                },
        color = resolvedColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private const val PRIVACY_AMOUNT_TAP_REVEAL_MILLIS = 3_000L
private const val PRIVACY_AMOUNT_MASK = "••••••"
