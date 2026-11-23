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
    /** How many months each payment usually covers; months in between aren't missed until the cycle ends. */
    val everyMonths: Int = 1,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "tag" to JsonPrimitive(tag),
            "shareMinor" to JsonPrimitive(shareMinor),
            "matchText" to JsonPrimitive(matchText),
            "ruleCreated" to JsonPrimitive(ruleCreated),
            "everyMonths" to JsonPrimitive(everyMonths),
        )

    companion object {
        fun fromJson(body: JsonObject) =
            SharedBillMember(
                name = body.text("name").orEmpty(),
                tag = body.text("tag").orEmpty(),
                shareMinor = body.long("shareMinor") ?: 0,
                matchText = body.text("matchText").orEmpty(),
                ruleCreated = body.text("ruleCreated") == "true",
                everyMonths = body.int("everyMonths")?.coerceIn(1, 12) ?: 1,
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

/** A money-in transaction that might be someone's share. */
data class PaymentCandidate(
    val id: String,
    val date: String,
    val amountMinor: Long,
    val payee: String,
    val notes: String,
)

/** A [candidate] and who it might be from, the likeliest first. */
data class PaymentSuggestion(
    val candidate: PaymentCandidate,
    val members: List<SharedBillMember>,
)

object SharedBills {
    private const val MAX_CREDITED_MONTHS = 120

    /** How each person stands for [monthId] (yyyy-MM), given payments tagged with the bill's tag. */
    fun month(
        bill: SharedBill,
        payments: List<TaggedPayment>,
        monthId: String,
        today: String,
    ): List<MemberMonth> {
        val tracked = bill.startMonth == null || monthId >= bill.startMonth
        return bill.members.map { member ->
            val paid = credits(bill, member, payments)[monthId] ?: 0
            val state =
                when {
                    paid >= member.shareMinor -> ShareState.PAID
                    paid > 0 -> ShareState.PARTLY_PAID
                    !tracked -> ShareState.NOT_TRACKED
                    !isDue(bill, member, monthId, today) -> ShareState.NOT_YET_DUE
                    else -> ShareState.MISSING
                }
            MemberMonth(member, paid, state)
        }
    }

    /**
     * What [member] has paid, credited to months in order from their first one, so a payment covering two months fills
     * both and a late payment fills the month it was owed for.
     */
    fun credits(
        bill: SharedBill,
        member: SharedBillMember,
        payments: List<TaggedPayment>,
    ): Map<String, Long> {
        val mine = payments.filter { it.amountMinor > 0 && it.hasTag(bill.tag) && it.hasTag(member.tag) }
        if (mine.isEmpty() || member.shareMinor <= 0) return emptyMap()
        var month = listOfNotNull(bill.startMonth, mine.minOf { it.date.take(7) }).min()
        var left = mine.sumOf { it.amountMinor }
        val credits = linkedMapOf<String, Long>()
        while (left > 0 && credits.size < MAX_CREDITED_MONTHS) {
            val credit = minOf(left, member.shareMinor)
            credits[month] = credit
            left -= credit
            month = SpendingInsights.shiftMonth(month, 1)
        }
        return credits
    }

    /** The last month [member] has paid for in full, when that's this month or later. */
    fun paidThrough(
        bill: SharedBill,
        member: SharedBillMember,
        payments: List<TaggedPayment>,
        today: String,
    ): String? =
        credits(bill, member, payments)
            .filter { (_, credit) -> credit >= member.shareMinor }
            .keys
            .maxOrNull()
            ?.takeIf { it >= today.take(7) }

    private fun isDue(
        bill: SharedBill,
        member: SharedBillMember,
        monthId: String,
        today: String,
    ): Boolean {
        val every = member.everyMonths.coerceAtLeast(1)
        val anchor = bill.startMonth ?: monthId
        val cycle = monthsBetween(anchor, monthId).floorDiv(every)
        val lastMonth = SpendingInsights.shiftMonth(anchor, cycle * every + every - 1)
        val thisMonth = today.take(7)
        return lastMonth < thisMonth || (lastMonth == thisMonth && today.drop(8).toInt() >= (bill.dueDay ?: 1))
    }

    private fun monthsBetween(
        from: String,
        to: String,
    ): Int = (to.take(4).toInt() - from.take(4).toInt()) * 12 + to.substring(5, 7).toInt() - from.substring(5, 7).toInt()

    /**
     * Untagged money in that looks like someone's share: a whole number of months of it, up to a year. When the payee or
     * notes name one of them, only they are offered; otherwise everyone whose share fits is.
     */
    fun suggestions(
        bill: SharedBill,
        candidates: List<PaymentCandidate>,
    ): List<PaymentSuggestion> =
        candidates
            .filter { it.amountMinor > 0 && !TaggedPayment(it.id, it.date, it.amountMinor, it.notes).hasTag(bill.tag) }
            .mapNotNull { candidate ->
                val fits = bill.members.filter { monthsCovered(it, candidate.amountMinor) != null }
                val named = fits.filter { namedIn(it, "${candidate.payee} ${candidate.notes}") }
                val members = named.ifEmpty { fits }
                if (members.isEmpty()) null else PaymentSuggestion(candidate, members)
            }

    /** How many months of [member]'s share [amountMinor] is, when it's a whole number from 1 to 12. */
    fun monthsCovered(
        member: SharedBillMember,
        amountMinor: Long,
    ): Int? =
        if (member.shareMinor > 0 && amountMinor % member.shareMinor == 0L) {
            (amountMinor / member.shareMinor).toInt().takeIf { it in 1..12 }
        } else {
            null
        }

    private fun namedIn(
        member: SharedBillMember,
        haystack: String,
    ): Boolean {
        val text = haystack.lowercase()
        val words = member.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        val match = member.matchText.trim().lowercase()
        return words.any { it in text } || (match.isNotEmpty() && match in text)
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

    /** [notes] with the bill's and [member]'s tags added, keeping any already there. */
    fun tagNotes(
        notes: String,
        bill: SharedBill,
        member: SharedBillMember,
    ): String {
        val payment = TaggedPayment("", "", 0, notes)
        val missing = listOf(bill.tag, member.tag).distinct().filterNot(payment::hasTag)
        return (listOf(notes.trim()) + missing.map { "#$it" }).filter(String::isNotEmpty).joinToString(" ")
    }

    /** [notes] without the bill's tag or any of its members' tags. */
    fun untagNotes(
        notes: String,
        bill: SharedBill,
    ): String =
        (listOf(bill.tag) + bill.members.map { it.tag })
            .fold(notes) { text, tag -> text.replace(Regex("(^|\\s)#${Regex.escape(tag)}(?![\\w-])"), "") }
            .trim()
            .replace(Regex("\\s{2,}"), " ")

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
