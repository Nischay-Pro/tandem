package app.tandemforactual.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickAddQueueTest {
    private fun entry(id: String) = WatchQuickAdd(id, -500, null, null, 0)

    @Test
    fun anEntryIsQueuedOnceHoweverOftenItIsAdded() {
        val queue = QuickAddQueue().enqueue(entry("a")).enqueue(entry("a")).enqueue(entry("b"))

        assertEquals(listOf("a", "b"), queue.due(0).map { it.entryId })
    }

    @Test
    fun anUnansweredEntryWaitsLongerEachTime() {
        val once = QuickAddQueue().enqueue(entry("a")).sent("a", nowEpochMs = 1_000)
        val twice = once.sent("a", nowEpochMs = 20_000)

        assertTrue(once.due(1_000 + 14_999).isEmpty())
        assertEquals(1, once.due(1_000 + 15_000).size)
        assertEquals(20_000 + 30_000, twice.nextAttemptAt())
        assertEquals(30L * 60 * 1000, QuickAddQueue.backoffMs(40))
    }

    @Test
    fun anAnswerFromThePhoneClearsTheEntry() {
        val queue = QuickAddQueue().enqueue(entry("a")).enqueue(entry("b"))

        val added = queue.resolved(WatchQuickAddResult("a", WatchQuickAddStatus.ADDED))
        val repeated = added.resolved(WatchQuickAddResult("b", WatchQuickAddStatus.REPEATED))

        assertEquals(listOf("b"), added.items.map { it.entry.entryId })
        assertTrue(repeated.items.isEmpty())
    }

    @Test
    fun aLockedBudgetHoldsTheEntryUntilItOpens() {
        val locked = QuickAddQueue().enqueue(entry("a")).resolved(WatchQuickAddResult("a", WatchQuickAddStatus.LOCKED))

        assertTrue(locked.due(Long.MAX_VALUE).isEmpty())
        assertNull(locked.nextAttemptAt())
        assertEquals(listOf("a"), locked.unlocked().due(0).map { it.entryId })
    }

    @Test
    fun theQueueSurvivesBeingStored() {
        val queue = QuickAddQueue().enqueue(entry("a")).sent("a", 5)

        assertEquals(queue.items, WatchCodec.decodeQueue(WatchCodec.encodeQueue(queue.items)))
        assertTrue(WatchCodec.decodeQueue("garbage").isEmpty())
        assertTrue(WatchCodec.decodeQueue(null).isEmpty())
    }

    @Test
    fun theOldestEntriesGiveWayOnceTheQueueIsFull() {
        val queue = (0..QuickAddQueue.MAX_ITEMS).fold(QuickAddQueue()) { acc, index -> acc.enqueue(entry("e$index")) }

        assertEquals(QuickAddQueue.MAX_ITEMS, queue.items.size)
        assertEquals(
            "e1",
            queue.items
                .first()
                .entry.entryId,
        )
    }
}
