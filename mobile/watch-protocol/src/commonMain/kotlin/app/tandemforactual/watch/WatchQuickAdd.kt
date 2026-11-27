package app.tandemforactual.watch

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A transaction entered on the watch; [entryId] stays the same however often it's sent, so the phone adds it once. */
@Serializable
data class WatchQuickAdd(
    @SerialName("id") val entryId: String,
    @SerialName("a") val amountMinor: Long,
    @SerialName("c") val categoryId: String? = null,
    @SerialName("p") val payee: String? = null,
    @SerialName("at") val createdAtEpochMs: Long,
)

@Serializable
enum class WatchQuickAddStatus {
    @SerialName("added")
    ADDED,

    @SerialName("repeat")
    REPEATED,

    @SerialName("locked")
    LOCKED,

    @SerialName("refused")
    REFUSED,
}

@Serializable
data class WatchQuickAddResult(
    @SerialName("id") val entryId: String,
    @SerialName("s") val status: WatchQuickAddStatus,
    @SerialName("m") val message: String? = null,
)

@Serializable
data class PendingQuickAdd(
    @SerialName("e") val entry: WatchQuickAdd,
    @SerialName("n") val attempts: Int = 0,
    @SerialName("t") val nextAttemptAtEpochMs: Long = 0,
    @SerialName("w") val waitingForUnlock: Boolean = false,
) {
    companion object {
        val listSerializer: KSerializer<List<PendingQuickAdd>> = ListSerializer(serializer())
    }
}

/**
 * Quick adds the phone hasn't confirmed yet. Each is sent again with a growing pause until the phone answers; one
 * the phone couldn't add while its budget was locked waits until the next summary shows it open.
 */
data class QuickAddQueue(
    val items: List<PendingQuickAdd> = emptyList(),
) {
    fun enqueue(entry: WatchQuickAdd): QuickAddQueue {
        if (items.any { it.entry.entryId == entry.entryId }) return this
        return copy(items = (items + PendingQuickAdd(entry)).takeLast(MAX_ITEMS))
    }

    fun due(nowEpochMs: Long): List<WatchQuickAdd> =
        items.filter { !it.waitingForUnlock && it.nextAttemptAtEpochMs <= nowEpochMs }.map { it.entry }

    /** When the next entry is due, or null when nothing will be sent without a new summary. */
    fun nextAttemptAt(): Long? = items.filterNot { it.waitingForUnlock }.minOfOrNull { it.nextAttemptAtEpochMs }

    fun sent(
        entryId: String,
        nowEpochMs: Long,
    ): QuickAddQueue =
        update(entryId) {
            it.copy(attempts = it.attempts + 1, nextAttemptAtEpochMs = nowEpochMs + backoffMs(it.attempts + 1))
        }

    fun resolved(result: WatchQuickAddResult): QuickAddQueue =
        when (result.status) {
            WatchQuickAddStatus.LOCKED -> update(result.entryId) { it.copy(waitingForUnlock = true) }
            else -> copy(items = items.filterNot { it.entry.entryId == result.entryId })
        }

    fun unlocked(): QuickAddQueue = copy(items = items.map { it.copy(waitingForUnlock = false, nextAttemptAtEpochMs = 0) })

    private fun update(
        entryId: String,
        change: (PendingQuickAdd) -> PendingQuickAdd,
    ): QuickAddQueue = copy(items = items.map { if (it.entry.entryId == entryId) change(it) else it })

    companion object {
        const val MAX_ITEMS = 50
        private const val FIRST_PAUSE_MS = 15_000L
        private const val LONGEST_PAUSE_MS = 30L * 60 * 1000

        fun backoffMs(attempts: Int): Long {
            val doublings = (attempts - 1).coerceIn(0, 16)
            return (FIRST_PAUSE_MS shl doublings).coerceAtMost(LONGEST_PAUSE_MS)
        }
    }
}
