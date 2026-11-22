@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import app.tandemforactual.ui.core.format.formatMomentAtEpochMilliseconds

internal fun budgetDisplayNameForHeader(name: String): String {
    val segments = name.split(" · ")
    return segments.firstOrNull { segment ->
        val codeStart = segment.lastIndexOf('(')
        segment.endsWith(')') &&
            codeStart >= 0 &&
            segment.substring(codeStart + 1, segment.lastIndex).length == 3 &&
            segment.substring(codeStart + 1, segment.lastIndex).all { it in 'A'..'Z' }
    } ?: segments.firstOrNull().orEmpty()
}

internal fun categoryCountLabel(count: Int): String = "${count.coerceAtLeast(0)} ${if (count == 1) "category" else "categories"}"

internal fun mainCloudStatusLabel(status: MainCloudStatus): String =
    when (status) {
        MainCloudStatus.OFF -> "Sync off"
        MainCloudStatus.SYNCED -> "Synced"
        MainCloudStatus.SYNCING -> "Syncing"
        MainCloudStatus.ERROR -> "Sync needs attention"
    }

internal fun mainCloudStatusAccessibilityDescription(
    status: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
): String {
    val conflictCount = pendingWriteStatuses.count { it == "conflict" }
    val uncertainCount = pendingWriteStatuses.count { it == "uncertain" }
    val rejectedCount = pendingWriteStatuses.count { it == "rejected" }
    val reviewCount = conflictCount + uncertainCount + rejectedCount
    if (reviewCount == 0) {
        return when (status) {
            MainCloudStatus.OFF -> "Sync is off"
            MainCloudStatus.SYNCED -> "Everything synced"
            MainCloudStatus.SYNCING -> "Syncing changes"
            MainCloudStatus.ERROR -> "Sync needs attention"
        }
    }

    val reasons =
        buildList {
            if (conflictCount > 0) add("$conflictCount ${pluralizeCloudCount(conflictCount, "conflict", "conflicts")}")
            if (uncertainCount > 0) {
                add("$uncertainCount ${pluralizeCloudCount(uncertainCount, "edit with unknown status", "edits with unknown status")}")
            }
            if (rejectedCount > 0) add("$rejectedCount ${pluralizeCloudCount(rejectedCount, "rejected edit", "rejected edits")}")
        }
    val changeNoun = if (reviewCount == 1) "change" else "changes"
    val verb = if (reviewCount == 1) "needs" else "need"
    return "$reviewCount $changeNoun $verb review: ${reasons.joinToString(", ")}."
}

private fun pluralizeCloudCount(
    count: Int,
    singular: String,
    plural: String,
): String = if (count == 1) singular else plural

internal enum class MainCloudStatus {
    OFF,
    SYNCED,
    SYNCING,
    ERROR,
}

internal fun resolveMainCloudStatus(
    syncEnabled: Boolean,
    isSyncing: Boolean,
    hasSyncError: Boolean,
    persistedSyncStatus: String?,
    pendingWriteStatuses: Collection<String>,
    hasSuccessfulSync: Boolean,
    hasPendingBudgetChanges: Boolean = false,
    budgetChangesWaitingForConnection: Boolean = false,
): MainCloudStatus {
    val hasWriteNeedingReview = pendingWriteStatuses.any { it in setOf("conflict", "uncertain", "rejected") }
    if (hasWriteNeedingReview) return MainCloudStatus.ERROR
    if (isSyncing || persistedSyncStatus == "SYNCING") return MainCloudStatus.SYNCING
    if (budgetChangesWaitingForConnection) return MainCloudStatus.ERROR
    if (!syncEnabled) return if (hasPendingBudgetChanges) MainCloudStatus.ERROR else MainCloudStatus.OFF

    if (hasSyncError || persistedSyncStatus in setOf("FAILED", "NEEDS_FULL_SYNC")) {
        return MainCloudStatus.ERROR
    }
    if (pendingWriteStatuses.any { it in setOf("queued", "sending") } || hasPendingBudgetChanges) return MainCloudStatus.SYNCING
    return if (hasSuccessfulSync) MainCloudStatus.SYNCED else MainCloudStatus.ERROR
}

internal fun deviceWriteActionLabel(action: String): String =
    when (action) {
        "CREATE" -> "Added"
        "DELETE" -> "Deleted"
        else -> "Edited"
    }

internal fun deviceWriteStatusLabel(
    status: String,
    lastErrorCode: String?,
): String =
    when (status) {
        "committed" -> "Sent"
        "queued" -> "Waiting"
        "sending" -> "Sending"
        "discarded" -> if (lastErrorCode == "discarded-by-user") "Undone" else "Discarded"
        "superseded" -> "Replaced by a later edit"
        "rejected" -> "Rejected"
        "conflict" -> "Needs review"
        "uncertain" -> "Unconfirmed"
        else -> status.replaceFirstChar { it.uppercase() }
    }

/** Short status shown under the budget name, so sync state reads at a glance without opening Sync Center. */
internal fun headerSyncCaption(
    status: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    lastSyncedAtEpochMs: Long?,
    nowEpochMs: Long,
    formatMoment: (Long, Long) -> String = ::formatMomentAtEpochMilliseconds,
): String? {
    val needsReview = pendingWriteStatuses.count { it in setOf("conflict", "uncertain", "rejected") }
    val waiting = pendingWriteStatuses.count { it in setOf("queued", "sending") }

    fun changes(count: Int) = if (count == 1) "1 change" else "$count changes"
    return when {
        needsReview > 0 -> "${changes(needsReview)} ${if (needsReview == 1) "needs" else "need"} review"
        status == MainCloudStatus.SYNCING -> "Syncing…"
        status == MainCloudStatus.ERROR -> "Couldn’t sync"
        waiting > 0 -> "${changes(waiting)} waiting to sync"
        status == MainCloudStatus.OFF -> "Saved on this device"
        lastSyncedAtEpochMs == null -> null
        nowEpochMs - lastSyncedAtEpochMs < 60_000L -> "Synced just now"
        nowEpochMs - lastSyncedAtEpochMs < 3_600_000L -> "Synced ${(nowEpochMs - lastSyncedAtEpochMs) / 60_000L} min ago"
        else -> "Synced ${formatMoment(lastSyncedAtEpochMs, nowEpochMs)}"
    }
}

internal val UNSYNCED_TRANSACTION_WRITE_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

internal fun writeStatusLabel(status: String): String =
    when (status) {
        "queued" -> "Waiting to sync"
        "sending" -> "Syncing"
        "uncertain" -> "Status unknown"
        "conflict" -> "Needs review"
        "rejected" -> "Rejected"
        "committed" -> "Saved"
        "discarded" -> "Discarded"
        else -> "Change"
    }
