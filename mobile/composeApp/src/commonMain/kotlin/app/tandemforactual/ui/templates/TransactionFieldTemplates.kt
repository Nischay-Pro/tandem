@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Trash2

@Composable
internal fun FieldLabel(label: String) {
    Text(label, color = tandemPalette.muted, fontSize = TandemTypography.label, fontWeight = FontWeight.Medium)
}

@Composable
internal fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(TandemSpacing.medium),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    val shape = RoundedCornerShape(TandemShapes.medium)
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        FieldLabel(label)
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = if (large) 82.dp else 52.dp)
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ).padding(
                        horizontal = if (large) TandemSpacing.screen else TandemSpacing.large,
                        vertical = if (large) TandemSpacing.screen else TandemSpacing.medium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
        ) {
            content()
        }
    }
}

internal data class TextFieldOptions(
    val placeholder: String = "",
    val singleLine: Boolean = true,
    val isSecret: Boolean = false,
    val secretKeyboardType: KeyboardType = KeyboardType.Password,
    val keyboardOptions: KeyboardOptions? = null,
    val textAlign: TextAlign = TextAlign.Start,
    val large: Boolean = false,
    val onFocusedChange: ((Boolean) -> Unit)? = null,
)

@Composable
internal fun TextFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: TextFieldOptions = TextFieldOptions(),
) {
    val palette = tandemPalette
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
            visualTransformation = if (options.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
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
                Box {
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
            },
        )
    }
}

@Composable
internal fun TransactionAmountField(
    amount: String,
    amountMinorUnits: Long,
    transactionType: String,
    focusRequester: FocusRequester,
    onShowKeypad: () -> Unit,
) {
    val palette = tandemPalette
    val entered = amountMinorUnits != 0L
    val amountColor =
        when {
            !entered -> palette.text
            transactionType == "Income" -> palette.positive
            else -> palette.negative
        }
    val outline = if (entered) amountColor else palette.border
    val fill = if (entered) amountColor.copy(alpha = 0.08f) else palette.surface
    val display = amount.ifBlank { "0.00" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(TandemShapes.large))
                .background(fill)
                .border(1.5.dp, outline, RoundedCornerShape(TandemShapes.large))
                .clickable(role = Role.Button, onClickLabel = "Enter amount", onClick = onShowKeypad)
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = TextFieldValue(display, selection = TextRange(display.length)),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = if (entered) amountColor else palette.muted,
                    fontSize = TandemTypography.amountField,
                    lineHeight = TandemTypography.amountFieldLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            cursorBrush = SolidColor(amountColor),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onShowKeypad() }
                    .semantics { contentDescription = "Amount, $display" },
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { innerTextField() }
            },
        )
    }
}

@Composable
internal fun TransactionTypeChip(
    type: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(palette.raised)
                .clickable(role = Role.Button, onClickLabel = "Choose transaction type", onClick = onClick)
                .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(Lucide.Receipt, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.text)
        Text(type, color = palette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(19.dp), tint = palette.muted)
    }
}

@Composable
internal fun TransactionPickerRow(
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val label = value.ifBlank { placeholder }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clickable(role = Role.Button, onClickLabel = placeholder, onClick = onClick)
                .semantics { contentDescription = "$placeholder: $label" }
                .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(TandemShapes.control)).background(palette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = palette.accent)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (value.isBlank()) palette.muted else palette.text,
            fontSize = TandemTypography.body,
            fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
    }
}

internal enum class AmountPadKeyStyle {
    NUMBER,
    OPERATOR,
    EQUALS,
    DONE,
}

@Composable
internal fun AmountPadKey(
    label: String,
    modifier: Modifier = Modifier,
    style: AmountPadKeyStyle = AmountPadKeyStyle.NUMBER,
    accessibilityLabel: String = label,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val background =
        when (style) {
            AmountPadKeyStyle.NUMBER -> Color.Transparent
            AmountPadKeyStyle.DONE -> palette.accent
            else -> palette.raised
        }
    val color =
        when (style) {
            AmountPadKeyStyle.DONE -> palette.accentContent
            AmountPadKeyStyle.NUMBER -> palette.text
            else -> palette.muted
        }
    val shape =
        when (style) {
            AmountPadKeyStyle.NUMBER -> RoundedCornerShape(TandemShapes.roomy)
            AmountPadKeyStyle.OPERATOR -> CircleShape
            else -> RoundedCornerShape(TandemShapes.capsule)
        }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(TandemSpacing.xxSmall)
                .clip(shape)
                .background(background)
                .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                .clickable(role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = if (style == AmountPadKeyStyle.NUMBER) TandemTypography.keypadNumber else TandemTypography.keypadOperator,
            fontWeight = if (style == AmountPadKeyStyle.DONE) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

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
                AmountPadKey("Done", Modifier.weight(1f), AmountPadKeyStyle.DONE, onClick = onDone)
            }
        }
    }
}

internal val AMOUNT_KEYPAD_HEIGHT = 252.dp

@Composable
internal fun FloatingTransactionSave(
    compact: Boolean,
    enabled: Boolean,
    saved: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = tandemPalette
    Row(
        modifier =
            modifier
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
                .height(56.dp)
                .clip(if (compact) CircleShape else RoundedCornerShape(TandemShapes.large))
                .background(
                    if (saved) {
                        palette.positive
                    } else if (enabled) {
                        palette.accent
                    } else {
                        palette.raised
                    },
                ).semantics(mergeDescendants = true) {
                    contentDescription = if (saved) "Saved on this device" else "Save transaction"
                }.clickable(enabled = enabled && !saved, role = Role.Button, onClickLabel = "Save transaction", onClick = onClick)
                .padding(horizontal = if (compact) TandemSpacing.large else TandemSpacing.extraLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else TandemSpacing.small),
    ) {
        Box(
            Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(if (saved) Color.Transparent else palette.accentContent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                modifier = Modifier.size(if (saved) 23.dp else 15.dp),
                tint = if (saved) palette.background else palette.accent,
            )
        }
        AnimatedVisibility(visible = !compact, enter = fadeIn(), exit = fadeOut()) {
            Text(
                if (saved) "Saved" else "Save",
                color = if (saved || enabled) palette.accentContent else palette.muted,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

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
    leading: String = "",
    leadingVector: ImageVector? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    FieldFrame(label, onClickLabel = "$label, $value", onClick = onClick) {
        if (leadingVector != null) {
            Icon(leadingVector, contentDescription = null, modifier = Modifier.size(30.dp), tint = palette.accent)
        } else if (leading.isNotEmpty()) {
            Text(
                leading,
                modifier = Modifier.clearAndSetSemantics {},
                color = palette.accent,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(value, color = palette.text, fontSize = TandemTypography.body, modifier = Modifier.weight(1f))
        Icon(
            Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp).clearAndSetSemantics {},
            tint = palette.muted,
        )
    }
}
