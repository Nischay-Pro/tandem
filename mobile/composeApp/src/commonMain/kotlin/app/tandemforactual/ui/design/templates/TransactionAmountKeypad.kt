@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun TransactionAmountKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onOperator: (String) -> Unit,
    onEquals: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Surface(modifier = modifier.fillMaxWidth().height(AMOUNT_KEYPAD_HEIGHT), color = palette.surface) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
        ) {
            Column(
                Modifier.weight(3.15f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        row.forEach { digit ->
                            AmountPadKey(digit.toString(), Modifier.weight(1f)) { onDigit(digit) }
                        }
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Spacer(Modifier.weight(1f).fillMaxSize())
                    AmountPadKey("0", Modifier.weight(1f)) { onDigit(0) }
                    AmountPadKey("⌫", Modifier.weight(1f), accessibilityLabel = "Delete last digit", onClick = onDelete)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                listOf(listOf("×", "÷"), listOf("+", "−")).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        row.forEach { operator ->
                            AmountPadKey(operator, Modifier.weight(1f), AmountPadKeyStyle.OPERATOR) { onOperator(operator) }
                        }
                    }
                }
                AmountPadKey("=", Modifier.weight(1f), AmountPadKeyStyle.EQUALS, "Calculate", onEquals)
                AmountPadKey("Done", Modifier.weight(1f), AmountPadKeyStyle.DONE, "Finish entering amount", onDone)
            }
        }
    }
}
