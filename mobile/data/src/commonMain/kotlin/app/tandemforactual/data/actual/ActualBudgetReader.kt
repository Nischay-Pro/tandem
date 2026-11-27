package app.tandemforactual.data.actual

import app.tandemforactual.data.planning.AccountMovement
import app.tandemforactual.data.planning.TaggedPayment
import app.tandemforactual.data.store.BudgetDisplaySettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One budget entity, shaped exactly like the records Tandem's API built with `@actual-app/api`. */
data class ActualRecord(
    val type: String,
    val id: String,
    val body: JsonObject,
    val revision: String,
)

data class ActualSnapshot(
    val records: List<ActualRecord>,
    val displaySettings: BudgetDisplaySettings,
)

/** Reads a synced Actual database into Tandem's budget records, with budget months computed on the device. */
class ActualBudgetReader(
    private val db: ActualDatabase,
) {
    private val tandemId = Regex("^tandem:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

    /** [today] is the device's local date as `YYYY-MM-DD`; it sets the current month and the account-balance cutoff. */
    fun read(today: String): ActualSnapshot {
        val preferences = db.query("SELECT id, value FROM preferences").associate { it["id"] as String to it["value"] as String? }
        val records = mutableListOf<ActualRecord>()
        db.query("SELECT id, name FROM account_groups WHERE tombstone = 0 ORDER BY sort_order, id").forEach {
            records += record("account-group", obj("id" to jsonString(it["id"]), "name" to jsonString(it["name"])))
        }
        val notes = notes()
        readAccounts(today, records, notes)
        val categories = categories()
        val groups = categoryGroups(categories)
        groups.forEach { records += record("category-group", withNote(it.external(), notes[it.id])) }
        categories.forEach { records += record("category", withNote(it.external(), notes[it.id])) }
        readPayees(records)
        val rules = rules()
        refreshScheduleJsonPaths(rules)
        scheduleRows().forEach { records += record("schedule", it) }
        rules.forEach { records += record("rule", it) }
        budgetMonths(today, preferences, groups, notes).forEach { (month, body) -> records += record("budget-month", body, month) }
        records += transactions()
        return ActualSnapshot(records, budgetDisplaySettings(preferences))
    }

    /** The schedule records [read] returns, without building the rest of the budget. */
    fun schedules(): List<JsonObject> {
        refreshScheduleJsonPaths(rules())
        return scheduleRows()
    }

    /** One schedule as [schedules] returns it, assuming its rule is unchanged since the last [schedules] or [read]. */
    fun schedule(id: String): JsonObject? = scheduleRows(id).firstOrNull()

    fun hasPayee(id: String): Boolean = payeeRows("AND p.id = ?", id).isNotEmpty()

    /** The budget-month record [read] returns for [month], or null when the budget has no such month. */
    fun budgetMonth(
        today: String,
        month: String,
    ): JsonObject? {
        val preferences = db.query("SELECT id, value FROM preferences").associate { it["id"] as String to it["value"] as String? }
        return budgetMonths(today, preferences, categoryGroups(categories()), notes()).firstOrNull { it.first == month }?.second
    }

    /** Each created month's sheet cells, as Actual's budget spreadsheet holds them for the goal templates. */
    internal fun budgetCells(
        today: String,
        tracking: Boolean,
    ): Map<String, Map<String, Any?>> {
        val months = ActualBudgetMonths(db, categoryGroups(categories()), tracking)
        return months.cells(months.createdMonths(ActualMonths.fromIsoDate(today)))
    }

    private fun budgetMonths(
        today: String,
        preferences: Map<String, String?>,
        groups: List<BudgetGroup>,
        notes: Map<String, String>,
    ): List<Pair<String, JsonObject>> {
        val months = ActualBudgetMonths(db, groups, preferences["budgetType"] == "tracking")
        val created = months.createdMonths(ActualMonths.fromIsoDate(today))
        return months.compute(created, created.dropLast(1)).map { (month, body) ->
            month to withNote(body, notes[ActualNoteIds.month(month)])
        }
    }

    private fun rules(): List<JsonObject> {
        val mappings =
            (
                db.query("SELECT id, transferId AS target FROM category_mapping") +
                    db.query("SELECT id, targetId AS target FROM payee_mapping")
            ).mapNotNull { row -> (row["target"] as String?)?.let { row["id"] as String to it } }
                .toMap()
        return ActualRules.load(
            db.query("SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"),
            mappings,
        )
    }

    private fun readAccounts(
        today: String,
        records: MutableList<ActualRecord>,
        notes: Map<String, String>,
    ) {
        val balances =
            db
                .query(
                    "SELECT acct, sum(amount) AS balance FROM transactions WHERE isParent = 0 AND tombstone = 0 AND date <= ? GROUP BY acct",
                    ActualMonths.dateInt(today),
                ).associate { it["acct"] to it["balance"] }
        db.query("SELECT * FROM accounts WHERE tombstone = 0 ORDER BY sort_order, name").forEach {
            val balance = balances[it["id"]]
            val fields =
                linkedMapOf(
                    "id" to jsonString(it["id"]),
                    "name" to jsonString(it["name"]),
                    "offbudget" to jsonBool(it["offbudget"]),
                    "closed" to jsonBool(it["closed"]),
                    "balance_current" to jsonNumber(if (truthy(balance)) balance else 0L),
                    "account_group_id" to jsonString(it["account_group_id"]),
                )
            (it["sort_order"] as? Number)?.let { order -> fields["sort_order"] = jsonNumber(order) }
            records += record("account", withNote(fields, notes[ActualNoteIds.account(it["id"] as String)]))
        }
    }

    private fun readPayees(records: MutableList<ActualRecord>) {
        payeeRows("").forEach {
            records +=
                record(
                    "payee",
                    obj(
                        "id" to jsonString(it["id"]),
                        "name" to jsonString(it["name"]),
                        "transfer_acct" to jsonString(it["transfer_acct"]),
                    ),
                )
        }
    }

    private fun payeeRows(
        filter: String,
        vararg args: Any?,
    ): List<Row> =
        db.query(
            """
            SELECT p.*, COALESCE(a.name, p.name) AS name FROM payees p
            LEFT JOIN accounts a ON (p.transfer_acct = a.id AND a.tombstone = 0)
            WHERE p.tombstone = 0 AND (p.transfer_acct IS NULL OR a.id IS NOT NULL)
            """.trimIndent() + if (filter.isEmpty()) "" else " $filter",
            *args,
        )

    private fun scheduleRows(id: String? = null): List<JsonObject> =
        (
            if (id == null) {
                db.query("SELECT * FROM v_schedules WHERE tombstone = 0")
            } else {
                db.query("SELECT * FROM v_schedules WHERE tombstone = 0 AND id = ?", id)
            }
        ).map {
            obj(
                "id" to jsonString(it["id"]),
                "name" to jsonString(it["name"]),
                "rule" to jsonString(it["rule"]),
                "next_date" to
                    ((it["next_date"] as? Number)?.let { d -> JsonPrimitive(ActualMonths.dateString(d.toLong())) } ?: JsonNull),
                "completed" to jsonBool(it["completed"]),
                "posts_transaction" to jsonBool(it["posts_transaction"]),
                "payee" to jsonString(it["_payee"]),
                "account" to jsonString(it["_account"]),
                "amount" to jsonFallback(it["_amount"]),
                "amountOp" to jsonString(it["_amountOp"]),
                "date" to jsonFallback(it["_date"]),
            )
        }

    private fun categories(): List<BudgetCategory> =
        db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id").map {
            BudgetCategory(
                it["id"] as String,
                it["name"] as String,
                truthy(it["is_income"]),
                truthy(it["hidden"]),
                it["cat_group"] as String,
            )
        }

    private fun notes(): Map<String, String> =
        db
            .query("SELECT id, note FROM notes WHERE note IS NOT NULL AND note != ''")
            .associate { it["id"] as String to it["note"] as String }

    private fun withNote(
        fields: Map<String, JsonElement>,
        note: String?,
    ): JsonObject = JsonObject(if (note == null) fields else fields + ("note" to JsonPrimitive(note)))

    private fun categoryGroups(categories: List<BudgetCategory>): List<BudgetGroup> {
        val byGroup = categories.groupBy { it.groupId }
        return db.query("SELECT * FROM category_groups WHERE tombstone = 0 ORDER BY is_income, sort_order, id").map { group ->
            val id = group["id"] as String
            BudgetGroup(id, group["name"] as String, truthy(group["is_income"]), truthy(group["hidden"]), byGroup[id].orEmpty())
        }
    }

    /** Actual keeps `schedules_json_paths` locally, rebuilt from the rules each time a budget loads. */
    private fun refreshScheduleJsonPaths(rules: List<JsonObject>) {
        db.transaction {
            rules.forEach { rule ->
                val scheduleId =
                    rule
                        .array(
                            "actions",
                        ).firstOrNull { it.text("op") == "link-schedule" }
                        ?.let { (it["value"] as? JsonPrimitive)?.contentOrNull }
                        ?: return@forEach
                val conditions = rule.array("conditions")

                fun path(match: (JsonObject) -> Boolean): String? = conditions.indexOfFirst(match).takeIf { it >= 0 }?.let { "$[$it]" }
                db.execute(
                    "INSERT OR REPLACE INTO schedules_json_paths (schedule_id, payee, account, amount, date) VALUES (?, ?, ?, ?, ?)",
                    scheduleId,
                    path { it.text("op") == "is" && it.text("field") == "payee" },
                    path { it.text("op") == "is" && it.text("field") == "account" },
                    path { it.text("op") in setOf("is", "isapprox", "isbetween") && it.text("field") == "amount" },
                    path { it.text("op") in setOf("is", "isapprox") && it.text("field") == "date" },
                )
            }
        }
    }

    private fun JsonObject.array(key: String): List<JsonObject> = (this[key] as? JsonArray)?.map { it as JsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Every transaction between two dates as CSV, in Actual's export columns, newest first. */
    fun exportCsv(
        from: String,
        to: String,
    ): String {
        val rows =
            db.query(
                "SELECT t.date, a.name AS account, p.name AS payee, c.name AS category, t.amount, t.notes, t.cleared, t.reconciled " +
                    "FROM v_transactions t LEFT JOIN accounts a ON a.id = t.account LEFT JOIN payees p ON p.id = t.payee " +
                    "LEFT JOIN categories c ON c.id = t.category WHERE t.is_parent = 0 AND t.date >= ? AND t.date <= ? " +
                    "ORDER BY t.date DESC, t.sort_order DESC",
                ActualDates.toInt(from),
                ActualDates.toInt(to),
            )

        fun cell(value: Any?): String {
            val text = value?.toString().orEmpty()
            return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
        }

        fun text(value: Any?): String {
            val text = value?.toString().orEmpty()
            return if (text.firstOrNull()?.let { it in "=+-@\t\r" } == true) "'$text" else text
        }
        val lines =
            rows.map { row ->
                val amount = (row["amount"] as Number).toLong()
                listOf(
                    ActualDates.fromInt((row["date"] as Number).toLong()),
                    text(row["account"]),
                    text(row["payee"]),
                    text(row["category"]),
                    (if (amount < 0) "-" else "") + (kotlin.math.abs(amount) / 100) + "." +
                        (kotlin.math.abs(amount) % 100).toString().padStart(2, '0'),
                    text(row["notes"]),
                    if ((row["reconciled"] as Number?)?.toLong() ==
                        1L
                    ) {
                        "Reconciled"
                    } else if ((row["cleared"] as Number?)?.toLong() == 1L) {
                        "Cleared"
                    } else {
                        "Not cleared"
                    },
                ).joinToString(",", transform = ::cell)
            }
        return (listOf("Date,Account,Payee,Category,Amount,Notes,Status") + lines).joinToString("\n") + "\n"
    }

    /** Every transaction in an account except its starting balance, oldest first, with its payee and transfer account. */
    fun accountMovements(accountId: String): List<AccountMovement> =
        db
            .query(
                "SELECT t.date, t.amount, t.notes, p.name AS payee, p.transfer_acct AS other FROM v_transactions t " +
                    "LEFT JOIN payees p ON p.id = t.payee WHERE t.account = ? AND t.is_parent = 0 AND t.starting_balance_flag = 0 " +
                    "AND t.amount != 0 ORDER BY t.date",
                accountId,
            ).map { row ->
                AccountMovement(
                    ActualMonths.dateString((row["date"] as Number).toLong()),
                    (row["amount"] as Number).toLong(),
                    (row["payee"] as String?)?.takeIf { it.isNotEmpty() },
                    (row["notes"] as String?)?.takeIf { it.isNotEmpty() },
                    row["other"] as String?,
                )
            }

    /** Money-in transactions on or after [fromDate] whose notes carry `#`[tag], newest first. */
    fun taggedPayments(
        tag: String,
        fromDate: String,
    ): List<TaggedPayment> =
        db
            .query(
                "SELECT id, date, amount, notes FROM v_transactions WHERE amount > 0 AND is_parent = 0 AND date >= ? AND notes LIKE ? " +
                    "ORDER BY date DESC",
                fromDate.replace("-", "").toInt(),
                "%#$tag%",
            ).map { row ->
                val date = (row["date"] as Number).toInt().toString()
                TaggedPayment(
                    row["id"] as String,
                    "${date.take(4)}-${date.substring(4, 6)}-${date.drop(6)}",
                    (row["amount"] as Number).toLong(),
                    row["notes"] as String,
                )
            }.filter { it.hasTag(tag) }

    /** One transaction record with its revision, by Actual id or Tandem id. */
    fun transactionRecord(id: String): ActualRecord? =
        transaction(id)?.let { ActualRecord("transaction", it.str("id"), it, Sha256.hex(stringifyJson(it))) }

    /** One transaction as the API's `readTransaction` returns it, by Actual id or Tandem id. */
    fun transaction(id: String): JsonObject? {
        val row = db.first("SELECT * FROM v_transactions WHERE id = ? OR imported_id = ?", id, id) ?: return null
        val children =
            db
                .query("SELECT * FROM v_transactions WHERE is_child = 1 AND parent_id = ?", row["id"])
                .sortedWith(compareByDescending<Row> { (it["sort_order"] as Number?)?.toDouble() ?: 0.0 }.thenBy { it["id"] as String })
                .map { transaction(it) }
        return transaction(row, if (truthy(row["is_parent"])) children else emptyList())
    }

    private fun transactions(): List<ActualRecord> {
        val rows = db.query("SELECT * FROM v_transactions")
        val children = rows.filter { truthy(it["is_child"]) }.groupBy { it["parent_id"] as String? }
        val records = mutableListOf<ActualRecord>()
        rows.filter { !truthy(it["is_child"]) }.sortedBy { it["id"] as String }.forEach { parent ->
            val subs =
                children[parent["id"] as String].orEmpty().sortedWith(
                    compareByDescending<Row> {
                        (it["sort_order"] as Number?)?.toDouble()
                            ?: 0.0
                    }.thenBy { it["id"] as String },
                )
            val split = subs.map(::transaction)
            val body = transaction(parent, split)
            records += ActualRecord("transaction", body.str("id"), body, Sha256.hex(stringifyJson(body)))
            split.forEach { records += ActualRecord("transaction", it.str("id"), it, Sha256.hex(stringifyJson(it))) }
        }
        return records
    }

    private fun transaction(
        row: Row,
        subtransactions: List<JsonObject> = emptyList(),
    ): JsonObject {
        val importedId = row["imported_id"] as String?
        val tandemId = importedId?.takeIf { tandemId.matches(it) }
        return obj(
            "id" to JsonPrimitive(tandemId ?: row["id"] as String),
            "account" to jsonString(row["account"]),
            "amount" to jsonNumber(row["amount"]),
            "date" to JsonPrimitive(ActualMonths.dateString((row["date"] as Number).toLong())),
            "payee" to jsonString(row["payee"]),
            "category" to jsonString(row["category"]),
            "notes" to jsonString((row["notes"] as String?)?.takeIf { it.isNotEmpty() }),
            "cleared" to jsonBool(row["cleared"]),
            "reconciled" to jsonBool(row["reconciled"]),
            "importedId" to jsonString(if (tandemId == null) importedId else null),
            "importedPayee" to jsonString(row["imported_payee"]),
            "startingBalance" to jsonBool(row["starting_balance_flag"]),
            "schedule" to jsonString(row["schedule"]),
            "transferId" to jsonString(row["transfer_id"]),
            "isParent" to jsonBool(row["is_parent"]),
            "isChild" to jsonBool(row["is_child"]),
            "parentId" to jsonString(row["parent_id"]),
            "subtransactions" to JsonArray(subtransactions),
        )
    }

    private fun record(
        type: String,
        body: JsonObject,
        id: String = body.str("id"),
    ): ActualRecord = ActualRecord(type, id, body, Sha256.hex(canonicalJson(body)))

    private fun jsonFallback(value: Any?): JsonElement =
        when (value) {
            is String -> runCatching { Json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }
            else -> jsonNumber(value)
        }

    private fun obj(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*fields))

    private fun JsonObject.str(key: String): String = (getValue(key) as JsonPrimitive).content
}

