package app.tandemforactual.data.actual

/** Actual's transaction mutations (`batchUpdateTransactions`) and transfer bookkeeping (`transfer.ts`). */
class ActualTransactions(
    private val writer: ActualWriter,
    private val runRules: (Map<String, Any?>) -> Map<String, Any?> = { it },
) {
    private val db get() = writer.db

    /** A transaction row from `v_transactions_internal` in Actual's public field names. */
    fun get(id: String): Map<String, Any?>? =
        db.first("SELECT * FROM v_transactions_internal WHERE id = ?", id)?.let { row ->
            row.mapValues { (key, value) ->
                when (key) {
                    "date" -> (value as Number?)?.let { ActualMonths.dateString(it.toLong()) }
                    "is_parent", "is_child", "starting_balance_flag", "cleared", "reconciled", "tombstone" -> truthy(value)
                    else -> value
                }
            }
        }

    fun insert(transaction: Map<String, Any?>): String {
        val id = transaction["id"] as String? ?: writer.uuid()
        writer.insert("transactions", TransactionSchema.forInsert(transaction + ("id" to id), writer.nowMillis()) + ("id" to id))
        return id
    }

    fun update(
        id: String,
        fields: Map<String, Any?>,
    ) = writer.update("transactions", id, TransactionSchema.forUpdate(fields))

    fun batchUpdate(
        added: List<Map<String, Any?>> = emptyList(),
        updated: List<Map<String, Any?>> = emptyList(),
        deleted: List<String> = emptyList(),
        runTransfers: Boolean = true,
    ): List<String> =
        writer.batch {
            val deletedIds = LinkedHashSet(deleted)
            if (deleted.isNotEmpty()) {
                db
                    .query(
                        "SELECT id FROM v_transactions_internal WHERE parent_id IN (${deleted.joinToString { "?" }})",
                        *deleted.toTypedArray(),
                    ).forEach { deletedIds += it["id"] as String }
            }
            val accounts = db.query("SELECT * FROM accounts WHERE tombstone = 0").associateBy { it["id"] as String }
            val addedIds =
                added.map { t ->
                    val offBudget = (accounts[t["account"]]?.get("offbudget") as Number?)?.toLong() == 1L
                    insert(if (truthy(t["is_parent"]) || offBudget) t + ("category" to null) else t)
                }
            deletedIds.forEach { writer.delete("transactions", it) }
            updated.forEach { t ->
                val id = t.getValue("id") as String
                var fields = t - "id"
                if (t["account"] != null) {
                    val offBudget = (accounts[t["account"]]?.get("offbudget") as Number?)?.toLong() == 1L
                    if (truthy(t["is_parent"]) || offBudget) fields = fields + ("category" to null)
                }
                update(id, fields)
            }
            if (runTransfers) {
                addedIds.mapNotNull(::get).forEach(::onInsert)
                updated.mapNotNull { get(it.getValue("id") as String) }.forEach(::onUpdate)
                deletedIds.mapNotNull(::get).forEach(::onDelete)
            }
            addedIds
        }

    private fun transferredAccount(transaction: Map<String, Any?>): String? {
        val payee = transaction["payee"] as String? ?: return null
        return db.first("SELECT transfer_acct FROM v_payees WHERE id = ?", payee)?.get("transfer_acct") as String?
    }

    private fun clearCategory(
        transaction: Map<String, Any?>,
        transferAccount: String,
    ) {
        val from = db.first("SELECT offbudget FROM accounts WHERE id = ?", transaction["account"])?.get("offbudget")
        val to = db.first("SELECT offbudget FROM accounts WHERE id = ?", transferAccount)?.get("offbudget")
        if (from == to) {
            update(transaction["id"] as String, mapOf("category" to null))
            (transaction["transfer_id"] as String?)?.let { update(it, mapOf("category" to null)) }
        }
    }

    private fun addTransfer(
        transaction: Map<String, Any?>,
        transferAccount: String,
    ) {
        if (truthy(transaction["is_parent"])) return
        val fromPayee = db.first("SELECT id FROM payees WHERE transfer_acct = ?", transaction["account"])?.get("id")
        val counterpart =
            linkedMapOf(
                "account" to transferAccount,
                "amount" to -((transaction["amount"] as Number).toLong()),
                "payee" to fromPayee,
                "date" to transaction["date"],
                "transfer_id" to transaction["id"],
                "notes" to (transaction["notes"] as String?)?.ifEmpty { null },
                "schedule" to transaction["schedule"],
                "cleared" to false,
            )
        val ruled = runRules(counterpart)
        val schedule = ruled["schedule"] ?: transaction["schedule"]
        val id = insert(counterpart + mapOf("notes" to ruled["notes"], "cleared" to ruled["cleared"], "schedule" to schedule))
        update(
            transaction["id"] as String,
            mapOf("transfer_id" to id) + (if (schedule != null) mapOf("schedule" to schedule) else emptyMap()),
        )
        clearCategory(transaction + ("transfer_id" to id), transferAccount)
    }

    private fun removeTransfer(transaction: Map<String, Any?>) {
        val counterpartId = transaction["transfer_id"] as String?
        val counterpart = counterpartId?.let { db.first("SELECT is_child FROM v_transactions WHERE id = ?", it) }
        if (counterpart != null) {
            if (truthy(counterpart["is_child"])) {
                update(counterpartId, mapOf("transfer_id" to null, "payee" to null))
            } else {
                writer.delete("transactions", counterpartId)
            }
        }
        update(transaction["id"] as String, mapOf("transfer_id" to null))
    }

    private fun updateTransfer(
        transaction: Map<String, Any?>,
        transferAccount: String,
    ) {
        val payee = db.first("SELECT * FROM payees WHERE transfer_acct = ?", transaction["account"])?.get("id")
        update(
            transaction["transfer_id"] as String,
            linkedMapOf(
                "account" to transferAccount,
                "payee" to payee,
                "notes" to transaction["notes"],
                "amount" to -((transaction["amount"] as Number).toLong()),
                "schedule" to transaction["schedule"],
            ),
        )
        clearCategory(transaction, transferAccount)
    }

    private fun onInsert(transaction: Map<String, Any?>) {
        transferredAccount(transaction)?.let { addTransfer(transaction, it) }
    }

    private fun onDelete(transaction: Map<String, Any?>) {
        if (transaction["transfer_id"] != null) removeTransfer(transaction)
    }

    private fun onUpdate(transaction: Map<String, Any?>) {
        val transferAccount = transferredAccount(transaction)
        val transferId = transaction["transfer_id"]
        when {
            truthy(transaction["is_parent"]) -> removeTransfer(transaction)
            transferAccount != null && transferId == null -> addTransfer(transaction, transferAccount)
            transferAccount == null && transferId != null -> removeTransfer(transaction)
            transferAccount != null -> updateTransfer(transaction, transferAccount)
        }
    }
}
