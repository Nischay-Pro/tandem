package app.tandemforactual.data.actual

/** One statement row, as `transactions-import` takes it. */
data class ImportRow(
    val date: String,
    val amount: Long,
    val payeeName: String?,
    val notes: String? = null,
    val importedId: String? = null,
    val category: String? = null,
    val cleared: Boolean? = null,
    val rawSyncedData: String? = null,
)

enum class ImportOutcome { ADD, UPDATE, UNCHANGED, RECONCILED }

data class ImportResult(
    val added: List<String>,
    val updated: List<String>,
    /** What happens to each row, in input order. */
    val outcomes: List<ImportOutcome>,
)

/**
 * Actual's `reconcileTransactions`: rows already imported (same `imported_id`) update their match; otherwise a
 * transaction with the same amount within seven days is matched, preferring one with the same payee, then the closest
 * date. Matches never change reconciled transactions, and anything unmatched is added after running the budget's rules.
 */
class ActualImport(
    private val writer: ActualWriter,
) {
    private val db get() = writer.db
    private val transactions = ActualTransactions(writer)

    fun reconcile(
        accountId: String,
        rows: List<ImportRow>,
        preview: Boolean = false,
        titleCasePayees: Boolean = true,
        defaultCleared: Boolean = true,
        reimportDeleted: Boolean = true,
        strictIdChecking: Boolean = true,
        updateDates: Boolean = false,
    ): ImportResult {
        val payeesToCreate = LinkedHashMap<String, Pair<String, String>>()
        val payeesByName =
            db.query("SELECT id, name FROM payees WHERE tombstone = 0").associate {
                (it["name"] as String?).orEmpty().lowercase() to
                    it["id"] as String
            }
        val matched = HashSet<String>()
        val steps =
            rows.map { row ->
                val payeeName =
                    row.payeeName
                        ?.trim()
                        ?.ifEmpty { null }
                        ?.let { if (titleCasePayees) titleCase(it) else it }
                val payee =
                    payeeName?.let { name ->
                        payeesToCreate[name.lowercase()]?.first ?: payeesByName[name.lowercase()]
                            ?: writer.uuid().also { payeesToCreate[name.lowercase()] = it to name }
                    }
                importStep(accountId, row, payeeName, payee, matched, reimportDeleted, strictIdChecking)
            }
        for (pass in 0..1) {
            for (step in steps) {
                if (step.match != null) continue
                step.match =
                    step.fuzzy.firstOrNull { (it["id"] as String) !in matched && (pass == 1 || it["payee"] == step.trans["payee"]) }
                step.match?.let { matched += it["id"] as String }
            }
        }
        val added = mutableListOf<Map<String, Any?>>()
        val updated = mutableListOf<Map<String, Any?>>()
        val outcomes =
            steps.map { step ->
                val match = step.match
                val trans = step.trans
                when {
                    match == null -> {
                        added += trans + mapOf("id" to writer.uuid(), "cleared" to (trans["cleared"] ?: defaultCleared))
                        ImportOutcome.ADD
                    }
                    truthy(match["reconciled"]) -> ImportOutcome.RECONCILED
                    else -> updateMatch(match, trans, updateDates, updated)
                }
            }
        val now = writer.nowMillis()
        val sorted = added.mapIndexed { index, t -> t + ("sort_order" to (t["sort_order"] ?: (now - index * 1024L))) }
        if (preview) return ImportResult(sorted.map { it["id"] as String }, updated.map { it["id"] as String }, outcomes)
        writer.batch {
            val used = (sorted + updated).map { it["payee"] }.toSet()
            payeesToCreate.values.filter { it.first in used }.forEach { (id, name) -> transactions.insertPayee(name, id) }
            transactions.batchUpdate(added = sorted, updated = updated)
        }
        return ImportResult(sorted.map { it["id"] as String }, updated.map { it["id"] as String }, outcomes)
    }

    private class Step(
        val trans: Map<String, Any?>,
        var match: Row?,
        val fuzzy: List<Row>,
    )

    /** Runs the budget's rules on [row] and finds its exact match by imported id, or the fuzzy candidates. */
    private fun importStep(
        accountId: String,
        row: ImportRow,
        payeeName: String?,
        payee: String?,
        matched: MutableSet<String>,
        reimportDeleted: Boolean,
        strictIdChecking: Boolean,
    ): Step {
        val trans =
            transactions.runRules(
                linkedMapOf(
                    "date" to row.date,
                    "amount" to row.amount,
                    "payee" to payee,
                    "account" to accountId,
                    "notes" to row.notes,
                    "imported_id" to row.importedId,
                    "imported_payee" to payeeName,
                    "category" to row.category,
                    "cleared" to row.cleared,
                ).apply { row.rawSyncedData?.let { put("raw_synced_data", it) } },
            )
        var match: Row? = null
        var fuzzy = emptyList<Row>()
        val importedId = trans["imported_id"] as String?
        if (importedId != null) {
            match =
                db.first(
                    "SELECT * FROM ${if (reimportDeleted) "v_transactions" else "v_transactions_internal"} WHERE imported_id = ? AND account = ?",
                    importedId,
                    accountId,
                )
            match?.let { matched += it["id"] as String }
        }
        if (match == null) fuzzy = fuzzyCandidates(accountId, row, trans, importedId, strictIdChecking)
        return Step(trans, match, fuzzy)
    }

    private fun fuzzyCandidates(
        accountId: String,
        row: ImportRow,
        trans: Map<String, Any?>,
        importedId: String?,
        strictIdChecking: Boolean,
    ): List<Row> {
        val date = ActualDates.toInt(row.date)
        return db
            .query(
                "SELECT * FROM v_transactions WHERE ${if (strictIdChecking) "(imported_id IS NULL OR ? IS NULL) AND " else ""}date >= ? AND date <= ? AND amount = ? AND account = ?",
                *(
                    (if (strictIdChecking) listOf<Any?>(importedId) else emptyList()) +
                        listOf(
                            ActualDates.toInt(ActualDates.addDays(row.date, -7)),
                            ActualDates.toInt(ActualDates.addDays(row.date, 7)),
                            trans["amount"] as Long,
                            accountId,
                        )
                ).toTypedArray(),
            ).sortedWith(
                compareBy<Row> {
                    kotlin.math.abs(
                        (it["date"] as Number).toLong().let(ActualDates::dayNumber) - ActualDates.dayNumber(date),
                    )
                }.thenBy { if (it["imported_id"] != null) 1 else 0 },
            )
    }

    /** Merges an imported row into its unreconciled [match], queuing the changes (and split children's) in [updated]. */
    private fun updateMatch(
        match: Row,
        trans: Map<String, Any?>,
        updateDates: Boolean,
        updated: MutableList<Map<String, Any?>>,
    ): ImportOutcome {
        val updates =
            linkedMapOf(
                "imported_id" to trans["imported_id"],
                "payee" to (match["payee"] ?: trans["payee"]),
                "category" to (match["category"] ?: trans["category"]),
                "imported_payee" to trans["imported_payee"],
                "notes" to (match["notes"] ?: trans["notes"]),
                "cleared" to (truthy(match["cleared"]) || truthy(trans["cleared"])),
            ).apply {
                if (match["raw_synced_data"] != null ||
                    trans["raw_synced_data"] != null
                ) {
                    put(
                        "raw_synced_data",
                        match["raw_synced_data"] ?: trans["raw_synced_data"],
                    )
                }
                if (updateDates) put("date", ActualDates.toInt(trans["date"] as String))
            }
        val existing =
            mapOf(
                "imported_id" to match["imported_id"],
                "payee" to match["payee"],
                "category" to match["category"],
                "imported_payee" to match["imported_payee"],
                "notes" to match["notes"],
                "cleared" to truthy(match["cleared"]),
                "raw_synced_data" to match["raw_synced_data"],
                "date" to (match["date"] as Number?)?.toLong(),
            )
        return if (updates.any { (key, value) -> existing[key] != value }) {
            updated += updates + ("id" to match["id"])
            val clearedChanged = existing["cleared"] != updates["cleared"]
            val dateChanged = updates.containsKey("date") && existing["date"] != updates["date"]
            if (truthy(match["is_parent"]) && (clearedChanged || dateChanged)) {
                db.query("SELECT id FROM v_transactions WHERE parent_id = ?", match["id"]).forEach {
                    updated +=
                        buildMap {
                            put("id", it["id"])
                            if (clearedChanged) put("cleared", updates["cleared"])
                            if (dateChanged) put("date", updates["date"])
                        }
                }
            }
            ImportOutcome.UPDATE
        } else {
            ImportOutcome.UNCHANGED
        }
    }

    companion object {
        private val lowerCase =
            (
                "for and nor but or yet so a an the aboard about above across after against along amid among anti around as at " +
                    "before behind below beneath beside besides between beyond but by concerning considering despite down during " +
                    "except excepting excluding following for from in inside into like minus near of off on onto opposite over past " +
                    "per plus regarding round save since than through to toward towards under underneath unlike until up upon " +
                    "versus via with within without"
            ).split(' ').toSet()
        private val specials =
            listOf(
                "CLI",
                "API",
                "HTTP",
                "HTTPS",
                "JSX",
                "DNS",
                "URL",
                "CI",
                "CDN",
                "GitHub",
                "CSS",
                "JS",
                "JavaScript",
                "TypeScript",
                "HTML",
                "WordPress",
                "Next.js",
                "Node.js",
            )
        private const val CHAR = "[\\p{L}\\p{N}]"
        private val word = Regex("(?:(?:(\\s?(?:^|[.()!?;:\"-])\\s*)($CHAR))|($CHAR))($CHAR*[’']*$CHAR*)")

        /** Actual's `title`: capitalises words but keeps short joining words lowercase unless they start a phrase. */
        fun titleCase(text: String): String {
            var result =
                word.replace(text.lowercase()) { m ->
                    val whole = m.value
                    val lead = m.groups[1]?.value.orEmpty()
                    val forced = m.groups[2]?.value
                    val lower = m.groups[3]?.value
                    val rest = m.groups[4]?.value.orEmpty()
                    val parsed =
                        when {
                            whole[0].isWhitespace() -> whole.substring(1)
                            whole[0] == '(' || whole[0] == ')' -> return@replace whole
                            else -> whole
                        }
                    if (forced == null && lowerCase.contains(lower + rest)) parsed else lead + (lower ?: forced)!!.uppercase() + rest
                }
            for (special in specials) result = result.replace(Regex("\\b${Regex.escape(special)}\\b", RegexOption.IGNORE_CASE), special)
            return result
        }
    }
}
