@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Trash2

@Composable
internal fun FieldLabel(label: String) {
    Text(label, color = tandemPalette.muted, fontSize = TandemTypography.label, fontWeight = FontWeight.Medium)
}

internal data class TextFieldOptions(
    val placeholder: String = "",
    val currencySymbol: String? = null,
    val currencySymbolAfter: Boolean = false,
    val currencySymbolSpace: Boolean = false,
    val amountFractionDigits: Int? = null,
    val singleLine: Boolean = true,
    val isSecret: Boolean = false,
    val secretKeyboardType: KeyboardType = KeyboardType.Password,
    val keyboardOptions: KeyboardOptions? = null,
    val keyboardActions: KeyboardActions? = null,
    val textAlign: TextAlign = TextAlign.Start,
    val large: Boolean = false,
    val onFocusedChange: ((Boolean) -> Unit)? = null,
    val leadingIcon: ImageVector? = null,
    val leadingIconTint: Color? = null,
    val leadingIconDescription: String? = null,
)

internal fun currencySymbolTransformation(
    symbol: String,
    afterAmount: Boolean,
    addSpace: Boolean,
    fractionDigits: Int = 0,
): VisualTransformation {
    val separator = if (addSpace) "\u202F" else ""
    return VisualTransformation { value ->
        val original = value.text
        if (original.isEmpty()) {
            TransformedText(value, OffsetMapping.Identity)
        } else {
            val negative = original.startsWith('-')
            val insertionLength = symbol.length + separator.length
            val transformed =
                when {
                    afterAmount -> "$original$separator$symbol"
                    negative -> "-${symbol}${separator}${original.drop(1)}"
                    else -> "$symbol$separator$original"
                }
            val offsetMapping =
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int =
                        when {
                            afterAmount -> offset.coerceIn(0, original.length)
                            negative && offset > 0 -> (offset + insertionLength).coerceAtMost(transformed.length)
                            negative -> 0
                            else -> (offset + insertionLength).coerceAtMost(transformed.length)
                        }

                    override fun transformedToOriginal(offset: Int): Int =
                        when {
                            afterAmount -> offset.coerceIn(0, original.length)
                            negative && offset == 0 -> 0
                            negative && offset <= 1 + insertionLength -> 1
                            negative && offset > 1 + insertionLength -> (offset - insertionLength).coerceIn(0, original.length)
                            offset <= insertionLength -> 0
                            else -> (offset - insertionLength).coerceIn(0, original.length)
                        }
                }
            TransformedText(amountAnnotatedString(transformed, fractionDigits), offsetMapping)
        }
    }
}

internal fun amountFractionTransformation(fractionDigits: Int): VisualTransformation =
    VisualTransformation { value ->
        TransformedText(amountAnnotatedString(value.text, fractionDigits), OffsetMapping.Identity)
    }

internal fun transactionTypeColor(
    type: String,
    palette: TandemPalette,
) = when (type) {
    "Income" -> palette.positive
    "Transfer" -> palette.accent
    else -> palette.negative
}

@Composable
internal fun AmountFieldShell(
    fill: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TandemShapes.large)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, outline, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Enter amount", onClick = onClick)
                } else {
                    Modifier
                },
            ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun TransactionTypeChip(
    type: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val typeTint = transactionTypeColor(type, palette)
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(typeTint.copy(alpha = 0.12f))
                .border(1.dp, typeTint.copy(alpha = 0.35f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Choose transaction type", onClick = onClick)
                .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(Lucide.Receipt, contentDescription = null, modifier = Modifier.size(20.dp), tint = typeTint)
        Text(type, color = typeTint, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(19.dp), tint = typeTint.copy(alpha = 0.82f))
    }
}

internal enum class AmountPadKeyStyle {
    NUMBER,
    OPERATOR,
    EQUALS,
    DONE,
}

internal val AMOUNT_KEYPAD_HEIGHT = 252.dp

@Composable
internal fun FloatingTransactionDeleteAction(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(palette.negative.copy(alpha = if (enabled) 0.15f else 0.08f))
                .border(1.dp, palette.negative.copy(alpha = if (enabled) 0.75f else 0.32f), CircleShape)
                .semantics(mergeDescendants = true) { contentDescription = "Delete transaction" }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Delete transaction", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Lucide.Trash2,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = if (enabled) palette.negative else palette.muted,
        )
    }
}

@Composable
internal fun ReadOnlyField(
    label: String,
    value: String,
    leadingVector: ImageVector,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    FieldFrame(label, onClickLabel = "$label, $value", onClick = onClick) {
        Icon(leadingVector, contentDescription = null, modifier = Modifier.size(30.dp), tint = palette.accent)
        Text(value, color = palette.text, fontSize = TandemTypography.body, modifier = Modifier.weight(1f))
        Icon(
            Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp).clearAndSetSemantics {},
            tint = palette.muted,
        )
    }
}
