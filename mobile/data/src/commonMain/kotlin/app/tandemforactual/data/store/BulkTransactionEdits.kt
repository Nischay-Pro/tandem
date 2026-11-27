package app.tandemforactual.data.store

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One change made to every selected transaction, as Actual's batch edit offers it. */
sealed interface BulkTransactionEdit {
    data class SetCategory(
        val categoryId: String?,
    ) : BulkTransactionEdit

    data class AddTag(
        val tag: String,
    ) : BulkTransactionEdit

    data class RemoveTag(
        val tag: String,
    ) : BulkTransactionEdit

    data class SetCleared(
        val cleared: Boolean,
    ) : BulkTransactionEdit

    data class MoveToAccount(
        val accountId: String,
    ) : BulkTransactionEdit

    data class SetPayee(
        val payeeId: String?,
    ) : BulkTransactionEdit

    data class LinkSchedule(
        val scheduleId: String?,
    ) : BulkTransactionEdit
}

/** A selected transaction: its local record id and the body the device shows for it. */
data class BulkTarget(
    val id: String,
    val body: JsonObject,
    /** The split it is a line of, for the fields a line takes from its parent. */
    val parent: BulkTarget? = null,
)

/** What the budget says about accounts and payees, for the rules Actual applies to transfers and off-budget accounts. */
data class BulkEditContext(
    val offBudgetAccounts: Set<String> = emptySet(),
    val transferAccountByPayee: Map<String, String> = emptyMap(),
)

/** The fields to write on each transaction, and how many selected ones the edit leaves alone. */
data class BulkEditPlan(
    val changes: List<Pair<BulkTarget, JsonObject>>,
    val skipped: Int,
)

/**
 * Plans [edit] the way Actual's batch edit applies it: a split line's account and cleared flag belong to its split,
 * a reconciled transaction keeps its cleared flag, moving to another account unreconciles, and a transfer between
 * two budget accounts has no category to set.
 */
fun planBulkEdit(
    selected: List<BulkTarget>,
    edit: BulkTransactionEdit,
    context: BulkEditContext = BulkEditContext(),
): BulkEditPlan {
    val changes = LinkedHashMap<String, Pair<BulkTarget, JsonObject>>()
    for (target in selected) {
        val owner = if (edit.parentOwned()) target.parent ?: target else target
        if (owner.id in changes) continue
        val fields = bulkFields(owner.body, edit, context).filter { (field, value) -> owner.body[field].orNull() != value }
        if (fields.isNotEmpty()) changes[owner.id] = owner to JsonObject(fields)
    }
    val owners = selected.map { if (edit.parentOwned()) (it.parent ?: it).id else it.id }.toSet()
    return BulkEditPlan(changes.values.toList(), owners.size - changes.size)
}

private fun BulkTransactionEdit.parentOwned() = this is BulkTransactionEdit.SetCleared || this is BulkTransactionEdit.MoveToAccount

private fun bulkFields(
    body: JsonObject,
    edit: BulkTransactionEdit,
    context: BulkEditContext,
): Map<String, JsonElement> =
    when (edit) {
        is BulkTransactionEdit.SetCategory ->
            if (body.isSplit() || !body.takesCategory(context)) emptyMap() else mapOf("category" to edit.categoryId.json())
        is BulkTransactionEdit.AddTag -> mapOf("notes" to addNoteTag(body.text("notes"), edit.tag).json())
        is BulkTransactionEdit.RemoveTag -> mapOf("notes" to removeNoteTag(body.text("notes"), edit.tag).json())
        is BulkTransactionEdit.SetCleared ->
            if (body.flag("reconciled")) emptyMap() else mapOf("cleared" to JsonPrimitive(edit.cleared))
        is BulkTransactionEdit.MoveToAccount -> moveFields(body, edit.accountId, context)
        is BulkTransactionEdit.SetPayee -> mapOf("payee" to edit.payeeId.json())
        is BulkTransactionEdit.LinkSchedule -> mapOf("schedule" to edit.scheduleId.json())
    }

private fun moveFields(
    body: JsonObject,
    accountId: String,
    context: BulkEditContext,
): Map<String, JsonElement> {
    val transferTo = body.text("payee")?.let(context.transferAccountByPayee::get)
    if (body.text("account") == accountId || transferTo == accountId) return emptyMap()
    val fields = linkedMapOf<String, JsonElement>("account" to JsonPrimitive(accountId), "reconciled" to JsonPrimitive(false))
    if (accountId in context.offBudgetAccounts && !body.isSplit()) fields["category"] = JsonNull
    return fields
}

/** Whether a category can be set: not in an off-budget account, and not a transfer Actual keeps uncategorized. */
private fun JsonObject.takesCategory(context: BulkEditContext): Boolean {
    val account = text("account")
    if (account in context.offBudgetAccounts) return false
    val transferTo = text("payee")?.let(context.transferAccountByPayee::get) ?: return true
    return transferTo in context.offBudgetAccounts
}

/** Actual's cleared toggle: clear them all when any is uncleared, otherwise unclear them. */
fun bulkClearedValue(selected: List<BulkTarget>): Boolean = selected.any { !(it.parent ?: it).body.flag("cleared") }

/** How many of the transactions the edit would touch are reconciled, which Actual asks about first. */
fun bulkReconciledCount(selected: List<BulkTarget>): Int =
    selected.map { it.parent ?: it }.distinctBy { it.id }.count { it.body.flag("reconciled") }

