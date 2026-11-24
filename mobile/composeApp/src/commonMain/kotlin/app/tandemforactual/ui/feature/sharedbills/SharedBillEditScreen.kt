@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.data.planning.SharedBillMember
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserPlus
import kotlin.random.Random
import kotlinx.coroutines.launch

private class MemberDraft(
    name: String = "",
    share: String = "",
    match: String = "",
    val tag: String = "",
    private val ruleFor: String? = null,
    everyMonths: Int = 1,
) {
    val ruleCreated: Boolean get() = ruleFor != null && ruleFor == match.trim()

    var name by mutableStateOf(name)
    var share by mutableStateOf(share)
    var match by mutableStateOf(match)
    var everyMonths by mutableStateOf(everyMonths)
}

/** Creates or edits a shared bill and the people who pay into it. */
@Composable
internal fun SharedBillEditScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val existing = mobileSession.planning.sharedBills.firstOrNull { it.id == state.selectedSharedBillId }
    val settings = state.budgetDisplaySettings
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var dueDay by remember(existing) { mutableStateOf(existing?.dueDay?.toString().orEmpty()) }
    val members =
        remember(existing) {
            mutableStateListOf<MemberDraft>().apply {
                existing?.members?.forEach {
                    add(
                        MemberDraft(
                            it.name,
                            formatAmount(it.shareMinor, settings),
                            it.matchText,
                            it.tag,
                            it.matchText.takeIf { _ ->
                                it.ruleCreated
                            },
                            it.everyMonths,
                        ),
                    )
                }
                if (isEmpty()) add(MemberDraft())
            }
        }
    val scope = rememberCoroutineScope()
    val amountOptions =
        TextFieldOptions(
            placeholder = "0",
            currencySymbol = settings?.currencySymbol ?: "$",
            currencySymbolAfter = settings?.currencySymbolPosition == "after",
            amountFractionDigits = settings?.currencyFractionDigits ?: 2,
        )
    val valid = name.isNotBlank() && members.any { it.name.isNotBlank() } && (dueDay.isBlank() || dueDay.toIntOrNull() in 1..28)
    PageColumn(verticalSpacing = TandemSpacing.large) {
        BillDetailsCard(name, dueDay, { name = it.take(40) }, { dueDay = it.filter(Char::isDigit).take(2) })
        members.forEachIndexed { index, member ->
            MemberEditor(index, member, amountOptions, removable = members.size > 1) { members.remove(member) }
        }
        TandemActionButton("Add person", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), icon = Lucide.UserPlus) {
            members.add(MemberDraft())
        }
        TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = valid && !state.readOnlyMode) {
            val bill = buildSharedBill(existing, name, dueDay, members, settings)
            scope.launch {
                mobileSession.planning.saveSharedBill(state.selectedBudgetId, bill)
                state.selectedSharedBillId = bill.id
                navigation.back()
            }
        }
        if (existing != null) {
            TandemActionButton("Delete bill", TandemActionButtonTone.DESTRUCTIVE, Modifier.fillMaxWidth(), icon = Lucide.Trash2) {
                scope.launch {
                    mobileSession.planning.deleteSharedBill(state.selectedBudgetId, existing.id)
                    navigation.backToList()
                }
            }
        }
    }
}

@Composable
private fun MemberEditor(
    index: Int,
    member: MemberDraft,
    amountOptions: TextFieldOptions,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    SectionCard("Person ${index + 1}", "Their share and how their payments look in your bank") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            TextFieldCard("Name", member.name, { member.name = it.take(30) }, TextFieldOptions(placeholder = "Their name"))
            TextFieldCard("Monthly share", member.share, { member.share = it }, amountOptions)
            TextFieldCard(
                "Payment text",
                member.match,
                { member.match = it.take(60) },
                TextFieldOptions(placeholder = "As it appears in your bank, e.g. Zelle from…"),
            )
            Text("How often they pay", color = tandemPalette.muted, fontSize = TandemTypography.label)
            FilterPills(
                listOf(1 to "Monthly", 2 to "Every 2 months", 3 to "Every 3 months", 6 to "Twice a year", 12 to "Yearly"),
                member.everyMonths,
                { member.everyMonths = it },
            )
            if (removable) {
                TandemActionButton(
                    "Remove",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Trash2,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun BillDetailsCard(
    name: String,
    dueDay: String,
    onName: (String) -> Unit,
    onDueDay: (String) -> Unit,
) {
    SectionCard("Bill", "The bill you pay in full each month") {
        Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            TextFieldCard("Name", name, onName, TextFieldOptions(placeholder = "e.g. Phone plan"))
            TextFieldCard(
                "Shares due by day",
                dueDay,
                onDueDay,
                TextFieldOptions(placeholder = "1 to 28, optional", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
            )
        }
    }
}

private fun buildSharedBill(
    existing: SharedBill?,
    name: String,
    dueDay: String,
    members: List<MemberDraft>,
    settings: BudgetDisplaySettings?,
) = SharedBill(
    id = existing?.id ?: "shared-${Random.nextLong().toULong().toString(36)}",
    name = name.trim(),
    tag = existing?.tag ?: SharedBill.tagFor(name),
    members =
        members.filter { it.name.isNotBlank() }.map { draft ->
            SharedBillMember(
                name = draft.name.trim(),
                tag = draft.tag.ifBlank { SharedBill.tagFor(draft.name) },
                shareMinor = parseAmountMinorUnits(draft.share, settings)?.coerceAtLeast(0) ?: 0,
                matchText = draft.match.trim(),
                ruleCreated = draft.ruleCreated,
                everyMonths = draft.everyMonths,
            )
        },
    dueDay = dueDay.toIntOrNull(),
    startMonth = existing?.startMonth ?: platformTodayIsoDate().take(7),
)
