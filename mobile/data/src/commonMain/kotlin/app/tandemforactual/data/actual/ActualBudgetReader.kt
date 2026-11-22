package app.tandemforactual.data.actual

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
    private val numberFormats = setOf("comma-dot", "dot-comma", "space-comma", "apostrophe-dot", "comma-dot-in")

    /** [today] is the device's local date as `YYYY-MM-DD`; it sets the current month and the account-balance cutoff. */
    fun read(today: String): ActualSnapshot {
        val preferences = db.query("SELECT id, value FROM preferences").associate { it["id"] as String to it["value"] as String? }
        val tracking = preferences["budgetType"] == "tracking"
        val records = mutableListOf<ActualRecord>()
        db.query("SELECT id, name FROM account_groups WHERE tombstone = 0 ORDER BY sort_order, id").forEach {
            records += record("account-group", obj("id" to jsonString(it["id"]), "name" to jsonString(it["name"])))
        }
        val cutoff = ActualMonths.dateInt(today)
        db.query("SELECT * FROM accounts WHERE tombstone = 0 ORDER BY sort_order, name").forEach {
            val balance =
                db
                    .first(
                        "SELECT sum(amount) as balance FROM transactions WHERE acct = ? AND isParent = 0 AND tombstone = 0 AND date <= ?",
                        it["id"],
                        cutoff,
                    )?.get("balance")
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
            records += record("account", JsonObject(fields))
        }
        val groups = categoryGroups()
        groups.forEach { records += record("category-group", JsonObject(it.external())) }
        db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id").forEach {
            records +=
                record(
                    "category",
                    JsonObject(
                        BudgetCategory(
                            it["id"] as String,
                            it["name"] as String,
                            truthy(it["is_income"]),
                            truthy(it["hidden"]),
                            it["cat_group"] as String,
                        ).external(),
                    ),
                )
        }
        db
            .query(
                """
                SELECT p.*, COALESCE(a.name, p.name) AS name FROM payees p
                LEFT JOIN accounts a ON (p.transfer_acct = a.id AND a.tombstone = 0)
                WHERE p.tombstone = 0 AND (p.transfer_acct IS NULL OR a.id IS NOT NULL)
                """.trimIndent(),
            ).forEach {
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
        val mappings =
            (
                db.query("SELECT id, transferId AS target FROM category_mapping") +
                    db.query("SELECT id, targetId AS target FROM payee_mapping")
            ).mapNotNull { row -> (row["target"] as String?)?.let { row["id"] as String to it } }
                .toMap()
        val rules =
            ActualRules.load(
                db.query("SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"),
                mappings,
            )
        refreshScheduleJsonPaths(rules)
        db.query("SELECT * FROM v_schedules WHERE tombstone = 0").forEach {
            records +=
                record(
                    "schedule",
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
                    ),
                )
        }
        rules.forEach { records += record("rule", it) }
        val months = ActualBudgetMonths(db, groups, tracking)
        val created = months.createdMonths(ActualMonths.fromIsoDate(today))
        months.compute(created, created.dropLast(1)).forEach { (month, body) -> records += record("budget-month", body, month) }
        records += transactions()
        return ActualSnapshot(records, displaySettings(preferences))
    }

    private fun categoryGroups(): List<BudgetGroup> {
        val categories =
            db.query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id").map {
                BudgetCategory(
                    it["id"] as String,
                    it["name"] as String,
                    truthy(it["is_income"]),
                    truthy(it["hidden"]),
                    it["cat_group"] as String,
                )
            }
        return db.query("SELECT * FROM category_groups WHERE tombstone = 0 ORDER BY is_income, sort_order, id").map { group ->
            val id = group["id"] as String
            BudgetGroup(
                id,
                group["name"] as String,
                truthy(group["is_income"]),
                truthy(group["hidden"]),
                categories.filter {
                    it.groupId ==
                        id
                },
            )
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

    /** Money put into an account each calendar year: deposits and transfers in, without its starting balance. */
    fun inflowsByYear(accountId: String): Map<Int, Long> =
        db
            .query(
                "SELECT date / 10000 AS year, sum(amount) AS total FROM v_transactions WHERE account = ? AND amount > 0 " +
                    "AND is_parent = 0 AND starting_balance_flag = 0 GROUP BY date / 10000 ORDER BY year",
                accountId,
            ).associate { (it["year"] as Number).toInt() to (it["total"] as Number).toLong() }

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

    private fun displaySettings(preferences: Map<String, String?>): BudgetDisplaySettings {
        val currencyEnabled = preferences["flags.currency"] == "true"
        val configured = preferences["defaultCurrencyCode"]?.takeIf { currencyEnabled && Regex("^[A-Z]{3}$").matches(it) }
        val configuredMetadata = configured?.let(ACTUAL_CURRENCIES::get)
        val currencyCode = if (configured != null && configuredMetadata != null) configured else "USD"
        val metadata = configuredMetadata ?: ACTUAL_CURRENCIES.getValue("USD")
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
