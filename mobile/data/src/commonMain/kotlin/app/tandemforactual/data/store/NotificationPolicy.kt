package app.tandemforactual.data.store

import app.tandemforactual.data.actual.ActualDates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Each kind of phone notification Tandem can post, with the preference that turns it on or off. */
enum class AlertKind(
    val preference: String,
    val onByDefault: Boolean,
) {
    SYNC_FAILURE("syncReminders", true),
    NEEDS_REVIEW("conflictReviewAlerts", true),
    REJECTED_EDIT("rejectedEditAlerts", true),
    OVERSPENDING("overspendingAlerts", true),
    BANK_SYNC_FAILURE("bankSyncAlerts", true),
    UPCOMING_BILLS("upcomingBillAlerts", false),
    LARGE_TRANSACTION("largeTransactionAlerts", false),
    UNCATEGORIZED("uncategorizedAlerts", true),
}

/** A daily window, in minutes after midnight, when notifications stay silent. It may wrap past midnight. */
data class QuietHours(
    val startMinute: Int,
    val endMinute: Int,
) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinute <= endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }

    fun encode(): String = "$startMinute-$endMinute"

    companion object {
        fun parse(text: String?): QuietHours? {
            val parts = text?.split('-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
            if (parts.any { it !in 0 until MINUTES_PER_DAY } || parts[0] == parts[1]) return null
            return QuietHours(parts[0], parts[1])
        }
    }
}

private const val MINUTES_PER_DAY = 24 * 60

/** Which notifications may fire, read from the saved app preferences. */
data class NotificationPolicy(
    val enabled: Set<AlertKind>,
    val quietHours: QuietHours? = null,
    val largeTransactionMinor: Long = DEFAULT_LARGE_TRANSACTION_MINOR,
    val billLeadDays: Int = DEFAULT_BILL_LEAD_DAYS,
    val hideAmounts: Boolean = false,
) {
    fun allows(
        kind: AlertKind,
        minuteOfDay: Int,
    ): Boolean = kind in enabled && quietHours?.contains(minuteOfDay) != true

    companion object {
        const val QUIET_HOURS = "quietHours"
        const val LARGE_TRANSACTION_MINOR = "largeTransactionMinor"
        const val BILL_LEAD_DAYS = "billLeadDays"
        const val DEFAULT_LARGE_TRANSACTION_MINOR = 50_000L
        const val DEFAULT_BILL_LEAD_DAYS = 3

        fun read(preferences: Map<String, String>): NotificationPolicy =
            NotificationPolicy(
                enabled =
                    AlertKind.entries
                        .filterTo(mutableSetOf()) { preferences[it.preference]?.toBooleanStrictOrNull() ?: it.onByDefault },
                quietHours = QuietHours.parse(preferences[QUIET_HOURS]),
                largeTransactionMinor =
                    preferences[LARGE_TRANSACTION_MINOR]?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_LARGE_TRANSACTION_MINOR,
                billLeadDays = preferences[BILL_LEAD_DAYS]?.toIntOrNull()?.coerceIn(0, 14) ?: DEFAULT_BILL_LEAD_DAYS,
                hideAmounts = preferences["privacyModeEnabled"] == "true",
            )
    }
}

/** A notification ready to post: a stable key so it replaces rather than stacks, and its text. */
data class LocalAlert(
    val kind: AlertKind,
    val title: String,
    val message: String,
)

/**
 * Schedules due from today through [NotificationPolicy.billLeadDays] days ahead that haven't been announced for that
 * date yet. A skipped or moved schedule announces again for its new date.
 */
