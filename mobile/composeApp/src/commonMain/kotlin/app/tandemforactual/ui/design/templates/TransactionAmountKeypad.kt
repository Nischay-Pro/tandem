@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette

internal const val AMOUNT_KEYPAD_TAG = "amount-keypad"

/** What the keypad does for each key, whether it's tapped or typed on a hardware keyboard. */
internal class AmountKeypadActions(
    val onDigit: (Int) -> Unit,
    val onDelete: () -> Unit,
    val onOperator: (String) -> Unit,
    val onEquals: () -> Unit,
    val onDone: () -> Unit,
    val onDecimal: (() -> Unit)? = null,
) {
    fun withoutDecimal(): AmountKeypadActions = AmountKeypadActions(onDigit, onDelete, onOperator, onEquals, onDone)
}

@Composable
internal fun TransactionAmountKeypad(
    actions: AmountKeypadActions,
    modifier: Modifier = Modifier,
    height: Dp = AMOUNT_KEYPAD_HEIGHT,
    decimalSeparator: Char = '.',
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .testTag(AMOUNT_KEYPAD_TAG)
                .focusRequester(focus)
                .onKeyEvent { handleAmountKeypadKey(it, actions) }
                .focusable(),
        color = tandemPalette.surface,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = TandemSpacing.extraSmall
            val sidePadding =
                ((maxWidth - KEYPAD_NARROW_WIDTH) / 4 + TandemSpacing.small).coerceIn(TandemSpacing.small, TandemSpacing.large)
            val rowsHeight = MIN_TOUCH_TARGET * 4 + gap * 3
            val endPadding = ((maxHeight - rowsHeight) / 2).coerceIn(TandemSpacing.xxSmall, TandemSpacing.medium)
            val inner = maxWidth - sidePadding * 2 - gap
            val operatorsWidth = maxOf(inner / KEYPAD_WEIGHTS, MIN_TOUCH_TARGET * 2 + gap).coerceAtMost(inner / 2)
            Row(
                Modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = endPadding),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                AmountKeypadDigits(actions, decimalSeparator, Modifier.weight(1f).fillMaxHeight())
                AmountKeypadOperators(actions, Modifier.width(operatorsWidth).fillMaxHeight())
            }
        }
    }
}

private val KEYPAD_NARROW_WIDTH = 280.dp
private const val KEYPAD_WEIGHTS = 4.15f

@Composable
private fun AmountKeypadDigits(
    actions: AmountKeypadActions,
    decimalSeparator: Char,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                row.forEach { digit ->
                    AmountPadKey(digit.toString(), Modifier.weight(1f)) { actions.onDigit(digit) }
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            val onDecimal = actions.onDecimal
            if (onDecimal != null) {
                AmountPadKey(
                    decimalSeparator.toString(),
                    Modifier.weight(1f),
                    accessibilityLabel = "Decimal separator",
                    onClick = onDecimal,
                )
            } else {
                Spacer(Modifier.weight(1f).fillMaxSize())
            }
            AmountPadKey("0", Modifier.weight(1f)) { actions.onDigit(0) }
            AmountPadKey("⌫", Modifier.weight(1f), accessibilityLabel = "Delete last digit", onClick = actions.onDelete)
        }
    }
}

@Composable
private fun AmountKeypadOperators(
    actions: AmountKeypadActions,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        listOf(listOf("×", "÷"), listOf("+", "−")).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                row.forEach { operator ->
                    AmountPadKey(operator, Modifier.weight(1f), AmountPadKeyStyle.OPERATOR) { actions.onOperator(operator) }
                }
            }
        }
        AmountPadKey("=", Modifier.weight(1f), AmountPadKeyStyle.EQUALS, "Calculate", actions.onEquals)
        AmountPadKey("Done", Modifier.weight(1f), AmountPadKeyStyle.DONE, "Finish entering amount", actions.onDone)
    }
}

/** Routes a hardware key to the keypad: digits, either decimal mark, + − × ÷ and =, Backspace, and Enter to finish. */
internal fun handleAmountKeypadKey(
    event: KeyEvent,
    actions: AmountKeypadActions,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return false
    when (event.key) {
        Key.Backspace, Key.Delete -> {
            actions.onDelete()
        }

        Key.Enter, Key.NumPadEnter -> {
            actions.onDone()
        }

        else -> {
            val character = event.utf16CodePoint.takeIf { it > 0 }?.toChar() ?: amountKeypadKeyCharacters[event.key]
            return character != null && handleAmountKeypadCharacter(character, actions)
        }
    }
    return true
}

private val amountKeypadKeyCharacters: Map<Key, Char> =
    listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        .mapIndexed { digit, key -> key to '0' + digit }
        .toMap() +
        listOf(
            Key.NumPad0,
            Key.NumPad1,
            Key.NumPad2,
            Key.NumPad3,
            Key.NumPad4,
            Key.NumPad5,
            Key.NumPad6,
            Key.NumPad7,
            Key.NumPad8,
            Key.NumPad9,
        ).mapIndexed { digit, key -> key to '0' + digit }
            .toMap() +
        mapOf(
            Key.Period to '.',
            Key.Comma to ',',
            Key.NumPadDot to '.',
            Key.NumPadComma to ',',
            Key.Plus to '+',
            Key.NumPadAdd to '+',
            Key.Minus to '-',
            Key.NumPadSubtract to '-',
            Key.NumPadMultiply to '*',
            Key.Slash to '/',
            Key.NumPadDivide to '/',
            Key.Equals to '=',
            Key.NumPadEquals to '=',
        )

private fun handleAmountKeypadCharacter(
    character: Char,
    actions: AmountKeypadActions,
): Boolean {
    when (character) {
        in '0'..'9' -> actions.onDigit(character - '0')
        '.', ',' -> actions.onDecimal?.invoke() ?: return false
        '+' -> actions.onOperator("+")
        '-', '−' -> actions.onOperator("−")
        '*', '×' -> actions.onOperator("×")
        '/', '÷' -> actions.onOperator("÷")
        '=' -> actions.onEquals()
        else -> return false
    }
    return true
}
