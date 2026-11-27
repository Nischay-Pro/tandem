package app.tandemforactual.ui.feature.schedules.components

import app.tandemforactual.ui.core.format.parseIsoDate
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.ScheduleSeed
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val RECUR_KEYS =
    setOf("frequency", "interval", "start", "patterns", "skipWeekend", "weekendSolveMode", "endMode", "endOccurrences", "endDate")

/** The form filled from an existing schedule, with [categoryId] read from its rule. */
internal fun scheduleDraftValues(
    schedule: LiveScheduleSummary,
    dateValue: JsonElement?,
    categoryId: String?,
    format: (Long) -> String,
): ScheduleDraftValues {
    val config = dateValue as? JsonObject

    fun text(key: String) = config?.get(key)?.jsonPrimitive?.contentOrNull

    val range = schedule.amountRangeMinor
    val single = schedule.amountMinor ?: 0L
    val outflow = if (range != null) range.second <= 0 else single <= 0
    val (low, high) = range?.let { if (outflow) abs(it.second) to abs(it.first) else it } ?: (abs(single) to null)
    return ScheduleDraftValues(
        name = schedule.name,
        amount = format(low),
        amountMax = high?.let(format).orEmpty(),
        amountRule = schedule.amountOp,
        outflow = outflow,
        accountId = schedule.accountId.orEmpty(),
        payeeId = schedule.payeeId.orEmpty(),
        categoryId = categoryId.orEmpty(),
        date = text("start") ?: (dateValue as? JsonPrimitive)?.contentOrNull ?: schedule.nextDate,
        frequency = text("frequency") ?: "once",
        interval = text("interval") ?: "1",
        patterns =
            (config?.get("patterns") as? JsonArray).orEmpty().mapNotNull { element ->
                val pattern = element as? JsonObject ?: return@mapNotNull null
                val type = pattern["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                pattern["value"]?.jsonPrimitive?.intOrNull?.let { SchedulePattern(type, it) }
            },
        skipWeekend = config?.get("skipWeekend")?.jsonPrimitive?.booleanOrNull ?: false,
        weekendMode = text("weekendSolveMode") ?: "after",
        endMode = text("endMode") ?: "never",
        endOccurrences = text("endOccurrences") ?: "12",
        endDate = text("endDate") ?: schedule.nextDate,
        postsTransaction = schedule.postsTransaction,
    )
}

/** A new monthly schedule filled from a transaction, as Actual starts one from the transactions it was made from. */
internal fun scheduleDraftValues(
    seed: ScheduleSeed,
    format: (Long) -> String,
): ScheduleDraftValues =
    ScheduleDraftValues(
        amount = format(abs(seed.amountMinor)),
        outflow = seed.amountMinor <= 0,
        accountId = seed.accountId,
        payeeId = seed.payeeId,
        categoryId = seed.categoryId,
        date = seed.date,
        endDate = seed.date,
    )

/** A field the form flags, with the message shown under it. */
internal data class ScheduleDraftError(
    val field: String,
    val message: String,
)

/** The first problem with the draft, checked in the order the fields appear. */
internal fun ScheduleEditDraft.validationError(
    amountMinor: Long?,
    amountMaxMinor: Long?,
    nameRequired: Boolean,
): ScheduleDraftError? {
    val interval = intervalDraft.toIntOrNull()
    val occurrences = endOccurrencesDraft.toIntOrNull()
    val repeats = frequencyDraft != "once"
    return when {
        nameRequired && nameDraft.isBlank() -> ScheduleDraftError("name", "Enter a schedule name.")
        nameDraft.trim().length > 100 -> ScheduleDraftError("name", "Schedule names can be up to 100 characters.")
        amountMinor == null -> ScheduleDraftError("amount", "Enter a valid amount.")
        amountRuleDraft == "isbetween" && amountMaxMinor == null -> ScheduleDraftError("maximum", "Enter a valid maximum amount.")
        amountRuleDraft == "isbetween" && amountMinor > amountMaxMinor!! ->
            ScheduleDraftError("maximum", "The maximum must be at least the minimum.")
        accountIdDraft.isBlank() -> ScheduleDraftError("account", "Choose an account.")
        payeeIdDraft.isBlank() -> ScheduleDraftError("payee", "Choose a payee.")
        repeats && (interval == null || interval !in 1..999) -> ScheduleDraftError("interval", "Repeat interval must be between 1 and 999.")
        parseIsoDate(dateDraft) == null -> ScheduleDraftError("startDate", "Choose a valid start date.")
        repeats && endModeDraft == "after_n_occurrences" && (occurrences == null || occurrences !in 1..100_000) ->
            ScheduleDraftError("occurrences", "Enter a valid number of repeats.")
        repeats && endModeDraft == "on_date" && parseIsoDate(endDateDraft) == null ->
            ScheduleDraftError("endDate", "Choose a valid end date.")
        repeats && endModeDraft == "on_date" && endDateDraft < dateDraft ->
            ScheduleDraftError("endDate", "The end date must be on or after the start date.")
        else -> null
    }
}

/** The date rule Actual stores: a plain date, or a recurring config keeping any keys this form doesn't edit. */
internal fun ScheduleEditDraft.dateRule(previous: JsonObject?): JsonElement {
    if (frequencyDraft == "once") return JsonPrimitive(dateDraft)
    return buildJsonObject {
        previous?.forEach { (key, value) -> if (key !in RECUR_KEYS) put(key, value) }
        put("frequency", frequencyDraft)
        put("interval", intervalDraft.toIntOrNull() ?: 1)
        put("start", dateDraft)
        put(
            "patterns",
            buildJsonArray {
                if (frequencyDraft == "monthly") {
                    patternsDraft.forEach { pattern ->
                        add(
                            buildJsonObject {
                                put("type", pattern.type)
                                put("value", pattern.value)
                            },
                        )
                    }
                }
            },
        )
        put("skipWeekend", skipWeekendDraft)
        put("weekendSolveMode", weekendModeDraft)
        put("endMode", endModeDraft)
        if (endModeDraft == "after_n_occurrences") put("endOccurrences", endOccurrencesDraft.toIntOrNull() ?: 1)
        if (endModeDraft == "on_date") put("endDate", endDateDraft)
    }
}

/** The fields sent to Actual, with the amount signed by direction; a new schedule leaves out an empty name and category. */
internal fun ScheduleEditDraft.scheduleFields(
    previousDate: JsonObject?,
    amountMinor: Long,
    amountMaxMinor: Long?,
    creating: Boolean,
): JsonObject =
    buildJsonObject {
        val name = nameDraft.trim()
        if (!creating || name.isNotEmpty()) put("name", name)
        put("accountId", accountIdDraft)
        put("payeeId", payeeIdDraft)
        put("amountOp", amountRuleDraft)
        if (amountRuleDraft == "isbetween") {
            val high = amountMaxMinor ?: amountMinor
            put(
                "amountRangeMinor",
                buildJsonObject {
                    put("num1", if (outflowDraft) -high else amountMinor)
                    put("num2", if (outflowDraft) -amountMinor else high)
                },
            )
        } else {
            put("amountMinor", if (outflowDraft) -amountMinor else amountMinor)
        }
        put("date", dateRule(previousDate))
        when {
            categoryIdDraft.isNotBlank() -> put("categoryId", categoryIdDraft)
            !creating -> put("categoryId", JsonNull)
        }
        put("postsTransaction", postsTransactionDraft)
    }
