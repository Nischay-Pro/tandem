@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun SavingsGoalSheet(
    goal: SavingsGoal?,
    visible: Boolean,
    month: LiveBudgetMonthSummary?,
    settings: BudgetDisplaySettings?,
    onSave: (SavingsGoal) -> Unit,
    onDelete: (SavingsGoal) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(goal, visible) { mutableStateOf(goal?.name.orEmpty()) }
    var target by remember(goal, visible) { mutableStateOf(goal?.targetMinor?.let { formatAmount(it, settings) }.orEmpty()) }
    var date by remember(goal, visible) { mutableStateOf(goal?.targetDate.orEmpty()) }
    var categoryId by remember(goal, visible) { mutableStateOf(goal?.categoryId) }
    var keepPrivate by remember(goal, visible) { mutableStateOf(goal?.shared == false) }
    var choosingCategory by remember { mutableStateOf(false) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    val categories = month?.groups.orEmpty().flatMap { it.categories }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader(if (goal == null) "New savings goal" else "Savings goal", onClose = onDismiss)
                TextFieldCard("Name", name, { name = it.take(60) }, TextFieldOptions(placeholder = "Emergency fund"))
                TextFieldCard("Target", target, { target = it }, settings.amountOptions())
                TextFieldCard("Target date (optional)", date, { date = it.take(10) }, TextFieldOptions(placeholder = "2027-06-01"))
                ListRow(
                    "Track with category",
                    categories.firstOrNull { it.id == categoryId }?.name ?: "None",
                    onClick = { choosingCategory = true },
                )
                ToggleRow("Keep private", "Stays on this device. Others on this budget won’t see it.", keepPrivate) { keepPrivate = it }
                error?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    if (goal != null) {
                        TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f)) { onDelete(goal) }
                    }
                    TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) {
                        val amount = parseAmountMinorUnits(target, settings)
                        error =
                            when {
                                name.isBlank() -> "Enter a name."
                                amount == null || amount <= 0 -> "Enter a target amount."
                                date.isNotBlank() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) -> "Use a date like 2027-06-01."
                                else -> null
                            }
                        if (error == null && amount != null) {
                            onSave(
                                SavingsGoal(
                                    id = goal?.id ?: Uuid.random().toString(),
                                    name = name.trim(),
                                    targetMinor = amount,
                                    currency = settings?.currencyCode.orEmpty(),
                                    targetDate = date.ifBlank { null },
                                    categoryId = categoryId,
                                    shared = !keepPrivate,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    CategoryChoiceDialog(choosingCategory, categories, onDismiss = { choosingCategory = false }) {
        categoryId = it
        choosingCategory = false
    }
}

/** Picks the budget category a goal tracks, or none. */
@Composable
private fun CategoryChoiceDialog(
    visible: Boolean,
    categories: List<LiveCategorySummary>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    TandemChoiceDialog(visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                ListRow("None", onClick = { onPick(null) })
                categories.forEach { category ->
                    DividerLine()
                    ListRow(category.name, onClick = { onPick(category.id) })
                }
            }
        }
    }
}
