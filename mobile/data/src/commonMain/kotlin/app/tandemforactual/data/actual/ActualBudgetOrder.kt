package app.tandemforactual.data.actual

/** One "move before" step; a null [beforeId] moves the item to the end. */
internal data class OrderMove(
    val id: String,
    val beforeId: String?,
)

/** The order merge the gateway used (`budget-order-merge.ts`), so reorders keep moves other devices made meanwhile. */
internal object BudgetOrderMerge {
    private fun fail(message: String): Nothing = throw ActualEditException(message)

    private fun unique(
        ids: List<String>,
        label: String,
    ) {
        if (ids.toSet().size != ids.size) fail("The $label contains an item more than once")
    }

    /** Replays the user's moves from [base] to [desired] onto the [live] order. */
    fun rebase(
        base: List<String>,
        desired: List<String>,
        live: List<String>,
    ): List<String> {
        unique(base, "base order")
        unique(desired, "order")
        unique(live, "live order")
        if (base.size != desired.size || desired.any { it !in base }) fail("The order must contain the same items as the base order")
        val baseIndex = base.withIndex().associate { it.value to it.index }
        val stable = longestIncreasingRun(desired.map { baseIndex.getValue(it) })
        val moved = desired.filterIndexed { index, _ -> index !in stable }.toSet()
        val liveSet = live.toSet()
        val result = live.filter { it !in moved }.toMutableList()
        for (index in desired.indices.reversed()) {
            val id = desired[index]
            if (id !in moved || id !in liveSet) continue
            val anchor = (index + 1 until desired.size).map { result.indexOf(desired[it]) }.firstOrNull { it >= 0 } ?: -1
            if (anchor < 0) result += id else result.add(anchor, id)
        }
        return result
    }

    /** The fewest moves that turn [current] into [target]; [pinned] items are never moved. */
    fun plan(
        current: List<String>,
        target: List<String>,
        pinned: Set<String> = emptySet(),
    ): List<OrderMove> {
        unique(current, "current order")
        unique(target, "target order")
        val currentIndex = current.withIndex().associate { it.value to it.index }
        if (current.size != target.size ||
            target.any { it !in currentIndex }
        ) {
            fail("The target order must contain the same items as the current order")
        }
        val positions = target.map { currentIndex.getValue(it) }
        val stable = mutableSetOf<String>()
        var segmentStart = 0
        var lowerBound = -1
        for (index in 0..target.size) {
            val isPinned = index < target.size && target[index] in pinned
            if (!isPinned && index < target.size) continue
            val upperBound = if (index < target.size) positions[index] else current.size
            if (upperBound <= lowerBound) fail("Pinned items must keep their relative order")
            val candidates = (segmentStart until index).filter { positions[it] in (lowerBound + 1) until upperBound }
            longestIncreasingRun(candidates.map { positions[it] }).forEach { stable += target[candidates[it]] }
            if (index < target.size) {
                stable += target[index]
                lowerBound = upperBound
            }
            segmentStart = index + 1
        }
        return target.indices
            .reversed()
            .filter { target[it] !in stable }
            .map { OrderMove(target[it], target.getOrNull(it + 1)) }
    }

    /** Indexes of one longest strictly increasing subsequence of [values]. */
    private fun longestIncreasingRun(values: List<Int>): Set<Int> {
        val tails = mutableListOf<Int>()
        val previous = IntArray(values.size) { -1 }
        for (index in values.indices) {
            var low = 0
            var high = tails.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (values[tails[middle]] < values[index]) low = middle + 1 else high = middle
            }
            if (low > 0) previous[index] = tails[low - 1]
            if (low == tails.size) tails += index else tails[low] = index
        }
        val run = mutableSetOf<Int>()
        var cursor = tails.lastOrNull() ?: -1
        while (cursor >= 0) {
            run += cursor
            cursor = previous[cursor]
        }
        return run
    }
}
