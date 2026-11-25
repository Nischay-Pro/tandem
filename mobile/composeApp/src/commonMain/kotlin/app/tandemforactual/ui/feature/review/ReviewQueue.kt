package app.tandemforactual.ui.feature.review

/** One decision in a review session, kept so it can be undone. */
internal sealed interface ReviewStep {
    val transactionId: String

    data class Skipped(
        override val transactionId: String,
    ) : ReviewStep

    data class Categorized(
        override val transactionId: String,
        val categoryId: String,
    ) : ReviewStep
}

/**
 * A pass over uncategorized transactions: each is skipped or categorized once, skipped ones can be revisited at the
 * end, and the latest decision can be undone.
 */
internal data class ReviewQueue(
    val pending: List<String>,
    val skipped: List<String> = emptyList(),
    val history: List<ReviewStep> = emptyList(),
) {
    val current: String? get() = pending.firstOrNull()

    val done: Boolean get() = pending.isEmpty()

    fun skip(): ReviewQueue {
        val id = current ?: return this
        return copy(pending = pending.drop(1), skipped = skipped + id, history = history + ReviewStep.Skipped(id))
    }

    fun categorize(categoryId: String): ReviewQueue {
        val id = current ?: return this
        return copy(pending = pending.drop(1), history = history + ReviewStep.Categorized(id, categoryId))
    }

    /** The queue before the latest decision, and that decision; null when there's nothing to undo. */
    fun undo(): Pair<ReviewQueue, ReviewStep>? {
        val last = history.lastOrNull() ?: return null
        val restored =
            copy(
                pending = listOf(last.transactionId) + pending.filterNot { it == last.transactionId },
                skipped = if (last is ReviewStep.Skipped) skipped.dropLast(1) else skipped,
                history = history.dropLast(1),
            )
        return restored to last
    }

    /** Puts [transactionId] back at the front after its category is undone, wherever the queue has got to since. */
    fun restore(transactionId: String): ReviewQueue =
        copy(
            pending = listOf(transactionId) + pending.filterNot { it == transactionId },
            skipped = skipped.filterNot { it == transactionId },
            history = history.filterNot { it.transactionId == transactionId },
        )

    fun revisitSkipped(): ReviewQueue = copy(pending = pending + skipped, skipped = emptyList())

    /** Drops transactions that left the review elsewhere, such as being categorized on another device. */
    fun keepOnly(stillUncategorized: Set<String>): ReviewQueue =
        copy(pending = pending.filter { it in stillUncategorized }, skipped = skipped.filter { it in stillUncategorized })
}
