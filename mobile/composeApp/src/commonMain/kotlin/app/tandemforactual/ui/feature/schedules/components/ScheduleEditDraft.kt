package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatAmount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The edit form's values for the selected schedule, its open date picker and its validation error. */
internal class ScheduleEditDraft(
    nameDraft: String,
    amountDraft: String,
    amountMaxDraft: String,
    amountRuleDraft: String,
    accountIdDraft: String,
    payeeIdDraft: String,
    dateDraft: String,
    frequencyDraft: String,
    intervalDraft: String,
    skipWeekendDraft: Boolean,
    endModeDraft: String,
    endOccurrencesDraft: String,
    endDateDraft: String,
    datePickerTarget: String? = null,
    editError: String? = null,
    editErrorField: String? = null,
) {
    var nameDraft by mutableStateOf(nameDraft)
    var amountDraft by mutableStateOf(amountDraft)
    var amountMaxDraft by mutableStateOf(amountMaxDraft)
    var amountRuleDraft by mutableStateOf(amountRuleDraft)
    var accountIdDraft by mutableStateOf(accountIdDraft)
    var payeeIdDraft by mutableStateOf(payeeIdDraft)
    var dateDraft by mutableStateOf(dateDraft)
    var frequencyDraft by mutableStateOf(frequencyDraft)
    var intervalDraft by mutableStateOf(intervalDraft)
    var skipWeekendDraft by mutableStateOf(skipWeekendDraft)
    var endModeDraft by mutableStateOf(endModeDraft)
    var endOccurrencesDraft by mutableStateOf(endOccurrencesDraft)
    var endDateDraft by mutableStateOf(endDateDraft)
    var datePickerTarget by mutableStateOf(datePickerTarget)
    var editError by mutableStateOf(editError)
    var editErrorField by mutableStateOf(editErrorField)

    fun clearEditError(field: String? = null) {
        if (field == null || editErrorField == field) {
            editError = null
            editErrorField = null
        }
    }

    companion object {
        val Saver: Saver<ScheduleEditDraft, Any> =
            listSaver(
                save = {
                    listOf(
                        it.nameDraft,
                        it.amountDraft,
                        it.amountMaxDraft,
                        it.amountRuleDraft,
                        it.accountIdDraft,
                        it.payeeIdDraft,
                        it.dateDraft,
                        it.frequencyDraft,
                        it.intervalDraft,
                        it.skipWeekendDraft,
                        it.endModeDraft,
                        it.endOccurrencesDraft,
                        it.endDateDraft,
                        it.datePickerTarget,
                        it.editError,
                        it.editErrorField,
                    )
                },
                restore = {
                    ScheduleEditDraft(
                        it[0] as String,
                        it[1] as String,
                        it[2] as String,
                        it[3] as String,
                        it[4] as String,
                        it[5] as String,
                        it[6] as String,
                        it[7] as String,
                        it[8] as String,
                        it[9] as Boolean,
                        it[10] as String,
                        it[11] as String,
                        it[12] as String,
                        it[13] as String?,
                        it[14] as String?,
                        it[15] as String?,
                    )
                },
            )
    }
}

/** The selected schedule's date rule: an object for repeating schedules, a plain date otherwise. */
@Composable
internal fun rememberScheduleDateValue(selectedSchedule: LiveScheduleSummary?): JsonElement? =
    remember(selectedSchedule?.id, selectedSchedule?.dateValueJson) {
        selectedSchedule?.dateValueJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
    }

/** A draft filled from the selected schedule, reset whenever another schedule is selected. */
@Composable
internal fun rememberScheduleEditDraft(
    state: AppState,
    selectedScheduleId: String?,
    selectedSchedule: LiveScheduleSummary?,
    dateValue: JsonElement?,
): ScheduleEditDraft =
    rememberSaveable(state.selectedBudgetId, selectedScheduleId, saver = ScheduleEditDraft.Saver) {
        val dateConfig = dateValue as? JsonObject
        ScheduleEditDraft(
            nameDraft = selectedSchedule?.name.orEmpty(),
            amountDraft = state.formatAmount(selectedSchedule?.amountMinor ?: selectedSchedule?.amountRangeMinor?.first ?: 0L),
            amountMaxDraft =
                selectedSchedule
                    ?.amountRangeMinor
                    ?.second
                    ?.let(state::formatAmount)
                    .orEmpty(),
            amountRuleDraft = selectedSchedule?.amountOp ?: "is",
            accountIdDraft = selectedSchedule?.accountId.orEmpty(),
            payeeIdDraft = selectedSchedule?.payeeId.orEmpty(),
            dateDraft =
                dateConfig?.get("start")?.jsonPrimitive?.contentOrNull
                    ?: (dateValue as? JsonPrimitive)?.contentOrNull
                    ?: selectedSchedule?.nextDate.orEmpty(),
            frequencyDraft = dateConfig?.get("frequency")?.jsonPrimitive?.contentOrNull ?: "once",
            intervalDraft = dateConfig?.get("interval")?.jsonPrimitive?.contentOrNull ?: "1",
            skipWeekendDraft = dateConfig?.get("skipWeekend")?.jsonPrimitive?.booleanOrNull ?: false,
            endModeDraft = dateConfig?.get("endMode")?.jsonPrimitive?.contentOrNull ?: "never",
            endOccurrencesDraft = dateConfig?.get("endOccurrences")?.jsonPrimitive?.contentOrNull ?: "12",
            endDateDraft = dateConfig?.get("endDate")?.jsonPrimitive?.contentOrNull ?: selectedSchedule?.nextDate.orEmpty(),
        )
    }

/** The fields sent to Actual for the edited schedule, keeping date-rule keys this form does not edit. */
internal fun ScheduleEditDraft.editedScheduleFields(
    dateConfig: JsonObject?,
    amountMinor: Long?,
    amountMaxMinor: Long?,
    interval: Int?,
    occurrences: Int?,
): JsonObject {
    val editedDate =
        if (frequencyDraft == "once") {
            JsonPrimitive(dateDraft) as JsonElement
        } else {
            buildJsonObject {
                dateConfig?.forEach { (key, value) ->
                    if (key !in
                        setOf(
                            "frequency",
                            "interval",
                            "start",
                            "skipWeekend",
                            "endMode",
                            "endOccurrences",
                            "endDate",
                        )
                    ) {
                        put(key, value)
                    }
                }
                put("frequency", frequencyDraft)
                put("interval", interval ?: 1)
                put("start", dateDraft)
                put("skipWeekend", skipWeekendDraft)
                put("endMode", endModeDraft)
                if (endModeDraft == "after_n_occurrences") put("endOccurrences", occurrences ?: 1)
                if (endModeDraft == "on_date") put("endDate", endDateDraft)
            }
        }
    return buildJsonObject {
        put("name", nameDraft.trim())
        put("accountId", accountIdDraft)
        put("payeeId", payeeIdDraft)
        put("amountOp", amountRuleDraft)
        if (amountRuleDraft == "isbetween") {
            put(
                "amountRangeMinor",
                buildJsonObject {
                    put("num1", amountMinor ?: 0L)
                    put("num2", amountMaxMinor ?: 0L)
                },
            )
        } else {
            put("amountMinor", amountMinor ?: 0L)
        }
        put("date", editedDate)
    }
}
