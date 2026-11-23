package app.tandemforactual.data.actual

/** Writes to an Actual database the way Actual's `db.insert`, `db.update` and `db.delete_` do: one message per field. */
class ActualWriter(
    val store: ActualMessageStore,
    val clock: StoredClock,
    private val now: () -> Long,
    private val newId: () -> String,
) {
    val db: ActualDatabase get() = store.db

    private var moment: Long? = null

    fun uuid(): String = newId()

    fun nowMillis(): Long = now()

    fun insert(
        table: String,
        row: Map<String, Any?>,
    ): String {
        val id = row["id"] as String? ?: newId()
        send(table, id, row.filterKeys { it != "id" })
        return id
    }

    fun update(
        table: String,
        id: String,
        fields: Map<String, Any?>,
    ) = send(table, id, fields)

    fun delete(
        table: String,
        id: String,
    ) = send(table, id, mapOf("tombstone" to 1L))

    fun <T> batch(block: () -> T): T {
        val timestamp = clock.clock.timestamp
        val merkle = clock.merkle
        return try {
            atOneMoment { db.transaction(block) }
        } catch (error: Throwable) {
            clock.clock.timestamp = timestamp
            clock.merkle = merkle
            throw error
        }
    }

    /**
     * Stamps everything [block] writes with the same millisecond, so one edit reads as one change in the history even
     * when it takes longer than a millisecond. The clock's counter keeps the messages in order.
     */
    fun <T> atOneMoment(block: () -> T): T {
        if (moment != null) return block()
        moment = now()
        return try {
            block()
        } finally {
            moment = null
        }
    }

    private fun send(
        table: String,
        id: String,
        fields: Map<String, Any?>,
    ) {
        if (fields.isEmpty()) return
        val messages =
            fields.map { (column, value) ->
                CrdtMessage(clock.clock.send(moment ?: now()), table, id, column, CrdtValue.of(value))
            }
        store.apply(messages, clock, deferUnknownSchema = false)
    }
}

/** A transaction in Actual's public field names, as `db.insertTransaction` accepts it. */
internal object TransactionSchema {
    private val internal =
        mapOf(
            "is_parent" to "isParent",
            "is_child" to "isChild",
            "account" to "acct",
            "imported_id" to "financial_id",
            "imported_payee" to "imported_description",
            "transfer_id" to "transferred_id",
            "payee" to "description",
        )
    private val booleans = setOf("is_parent", "is_child", "starting_balance_flag", "cleared", "reconciled", "tombstone")
    private val fields =
        setOf(
            "id",
            "is_parent",
            "is_child",
            "parent_id",
            "account",
            "category",
            "amount",
            "payee",
            "notes",
            "date",
            "imported_id",
            "error",
            "imported_payee",
            "starting_balance_flag",
            "transfer_id",
            "sort_order",
            "cleared",
            "reconciled",
            "tombstone",
            "schedule",
            "raw_synced_data",
        )

    fun forInsert(
        transaction: Map<String, Any?>,
        now: Long,
    ): Map<String, Any?> {
        val row = LinkedHashMap(transaction)
        if (row["amount"] == null) row["amount"] = 0L
        if (row["sort_order"] == null) row["sort_order"] = now
        if (row["cleared"] == null) row["cleared"] = true
        if (row["reconciled"] == null) row["reconciled"] = false
        require(row["account"] != null) { "\"account\" is required for table \"transactions\"" }
        require(row["date"] != null) { "\"date\" is required for table \"transactions\"" }
        return conform(row, skipNull = true)
    }

    fun forUpdate(fields: Map<String, Any?>): Map<String, Any?> = conform(fields, skipNull = false)

    private fun conform(
        row: Map<String, Any?>,
        skipNull: Boolean,
    ): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((field, value) in row) {
            if (field.startsWith("_")) continue
            require(field in fields) { "Field \"$field\" does not exist on table transactions" }
            if (skipNull && value == null) continue
            out[internal[field] ?: field] =
                when {
                    field in booleans -> if (truthy(value)) 1L else 0L
                    field == "date" && value != null -> {
                        val date = value as String
                        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) && date >= "1995-01-01") { "Invalid date: $date" }
                        ActualMonths.dateInt(date)
                    }
                    else -> value
                }
        }
        return out
    }
}
