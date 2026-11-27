package app.tandemforactual.ui.feature.schedules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.schedules.manageBudgetSchedule
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.feature.schedules.components.ScheduleDialogState
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDraft
import app.tandemforactual.ui.feature.schedules.components.ScheduleFormOptions
import app.tandemforactual.ui.feature.schedules.components.ScheduleSelectionState
import app.tandemforactual.ui.feature.schedules.components.scheduleFields
import app.tandemforactual.ui.feature.schedules.components.validationError
import kotlin.math.abs
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The category a schedule's rule sets, read from its plain "set category" action. */
internal fun scheduleCategoryId(
    ruleRecords: List<BudgetRecordEntity>,
    ruleId: String?,
): String? {
    val rule = ruleRecords.firstOrNull { it.recordId == ruleId }?.recordBody() ?: return null
    return (rule["actions"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.stringValue("op") == "set" && it.stringValue("field") == "category" && it["options"] !is JsonObject }
        ?.stringValue("value")
}

/** Open accounts, payees with transfers labelled and listed last, and visible categories, for the schedule form. */
@Composable
internal fun rememberScheduleFormOptions(
    accounts: List<LiveAccountSummary>,
    payeeRecords: List<BudgetRecordEntity>,
    categoryRecords: List<BudgetRecordEntity>,
    selectedSchedule: LiveScheduleSummary?,
): ScheduleFormOptions =
    remember(accounts, payeeRecords, categoryRecords, selectedSchedule?.payeeId) {
        val live = { record: BudgetRecordEntity -> !record.localDeleted && !record.remoteDeleted }
        val payees =
            payeeRecords.filter(live).mapNotNull { record ->
                val name = record.recordName() ?: return@mapNotNull null
                val transfer = record.recordBody()?.stringValue("transfer_acct") != null
                Triple(record.recordId, if (transfer) "Transfer: $name" else name, transfer)
            }
        val payeeOptions =
            payees
                .sortedWith(compareBy({ it.third }, { it.second.lowercase() }))
                .map { it.first to it.second }
                .toMutableList()
        selectedSchedule?.payeeId?.let { id -> if (payeeOptions.none { it.first == id }) payeeOptions += id to id }
        ScheduleFormOptions(
            accounts = accounts.filterNot(LiveAccountSummary::closed).sortedBy { it.name.lowercase() }.map { it.id to it.name },
            payees = payeeOptions,
            categories =
                categoryRecords
                    .filter { live(it) && it.recordBody()?.booleanValue("hidden") != true }
                    .mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                    .sortedBy { it.second.lowercase() },
        )
    }

/** Parses the draft's amounts and shows its first problem, returning the unsigned amounts when it's valid. */
private fun ScheduleEditDraft.checked(
    state: AppState,
    nameRequired: Boolean,
): Pair<Long, Long?>? {
    val amountMinor = state.parseAmountMinorUnits(amountDraft)?.let { abs(it) }
    val amountMaxMinor = state.parseAmountMinorUnits(amountMaxDraft)?.let { abs(it) }
    val error = validationError(amountMinor, amountMaxMinor, nameRequired)
    editErrorField = error?.field
    editError = error?.message
    return if (error == null && amountMinor != null) amountMinor to amountMaxMinor else null
}

/** Validates the draft and, when it is valid, sends the edit and closes the sheet once saved. */
internal fun saveScheduleEdit(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    draft: ScheduleEditDraft,
    dateConfig: JsonObject?,
    selectedSchedule: LiveScheduleSummary,
    selection: ScheduleSelectionState,
) {
    val (amountMinor, amountMaxMinor) = draft.checked(state, nameRequired = true) ?: return
    val fields = draft.scheduleFields(dateConfig, amountMinor, amountMaxMinor, creating = false)
    scope.launch {
        val saved =
            mobileSession.schedules.manageBudgetSchedule(
                state.selectedBudgetId,
                selectedSchedule.id,
                "edit",
                null,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                fields,
            )
        if (saved) selection.showScheduleEditor = false
    }
}

/** Validates the new schedule and queues it, linking the transaction it was made from. */
internal fun saveNewSchedule(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    draft: ScheduleEditDraft,
    selection: ScheduleSelectionState,
) {
    val (amountMinor, amountMaxMinor) = draft.checked(state, nameRequired = false) ?: return
    val linked = selection.linkTransactionId
    val fields =
        draft.scheduleFields(null, amountMinor, amountMaxMinor, creating = true).let { fields ->
            if (linked == null) fields else JsonObject(fields + ("linkTransactionIds" to JsonArray(listOf(JsonPrimitive(linked)))))
        }
    val id = Uuid.random().toString()
    scope.launch {
        val saved =
            mobileSession.schedules.manageBudgetSchedule(
                state.selectedBudgetId,
                id,
                "create",
                null,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                fields,
            )
        if (saved) selection.creating = false
    }
}

internal fun deleteSchedule(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    selectedSchedule: LiveScheduleSummary,
    selection: ScheduleSelectionState,
    dialog: ScheduleDialogState,
) {
    scope.launch {
        val deleted =
            mobileSession.schedules.manageBudgetSchedule(
                state.selectedBudgetId,
                selectedSchedule.id,
                "delete",
                null,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        dialog.showDeleteScheduleConfirmation = false
        if (deleted) selection.showScheduleEditor = false else dialog.dialogMode = "menu"
    }
}
