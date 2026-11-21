package app.tandemforactual.data.actual

/**
 * Actual's hybrid logical clock timestamp: `2015-04-24T22:23:42.123Z-1000-0123456789ABCDEF`. Strings sort in time order,
 * which Actual relies on for last-writer-wins merging.
 */
data class ActualTimestamp(
    val millis: Long,
    val counter: Int,
    val node: String,
) : Comparable<ActualTimestamp> {
    override fun toString(): String =
        "${isoMillis(millis)}-${counter.toString(16).uppercase().padStart(4, '0').takeLast(4)}-${node.padStart(16, '0').takeLast(16)}"

    /** MurmurHash3 of the string form, unsigned, as Actual's `Timestamp.hash()`. */
    fun hash(): Long = murmurHash3(toString())

    override fun compareTo(other: ActualTimestamp): Int = toString().compareTo(other.toString())

    companion object {
        const val MAX_COUNTER = 0xFFFF
        private const val MAX_NODE_LENGTH = 16

        val zero = ActualTimestamp(0, 0, "0000000000000000")

        /** Parses Actual's 46-character form; null when it isn't a valid timestamp. */
        fun parse(value: String): ActualTimestamp? {
            val parts = value.split('-')
            if (parts.size != 5) return null
            val millis = parseIsoMillis(parts.subList(0, 3).joinToString("-")) ?: return null
            val counter = parseLeadingHex(parts[3]) ?: return null
            val node = parts[4]
            if (millis < 0 || counter > MAX_COUNTER || node.length > MAX_NODE_LENGTH) return null
            return ActualTimestamp(millis, counter, node)
        }

        /** The lower bound Actual uses when asking a server for messages since an instant. */
        fun since(isoString: String): String = "$isoString-0000-0000000000000000"
    }
}

class ClockDriftException(
    message: String,
) : IllegalStateException(message)

class ClockOverflowException : IllegalStateException("timestamp counter overflow")

/** Generates monotonic timestamps for one device and merges in remote ones, exactly as Actual's send/recv. */
class HybridLogicalClock(
    var timestamp: ActualTimestamp,
    private val maxDriftMillis: Long = DEFAULT_MAX_DRIFT_MILLIS,
) {
    fun send(physicalMillis: Long): ActualTimestamp {
        val lOld = timestamp.millis
        val lNew = maxOf(lOld, physicalMillis)
        val cNew = if (lOld == lNew) timestamp.counter + 1 else 0
        if (lNew - physicalMillis > maxDriftMillis) throw ClockDriftException("maximum clock drift exceeded")
        if (cNew > ActualTimestamp.MAX_COUNTER) throw ClockOverflowException()
        timestamp = timestamp.copy(millis = lNew, counter = cNew)
        return timestamp
    }

    fun receive(
        remote: ActualTimestamp,
        physicalMillis: Long,
    ): ActualTimestamp {
        if (remote.millis - physicalMillis > maxDriftMillis) throw ClockDriftException("maximum clock drift exceeded")
        val lOld = timestamp.millis
        val cOld = timestamp.counter
        val lNew = maxOf(maxOf(lOld, physicalMillis), remote.millis)
        val cNew =
            when {
                lNew == lOld && lNew == remote.millis -> maxOf(cOld, remote.counter) + 1
                lNew == lOld -> cOld + 1
                lNew == remote.millis -> remote.counter + 1
                else -> 0
            }
        if (lNew - physicalMillis > maxDriftMillis) throw ClockDriftException("maximum clock drift exceeded")
        if (cNew > ActualTimestamp.MAX_COUNTER) throw ClockOverflowException()
        timestamp = timestamp.copy(millis = lNew, counter = cNew)
        return timestamp
    }

    companion object {
        const val DEFAULT_MAX_DRIFT_MILLIS = 5 * 60 * 1000L
    }
}

private fun parseLeadingHex(value: String): Int? {
    val digits = value.takeWhile { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    if (digits.isEmpty()) return null
    return digits.toLongOrNull(16)?.let { if (it > Int.MAX_VALUE) Int.MAX_VALUE else it.toInt() }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** Formats epoch milliseconds like JavaScript's `toISOString()` for years 0–9999. */
internal fun isoMillis(millis: Long): String {
    val days = millis.floorDiv(MILLIS_PER_DAY)
    val ofDay = millis.mod(MILLIS_PER_DAY)
    val (year, month, day) = civilFromDays(days)
    val hours = ofDay / 3_600_000
    val minutes = ofDay / 60_000 % 60
    val seconds = ofDay / 1000 % 60
    val ms = ofDay % 1000
    return "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}T${pad(hours, 2)}:${pad(minutes, 2)}:${pad(seconds, 2)}.${pad(ms, 3)}Z"
}

/** Parses exactly `YYYY-MM-DDTHH:MM:SS.sssZ`, the only form Actual writes into timestamps. */
internal fun parseIsoMillis(value: String): Long? {
    val match = ISO.matchEntire(value) ?: return null
    val (y, mo, d, h, mi, s, ms) = match.destructured
    val year = y.toLong()
    val month = mo.toLong()
    val day = d.toLong()
    if (month !in 1..12 || day !in 1..daysInMonth(year, month) || h.toLong() > 23 || mi.toLong() > 59 || s.toLong() > 59) {
        return null
    }
    return daysFromCivil(year, month, day) * MILLIS_PER_DAY + h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 +
        ms.toLong()
}

private val ISO = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{3})Z""")

private fun pad(
    value: Long,
    width: Int,
): String = value.toString().padStart(width, '0')

private fun daysInMonth(
    year: Long,
    month: Long,
): Long =
    when (month) {
        2L -> if (year % 4 == 0L && (year % 100 != 0L || year % 400 == 0L)) 29 else 28
        4L, 6L, 9L, 11L -> 30
        else -> 31
    }

// Howard Hinnant's days-from-civil algorithms.
private fun daysFromCivil(
    year: Long,
    month: Long,
    day: Long,
): Long {
    val y = if (month <= 2) year - 1 else year
    val era = y.floorDiv(400L)
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

private fun civilFromDays(days: Long): Triple<Long, Long, Long> {
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    return Triple(if (month <= 2) yoe + era * 400 + 1 else yoe + era * 400, month, day)
}
