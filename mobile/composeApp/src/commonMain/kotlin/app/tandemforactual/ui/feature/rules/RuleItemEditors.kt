@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2

@Composable
private fun RuleItemHeader(
    title: String,
    removable: Boolean,
    removeLabel: String,
    onRemove: () -> Unit,
) {
    if (!removable) return
    Row(
        Modifier.fillMaxWidth().padding(start = TandemLayout.listRowHorizontalInset, end = TandemSpacing.small, top = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = tandemPalette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
        TandemIconButton(Lucide.Trash2, removeLabel, sizeDp = 36, iconSizeDp = 18, tint = tandemPalette.muted, onClick = onRemove)
    }
}

@Composable
internal fun RuleConditionEditor(
    number: Int,
    condition: RuleConditionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleConditionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        RuleItemHeader("Condition $number", removable, "Remove condition $number", onRemove)
        RuleChoiceField(
            "Field",
            condition.field.label,
            RuleConditionField.entries.map { it.name to it.label },
            selectedIds = setOf(condition.field.name),
        ) { onChange(condition.withField(RuleConditionField.valueOf(it.first()))) }
        DividerLine()
        RuleChoiceField(
            "Comparison",
            ruleOperatorLabel(condition.op),
            condition.field.ops.map { it to ruleOperatorLabel(it) },
            selectedIds = setOf(condition.op),
        ) { onChange(condition.withOp(it.first())) }
        val field = condition.field
        if (field.usesIds) {
            DividerLine()
            val multiple = condition.op in setOf("oneOf", "notOneOf")
            val label =
                condition.ids
                    .mapNotNull { choices.name(field.wire, it) }
                    .joinToString(", ")
                    .ifBlank { "Choose" }
            RuleChoiceField(
                field.label,
                label,
                choices.options(field.wire),
                selectedIds = condition.ids.toSet(),
                multiple = multiple,
            ) { onChange(condition.copy(ids = it)) }
        } else {
            RuleConditionValueFields(condition, onChange)
        }
    }
}

/** The text or amount a non-id condition compares against. */
@Composable
private fun RuleConditionValueFields(
    condition: RuleConditionDraft,
    onChange: (RuleConditionDraft) -> Unit,
) {
    val field = condition.field
    Column(
        Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        if (field.usesAmount) {
            RuleAmountFields(condition, onChange)
        } else {
            TextFieldCard(
                if (condition.op == "matches") "Pattern" else "Text",
                condition.text,
                { onChange(condition.copy(text = it)) },
            )
        }
    }
}

/** One amount, or a from and to amount for "is between". */
@Composable
private fun RuleAmountFields(
    condition: RuleConditionDraft,
    onChange: (RuleConditionDraft) -> Unit,
) {
    val amountOptions = TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    if (condition.op == "isbetween") {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Column(Modifier.weight(1f)) {
                TextFieldCard("From", condition.amountText, { onChange(condition.copy(amountText = it)) }, amountOptions)
            }
            Column(Modifier.weight(1f)) {
                TextFieldCard(
                    "To",
                    condition.amountMaxText,
                    { onChange(condition.copy(amountMaxText = it)) },
                    amountOptions,
                )
            }
        }
    } else {
        TextFieldCard("Amount", condition.amountText, { onChange(condition.copy(amountText = it)) }, amountOptions)
    }
    if (condition.field == RuleConditionField.AMOUNT) {
        Text(
            "Use a minus sign for money out, or choose Money out as the field.",
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
    }
}

@Composable
internal fun RuleActionEditor(
    number: Int,
    action: RuleActionDraft,
    choices: RuleChoices,
    removable: Boolean,
    onChange: (RuleActionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        RuleItemHeader("Action $number", removable, "Remove action $number", onRemove)
        RuleChoiceField(
            "Action",
            action.kind.label,
            RuleActionKind.entries.map { it.name to it.label },
            selectedIds = setOf(action.kind.name),
        ) {
            val next = RuleActionKind.valueOf(it.first())
            if (next != action.kind) onChange(RuleActionDraft(next))
        }
        val kind = action.kind
        when {
            kind == RuleActionKind.SET_CLEARED ->
                Column(Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium)) {
                    SegmentedSelector(listOf("Cleared" to action.cleared, "Not cleared" to !action.cleared)) {
                        onChange(action.copy(cleared = it == 0))
                    }
                }
            kind.usesId -> {
                DividerLine()
                val field = kind.field!!
                RuleChoiceField(
                    field.replaceFirstChar { it.uppercase() },
                    choices.name(field, action.id) ?: "Choose",
                    choices.options(field),
                    selectedIds = setOfNotNull(action.id.takeIf { it.isNotBlank() }),
                ) { onChange(action.copy(id = it.first())) }
            }
            else ->
                Column(Modifier.padding(start = TandemSpacing.medium, end = TandemSpacing.medium, bottom = TandemSpacing.medium)) {
                    TextFieldCard("Notes", action.text, { onChange(action.copy(text = it)) })
                }
        }
    }
}
