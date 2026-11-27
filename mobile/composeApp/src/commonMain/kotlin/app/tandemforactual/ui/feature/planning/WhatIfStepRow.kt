@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SlidersHorizontal

internal enum class WhatIfRow { INCOME, SPENDING }

/** One row of change amounts, with a custom decrease at the start and a custom increase at the end. */
@Composable
internal fun WhatIfStepRow(
    title: String,
    state: AppState,
    steps: List<Long>,
    value: Long,
    onChange: (Long) -> Unit,
    onCustom: (increase: Boolean) -> Unit,
) {
    fun amount(change: Long) = state.formatCurrency(kotlin.math.abs(change)).replace(Regex("[.,]00$"), "")

    fun label(change: Long) = if (change == 0L) "No change" else (if (change > 0) "+" else "−") + amount(change)
    val isCustom = value !in steps
    val options =
        listOf(WhatIfSteps.CUSTOM_LESS to if (isCustom && value < 0) label(value) else "− Custom") +
            steps.map { it to label(it) } +
            (WhatIfSteps.CUSTOM_MORE to if (isCustom && value > 0) label(value) else "+ Custom")
    val selected =
        when {
            !isCustom -> value
            value < 0 -> WhatIfSteps.CUSTOM_LESS
            else -> WhatIfSteps.CUSTOM_MORE
        }
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(title, color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.Medium)
        FilterPills(options, selected, { choice ->
            when (choice) {
                WhatIfSteps.CUSTOM_LESS -> onCustom(false)
                WhatIfSteps.CUSTOM_MORE -> onCustom(true)
                else -> onChange(choice)
            }
        })
    }
}

/** Asks for a custom monthly change; [increase] is null while the dialog is closed. */
@Composable
internal fun WhatIfAmountDialog(
    state: AppState,
    increase: Boolean?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var draft by remember(increase) { mutableStateOf("") }
    val settings = state.budgetDisplaySettings
    val amount = parseAmountMinorUnits(draft, settings)?.takeIf { it > 0 }
    TandemChoiceDialog(visible = increase != null, onDismissRequest = onDismiss) {
        SheetCard(
            header = {
                TandemModalHeader(
                    if (increase == true) "Custom increase" else "Custom decrease",
                    onClose = onDismiss,
                    leadingIcon = Lucide.SlidersHorizontal,
                )
            },
            actions = {
                TandemActionButton("Apply", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = amount != null) {
                    amount?.let { onConfirm(if (increase == true) it else -it) }
                }
            },
        ) {
            TextFieldCard(
                "Amount each month",
                draft,
                { draft = it },
                TextFieldOptions(
                    placeholder = "0",
                    currencySymbol = settings?.currencySymbol ?: "$",
                    currencySymbolAfter = settings?.currencySymbolPosition == "after",
                    currencySymbolSpace = settings?.currencySpaceBetweenAmountAndSymbol == true,
                    amountFractionDigits = settings?.currencyFractionDigits ?: 2,
                ),
            )
        }
    }
}
