@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

@Composable
internal fun TransactionAmountField(
    amount: String,
    amountMinorUnits: Long,
    transactionType: String,
    neutralDisplay: Boolean = false,
    showCursor: Boolean = false,
    fractionDigits: Int = 2,
    onShowKeypad: () -> Unit,
    amountColorOverride: Color? = null,
) {
    val palette = tandemPalette
    val entered = amountMinorUnits != 0L
    val amountColor =
        amountColorOverride ?: if (neutralDisplay) {
            palette.text
        } else {
            transactionTypeColor(transactionType, palette)
        }
    val outline = if (entered && !neutralDisplay) amountColor else palette.outline
    val fill = if (entered && !neutralDisplay) amountColor.copy(alpha = 0.08f) else palette.surface
    val display = amount.ifBlank { "0.00" }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val cursorAlpha by rememberCursorAlpha(blinking = showCursor && !tandemReduceMotion)
    AmountFieldShell(
        fill = fill,
        outline = outline,
        onClick = onShowKeypad,
    ) {
        Text(
            amountAnnotatedString(display, fractionDigits),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Amount, $display" }
                    .drawWithContent {
                        drawContent()
                        val caret = textLayout?.getCursorRect(display.length)
                        if (showCursor && caret != null) {
                            val inset = 3.dp.toPx()
                            drawLine(
                                color = palette.text.copy(alpha = cursorAlpha),
                                start = Offset(caret.left, caret.top + inset),
                                end = Offset(caret.left, caret.bottom - inset),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
            color = amountColor,
            fontSize = TandemTypography.amountField,
            lineHeight = TandemTypography.amountFieldLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayout = it },
        )
    }
}

@Composable
private fun rememberCursorAlpha(blinking: Boolean): State<Float> =
    if (blinking) {
        rememberInfiniteTransition(label = "amount-cursor").animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(520), repeatMode = RepeatMode.Reverse),
            label = "amount-cursor-alpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
