package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.OverspentCategory
import app.tandemforactual.data.store.dueSchedules
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.newlyOverspentCategories
import app.tandemforactual.data.store.overspentCategories
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.unannouncedSchedules
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class AutomationEventKind { CATEGORY_OVERSPENT, SCHEDULE_DUE, SYNC_FAILED }

/** Something that happened that automation apps can react to; [values] become their variables. */
data class AutomationEvent(
    val id: String,
    val kind: AutomationEventKind,
    val values: Map<String, String>,
    val atEpochMs: Long,
)

object AutomationEventValue {
    const val CATEGORY = "category"
    const val CATEGORY_ID = "category_id"
    const val OVERSPENT_BY = "overspent_by"
    const val SCHEDULE = "schedule"
    const val AMOUNT = "amount"
    const val DUE_DATE = "due_date"
    const val ERROR = "error"
}

/** Budget events name categories and schedules and carry amounts, so they follow Read budget's rules exactly. */
fun budgetEventsAllowed(preferences: Map<String, String>): Boolean =
    runCatching { automationTarget(preferences, AutomationKind.READ) }.isSuccess

/** A failed sync says nothing about the budget, so it needs only the read opt-in. */
fun syncEventsAllowed(preferences: Map<String, String>): Boolean = AutomationAccess.readsAllowed(preferences)

private const val OVERSPENT_KEY = "internal:automation-overspent"
private const val SCHEDULES_KEY = "internal:automation-due-schedules"
private const val SYNC_FAILED_KEY = "internal:automation-sync-failed"

/**
 * Categories newly over budget this month and schedules newly due today, each once. The record of what was announced
 * stays in [mainDatabase] beside the notifications' own, holding ids only.
 */
private suspend fun budgetAutomationEvents(
    mainDatabase: TandemDatabase,
    budgetDatabase: TandemDatabase,
    budgetId: String,
    todayIso: String,
    nowEpochMs: Long,
): List<AutomationEvent> {
    val store = AppPreferencesStore(mainDatabase)
    val repository = LocalBudgetRepository(budgetDatabase) { nowEpochMs }
    val digits = repository.readBudgetDisplaySettings(budgetId)?.currencyFractionDigits ?: 2
    val overspent =
        newlyOverspentCategories(repository, store, OVERSPENT_KEY, budgetId, todayIso.take(7)).orEmpty().map { category ->
            AutomationEvent(Uuid.random().toString(), AutomationEventKind.CATEGORY_OVERSPENT, category.values(digits), nowEpochMs)
        }
    val due = dueSchedules(repository, budgetId, todayIso, todayIso)
    val schedules =
        unannouncedSchedules(store, "$SCHEDULES_KEY:$budgetId", due).map { schedule ->
            val values =
                mapOf(
                    AutomationEventValue.SCHEDULE to schedule.name,
                    AutomationEventValue.AMOUNT to schedule.amountMinor?.let { automationDecimal(it, digits) }.orEmpty(),
                    AutomationEventValue.DUE_DATE to schedule.date,
                )
            AutomationEvent(Uuid.random().toString(), AutomationEventKind.SCHEDULE_DUE, values, nowEpochMs)
        }
    return overspent + schedules
}

/** A failed sync, once until a sync succeeds again; null when this failure was already announced. */
private suspend fun syncFailedEvent(
    mainDatabase: TandemDatabase,
    error: String,
    nowEpochMs: Long,
): AutomationEvent? {
    val store = AppPreferencesStore(mainDatabase)
    if (store.read()[SYNC_FAILED_KEY] == "true") return null
    store.putValue(SYNC_FAILED_KEY, "true")
    return AutomationEvent(
        Uuid.random().toString(),
        AutomationEventKind.SYNC_FAILED,
        mapOf(AutomationEventValue.ERROR to error),
        nowEpochMs,
    )
}

private suspend fun syncSucceeded(mainDatabase: TandemDatabase) {
    val store = AppPreferencesStore(mainDatabase)
    if (store.read()[SYNC_FAILED_KEY] != null) store.putValue(SYNC_FAILED_KEY, "")
}

/**
 * After a sync: records the budget events automation apps may see and hands each to [send]. Nothing is recorded or
 * sent while reads are off, app lock or Hide budget amounts is on; those occurrences come up again once allowed.
 */
suspend fun announceAfterSync(
    mainDatabase: TandemDatabase,
    budgetDatabase: TandemDatabase,
    budgetId: String,
    preferences: Map<String, String>,
    todayIso: String,
    nowEpochMs: Long,
    send: (AutomationEvent) -> Unit,
) {
    syncSucceeded(mainDatabase)
    if (!budgetEventsAllowed(preferences)) return
    val events = budgetAutomationEvents(mainDatabase, budgetDatabase, budgetId, todayIso, nowEpochMs)
    AutomationEventStore(budgetDatabase).add(events, nowEpochMs)
    events.forEach(send)
}

