package app.tandemforactual.data.planning

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Someone who pays you back their share of a [SharedBill], recognized by text in their incoming payments. */
data class SharedBillMember(
    val name: String,
    val tag: String,
    val shareMinor: Long,
    /** Text in the bank's payee for their payments, such as "Zelle from Alex" or "VENMO ALEX". */
    val matchText: String = "",
    /** Whether an Actual rule already tags payments matching [matchText]. */
    val ruleCreated: Boolean = false,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "tag" to JsonPrimitive(tag),
            "shareMinor" to JsonPrimitive(shareMinor),
            "matchText" to JsonPrimitive(matchText),
            "ruleCreated" to JsonPrimitive(ruleCreated),
        )

    companion object {
        fun fromJson(body: JsonObject) =
            SharedBillMember(
                name = body.text("name").orEmpty(),
                tag = body.text("tag").orEmpty(),
                shareMinor = body.long("shareMinor") ?: 0,
                matchText = body.text("matchText").orEmpty(),
                ruleCreated = body.text("ruleCreated") == "true",
            )
    }
}

/**
 * A bill you pay in full and others pay you back for each month, like a family phone plan. Payments are found by Actual
 * tags in their notes: the bill's tag and the person's tag, which rules add as payments arrive.
 */
data class SharedBill(
    val id: String,
    val name: String,
    val tag: String,
    val members: List<SharedBillMember>,
    /** Day of the month shares are due; a month before then isn't late yet. */
    val dueDay: Int? = null,
    /** The first month tracked (yyyy-MM); earlier months without payments aren't counted as missed. */
    val startMonth: String? = null,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "tag" to JsonPrimitive(tag),
            "members" to JsonArray(members.map { it.toJson() }),
            "dueDay" to (dueDay?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull),
            "startMonth" to startMonth.json(),
        )

    companion object {
        const val TYPE = "planning.shared_bill"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = SharedBill(
            id = id,
            name = body.text("name").orEmpty(),
            tag = body.text("tag").orEmpty(),
            members = (body["members"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(SharedBillMember::fromJson) },
            dueDay = body.int("dueDay"),
            startMonth = body.text("startMonth"),
        )

        /** A tag Actual accepts: lowercase letters, digits and dashes. */
        fun tagFor(name: String): String =
            name
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "shared" }
    }
}

/** A money-in transaction whose notes carry tags. */
data class TaggedPayment(
    val id: String,
    val date: String,
    val amountMinor: Long,
    val notes: String,
) {
    fun hasTag(tag: String): Boolean = Regex("(^|\\s)#${Regex.escape(tag)}(?![\\w-])").containsMatchIn(notes)
}

enum class ShareState { PAID, PARTLY_PAID, NOT_YET_DUE, MISSING, NOT_TRACKED }

data class MemberMonth(
    val member: SharedBillMember,
    val paidMinor: Long,
    val state: ShareState,
)

object SharedBills {
    /** How each person stands for [monthId] (yyyy-MM), given payments tagged with the bill's tag. */
    fun month(
        bill: SharedBill,
        payments: List<TaggedPayment>,
        monthId: String,
        today: String,
    ): List<MemberMonth> {
        val inMonth = payments.filter { it.date.startsWith(monthId) && it.hasTag(bill.tag) }
        val due = monthId < today.take(7) || (monthId == today.take(7) && today.drop(8).toInt() >= (bill.dueDay ?: 1))
        val tracked = bill.startMonth == null || monthId >= bill.startMonth
        return bill.members.map { member ->
            val paid = inMonth.filter { it.hasTag(member.tag) }.sumOf { it.amountMinor }
            val state =
                when {
                    paid >= member.shareMinor -> ShareState.PAID
                    paid > 0 -> ShareState.PARTLY_PAID
                    !tracked -> ShareState.NOT_TRACKED
                    !due -> ShareState.NOT_YET_DUE
                    else -> ShareState.MISSING
                }
            MemberMonth(member, paid, state)
        }
    }

    /** Months in a row, ending with the latest finished month, that [member] paid in full. */
    fun streak(
        bill: SharedBill,
        member: SharedBillMember,
        payments: List<TaggedPayment>,
        months: List<String>,
        today: String,
    ): Int =
        months
            .filter { it < today.take(7) }
            .sortedDescending()
            .takeWhile { month -> month(bill, payments, month, today).first { it.member == member }.state == ShareState.PAID }
            .size

    /** An Actual rule that tags [member]'s incoming payments with the bill's and the person's tags. */
    fun rule(
        bill: SharedBill,
        member: SharedBillMember,
    ): JsonObject =
        buildJsonObject {
            put("stage", "post")
            put("conditionsOp", "and")
            put(
                "conditions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("field", "imported_payee")
                            put("op", "contains")
                            put("text", member.matchText)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("field", "amount")
                            put("op", "gt")
                            put("amountMinor", 0)
                            put("inflow", true)
                        },
                    )
                },
            )
            put(
                "actions",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("op", "append-notes")
                            put("text", " #${bill.tag} #${member.tag}")
                        },
                    )
                },
            )
        }
}