/** Appends `#tag` to [notes] unless it is already there. */
fun addNoteTag(
    notes: String?,
    tag: String,
): String? {
    val name = noteTagName(tag) ?: return notes
    if (noteTags(notes).any { it.equals(name, ignoreCase = true) }) return notes
    return listOfNotNull(notes?.trimEnd()?.ifEmpty { null }, "#$name").joinToString(" ")
}

/** Removes every `#tag` from [notes], leaving the rest of the text as it was. */
fun removeNoteTag(
    notes: String?,
    tag: String,
): String? {
    val name = noteTagName(tag)
    if (notes == null || name == null) return notes
    val pattern = Regex("(?<!#)#${Regex.escape(name)}(?=[\\s#]|$)", RegexOption.IGNORE_CASE)
    return notes
        .replace(pattern, "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()
        .ifEmpty { null }
}

/** A tag as typed, without its `#`, or null when it is empty or has spaces or another `#` in it. */
fun noteTagName(tag: String): String? =
    tag.trim().removePrefix("#").takeIf { name -> name.isNotEmpty() && name.none { it == '#' || it.isWhitespace() } }

/** The tags in [notes], as Actual reads them: `#` followed by anything but spaces and another `#`. */
fun noteTags(notes: String?): List<String> = Regex("(?<!#)#([^#\\s]+)").findAll(notes.orEmpty()).map { it.groupValues[1] }.toList()

/** How two transactions merge: the one kept, the one removed, and what the kept one takes from the other. */
data class BulkMergePlan(
    val keep: BulkTarget,
    val drop: BulkTarget,
    val keepFields: JsonObject,
)

enum class BulkMergeIssue { NOT_TWO, AMOUNTS_DIFFER, SPLIT }

/**
 * Actual's merge: keeps the bank-imported transaction, then the file-imported one, then the first; the kept one
 * takes the other's payee, category and notes where it has none, and is cleared or reconciled if either was.
 */
fun planBulkMerge(selected: List<BulkTarget>): Pair<BulkMergePlan?, BulkMergeIssue?> {
    if (selected.size != 2) return null to BulkMergeIssue.NOT_TWO
    val (a, b) = selected
    if (a.body.long("amount") != b.body.long("amount")) return null to BulkMergeIssue.AMOUNTS_DIFFER
    if (selected.any { it.parent != null || it.body.isSplit() || it.body.flag("isChild") }) return null to BulkMergeIssue.SPLIT
    val keepB =
        when {
            (b.body.text("importedId") != null) != (a.body.text("importedId") != null) -> b.body.text("importedId") != null
            else -> b.body.text("importedPayee") != null && a.body.text("importedPayee") == null
        }
    val (keep, drop) = if (keepB) b to a else a to b
    val merged =
        buildJsonObject {
            for (field in listOf("payee", "category", "notes")) {
                put(field, (keep.body.text(field) ?: drop.body.text(field)).json())
            }
            for (field in listOf("cleared", "reconciled")) {
                put(field, JsonPrimitive(keep.body.flag(field) || drop.body.flag(field)))
            }
        }
    val fields = merged.filter { (field, value) -> keep.body[field].orNull() != value }
    return BulkMergePlan(keep, drop, JsonObject(fields)) to null
}

/**
 * The transactions a duplicate creates, as Actual copies them: a split line copies its whole split, and of a
 * transfer whose two sides are both selected only one is copied, since copying it makes the other side too.
 */
fun bulkDuplicateSources(selected: List<BulkTarget>): List<BulkTarget> {
    val sources = LinkedHashMap<String, BulkTarget>()
    for (target in selected) {
        val source = target.parent ?: target
        val pairedWith = source.body.text("transferId")
        if (pairedWith != null && (pairedWith in sources || sources.values.any { it.body.text("transferId") == source.id })) continue
        sources.getOrPut(source.id) { source }
    }
    return sources.values.toList()
}

/** The create patch for a copy of [source]: its fields, and its lines when it is split. */
fun bulkDuplicatePatch(source: JsonObject): JsonObject =
    buildJsonObject {
        val lines = (source["subtransactions"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        for (field in listOf("account", "amount", "date", "payee", "category", "notes")) {
            put(field, source[field].orNull())
        }
        put("cleared", JsonPrimitive(source.flag("cleared")))
        if (lines.isNotEmpty()) {
            put("category", JsonNull)
            put(
                "subtransactions",
                JsonArray(
                    lines.map { line ->
                        buildJsonObject { listOf("amount", "category", "notes").forEach { put(it, line[it].orNull()) } }
                    },
                ),
            )
        }
    }

private const val BULK_KEY_PREFIX = "bulk:"

/** The outbox key for one write of a bulk action, so the writes it sends stay together as one change in Actual. */
fun bulkIdempotencyKey(
    groupId: String,
    operationId: String,
): String = "$BULK_KEY_PREFIX$groupId:$operationId"

/** The bulk action a write belongs to, or null for a write made on its own. */
fun bulkGroupOf(idempotencyKey: String): String? =
    idempotencyKey
        .takeIf { it.startsWith(BULK_KEY_PREFIX) }
        ?.removePrefix(BULK_KEY_PREFIX)
        ?.substringBefore(':', "")
        ?.ifEmpty { null }

private fun JsonObject.isSplit() = flag("isParent") || (this["subtransactions"] as? JsonArray)?.isNotEmpty() == true

private fun JsonObject.text(field: String): String? = (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.flag(field: String): Boolean = (this[field] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.long(field: String): Long? = (this[field] as? JsonPrimitive)?.longOrNull

private fun JsonElement?.orNull(): JsonElement = this ?: JsonNull

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
