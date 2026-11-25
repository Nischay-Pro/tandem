@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.SplitLine
import app.tandemforactual.ui.core.state.TransactionSplitDraft
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.TransactionPickerRow
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Split
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.X

/** Starts splitting the transaction, with the form's category and amount on the first line. */
@Composable
internal fun TransactionSplitButton(
    state: AppState,
    onDismissKeypad: () -> Unit,
) {
    TandemActionButton("Split transaction", TandemActionButtonTone.NEUTRAL, icon = Lucide.Split) {
        onDismissKeypad()
        state.transactionSplit =
            TransactionSplitDraft.start(state.transactionAmount, state.newTransactionCategoryId, state.transactionCategory)
    }
}

/**
 * Splits the transaction across categories: a line per category with its share of the amount, and how much is still
 * unassigned. Saving waits until the lines add up to the total, as Actual requires.
 */
@Composable
internal fun TransactionSplitCard(
    state: AppState,
    navigation: AppNavigation,
    totalMinor: Long,
    onDismissKeypad: () -> Unit,
) {
    val split = state.transactionSplit ?: return
    val parse = state::parseAmountMinorUnits
    val remaining = split.remaining(totalMinor, parse)
    SectionCard(
        "Split",
        splitStatus(state, remaining),
        action = "Remove split",
        onClick = {
            state.transactionCategory =
                split.lines
                    .firstOrNull()
                    ?.categoryName
                    .orEmpty()
            state.newTransactionCategoryId =
                split.lines
                    .firstOrNull()
                    ?.categoryId
                    .orEmpty()
            state.transactionSplit = null
        },
    ) {
        split.lines.forEachIndexed { index, line ->
            if (index > 0) DividerLine()
            SplitLineRow(state, line, removable = split.lines.size > 2) {
                onDismissKeypad()
                state.transactionSplit = split.choosingCategoryFor(line.key)
                navigation.open(AppRoute.CHOOSE_CATEGORY)
            }
        }
        DividerLine()
        ListRow(
            "Add a category",
            leading = RowLeading.Icon(Lucide.Plus),
            onClick = { state.transactionSplit = split.addLine(if (remaining > 0) state.formatAmount(remaining) else "") },
        )
    }
}

private fun splitStatus(
    state: AppState,
    remaining: Long,
): String =
    when {
        remaining == 0L -> "Every part is assigned"
        remaining > 0 -> "${state.formatAmount(remaining)} left to assign"
        else -> "${state.formatAmount(-remaining)} more than the total"
    }

/** A line's category and its share of the amount, with a way to drop the line when there are more than two. */
@Composable
private fun SplitLineRow(
    state: AppState,
    line: SplitLine,
    removable: Boolean,
    onChooseCategory: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(end = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Box(Modifier.weight(1f)) { TransactionPickerRow(line.categoryName, "Choose category", Lucide.Tags, onChooseCategory) }
        SplitAmountField(state, line.amountText) { text ->
            state.transactionSplit = state.transactionSplit?.setAmount(line.key, text)
        }
        if (removable) {
            IconButton(onClick = { state.transactionSplit = state.transactionSplit?.removeLine(line.key) }) {
                Icon(Lucide.X, contentDescription = "Remove ${line.categoryName.ifBlank { "this part" }}", tint = tandemPalette.muted)
            }
        }
    }
}

@Composable
private fun SplitAmountField(
    state: AppState,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val palette = tandemPalette
    Column(
        Modifier
            .width(112.dp)
            .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.control))
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle =
                TextStyle(
                    color = palette.text,
                    fontSize = TandemTypography.body,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                ),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Amount" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(state.formatAmount(0), color = palette.muted, fontSize = TandemTypography.body)
                    inner()
                }
            },
        )
    }
}