suspend fun upcomingBillsAlert(
    repository: LocalBudgetRepository,
    preferences: AppPreferencesStore,
    budgetId: String,
    today: String,
    policy: NotificationPolicy,
    formatAmount: (Long) -> String,
): LocalAlert? {
    val last = ActualDates.addDays(today, policy.billLeadDays)
    val due =
        repository
            .findRecords(budgetId, "schedule")
            .mapNotNull { record ->
                val body = record.body() ?: return@mapNotNull null
                if ((body["completed"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                val next = (body["next_date"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (next < today || next > last) return@mapNotNull null
                val name = (body["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "A scheduled payment"
                Triple("${record.recordId}@$next", name, (body["amount"] as? JsonPrimitive)?.longOrNull)
            }.sortedBy { it.first.substringAfter('@') }
    val key = "internal:announced-bills:$budgetId"
    val announced =
        preferences
            .read()[key]
            .orEmpty()
            .split(',')
            .filter(String::isNotEmpty)
            .toSet()
    preferences.putValue(key, due.joinToString(",") { it.first })
    val fresh = due.filterNot { it.first in announced }
    if (fresh.isEmpty()) return null
    val message =
        if (fresh.size == 1) {
            val (_, name, amount) = fresh.single()
            val money = amount?.takeUnless { policy.hideAmounts }?.let { " for ${formatAmount(kotlin.math.abs(it))}" }.orEmpty()
            "$name is due soon$money."
        } else {
            "${fresh[0].second} and ${fresh.size - 1} more are due in the next ${policy.billLeadDays} days."
        }
    return LocalAlert(AlertKind.UPCOMING_BILLS, "Upcoming bills", message)
}

/** Spending of at least [NotificationPolicy.largeTransactionMinor] in the last few days, announced once each. */
suspend fun largeTransactionAlert(
    repository: LocalBudgetRepository,
    preferences: AppPreferencesStore,
    budgetId: String,
    today: String,
    policy: NotificationPolicy,
    formatAmount: (Long) -> String,
): LocalAlert? {
    val large =
        repository
            .findRecords(budgetId, "transaction", fromDate = ActualDates.addDays(today, -LARGE_TRANSACTION_WINDOW_DAYS))
            .filter { (it.amountMinor ?: 0) <= -policy.largeTransactionMinor && !it.isParent }
    val key = "internal:announced-large:$budgetId"
    val announced =
        preferences
            .read()[key]
            .orEmpty()
            .split(',')
            .filter(String::isNotEmpty)
            .toSet()
    preferences.putValue(key, large.joinToString(",") { it.recordId })
    val fresh = large.filterNot { it.recordId in announced }
    if (fresh.isEmpty()) return null
    val first = fresh.first()
    val payee = first.payeeId?.let { repository.findRecord(budgetId, "payee", it)?.displayName }
    val message =
        when {
            fresh.size > 1 -> "${fresh.size} large transactions came in. Open Tandem to check them."
            policy.hideAmounts -> "A large transaction${payee?.let { " at $it" }.orEmpty()} came in."
            else -> "${formatAmount(-(first.amountMinor ?: 0))}${payee?.let { " at $it" }.orEmpty()} came in."
        }
    return LocalAlert(AlertKind.LARGE_TRANSACTION, "Large transaction", message)
}

/**
 * New transactions in on-budget accounts from the last couple of weeks that still need a category, announced once each.
 * The first check only notes what is already waiting, so turning this on doesn't announce an old backlog.
 */
suspend fun uncategorizedAlert(
    repository: LocalBudgetRepository,
    preferences: AppPreferencesStore,
    budgetId: String,
    today: String,
    policy: NotificationPolicy,
    formatAmount: (Long) -> String,
): LocalAlert? {
    val offBudget =
        repository
            .findRecords(budgetId, "account")
            .filter { account -> account.body()?.get("offbudget")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true }
            .map { it.recordId }
            .toSet()
    val waiting =
        repository
            .findRecords(budgetId, "transaction", fromDate = ActualDates.addDays(today, -UNCATEGORIZED_WINDOW_DAYS))
            .filter { it.categoryId == null && !it.isParent && it.transfer != true && it.accountId !in offBudget && !it.localDeleted }
    val key = "internal:announced-uncategorized:$budgetId"
    val stored = preferences.read()[key]
    preferences.putValue(key, waiting.joinToString(",") { it.recordId })
    val announced = stored?.split(',')?.filter(String::isNotEmpty)?.toSet() ?: return null
    val fresh = waiting.filterNot { it.recordId in announced }
    if (fresh.isEmpty()) return null
    if (fresh.size >
        1
    ) {
        return LocalAlert(AlertKind.UNCATEGORIZED, "New transactions to categorize", "${fresh.size} new transactions need a category.")
    }
    val only = fresh.single()
    val payee = only.payeeId?.let { repository.findRecord(budgetId, "payee", it)?.displayName }
    val at = payee?.let { " at $it" }.orEmpty()
    val message =
        if (policy.hideAmounts) "A transaction$at needs a category." else "${formatAmount(-(only.amountMinor ?: 0))}$at needs a category."
    return LocalAlert(AlertKind.UNCATEGORIZED, "New transaction to categorize", message)
}

/** Bank links whose last sync failed and that haven't been announced since they started failing. */
suspend fun bankSyncFailureAlert(
    preferences: AppPreferencesStore,
    budgetId: String,
    failingAccounts: List<String>,
): LocalAlert? {
    val key = "internal:announced-bank-failures:$budgetId"
    val announced =
        preferences
            .read()[key]
            .orEmpty()
            .split('\n')
            .filter(String::isNotEmpty)
            .toSet()
    preferences.putValue(key, failingAccounts.joinToString("\n"))
    val fresh = failingAccounts.filterNot { it in announced }
    if (fresh.isEmpty()) return null
    val message =
        if (fresh.size == 1) {
            "${fresh.single()} couldn’t sync with its bank. Open Bank connections to fix it."
        } else {
            "${fresh.size} accounts couldn’t sync with their banks. Open Bank connections to fix them."
        }
    return LocalAlert(AlertKind.BANK_SYNC_FAILURE, "Bank sync failed", message)
}

private const val LARGE_TRANSACTION_WINDOW_DAYS = 3
private const val UNCATEGORIZED_WINDOW_DAYS = 14

private fun app.tandemforactual.data.database.BudgetRecordEntity.body(): JsonObject? =
    runCatching { Json.parseToJsonElement(localJson ?: remoteJson ?: "") as? JsonObject }.getOrNull()
