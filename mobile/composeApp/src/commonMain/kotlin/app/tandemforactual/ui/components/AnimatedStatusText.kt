@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.tandemforactual.ui.TandemMotion
import app.tandemforactual.ui.TandemTypography
import app.tandemforactual.ui.tandemMotionSpec
import app.tandemforactual.ui.tandemReduceMotion

@Composable
internal fun AnimatedStatusText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = TandemTypography.body,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    animateSizeChanges: Boolean = false,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedContent(
        targetState = AnimatedTextContent(text, color),
        modifier =
            modifier.then(
                if (animateSizeChanges) {
                    Modifier.animateContentSize(
                        animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
                    )
                } else {
                    Modifier
                },
            ),
        transitionSpec = {
            val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.STATUS_CHANGE_MILLIS)
            enter togetherWith exit using null
        },
        contentKey = { it.text },
        contentAlignment = Alignment.CenterStart,
        label = "sync-status-text-transition",
    ) { visibleText ->
        val visibleColor by
            animateColorAsState(
                targetValue = visibleText.color,
                animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.COLOR_MILLIS),
                label = "sync-status-text-color",
            )
        Text(
            visibleText.text,
            color = visibleColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

private data class AnimatedTextContent(
    val text: String,
    val color: Color,
)

internal fun crossfadeTransitions(
    reduceMotion: Boolean,
    durationMillis: Int,
): Pair<EnterTransition, ExitTransition> =
    if (reduceMotion) {
        EnterTransition.None to ExitTransition.None
    } else {
        val fadeMillis = minOf(TandemMotion.CROSSFADE_MILLIS, durationMillis)
        Pair(
            fadeIn(tween(fadeMillis, easing = LinearEasing)),
            fadeOut(tween(fadeMillis, easing = LinearEasing)),
        )
    }
