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
    /** The other tables the same edit wrote this row id in, such as a new payee's mapping. */
    val alongside: Set<String> = emptySet(),
) {
    /**
     * A snapshot download has no earlier log, so a row counts as created only when the edit wrote every column Actual
     * writes on insert, and the mapping row an insert adds. A rename or move of an older row writes fewer, so undo
     * reverts it instead of deleting the row.
     */
    val created get() =
        fields.all { it.before == null } &&
            INSERT_COLUMNS[dataset]?.all { column -> fields.any { it.column == column } } == true &&
            INSERT_MAPPINGS[dataset]?.let { it in alongside } != false
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
            val tablesByRow = ordered.groupBy({ it.row }, { it.dataset })
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
                                tablesByRow.getValue(key.second).toSet() - key.first,
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
                if (row.dataset in MAPPING_TABLES) continue
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
                    val current = latest(row.dataset, row.row, field.column)
                    val before = earlierValue(row.dataset, field)
                    if (before == null || current?.first != field.timestamp) {
                        skipped++
                    } else {
                        fields[field.column] = before.value
                        reverted++
                    }
                }
                writer.update(row.dataset, row.row, fields)
            }
        }
        return UndoResult(reverted, skipped)
    }

    /**
     * The value to restore [field] to, or null when it can't be told. A cell with no earlier log entry goes back to
     * what it is before anything is written: not deleted, and no budget amount or note.
     */
    private fun earlierValue(
        dataset: String,
        field: FieldChange,
    ): Restore? =
        when (val before = field.before) {
            null ->
                when {
                    field.column == "tombstone" -> Restore(0L)
                    dataset in BLANK_TABLES && field.column in BLANK_VALUES -> Restore(BLANK_VALUES.getValue(field.column))
                    else -> null
                }
            is CrdtValue.Unknown -> null
            else -> Restore(before.raw())
        }

    private class Restore(
        val value: Any?,
    )

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

private val INSERT_COLUMNS =
    mapOf(
        "transactions" to listOf("acct", "date", "amount"),
        "payees" to listOf("name"),
        "categories" to listOf("name", "cat_group"),
        "category_groups" to listOf("name", "is_income"),
        "accounts" to listOf("name", "offbudget"),
        "rules" to listOf("conditions", "actions"),
        "schedules" to listOf("rule"),
    )

private val INSERT_MAPPINGS = mapOf("payees" to "payee_mapping", "categories" to "category_mapping")

private val MAPPING_TABLES = setOf("payee_mapping", "category_mapping")

/** Tables without a tombstone column, whose cells go back to these values when nothing was set before. */
private val BLANK_TABLES = setOf("zero_budgets", "reflect_budgets", "zero_budget_months", "notes")

private val BLANK_VALUES = mapOf("amount" to 0L, "carryover" to 0L, "goal" to null, "long_goal" to null, "buffered" to 0L, "note" to null)