private val numberFormats = setOf("comma-dot", "dot-comma", "space-comma", "apostrophe-dot", "comma-dot-in")

internal fun budgetDisplaySettings(preferences: Map<String, String?>): BudgetDisplaySettings {
    val currencyEnabled = preferences["flags.currency"] == "true"
    val configured = preferences["defaultCurrencyCode"]?.takeIf { currencyEnabled && Regex("^[A-Z]{3}$").matches(it) }
    val configuredMetadata = configured?.let(ACTUAL_CURRENCIES::get)
    val currencyCode = configured ?: "USD"
    val metadata = configuredMetadata ?: if (configured != null) UNKNOWN_ACTUAL_CURRENCY else ACTUAL_CURRENCIES.getValue("USD")
    val numberFormat =
        preferences["numberFormat"]?.takeIf { it in numberFormats } ?: configuredMetadata?.numberFormat
    val symbolPosition = preferences["currencySymbolPosition"]
    return BudgetDisplaySettings(
        currencyCode = currencyCode,
        currencySymbol = metadata.symbol,
        currencyFractionDigits = metadata.decimalPlaces,
        numberFormat = numberFormat,
        hideFraction = preferences["hideFraction"] == "true",
        currencySymbolPosition =
            if (configuredMetadata != null && (symbolPosition == "before" || symbolPosition == "after")) {
                symbolPosition
            } else if (metadata.symbolFirst) {
                "before"
            } else {
                "after"
            },
        currencySpaceBetweenAmountAndSymbol =
            configuredMetadata != null && preferences["currencySpaceBetweenAmountAndSymbol"] == "true",
        budgetType = if (preferences["budgetType"] == "tracking") "tracking" else "envelope",
    )
}
