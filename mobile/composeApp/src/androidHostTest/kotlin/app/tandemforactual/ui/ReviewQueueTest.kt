package app.tandemforactual.ui

import app.tandemforactual.ui.feature.review.ReviewQueue
import app.tandemforactual.ui.feature.review.ReviewStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewQueueTest {
    @Test
    fun skipsCategorizesRevisitsAndUndoes() {
        var queue = ReviewQueue(listOf("a", "b", "c"))
        assertEquals("a", queue.current)
        queue = queue.skip().categorize("groceries")
        assertEquals(listOf("c"), queue.pending)
        assertEquals(listOf("a"), queue.skipped)

        val (undone, step) = checkNotNull(queue.undo())
        assertEquals(ReviewStep.Categorized("b", "groceries"), step)
        assertEquals(listOf("b", "c"), undone.pending)

        val (beforeSkip, skip) = checkNotNull(undone.undo())
        assertEquals(ReviewStep.Skipped("a"), skip)
        assertEquals(listOf("a", "b", "c"), beforeSkip.pending)
        assertTrue(beforeSkip.skipped.isEmpty())
        assertNull(beforeSkip.undo())

        queue = queue.categorize("rent")
        assertTrue(queue.done)
        assertNull(queue.current)
        assertEquals(queue, queue.skip())
        assertEquals(queue, queue.categorize("x"))
        queue = queue.revisitSkipped()
        assertEquals(listOf("a"), queue.pending)
        assertTrue(queue.skipped.isEmpty())
    }

    @Test
    fun dropsTransactionsCategorizedElsewhere() {
        val queue = ReviewQueue(listOf("a", "b"), skipped = listOf("c"))
        assertEquals(ReviewQueue(listOf("b"), skipped = emptyList()), queue.keepOnly(setOf("b")))
    }
}
