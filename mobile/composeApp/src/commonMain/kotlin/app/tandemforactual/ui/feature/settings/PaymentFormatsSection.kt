@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.CustomPaymentFormat
import app.tandemforactual.ui.core.format.PaymentTextFormat
import app.tandemforactual.ui.core.format.describeReadPayment
import app.tandemforactual.ui.core.format.paymentFormatProblem
import app.tandemforactual.ui.core.format.readPaymentText
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

/** The user's own patterns for payment messages shared into Tandem, tried before the built-in formats. */
@Composable
internal fun PaymentFormatsSection(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val formats = mobileSession.planning.paymentFormats
    var editing by remember(state.selectedBudgetId) { mutableStateOf<CustomPaymentFormat?>(null) }
    SectionCard(
        "Payment message formats",
        detail = "Your own patterns for bank or payment messages you share to Tandem, tried before the built-in ones.",
    ) {
        formats.forEach { format ->
            ListRow(
                format.name.ifBlank { "Untitled format" },
                subtitle = format.sample.take(SAMPLE_PREVIEW),
                onClick = { editing = format },
            )
            DividerLine()
        }
        TandemActionButton("Add a format", TandemActionButtonTone.NEUTRAL, icon = Lucide.Plus, enabled = !state.readOnlyMode) {
            editing = CustomPaymentFormat(Uuid.random().toString(), "", "", "")
        }
    }
    editing?.let { format ->
        val existing = formats.any { it.id == format.id }
        PaymentFormatSheet(format, existing, state, mobileSession) { editing = null }
    }
}

@Composable
private fun PaymentFormatSheet(
    initial: CustomPaymentFormat,
    existing: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    onClose: () -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var pattern by remember(initial.id) { mutableStateOf(initial.pattern) }
    var sample by remember(initial.id) { mutableStateOf(initial.sample) }
    val problem = remember(pattern, sample) { paymentFormatProblem(pattern, sample) }
    val preview =
        remember(pattern, sample, problem) {
            if (problem != null) return@remember null
            val format = PaymentTextFormat(name, Regex(pattern, RegexOption.IGNORE_CASE), emptyList())
            readPaymentText(sample, listOf(format))?.let { read -> describeReadPayment(read) { state.formatCurrency(it) } }
        }
    val scope = rememberCoroutineScope()
    val budgetId = state.selectedBudgetId
    TandemTaskSheet(visible = true, onDismissRequest = onClose, onDismissed = {}) {
        SheetCard(
            maxWidth = 560.dp,
            header = { TandemModalHeader(title = "Payment message format", onClose = onClose) },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    if (existing) {
                        TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f), icon = Lucide.Trash2) {
                            scope.launch { mobileSession.planning.deletePaymentFormat(budgetId, initial.id) }
                            onClose()
                        }
                    }
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = problem == null && !state.readOnlyMode,
                    ) {
                        val saved = CustomPaymentFormat(initial.id, name.trim(), pattern, sample)
                        scope.launch { if (mobileSession.planning.savePaymentFormat(budgetId, saved)) onClose() }
                    }
                }
            },
        ) {
            BodyText(
                "Name the parts with groups: (?<amount>…) is required; (?<type>…), (?<payee>…), (?<account>…), " +
                    "(?<date>…) and (?<ref>…) are optional.",
            )
            TextFieldCard("Name", name, { name = it })
            TextFieldCard("Pattern", pattern, { pattern = it })
            TextFieldCard("Sample message", sample, { sample = it })
            BodyText(problem ?: "Reads: ${preview.orEmpty()}")
        }
    }
}

private const val SAMPLE_PREVIEW = 60
