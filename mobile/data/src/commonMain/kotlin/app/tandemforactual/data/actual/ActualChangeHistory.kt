package app.tandemforactual.data.actual

/** One cell's change, with the value it replaced when the log still holds it. */
data class FieldChange(
    val column: String,
    val before: CrdtValue?,
    val after: CrdtValue,
    val timestamp: String,
)

data class RowChange(
    val dataset: String,
    val row: String,
    val fields: List<FieldChange>,
    /** The row as it is now, for naming it in the history. */
    val current: Map<String, String?> = emptyMap(),
) {
    /** A snapshot download has no earlier log, so a row counts as created only when it sets the column Actual writes on insert. */
    val created get() = fields.all { it.before == null } && fields.any { it.column == CREATION_COLUMNS[dataset] ?: it.column }
    val deleted get() = fields.any { it.column == "tombstone" && it.after == CrdtValue.Number(1.0) }
}

/** Everything one device wrote at one moment: a single edit, import or sync from that device. */
data class ChangeSet(
    val id: String,
    val millis: Long,
    val node: String,
    val rows: List<RowChange>,
)

data class UndoResult(
    val reverted: Int,
    /** Cells changed again after this edit, which undo leaves alone. */
    val skipped: Int,
)

/** Reads Actual's change log (`messages_crdt`) as a history of edits, and reverts an edit by writing the earlier values. */
class ActualChangeHistory(
    private val writer: ActualWriter,
) {
    private val db get() = writer.db

    fun recent(limit: Int = 50): List<ChangeSet> {
        val scan = limit * SCAN_PER_SET
        val rows = db.query("SELECT timestamp, dataset, row, column, value FROM messages_crdt ORDER BY timestamp DESC LIMIT ?", scan)
        val messages =
            rows.mapNotNull { row ->
                ActualTimestamp.parse(row["timestamp"] as String)?.let { timestamp ->
                    CrdtMessage(
                        timestamp,
                        row["dataset"] as String,
                        row["row"] as String,
                        row["column"] as String,
                        CrdtValue.deserialize(row["value"] as String),
                    )
                }
            }
        val groups = messages.groupBy { it.timestamp.millis to it.timestamp.node }.values.toList()
        val complete = if (rows.size < scan) groups else groups.dropLast(1)
        return complete.take(limit).map { group ->
            val ordered = group.sortedBy { it.timestamp }
            ChangeSet(
                id = ordered.first().timestamp.toString(),
                millis = ordered.first().timestamp.millis,
                node = ordered.first().timestamp.node,
                rows =
                    ordered
                        .groupBy { it.dataset to it.row }
                        .map { (key, cells) ->
                            RowChange(
                                key.first,
                                key.second,
                                cells.map { FieldChange(it.column, valueBefore(it), it.value, it.timestamp.toString()) },
                                currentRow(key.first, key.second),
                            )
                        },
            )
        }
    }

    /** Writes back each changed cell's earlier value; a created row is deleted instead. */
    fun undo(set: ChangeSet): UndoResult {
        var reverted = 0
        var skipped = 0
        writer.batch {
            for (row in set.rows) {
                if (row.created) {
                    if (latest(row.dataset, row.row, "tombstone")?.second == CrdtValue.Number(1.0)) {
                        skipped++
                    } else {
                        writer.update(row.dataset, row.row, mapOf("tombstone" to 1L))
                        reverted++
                    }
                    continue
                }
                val fields = LinkedHashMap<String, Any?>()
                for (field in row.fields) {
                    val before = field.before
                    val current = latest(row.dataset, row.row, field.column)
                    if (before == null || before is CrdtValue.Unknown || current?.first != field.timestamp) {
                        skipped++
                    } else {
                        fields[field.column] = before.raw()
                        reverted++
                    }
                }
                writer.update(row.dataset, row.row, fields)
            }
        }
        return UndoResult(reverted, skipped)
    }

    private fun latest(
        dataset: String,
        row: String,
        column: String,
    ): Pair<String, CrdtValue>? =
        db
            .first(
                "SELECT timestamp, value FROM messages_crdt WHERE dataset = ? AND row = ? AND column = ? ORDER BY timestamp DESC LIMIT 1",
                dataset,
                row,
                column,
            )?.let { it["timestamp"] as String to CrdtValue.deserialize(it["value"] as String) }

    private fun currentRow(
        dataset: String,
        row: String,
    ): Map<String, String?> {
        val table = dataset.filter { it.isLetterOrDigit() || it == '_' }
        return runCatching { db.first("SELECT * FROM $table WHERE id = ?", row) }.getOrNull()?.mapValues { it.value?.toString() }.orEmpty()
    }

    private fun valueBefore(message: CrdtMessage): CrdtValue? =
        db
            .first(
                "SELECT value FROM messages_crdt WHERE dataset = ? AND row = ? AND column = ? AND timestamp < ? ORDER BY timestamp DESC LIMIT 1",
                message.dataset,
                message.row,
                message.column,
                message.timestamp.toString(),
            )?.let { CrdtValue.deserialize(it["value"] as String) }

    private fun CrdtValue.raw(): Any? =
        when (this) {
            is CrdtValue.Number -> if (value == kotlin.math.floor(value)) value.toLong() else value
            is CrdtValue.Text -> value
            else -> null
        }

    private companion object {
        const val SCAN_PER_SET = 40
    }
}

private val CREATION_COLUMNS =
    mapOf(
        "transactions" to "acct",
        "payees" to "name",
        "categories" to "cat_group",
        "category_groups" to "is_income",
        "accounts" to "offbudget",
        "rules" to "conditions",
        "schedules" to "rule",
    )
