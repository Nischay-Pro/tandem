package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** One "on specific days" entry of a monthly schedule: a day of the month, or the nth weekday ([value] -1 is the last). */
internal data class SchedulePattern(
    val type: String,
    val value: Int,
)

/** The schedule form's values, its open date picker and its validation error. Amounts are entered without a sign. */
internal class ScheduleEditDraft(
    values: ScheduleDraftValues,
    datePickerTarget: String? = null,
    editError: String? = null,
    editErrorField: String? = null,
) {
    var nameDraft by mutableStateOf(values.name)
    var amountDraft by mutableStateOf(values.amount)
    var amountMaxDraft by mutableStateOf(values.amountMax)
    var amountRuleDraft by mutableStateOf(values.amountRule)
    var outflowDraft by mutableStateOf(values.outflow)
    var accountIdDraft by mutableStateOf(values.accountId)
    var payeeIdDraft by mutableStateOf(values.payeeId)
    var categoryIdDraft by mutableStateOf(values.categoryId)
    var dateDraft by mutableStateOf(values.date)
    var frequencyDraft by mutableStateOf(values.frequency)
    var intervalDraft by mutableStateOf(values.interval)
    var patternsDraft by mutableStateOf(values.patterns)
    var skipWeekendDraft by mutableStateOf(values.skipWeekend)
    var weekendModeDraft by mutableStateOf(values.weekendMode)
    var endModeDraft by mutableStateOf(values.endMode)
    var endOccurrencesDraft by mutableStateOf(values.endOccurrences)
    var endDateDraft by mutableStateOf(values.endDate)
    var postsTransactionDraft by mutableStateOf(values.postsTransaction)
    var datePickerTarget by mutableStateOf(datePickerTarget)
    var editError by mutableStateOf(editError)
    var editErrorField by mutableStateOf(editErrorField)

    fun clearEditError(field: String? = null) {
        if (field == null || editErrorField == field) {
            editError = null
            editErrorField = null
        }
    }

    fun values() =
        ScheduleDraftValues(
            nameDraft,
            amountDraft,
            amountMaxDraft,
            amountRuleDraft,
            outflowDraft,
            accountIdDraft,
            payeeIdDraft,
            categoryIdDraft,
            dateDraft,
            frequencyDraft,
            intervalDraft,
            patternsDraft,
            skipWeekendDraft,
            weekendModeDraft,
            endModeDraft,
            endOccurrencesDraft,
            endDateDraft,
            postsTransactionDraft,
        )

    companion object {
        val Saver: Saver<ScheduleEditDraft, Any> =
            listSaver(
                save = { listOf(it.values().encode(), it.datePickerTarget, it.editError, it.editErrorField) },
                restore = {
                    ScheduleEditDraft(ScheduleDraftValues.decode(it[0] as List<*>), it[1] as String?, it[2] as String?, it[3] as String?)
                },
            )
    }
}

/** A snapshot of every schedule form field, used to start a draft and to save one across configuration changes. */
internal data class ScheduleDraftValues(
    val name: String = "",
    val amount: String = "",
    val amountMax: String = "",
    val amountRule: String = "isapprox",
    val outflow: Boolean = true,
    val accountId: String = "",
    val payeeId: String = "",
    val categoryId: String = "",
    val date: String = "",
    val frequency: String = "monthly",
    val interval: String = "1",
    val patterns: List<SchedulePattern> = emptyList(),
    val skipWeekend: Boolean = false,
    val weekendMode: String = "after",
    val endMode: String = "never",
    val endOccurrences: String = "12",
    val endDate: String = "",
    val postsTransaction: Boolean = false,
) {
    fun encode(): List<Any> =
        listOf(
            name,
            amount,
            amountMax,
            amountRule,
            outflow,
            accountId,
            payeeId,
            categoryId,
            date,
            frequency,
            interval,
            patterns.joinToString(",") { "${it.type}:${it.value}" },
            skipWeekend,
            weekendMode,
            endMode,
            endOccurrences,
            endDate,
            postsTransaction,
        )

    companion object {
        fun decode(saved: List<*>): ScheduleDraftValues {
            fun text(index: Int) = saved[index] as String
            return ScheduleDraftValues(
                text(0),
                text(1),
                text(2),
                text(3),
                saved[4] as Boolean,
                text(5),
                text(6),
                text(7),
                text(8),
                text(9),
                text(10),
                text(11).split(',').mapNotNull { entry ->
                    val parts = entry.split(':')
                    parts.getOrNull(1)?.toIntOrNull()?.let { SchedulePattern(parts[0], it) }
                },
                saved[12] as Boolean,
                text(13),
                text(14),
                text(15),
                text(16),
                saved[17] as Boolean,
            )
        }
    }
}
