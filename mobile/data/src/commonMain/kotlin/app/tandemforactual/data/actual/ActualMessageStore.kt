package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A change to one cell, with its value decoded. */
data class CrdtMessage(
    val timestamp: ActualTimestamp,
    val dataset: String,
    val row: String,
    val column: String,
    val value: CrdtValue,
)

/** The device's clock and Merkle trie, stored in `messages_clock` exactly as Actual stores them. */
data class StoredClock(
    val clock: HybridLogicalClock,
    var merkle: MerkleTrie,
)

/** Applies CRDT messages to an Actual database with Actual's last-writer-wins rules, logging them for sync. */
class ActualMessageStore(
    val db: ActualDatabase,
) {
    fun loadClock(): StoredClock {
        val raw = db.first("SELECT clock FROM messages_clock WHERE id = 1")?.get("clock") as String?
        val parsed = raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val timestamp =
            parsed
                ?.get("timestamp")
                ?.jsonPrimitive
                ?.content
                ?.let(ActualTimestamp::parse)
                ?: ActualTimestamp(0, 0, makeClientId())
        val merkle = parsed?.get("merkle")?.let { MerkleTrie.fromJson(it.jsonObject) } ?: MerkleTrie()
        return StoredClock(HybridLogicalClock(timestamp), merkle)
    }

    /** Catches [clock] up with what another connection to this database saved since it was loaded. */
    fun reload(clock: StoredClock) {
        val saved = loadClock()
        if (saved.clock.timestamp > clock.clock.timestamp) clock.clock.timestamp = saved.clock.timestamp
        clock.merkle = saved.merkle
    }

    /** Gives this device a fresh node id, as Actual does when it opens a newly downloaded budget. */
    fun resetNode(clock: StoredClock) {
        clock.clock.timestamp = clock.clock.timestamp.copy(node = makeClientId())
        saveClock(clock)
    }

    fun saveClock(clock: StoredClock) {
        val json =
            buildJsonObject {
                put("timestamp", JsonPrimitive(clock.clock.timestamp.toString()))
                put("merkle", clock.merkle.toJson())
            }
        db.execute("INSERT OR REPLACE INTO messages_clock (id, clock) VALUES (1, ?)", json.toString())
    }

    fun messagesSince(since: String): List<CrdtMessage> =
        db.query("SELECT timestamp, dataset, row, column, value FROM messages_crdt WHERE timestamp > ?", since).map {
            CrdtMessage(
                checkNotNull(ActualTimestamp.parse(it["timestamp"] as String)),
                it["dataset"] as String,
                it["row"] as String,
                it["column"] as String,
                CrdtValue.deserialize(it["value"] as String),
            )
        }

    /**
     * Applies [messages] in one transaction. Messages already in the log are skipped, older values for a cell are logged
     * but not applied, and messages for schema this version doesn't have are kept in `messages_pending`.
     */
    fun apply(
        messages: List<CrdtMessage>,
        clock: StoredClock,
        deferUnknownSchema: Boolean,
    ): List<CrdtMessage> =
        db.transaction {
            val fresh = compare(messages).sortedBy { it.first.timestamp.toString() }
            val applied = mutableListOf<CrdtMessage>()
            var merkle = clock.merkle
            for ((message, old) in fresh) {
                if (!old && applyCell(message, deferUnknownSchema)) applied += message
                db.execute(
                    "INSERT INTO messages_crdt (timestamp, dataset, row, column, value) VALUES (?, ?, ?, ?, ?)",
                    message.timestamp.toString(),
                    message.dataset,
                    message.row,
                    message.column,
                    message.value.serialize(),
                )
                merkle = merkle.insert(message.timestamp)
            }
            clock.merkle = merkle.prune()
            saveClock(clock)
            applied
        }

    private fun compare(messages: List<CrdtMessage>): List<Pair<CrdtMessage, Boolean>> =
        messages.mapNotNull { message ->
            val logged =
                db
                    .query(
                        "SELECT timestamp FROM messages_crdt WHERE dataset = ? AND row = ? AND column = ? AND timestamp >= ?",
                        message.dataset,
                        message.row,
                        message.column,
                        message.timestamp.toString(),
                    ).map { it["timestamp"] as String }
            val ts = message.timestamp.toString()
            when {
                logged.isEmpty() -> message to false
                ts in logged -> null
                else -> message to logged.any { it > ts }
            }
        }

    private fun applyCell(
        message: CrdtMessage,
        deferUnknownSchema: Boolean,
    ): Boolean {
        val (_, dataset, row, column, value) = message
        if (dataset == "prefs" || dataset == "spreadsheet_cells") return true
        if (value is CrdtValue.Unknown) {
            if (deferUnknownSchema) {
                defer(message)
                return false
            }
            throw ActualSchemaException("Unknown value format: ${value.raw}")
        }
        val bound: Any? = (value as? CrdtValue.Number)?.value ?: (value as? CrdtValue.Text)?.value
        try {
            val exists = db.first("SELECT 1 AS found FROM ${quote(dataset)} WHERE id = ?", row) != null
            if (exists) {
                db.execute("UPDATE ${quote(dataset)} SET ${quote(column)} = ? WHERE id = ?", bound, row)
            } else {
                db.execute("INSERT INTO ${quote(dataset)} (id, ${quote(column)}) VALUES (?, ?)", row, bound)
            }
        } catch (error: Exception) {
            if (deferUnknownSchema && isMissingSchema(error)) {
                defer(message)
                return false
            }
            throw ActualSchemaException(error.message ?: "could not apply change", error)
        }
        return true
    }

    private fun defer(message: CrdtMessage) {
        db.execute(PENDING_MESSAGES_TABLE_SQL)
        db.execute(
            """
            INSERT INTO messages_pending (timestamp, dataset, row, column, value) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(dataset, row, column) DO UPDATE SET timestamp = excluded.timestamp, value = excluded.value
            WHERE excluded.timestamp > messages_pending.timestamp
            """.trimIndent(),
            message.timestamp.toString(),
            message.dataset,
            message.row,
            message.column,
            message.value.serialize(),
        )
    }

    /**
     * Applies messages deferred by an older version whose schema or value format is now known, as Actual does when it
     * opens a budget. Ones still unknown stay; ones SQLite keeps rejecting are dropped. Returns how many were applied.
     */
    fun replayPending(): Int =
        db.transaction {
            db.execute(PENDING_MESSAGES_TABLE_SQL)
            db.execute(
                """
                DELETE FROM messages_pending WHERE EXISTS (SELECT 1 FROM messages_crdt c WHERE c.dataset = messages_pending.dataset
                  AND c.row = messages_pending.row AND c.column = messages_pending.column AND c.timestamp > messages_pending.timestamp)
                """.trimIndent(),
            )
            var waiting =
                db.query("SELECT dataset, row, column, value FROM messages_pending ORDER BY timestamp").map {
                    PendingCell(it["dataset"] as String, it["row"] as String, it["column"] as String, it["value"] as String)
                }
            var applied = 0
            while (waiting.isNotEmpty()) {
                val outcomes = waiting.map { it to replayOne(it) }
                applied += outcomes.count { it.second == Replay.APPLIED }
                val failed = outcomes.filter { it.second == Replay.FAILED }.map { it.first }
                if (failed.size == waiting.size) {
                    failed.forEach { it.forget() }
                    break
                }
                waiting = failed
            }
            applied
        }

    private data class PendingCell(
        val dataset: String,
        val row: String,
        val column: String,
        val value: String,
    )

    private enum class Replay { APPLIED, NEWER_VERSION, FAILED }

    private fun replayOne(cell: PendingCell): Replay {
        val value = CrdtValue.deserialize(cell.value)
        if (value is CrdtValue.Unknown) return Replay.NEWER_VERSION
        val bound: Any? = (value as? CrdtValue.Number)?.value ?: (value as? CrdtValue.Text)?.value
        try {
            val exists = db.first("SELECT 1 AS found FROM ${quote(cell.dataset)} WHERE id = ?", cell.row) != null
            if (exists) {
                db.execute("UPDATE ${quote(cell.dataset)} SET ${quote(cell.column)} = ? WHERE id = ?", bound, cell.row)
            } else {
                db.execute("INSERT INTO ${quote(cell.dataset)} (id, ${quote(cell.column)}) VALUES (?, ?)", cell.row, bound)
            }
        } catch (error: Exception) {
            if (isMissingSchema(error)) return Replay.NEWER_VERSION
            if (!DETERMINISTIC_SQLITE_ERROR.containsMatchIn(error.message.orEmpty())) throw error
            return Replay.FAILED
        }
        cell.forget()
        return Replay.APPLIED
    }

    private fun PendingCell.forget() =
        db.execute("DELETE FROM messages_pending WHERE dataset = ? AND row = ? AND column = ?", dataset, row, column)

    private fun isMissingSchema(error: Exception): Boolean {
        val text = error.message.orEmpty()
        return "no such table" in text || "no such column" in text || "has no column named" in text
    }

    companion object {
        private const val PENDING_MESSAGES_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS messages_pending (dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, " +
                "timestamp TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (dataset, row, column))"

        private val DETERMINISTIC_SQLITE_ERROR =
            Regex(
                "constraint failed|datatype mismatch|cannot store|generated column|string or blob too big|syntax error",
                RegexOption.IGNORE_CASE,
            )

        fun makeClientId(): String = (1..16).joinToString("") { "0123456789abcdef"[kotlin.random.Random.nextInt(16)].toString() }

        private fun quote(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""
    }
}

class ActualSchemaException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
