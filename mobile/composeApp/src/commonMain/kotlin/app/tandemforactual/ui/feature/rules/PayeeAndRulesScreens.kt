@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatCurrency
import kotlinx.coroutines.flow.first

internal fun ruleFieldLabel(wireField: String?): String =
    RuleConditionField.entries
        .firstOrNull {
            it.wire == wireField && it != RuleConditionField.MONEY_IN && it != RuleConditionField.MONEY_OUT
        }?.label
        ?: wireField?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
        ?: "Field"

internal fun LiveScheduleSummary.amountLabel(state: AppState): String =
    when {
        amountMinor != null -> state.formatCurrency(amountMinor)
        amountRangeMinor != null -> "${state.formatCurrency(amountRangeMinor.first)}–${state.formatCurrency(amountRangeMinor.second)}"
        else -> "Variable"
    }
