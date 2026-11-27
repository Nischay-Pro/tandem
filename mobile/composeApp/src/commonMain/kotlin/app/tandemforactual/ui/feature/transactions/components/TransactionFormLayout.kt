@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.design.feedback.reservesNoticeSpace
import app.tandemforactual.ui.design.templates.AMOUNT_KEYPAD_HEIGHT
import app.tandemforactual.ui.design.templates.AmountKeypadActions
import app.tandemforactual.ui.design.templates.TransactionAmountKeypad
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.WindowSizeInfo
import app.tandemforactual.ui.design.theme.WindowWidthClass
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemFadeExit
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit

internal val SHORT_FORM_MAX_HEIGHT = 560.dp
private val KEYPAD_SIDE_WIDTH = 320.dp
private val KEYPAD_BESIDE_MIN_WIDTH = KEYPAD_SIDE_WIDTH + TandemLayout.minDetailPaneWidth
private val KEYPAD_SIDE_MAX_HEIGHT = 320.dp
private val KEYPAD_MIN_HEIGHT = 168.dp
private val KEYPAD_MAX_WIDTH = 560.dp

/** Where the amount keypad goes and how tall it is, from the space the form has. */
@Immutable
internal data class TransactionFormLayout(
    val keypadBeside: Boolean,
    val keypadHeight: Dp,
    val shortWindow: Boolean,
) {
    /** A short window with the keypad under the form leaves too little room to show both, so the keypad starts closed. */
    val keypadStartsClosed: Boolean get() = shortWindow && !keypadBeside
}

internal fun transactionFormLayout(
    window: WindowSizeInfo,
    width: Dp,
    height: Dp,
): TransactionFormLayout {
    val beside = window.widthClass >= WindowWidthClass.MEDIUM && width >= KEYPAD_BESIDE_MIN_WIDTH
    val keypadHeight =
        if (beside) {
            min(KEYPAD_SIDE_MAX_HEIGHT, max(KEYPAD_MIN_HEIGHT, height - TandemSpacing.large * 2))
        } else {
            (height * 0.4f).coerceIn(KEYPAD_MIN_HEIGHT, AMOUNT_KEYPAD_HEIGHT)
        }
    return TransactionFormLayout(beside, keypadHeight, window.compactHeight || height < SHORT_FORM_MAX_HEIGHT)
}

/** Measures the space the form has, above the software keyboard, and lays it out for that space. */
@Composable
internal fun TransactionFormFrame(content: @Composable (TransactionFormLayout) -> Unit) {
    val window = currentWindowSizeInfo()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        content(transactionFormLayout(window, maxWidth, maxHeight))
    }
}

/** Closes the keypad when the window becomes too short to show it under the form. */
@Composable
internal fun CollapseKeypadWhenShort(
    layout: TransactionFormLayout,
    entry: TransactionAmountEntryState,
) {
    LaunchedEffect(layout.keypadStartsClosed) {
        if (layout.keypadStartsClosed) entry.keypadVisible = false
    }
}

/** The form and its action bar, with the amount keypad under them, or beside them in a wide window. */
@Composable
internal fun TransactionFormScaffold(
    layout: TransactionFormLayout,
    keypadVisible: Boolean,
    keypad: @Composable (Modifier) -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = tandemReduceMotion
    if (layout.keypadBeside) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight(), content = form)
            AnimatedVisibility(
                visible = keypadVisible,
                enter = expandHorizontally(tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS)) + tandemFadeEnter(reduceMotion),
                exit = shrinkHorizontally(tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS)) + tandemFadeExit(reduceMotion),
            ) {
                Box(Modifier.fillMaxHeight().padding(vertical = TandemSpacing.large), contentAlignment = Alignment.BottomCenter) {
                    keypad(Modifier.width(KEYPAD_SIDE_WIDTH))
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            form()
            AnimatedVisibility(
                visible = keypadVisible,
                enter = tandemRevealEnter(reduceMotion),
                exit = tandemRevealExit(reduceMotion),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    keypad(Modifier.widthIn(max = KEYPAD_MAX_WIDTH))
                }
            }
        }
    }
}

/** The transaction keypad sized for [layout], with the budget's decimal separator when amounts have a fraction. */
@Composable
internal fun TransactionFormKeypad(
    state: AppState,
    layout: TransactionFormLayout,
    actions: AmountKeypadActions,
    modifier: Modifier,
) {
    val fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2
    TransactionAmountKeypad(
        actions = if (fractionDigits > 0) actions else actions.withoutDecimal(),
        modifier = modifier.reservesNoticeSpace(),
        height = layout.keypadHeight,
        decimalSeparator = state.formatAmount(1).firstOrNull { !it.isDigit() } ?: '.',
    )
}
