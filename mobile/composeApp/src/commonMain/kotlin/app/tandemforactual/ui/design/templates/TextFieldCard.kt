@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide

@Composable
internal fun TextFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: TextFieldOptions = TextFieldOptions(),
) {
    val palette = tandemPalette
    var secretRevealed by remember { mutableStateOf(false) }
    FieldFrame(label, large = options.large) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = options.singleLine,
            keyboardOptions =
                options.keyboardOptions ?: if (options.isSecret) {
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = options.secretKeyboardType,
                    )
                } else {
                    KeyboardOptions.Default
                },
            keyboardActions = options.keyboardActions ?: KeyboardActions.Default,
            visualTransformation = textFieldVisualTransformation(options, secretRevealed),
            textStyle =
                TextStyle(
                    color = palette.text,
                    fontSize = if (options.large) TandemTypography.amount else TandemTypography.action,
                    lineHeight = if (options.large) TandemTypography.largeInputLineHeight else TandemTypography.bodyLineHeight,
                    fontWeight = if (options.large) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = options.textAlign,
                ),
            cursorBrush = SolidColor(palette.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState -> options.onFocusedChange?.invoke(focusState.isFocused) }
                    .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                TextFieldDecoration(value, options, secretRevealed, { secretRevealed = !secretRevealed }, innerTextField)
            },
        )
    }
}

private fun textFieldVisualTransformation(
    options: TextFieldOptions,
    secretRevealed: Boolean,
): VisualTransformation =
    when {
        options.isSecret && secretRevealed -> {
            VisualTransformation.None
        }

        options.isSecret -> {
            PasswordVisualTransformation()
        }

        !options.currencySymbol.isNullOrBlank() -> {
            currencySymbolTransformation(
                options.currencySymbol,
                options.currencySymbolAfter,
                options.currencySymbolSpace,
                options.amountFractionDigits ?: 0,
            )
        }

        options.amountFractionDigits != null -> {
            amountFractionTransformation(options.amountFractionDigits)
        }

        else -> {
            VisualTransformation.None
        }
    }

/** Leading icon, placeholder, input and secret toggle around a text field. */
@Composable
private fun TextFieldDecoration(
    value: String,
    options: TextFieldOptions,
    secretRevealed: Boolean,
    onToggleSecret: () -> Unit,
    innerTextField: @Composable () -> Unit,
) {
    val palette = tandemPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        options.leadingIcon?.let { icon ->
            Icon(
                icon,
                contentDescription = options.leadingIconDescription,
                modifier = Modifier.padding(end = TandemSpacing.small).size(18.dp),
                tint = options.leadingIconTint ?: palette.muted,
            )
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && options.placeholder.isNotEmpty()) {
                Text(
                    options.placeholder,
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.muted,
                    fontSize = if (options.large) TandemTypography.keypadNumber else TandemTypography.body,
                    textAlign = options.textAlign,
                )
            }
            innerTextField()
        }
        if (options.isSecret) {
            TandemIconButton(
                if (secretRevealed) Lucide.EyeOff else Lucide.Eye,
                if (secretRevealed) "Hide password" else "Show password",
                sizeDp = 32,
                iconSizeDp = 18,
                tint = palette.muted,
            ) { onToggleSecret() }
        }
    }
}
