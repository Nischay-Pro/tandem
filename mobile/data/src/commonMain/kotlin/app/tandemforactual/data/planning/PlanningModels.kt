package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Where a value came from. Planning never fills a missing value with a guess. */
enum class Provenance { USER_SUPPLIED, UNKNOWN }

/** Something being saved for. Progress comes from a linked Actual category or account balance when one is set. */
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetDate: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "targetMinor" to JsonPrimitive(targetMinor),
            "currency" to JsonPrimitive(currency),
            "targetDate" to targetDate.json(),
            "categoryId" to categoryId.json(),
            "accountId" to accountId.json(),
        )

    companion object {
        const val TYPE = "planning.savings_goal"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = SavingsGoal(
            id = id,
            name = body.text("name").orEmpty(),
            targetMinor = body.long("targetMinor") ?: 0,
            currency = body.text("currency").orEmpty(),
            targetDate = body.text("targetDate"),
            categoryId = body.text("categoryId"),
            accountId = body.text("accountId"),
        )
    }
}

/** What an account with a negative balance is: a revolving card, an installment loan, or not a debt at all. */
enum class DebtKind { UNSPECIFIED, CARD, LOAN, NOT_DEBT }

/**
 * A debt account's terms as the person entered them. An unknown APR or minimum stays unknown, so estimates are only
 * made from what was actually supplied.
 */
data class DebtTerms(
    val id: String,
    val accountId: String,
    val aprBasisPoints: Int? = null,
    val minimumPaymentMinor: Long? = null,
    val dueDay: Int? = null,
    val promoAprBasisPoints: Int? = null,
    val promoEndsOn: String? = null,
    val kind: DebtKind = DebtKind.UNSPECIFIED,
    /** A card whose full statement balance is paid each month, so it never carries interest. */
    val paysInFull: Boolean = false,
) {
    val aprProvenance: Provenance get() = if (aprBasisPoints == null) Provenance.UNKNOWN else Provenance.USER_SUPPLIED

    fun toJson(): JsonObject =
        jsonOf(
            "accountId" to JsonPrimitive(accountId),
            "aprBasisPoints" to aprBasisPoints.json(),
            "minimumPaymentMinor" to minimumPaymentMinor.json(),
            "dueDay" to dueDay.json(),
            "promoAprBasisPoints" to promoAprBasisPoints.json(),
            "promoEndsOn" to promoEndsOn.json(),
            "kind" to (if (kind == DebtKind.UNSPECIFIED) JsonNull else JsonPrimitive(kind.name)),
            "paysInFull" to (if (paysInFull) JsonPrimitive(true) else JsonNull),
        )

    companion object {
        const val TYPE = "planning.debt_terms"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = DebtTerms(
            id = id,
            accountId = body.text("accountId").orEmpty(),
            aprBasisPoints = body.int("aprBasisPoints"),
            minimumPaymentMinor = body.long("minimumPaymentMinor"),
            dueDay = body.int("dueDay"),
            promoAprBasisPoints = body.int("promoAprBasisPoints"),
            promoEndsOn = body.text("promoEndsOn"),
            kind = DebtKind.entries.firstOrNull { it.name == body.text("kind") } ?: DebtKind.UNSPECIFIED,
            paysInFull = body.text("paysInFull") == "true",
        )
    }
}

