@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.grid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.keyboard.keyPress
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

internal val GridCellMinHeight = TandemLayout.compactListRowMinHeight
internal const val GRID_EDITOR_TAG = "budget-grid-editor"
private val BAR_HEIGHT = 3.dp
private val PENDING_DOT = 6.dp
private const val APPROACHING = 0.9f

/** The budgeted amount as typed: arithmetic is allowed, and an amount that doesn't add up turns the field red. */
@Composable
internal fun BudgetGridEditor(
    context: GridCellContext,
    modifier: Modifier = Modifier,
) {
    val grid = context.grid
    val palette = tandemPalette
    val requester = remember { FocusRequester() }
    val hadFocus = remember { mutableStateOf(false) }
    LaunchedEffect(requester) { runCatching { requester.requestFocus() } }
    val outline by animateColorAsState(
        if (grid.draftInvalid) palette.negative else palette.accent,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "grid editor outline",
    )
    BasicTextField(
        value = grid.draft,
        onValueChange = { grid.draft = it },
        modifier =
            modifier
                .testTag(GRID_EDITOR_TAG)
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused) hadFocus.value = true
                    if (!it.isFocused && hadFocus.value && grid.editing) grid.finishEditing(context.data, context.io)
                }.onPreviewKeyEvent { grid.onEditorKey(it.keyPress(), context.data, context.io) }
                .background(palette.surface, RoundedCornerShape(TandemShapes.small))
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRect(outline, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
                }.padding(horizontal = TandemSpacing.extraSmall, vertical = TandemSpacing.xxSmall),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { grid.commit(context.data, context.io) }),
        textStyle =
            TextStyle(
                color = palette.text,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            ),
        cursorBrush = SolidColor(palette.accent),
    )
}

/** One of a cell's three figures; a dot marks a budgeted amount still waiting to sync. */
@Composable
internal fun RowScope.GridAmountText(
    state: AppState,
    amountMinor: Long?,
    color: Color,
    bold: Boolean = false,
    pending: Boolean = false,
) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (pending) {
            Box(
                Modifier
                    .padding(end = TandemSpacing.extraSmall)
                    .size(PENDING_DOT)
                    .background(tandemPalette.syncing, CircleShape)
                    .semantics { contentDescription = "Waiting to sync" },
            )
        }
        if (amountMinor == null) {
            Text("–", color = tandemPalette.muted, fontSize = TandemTypography.caption, textAlign = TextAlign.End)
        } else {
            PrivacyAmountText(
                state.formatCurrency(amountMinor),
                state.privacyModeEnabled,
                color = color,
                fontSize = TandemTypography.caption,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.End,
                fractionDigits = state.currencyFractionDigitsForDisplay,
            )
        }
    }
}

/** How much of the budgeted amount is spent: calm while under, amber when nearly gone, red once over. */
@Composable
internal fun GridSpendingBar(
    amounts: GridAmounts,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    val fraction = spendingFraction(amounts)
    val color =
        when (spendingStatus(amounts)) {
            SpendingStatus.OVER -> palette.negative
            SpendingStatus.APPROACHING -> palette.warning
            SpendingStatus.UNDER -> palette.positive
        }
    val shown by animateFloatAsState(
        fraction,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
        label = "grid spending",
    )
    val tint by animateColorAsState(color, tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS), label = "grid spending color")
    val track = palette.border
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TandemSpacing.small)
            .height(BAR_HEIGHT)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                if (shown > 0f) drawRoundRect(tint, size = Size(size.width * shown, size.height), cornerRadius = radius)
            },
    )
}

internal enum class SpendingStatus { UNDER, APPROACHING, OVER }

internal fun spendingFraction(amounts: GridAmounts): Float =
    when {
        amounts.spent <= 0 -> 0f
        amounts.budgeted <= 0 -> 1f
        else -> (amounts.spent.toFloat() / amounts.budgeted).coerceIn(0f, 1f)
    }

internal fun spendingStatus(amounts: GridAmounts): SpendingStatus =
    when {
        amounts.balance < 0 || amounts.spent > amounts.budgeted -> SpendingStatus.OVER
        amounts.spent > 0 && spendingFraction(amounts) >= APPROACHING -> SpendingStatus.APPROACHING
        else -> SpendingStatus.UNDER
    }

@Composable
internal fun balanceColor(amountMinor: Long): Color =
    when {
        amountMinor < 0 -> tandemPalette.negative
        amountMinor > 0 -> tandemPalette.positive
        else -> tandemPalette.muted
    }