/** After a failed sync: one event per run of failures, while reads are allowed. */
suspend fun announceSyncFailure(
    mainDatabase: TandemDatabase,
    preferences: Map<String, String>,
    error: String,
    nowEpochMs: Long,
    send: (AutomationEvent) -> Unit,
) {
    if (!syncEventsAllowed(preferences)) return
    val event = syncFailedEvent(mainDatabase, error, nowEpochMs) ?: return
    AutomationEventStore(mainDatabase).add(listOf(event), nowEpochMs)
    send(event)
}

/** Whether a "Category overspent" event matches the category an automation asked for; blank means any. */
fun AutomationEvent.matchesCategory(category: String?): Boolean {
    val wanted = automationInput(category) ?: return true
    return values[AutomationEventValue.CATEGORY_ID] == wanted ||
        values[AutomationEventValue.CATEGORY]?.trim().equals(wanted, ignoreCase = true)
}

/** Over-budget categories this month, from this device's copy only; [category] narrows it to one, blank means any. */
suspend fun categoriesOverBudget(
    database: TandemDatabase,
    budgetId: String,
    category: String?,
    todayIso: String,
): List<OverspentCategory> {
    val record = LocalBudgetRepository(database) { 0L }.findRecord(budgetId, "budget-month", todayIso.take(7)) ?: return emptyList()
    val all = overspentCategories(record.localJson ?: record.remoteJson.orEmpty())
    val wanted = automationInput(category) ?: return all
    return all.filter { it.id == wanted || it.name.trim().equals(wanted, ignoreCase = true) }
}

/** What a "Category over budget" state reports: names joined with commas, and how far over they are together. */
suspend fun overBudgetValues(
    database: TandemDatabase,
    budgetId: String,
    categories: List<OverspentCategory>,
): Map<String, String> {
    val digits = LocalBudgetRepository(database) { 0L }.readBudgetDisplaySettings(budgetId)?.currencyFractionDigits ?: 2
    return mapOf(
        AutomationEventValue.CATEGORY to categories.joinToString(", ") { it.name },
        AutomationEventValue.OVERSPENT_BY to automationDecimal(-categories.sumOf { it.balanceMinor }, digits),
    )
}

private fun OverspentCategory.values(digits: Int) =
    mapOf(
        AutomationEventValue.CATEGORY to name,
        AutomationEventValue.CATEGORY_ID to id,
        AutomationEventValue.OVERSPENT_BY to automationDecimal(-balanceMinor, digits),
    )

private const val EVENT_KEY = "internal:automation-events-v1"
private const val EVENT_KEEP_MS = 60L * 60 * 1000
private const val MAX_EVENTS = 50

/**
 * Events waiting for automation apps to collect. Only an event's id leaves the app in the broadcast that wakes them;
 * names and amounts stay in the database the budget lives in until an app with the read opt-in asks.
 */
class AutomationEventStore(
    database: TandemDatabase,
) {
    private val store = AppPreferencesStore(database)

    suspend fun add(
        events: List<AutomationEvent>,
        nowEpochMs: Long,
    ) {
        if (events.isEmpty()) return
        val kept = (all().filter { nowEpochMs - it.atEpochMs < EVENT_KEEP_MS } + events).takeLast(MAX_EVENTS)
        store.putValue(EVENT_KEY, encode(kept))
    }

    suspend fun find(
        id: String,
        nowEpochMs: Long,
    ): AutomationEvent? = all().firstOrNull { it.id == id && nowEpochMs - it.atEpochMs < EVENT_KEEP_MS }

    private suspend fun all(): List<AutomationEvent> {
        val array = store.read()[EVENT_KEY]?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
        return array.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            AutomationEvent(
                id = (item["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                kind =
                    AutomationEventKind.entries.firstOrNull { it.name == (item["kind"] as? JsonPrimitive)?.contentOrNull }
                        ?: return@mapNotNull null,
                values =
                    (item["values"] as? JsonObject)
                        .orEmpty()
                        .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }
                        .toMap(),
                atEpochMs = (item["at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
            )
        }
    }

    private fun encode(events: List<AutomationEvent>): String =
        JsonArray(
            events.map { event ->
                buildJsonObject {
                    put("id", event.id)
                    put("kind", event.kind.name)
                    put("values", JsonObject(event.values.mapValues { JsonPrimitive(it.value) }))
                    put("at", event.atEpochMs)
                }
            },
        ).toString()
}