/** Planning choices that apply to the whole budget, such as which countries' accounts and rules to use. */
data class PlanningSettings(
    val regions: List<String> = emptyList(),
) {
    fun toJson(): JsonObject = jsonOf("regions" to JsonArray(regions.map(::JsonPrimitive)))

    companion object {
        const val TYPE = "planning.settings"
        const val ID = "settings"

        fun fromJson(body: JsonObject) =
            PlanningSettings((body["regions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
    }
}

/** Money someone owes back for a purchase, keyed by the Actual transaction it came from. */
data class Reimbursement(
    val transactionId: String,
    val amountMinor: Long,
    val from: String? = null,
    val description: String? = null,
    val date: String? = null,
    val receivedOn: String? = null,
) {
    val received get() = receivedOn != null

    fun toJson(): JsonObject =
        jsonOf(
            "amountMinor" to JsonPrimitive(amountMinor),
            "from" to from.json(),
            "description" to description.json(),
            "date" to date.json(),
            "receivedOn" to receivedOn.json(),
        )

    companion object {
        const val TYPE = "planning.reimbursement"

        fun fromJson(
            transactionId: String,
            body: JsonObject,
        ) = Reimbursement(
            transactionId = transactionId,
            amountMinor = body.long("amountMinor") ?: 0,
            from = body.text("from"),
            description = body.text("description"),
            date = body.text("date"),
            receivedOn = body.text("receivedOn"),
        )
    }
}

/** The currency an Actual account holds. Actual has no per-account currency, so Tandem keeps it as planning data. */
data class AccountCurrency(
    val accountId: String,
    val currency: String,
) {
    val id: String get() = "currency:$accountId"

    fun toJson(): JsonObject = jsonOf("accountId" to JsonPrimitive(accountId), "currency" to JsonPrimitive(currency))

    companion object {
        const val TYPE = "planning.account_currency"

        fun fromJson(body: JsonObject) = AccountCurrency(body.text("accountId").orEmpty(), body.text("currency").orEmpty())
    }
}

/** One exchange rate the person entered: one [from] is worth [rate] of [to], as of [asOf]. */
data class ExchangeRate(
    val from: String,
    val to: String,
    val rate: String,
    val asOf: String,
) {
    val id: String get() = "fx:$from:$to"

    val value: Double? get() = rate.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    fun toJson(): JsonObject =
        jsonOf(
            "from" to JsonPrimitive(from),
            "to" to JsonPrimitive(to),
            "rate" to JsonPrimitive(rate),
            "asOf" to JsonPrimitive(asOf),
        )

    companion object {
        const val TYPE = "planning.exchange_rate"

        fun fromJson(body: JsonObject) =
            ExchangeRate(body.text("from").orEmpty(), body.text("to").orEmpty(), body.text("rate").orEmpty(), body.text("asOf").orEmpty())
    }
}

/** What the person entered for one retirement plan; the plan's limits come from its region pack. */
data class RetirementProfile(
    val planId: String,
    /** The Actual account this plan's money sits in; contributions are read from its deposits. */
    val accountId: String? = null,
    val age: Int? = null,
    val family: Boolean = false,
    val contributedMinor: Long = 0,
    val year: Int? = null,
    val salaryMinor: Long? = null,
    val contributionBasisPoints: Int? = null,
    val match: EmployerMatch? = null,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "accountId" to accountId.json(),
            "age" to age.json(),
            "family" to JsonPrimitive(family),
            "contributedMinor" to JsonPrimitive(contributedMinor),
            "year" to year.json(),
            "salaryMinor" to salaryMinor.json(),
            "contributionBasisPoints" to contributionBasisPoints.json(),
            "match" to (match?.let(::matchJson) ?: JsonNull),
        )

    companion object {
        const val TYPE = "planning.retirement"

        fun fromJson(
            planId: String,
            body: JsonObject,
        ) = RetirementProfile(
            planId = planId,
            accountId = body.text("accountId"),
            age = body.int("age"),
            family = body.text("family") == "true",
            contributedMinor = body.long("contributedMinor") ?: 0,
            year = body.int("year"),
            salaryMinor = body.long("salaryMinor"),
            contributionBasisPoints = body.int("contributionBasisPoints"),
            match = (body["match"] as? JsonObject)?.let(::matchFrom),
        )
    }
}

private fun matchJson(match: EmployerMatch): JsonObject =
    jsonOf(
        "capMinor" to match.capMinor.json(),
        "tiers" to
            JsonArray(
                match.tiers.map { tier ->
                    jsonOf(
                        "rate" to JsonPrimitive(tier.rateBasisPoints),
                        "band" to JsonPrimitive(tier.band.name),
                        "size" to JsonPrimitive(tier.size),
                    )
                },
            ),
    )

private fun matchFrom(body: JsonObject): EmployerMatch =
    EmployerMatch(
        (body["tiers"] as? JsonArray).orEmpty().mapNotNull { element ->
            val tier = element as? JsonObject ?: return@mapNotNull null
            val band = MatchBand.entries.firstOrNull { it.name == tier.text("band") } ?: return@mapNotNull null
            MatchTier(tier.int("rate") ?: 0, band, tier.long("size") ?: 0)
        },
        body.long("capMinor"),
    )

private fun jsonOf(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.filter { it.second != JsonNull }.toMap())

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

private fun Number?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
