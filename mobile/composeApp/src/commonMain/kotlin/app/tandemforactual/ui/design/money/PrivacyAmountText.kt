@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
    if (!privacyModeEnabled) {
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
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var tapReveal by remember { mutableStateOf(false) }
    var revealRequest by remember { mutableIntStateOf(0) }
    var wasHovered by remember { mutableStateOf(false) }
    LaunchedEffect(revealRequest) {
        if (revealRequest > 0) {
            delay(PRIVACY_AMOUNT_TAP_REVEAL_MILLIS)
            tapReveal = false
        }
    }
    LaunchedEffect(isHovered) {
        if (isHovered) {
            wasHovered = true
        } else if (wasHovered) {
            wasHovered = false
            tapReveal = false
        }
    }

    val hidden = !isHovered && !tapReveal

    fun revealBriefly() {
        tapReveal = true
        revealRequest += 1
    }
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
                            revealBriefly()
                        } else {
                            tapReveal = false
                        }
                    },
                ).clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = if (hidden) "Amount hidden" else text
                    stateDescription = if (hidden) "Hidden. Tap to reveal briefly." else "Visible temporarily."
                    onClick(actionLabel) {
                        if (hidden) {
                            revealBriefly()
                        } else {
                            tapReveal = false
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
