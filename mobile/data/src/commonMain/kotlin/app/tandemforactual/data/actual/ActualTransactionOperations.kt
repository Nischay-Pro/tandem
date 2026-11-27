package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

enum class OperationStatus { COMMITTED, CONFLICT, REJECTED }

data class OperationOutcome(
    val operationId: String,
    val transactionId: String,
    val status: OperationStatus,
    val errorCode: String? = null,
    val conflictType: String? = null,
    val conflictingFields: List<String> = emptyList(),
)

class InvalidTransactionRequestException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Applies Tandem's transaction writes to the device's Actual database with the checks Tandem's API made: validation,
 * three-way conflict detection against the latest synced state, and transfer-pair integrity. Each outcome is stored so
 * a retried operation returns the same result instead of writing twice.
 */
class ActualTransactionOperations(
    private val writer: ActualWriter,
    private val transactions: ActualTransactions = ActualTransactions(writer),
) {
    private val db get() = writer.db
    private val reader = ActualBudgetReader(writer.db)

    init {
        db.execute(
            "CREATE TABLE IF NOT EXISTS tandem_operations " +
                "(operation_id TEXT PRIMARY KEY, transaction_id TEXT NOT NULL, status TEXT NOT NULL, " +
                "error_code TEXT, conflict_type TEXT, conflicting_fields TEXT NOT NULL DEFAULT '[]')",
        )
    }

    fun find(operationId: String): OperationOutcome? =
        db.first("SELECT * FROM tandem_operations WHERE operation_id = ?", operationId)?.let {
            OperationOutcome(
                it["operation_id"] as String,
                it["transaction_id"] as String,
                OperationStatus.valueOf(it["status"] as String),
                it["error_code"] as String?,
                it["conflict_type"] as String?,
                Json.parseToJsonElement(it["conflicting_fields"] as String).jsonArray.map { field -> (field as JsonPrimitive).content },
            )
        }

    fun submit(
        operationId: String,
        transactionId: String,
        action: String,
        patch: JsonObject,
        baseSnapshot: JsonObject?,
    ): OperationOutcome =
        find(operationId) ?: writer.batch {
            val outcome =
                when (action) {
                    "create" -> create(operationId, transactionId, patch)
                    "update", "delete" ->
                        change(
                            operationId,
                            transactionId,
                            action,
                            patch,
                            baseSnapshot ?: throw InvalidTransactionRequestException("baseSnapshot is required"),
                        )
                    else -> throw InvalidTransactionRequestException("Unsupported action $action")
                }
            db.execute(
                "INSERT INTO tandem_operations " +
                    "(operation_id, transaction_id, status, error_code, conflict_type, conflicting_fields) VALUES (?, ?, ?, ?, ?, ?)",
                outcome.operationId,
                outcome.transactionId,
                outcome.status.name,
                outcome.errorCode,
                outcome.conflictType,
                JsonArray(outcome.conflictingFields.map(::JsonPrimitive)).toString(),
            )
            outcome
        }

    private fun create(
        operationId: String,
        transactionId: String,
        patch: JsonObject,
    ): OperationOutcome {
        if (!TANDEM_ID.matches(
                transactionId,
            )
        ) {
            throw InvalidTransactionRequestException("transactionId must be a stable Tandem transaction ID")
        }
        val input = parseCreate(patch)
        reader.transaction(transactionId)?.let { existing ->
            val mismatched = createMismatches(existing, input)
            return if (mismatched.isEmpty()) {
                OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
            } else {
                OperationOutcome(
                    operationId,
                    transactionId,
                    OperationStatus.CONFLICT,
                    conflictType = "transaction-id-already-exists",
                    conflictingFields = mismatched,
                )
            }
        }
        val split = parseSplit(patch, input["amount"] as Long)
        (validateCreate(input) ?: split?.let(::splitIssue))?.let {
            return OperationOutcome(operationId, transactionId, OperationStatus.REJECTED, errorCode = it)
        }
        val transaction =
            linkedMapOf(
                "date" to input["date"],
                "amount" to input["amount"],
                "payee" to input["payee"],
            )
        if (input["payee"] == null) input["payee_name"]?.let { transaction["payee_name"] = it }
        val offBudget = truthy(db.first("SELECT offbudget FROM accounts WHERE id = ?", input["account"])?.get("offbudget"))
        if (input["category"] != null && !offBudget) transaction["category"] = input["category"]
        transaction["notes"] = input["notes"]
        transaction["cleared"] = input["cleared"]
        transaction["imported_id"] = transactionId
        if (split != null && !offBudget) {
            transaction["category"] = null
            transaction["subtransactions"] = split
        }
        val transfer =
            input["payee"]?.let { payee ->
                db.first("SELECT transfer_acct FROM payees WHERE id = ?", payee)?.get("transfer_acct")
            }
        transactions.addTransactions(input["account"] as String, listOf(transaction), runTransfers = transfer != null)
        return OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
    }

    private fun change(
        operationId: String,
        transactionId: String,
        action: String,
        patchJson: JsonObject,
        baseJson: JsonObject,
    ): OperationOutcome {
        fun conflict(
            type: String,
            fields: List<String>,
        ) = OperationOutcome(operationId, transactionId, OperationStatus.CONFLICT, conflictType = type, conflictingFields = fields)
        val base = baseJson
        val latest = reader.transaction(transactionId)
        if (latest == null) {
            return if (action == "delete") {
                OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
            } else {
                conflict("deleted-remotely", listOf("\$entity"))
            }
        }
        if (base.value("transferId") != latest.value("transferId")) return conflict("transfer-pair-changed", listOf("transferId"))
        val actualId = actualId(transactionId)
        latest.value("transferId")?.let { transferId ->
            val counterpart = reader.transaction(transferId as String) ?: return conflict("transfer-pair-incomplete", listOf("transferId"))
            // Actual stores the pair links as row IDs, while Tandem snapshots can use imported IDs.
            if (actualId(counterpart.value("id") as String) != transferId ||
                counterpart.value("transferId") != actualId ||
                (counterpart.value("amount") as Long) != -(latest.value("amount") as Long)
            ) {
                return conflict("transfer-pair-changed", listOf("transferId", "amount"))
            }
        }
        if (action == "delete") {
            if (!sameStructure(base, latest)) return conflict("delete-versus-edit", structuredConflicts(base, latest))
            val edited = EDIT_FIELDS.filter { !same(it, base.value(it), latest.value(it)) }
            if (edited.isNotEmpty()) return conflict("delete-versus-edit", edited)
            transactions.batchUpdate(deleted = listOf(actualId))
            return OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
        }
        if ("subtransactions" in patchJson) return resplit(operationId, transactionId, patchJson, base, latest, actualId)
        val patch = parsePatch(patchJson)
        if (!sameStructure(base, latest)) return conflict("same-field-edit", structuredConflicts(base, latest))
        if (base.value("isChild") == true) {
            val unsupported =
                listOf("account", "date", "cleared", "reconciled").filter {
                    it in patch &&
                        !same(it, patch[it], base.value(it))
                }
            if (unsupported.isNotEmpty()) {
                return OperationOutcome(
                    operationId,
                    transactionId,
                    OperationStatus.REJECTED,
                    errorCode = "split-child-field-not-editable",
                )
            }
        }
        val (applicable, conflicts) = mergeEditFields(patch, base, latest)
        if (conflicts.isNotEmpty()) return conflict("same-field-edit", conflicts.distinct())
        if (applicable.isEmpty()) return OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
        validateUpdate(
            applicable,
            latest,
        )?.let { return OperationOutcome(operationId, transactionId, OperationStatus.REJECTED, errorCode = it) }
        val lines = if (latest.value("isParent") == true) lineUpdates(actualId, applicable, latest) else emptyList()
        transactions.batchUpdate(updated = listOf(applicable + ("id" to actualId)) + lines)
        return OperationOutcome(operationId, transactionId, OperationStatus.COMMITTED)
    }

    /** The new and changed rows for a resplit's [lines], with what each kept line takes from its parent. */
    private fun splitLineWrites(
        lines: List<Map<String, Any?>>,
        parent: Map<String, Any?>,
        applicable: Map<String, Any?>,
        latest: JsonObject,
        actualId: String,
    ): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
        val payee = if ("payee" in applicable) applicable["payee"] else latest.value("payee")
        val following = lineUpdates(actualId, applicable, latest).associateBy { it["id"] }
        val added = mutableListOf<Map<String, Any?>>()
        val updated = mutableListOf<Map<String, Any?>>(parent)
        lines.forEachIndexed { index, line ->
            val fields =
                mapOf(
                    "amount" to line["amount"],
                    "category" to line["category"],
                    "notes" to line["notes"],
                    "sort_order" to -index.toLong(),
                )
            val id = line["id"] as String?
            if (id != null) {
                updated += fields + following[id].orEmpty() + ("id" to id)
            } else {
                added +=
                    fields +
                    mapOf(
                        "id" to writer.uuid(),
                        "payee" to payee,
                        "account" to latest.value("account"),
                        "date" to latest.value("date"),
                        "cleared" to latest.value("cleared"),
                        "is_child" to true,
                        "parent_id" to actualId,
                    )
            }
        }
        return added to updated
    }

    /**
     * What a split's lines take from a change to their parent, as Actual does: its account, date and cleared and
     * reconciled flags, and its payee on lines that shared the old one.
     */
    private fun lineUpdates(
        parentId: String,
        changes: Map<String, Any?>,
        latest: JsonObject,
    ): List<Map<String, Any?>> {
        val inherited = changes.filterKeys { it in LINE_FIELDS }
        if (inherited.isEmpty() && "payee" !in changes) return emptyList()
        val oldPayee = latest.value("payee")
        return db.query("SELECT id, payee FROM v_transactions WHERE parent_id = ?", parentId).map { line ->
            val payee = if ("payee" in changes && line["payee"] == oldPayee) mapOf("payee" to changes["payee"]) else emptyMap()
            inherited + payee + ("id" to line["id"])
        }
    }

    /** The patch fields that still apply on top of [latest], and the fields edited on both sides. */
    private fun mergeEditFields(
        patch: Map<String, Any?>,
        base: JsonObject,
        latest: JsonObject,
    ): Pair<LinkedHashMap<String, Any?>, List<String>> {
        val applicable = LinkedHashMap<String, Any?>()
        val conflicts = mutableListOf<String>()
        for (field in EDIT_FIELDS) {
            if (field !in patch) continue
            val local = patch[field]
            if (same(field, local, base.value(field))) continue
            if (same(field, latest.value(field), base.value(field))) {
                applicable[field] = local
                continue
            }
            if (!same(field, latest.value(field), local)) conflicts += field
        }
        if (base.value("isParent") == true &&
            "category" in patch &&
            !same("category", patch["category"], base.value("category")) &&
            !same("category", patch["category"], latest.value("category"))
        ) {
            conflicts += "category"
        }
        return applicable to conflicts
    }

    private fun actualId(transactionId: String): String =
        if (TANDEM_ID.matches(transactionId)) {
            db.first("SELECT id FROM v_transactions WHERE imported_id = ?", transactionId)?.get("id") as String
        } else {
            transactionId
        }

    private fun validateCreate(input: Map<String, Any?>): String? {
        val accounts = db.query("SELECT * FROM accounts WHERE tombstone = 0").associateBy { it["id"] as String }
        val account = accounts[input["account"]] ?: return "account-not-found"
        if (truthy(account["closed"])) return "account-closed"
        (input["payee"] as String?)?.let { payeeId ->
            val payee = payees()[payeeId] ?: return "payee-not-found"
            transferIssue(input["account"] as String, payee["transfer_acct"] as String?, accounts)?.let { return it }
        }
        return (input["category"] as String?)?.let(::categoryIssue)
    }

    private fun validateUpdate(
        fields: Map<String, Any?>,
        current: JsonObject,
    ): String? {
        val accounts = db.query("SELECT * FROM accounts WHERE tombstone = 0").associateBy { it["id"] as String }
        if ("account" in fields) {
            val account = accounts[fields["account"]] ?: return "account-not-found"
            if (truthy(account["closed"])) return "account-closed"
        }
        val payeeChanged = "payee" in fields
        val payeeId = if (payeeChanged) fields["payee"] as String? else current.value("payee") as String?
        val accountId = fields["account"] as String? ?: current.value("account") as String
        if ((payeeChanged && fields["payee"] != null) || ("account" in fields && current.value("payee") != null)) {
            val payee = payees()[payeeId]
            if (payeeChanged && fields["payee"] != null && payee == null) return "payee-not-found"
            (payee?.get("transfer_acct") as String?)?.let { transfer -> transferIssue(accountId, transfer, accounts)?.let { return it } }
        }
        return (fields["category"] as String?)?.let(::categoryIssue)
    }

    private fun payees(): Map<String, Row> =
        db
            .query(
                "SELECT p.* FROM payees p LEFT JOIN accounts a ON (p.transfer_acct = a.id AND a.tombstone = 0) " +
                    "WHERE p.tombstone = 0 AND (p.transfer_acct IS NULL OR a.id IS NOT NULL)",
            ).associateBy { it["id"] as String }

    private fun categoryIssue(categoryId: String): String? {
        val category = db.first("SELECT hidden FROM categories WHERE id = ? AND tombstone = 0", categoryId) ?: return "category-not-found"
        return if (truthy(category["hidden"])) "category-hidden" else null
    }

    private fun transferIssue(
        source: String,
        destination: String?,
        accounts: Map<String, Row>,
    ): String? {
        if (destination == null) return null
        val account = accounts[destination] ?: return "account-not-found"
        if (truthy(account["closed"])) return "transfer-account-closed"
        return if (destination == source) "transfer-account-same-as-source" else null
    }

    private fun createMismatches(
        existing: JsonObject,
        input: Map<String, Any?>,
    ): List<String> =
        listOf("account", "amount", "date", "payee", "category", "notes", "cleared")
            .filter { it != "payee" || input["payee_name"] == null }
            .filter { !same(it, existing.value(it), input[it]) }

    /**
     * A split's lines, each with an amount and a category and optional notes, or null when the transaction isn't split.
     * The lines must add up to the transaction, as Actual requires. An edit may name the existing lines it keeps by
     * [allowIds], and may pass no lines to turn the split back into one transaction.
     */
    private fun parseSplit(
        patch: JsonObject,
        amount: Long,
        allowIds: Boolean = false,
    ): List<Map<String, Any?>>? {
        val lines = patch["subtransactions"] ?: return null
        val keys = if (allowIds) setOf("id", "amount", "category", "notes") else setOf("amount", "category", "notes")
        val parsed =
            (lines as? JsonArray)?.map { line ->
                val fields = line as? JsonObject ?: throw InvalidTransactionRequestException("subtransactions must be objects")
                if (!keys.containsAll(fields.keys) || "amount" !in fields) {
                    throw InvalidTransactionRequestException("each subtransaction needs an amount and may have a category and notes")
                }
                parseFields(fields)
            } ?: throw InvalidTransactionRequestException("subtransactions must be a list")
        if (allowIds && parsed.isEmpty()) return parsed
        if (parsed.size < 2) throw InvalidTransactionRequestException("a split needs at least two lines")
        return parsed.takeIf { lines -> lines.sumOf { it["amount"] as Long } == amount }
            ?: throw InvalidTransactionRequestException("split lines must add up to the transaction amount")
    }

    /**
     * Replaces a transaction's split lines in one go: lines naming an existing line's id update it, the rest are added,
     * and lines left out are removed. No lines turns the split back into one transaction in the patch's category. The
     * parent's amount, payee, category and notes may change alongside; a split changed elsewhere since is a conflict.
     */
    private fun resplit(
        operationId: String,
        transactionId: String,
        patchJson: JsonObject,
        base: JsonObject,
        latest: JsonObject,
        actualId: String,
    ): OperationOutcome {
        fun outcome(
            status: OperationStatus,
            errorCode: String? = null,
        ) = OperationOutcome(
            operationId,
            transactionId,
            status,
            errorCode = errorCode,
            conflictType = "same-field-edit".takeIf { status == OperationStatus.CONFLICT },
            conflictingFields = listOf("subtransactions").takeIf { status == OperationStatus.CONFLICT }.orEmpty(),
        )
        val others = JsonObject(patchJson - "subtransactions")
        if (!SPLIT_EDIT_FIELDS.containsAll(others.keys)) {
            throw InvalidTransactionRequestException("a split edit may only change amount, payee, category and notes")
        }
        if (!sameStructure(base, latest) || splitLines(base) != splitLines(latest)) return outcome(OperationStatus.CONFLICT)
        if (latest.value("isChild") == true || latest.value("transferId") != null) {
            return outcome(OperationStatus.REJECTED, "split-not-editable")
        }
        val requested = if (others.isEmpty()) emptyMap() else parseFields(others)
        val (applicable, conflicts) = mergeEditFields(requested - "category", base, latest)
        if (conflicts.isNotEmpty()) return outcome(OperationStatus.CONFLICT)
        val amount = applicable["amount"] as Long? ?: latest.value("amount") as Long
        val lines = checkNotNull(parseSplit(patchJson, amount, allowIds = true))
        (validateUpdate(applicable, latest) ?: splitIssue(lines))?.let { return outcome(OperationStatus.REJECTED, it) }
        val existing = latest.children().toSet()
        if (lines.any { it["id"] != null && it["id"] !in existing }) return outcome(OperationStatus.REJECTED, "split-line-not-found")
        val parent = LinkedHashMap(applicable).apply { put("id", actualId) }
        if (lines.isEmpty()) {
            parent["is_parent"] = false
            parent["category"] = requested["category"]
            (parent["category"] as String?)?.let(::categoryIssue)?.let { return outcome(OperationStatus.REJECTED, it) }
            transactions.batchUpdate(updated = listOf(parent), deleted = existing.toList())
            return outcome(OperationStatus.COMMITTED)
        }
        parent["is_parent"] = true
        parent["category"] = null
        val (added, updated) = splitLineWrites(lines, parent, applicable, latest, actualId)
        transactions.batchUpdate(
            added = added,
            updated = updated,
            deleted = (existing - lines.mapNotNull { it["id"] as String? }.toSet()).toList(),
        )
        return outcome(OperationStatus.COMMITTED)
    }

    private fun splitLines(transaction: JsonObject): List<List<Any?>> =
        (transaction["subtransactions"] as? JsonArray).orEmpty().map { line ->
            val fields = line as JsonObject
            listOf("id", "amount", "category", "notes").map { fields.value(it) }
        }

    private fun splitIssue(lines: List<Map<String, Any?>>): String? =
        lines.firstNotNullOfOrNull {
            (it["category"] as String?)?.let(::categoryIssue)
        }

    private fun parseCreate(patch: JsonObject): Map<String, Any?> {
        val allowed = listOf("account", "amount", "date", "payee", "category", "notes", "cleared")
        val unsupported = patch.keys - allowed.toSet() - "subtransactions" - "payee_name"
        if (unsupported.isNotEmpty()) {
            throw InvalidTransactionRequestException(
                "transaction create input contains unsupported fields: ${unsupported.joinToString()}",
            )
        }
        allowed.forEach { if (it !in patch) throw InvalidTransactionRequestException("transaction create input is missing $it") }
        return parseFields(JsonObject(patch - "subtransactions"))
    }

    private fun parsePatch(patch: JsonObject): Map<String, Any?> {
        if (patch.isEmpty()) throw InvalidTransactionRequestException("transaction patch must contain at least one field")
        val unsupported = patch.keys - EDIT_FIELDS.toSet()
        if (unsupported.isNotEmpty()) {
            throw InvalidTransactionRequestException(
                "transaction patch contains unsupported fields: ${unsupported.joinToString()}",
            )
        }
        return parseFields(patch)
    }

    private fun parseFields(patch: JsonObject): Map<String, Any?> =
        patch.mapValues { (field, element) ->
            val primitive = element as? JsonPrimitive ?: throw InvalidTransactionRequestException("$field must be a scalar")
            when (field) {
                "account" ->
                    primitive.takeIf { it.isString && it.content.isNotBlank() }?.content
                        ?: throw InvalidTransactionRequestException("account must be a non-empty string")
                "amount" ->
                    primitive.takeIf { !it.isString }?.longOrNull
                        ?: throw InvalidTransactionRequestException("amount must be an integer")
                "date" ->
                    primitive.takeIf { it.isString && DATE.matches(it.content) }?.content
                        ?: throw InvalidTransactionRequestException("date must use YYYY-MM-DD format")
                "cleared", "reconciled" ->
                    primitive.takeIf { !it.isString }?.booleanOrNull
                        ?: throw InvalidTransactionRequestException("$field must be a boolean")
                "notes" -> if (primitive is JsonNull) null else primitive.contentOrNull?.ifEmpty { null }
                else ->
                    if (primitive is JsonNull) {
                        null
                    } else {
                        primitive.takeIf { it.isString && it.content.isNotBlank() }?.content
                            ?: throw InvalidTransactionRequestException("$field must be a non-empty string or null")
                    }
            }
        }

    private fun sameStructure(
        base: JsonObject,
        remote: JsonObject,
    ): Boolean {
        if (STRUCTURE.any { base.value(it) != remote.value(it) }) return false
        val baseChildren = base.children()
        val remoteChildren = remote.children()
        if (base.value("isParent") != true &&
            remote.value("isParent") != true &&
            baseChildren.isEmpty() &&
            remoteChildren.isEmpty()
        ) {
            return true
        }
        return baseChildren.sorted() == remoteChildren.sorted()
    }

    private fun structuredConflicts(
        base: JsonObject,
        remote: JsonObject,
    ): List<String> {
        val fields =
            STRUCTURE
                .filter {
                    if (it == "transferId" ||
                        it == "parentId"
                    ) {
                        base.value(it) != null || remote.value(it) != null
                    } else {
                        base.value(it) == true ||
                            remote.value(it) == true
                    }
                }.toMutableList()
        if (base.children().isNotEmpty() || remote.children().isNotEmpty()) fields += "subtransactions"
        return fields
    }

    private fun JsonObject.children(): List<String> =
        (this["subtransactions"] as? JsonArray)?.map { ((it as JsonObject)["id"] as JsonPrimitive).content }.orEmpty()

    private fun same(
        field: String,
        left: Any?,
        right: Any?,
    ): Boolean {
        fun normal(value: Any?): Any? =
            when {
                field == "notes" && value == "" -> null
                value is Int -> value.toLong()
                else -> value
            }
        return normal(left) == normal(right)
    }
}

private val EDIT_FIELDS = listOf("account", "amount", "date", "payee", "category", "notes", "cleared", "reconciled")
private val SPLIT_EDIT_FIELDS = setOf("amount", "payee", "category", "notes")
private val LINE_FIELDS = setOf("account", "date", "cleared", "reconciled")
private val STRUCTURE = listOf("transferId", "isParent", "isChild", "parentId")
private val TANDEM_ID = Regex("^tandem:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun JsonObject.value(key: String): Any? = scalar(this[key])

internal fun scalar(element: JsonElement?): Any? =
    when (element) {
        null, JsonNull -> null
        is JsonPrimitive ->
            if (element.isString) {
                element.content
            } else {
                element.booleanOrNull ?: element.longOrNull
                    ?: element.content.toDouble()
            }
        else -> element
    }
