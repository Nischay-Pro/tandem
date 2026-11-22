@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun ruleFieldLabel(wireField: String?): String =
    RuleConditionField.entries
        .firstOrNull {
            it.wire == wireField && it != RuleConditionField.MONEY_IN && it != RuleConditionField.MONEY_OUT
        }?.label
        ?: wireField?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
        ?: "Field"
