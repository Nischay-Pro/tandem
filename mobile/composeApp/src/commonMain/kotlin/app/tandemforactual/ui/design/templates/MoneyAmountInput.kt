@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun MoneyAmountInput(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fractionDigits: Int = 2,
) {
    val palette = tandemPalette
    val entered = value.isNotBlank()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        AmountFieldShell(
            fill = if (entered) palette.accent.copy(alpha = 0.08f) else palette.surface,
            outline = if (entered) palette.accent else palette.border,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = amountFractionTransformation(fractionDigits),
                textStyle =
                    TextStyle(
                        color = palette.accent,
                        fontSize = TandemTypography.amountField,
                        lineHeight = TandemTypography.amountFieldLineHeight,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                color = palette.muted,
                                fontSize = TandemTypography.keypadNumber,
                                textAlign = TextAlign.Center,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}
