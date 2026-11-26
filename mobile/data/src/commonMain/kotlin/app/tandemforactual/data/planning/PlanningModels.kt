package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
import app.tandemforactual.data.regions.us.retirement.k401.Vesting
import app.tandemforactual.data.regions.us.retirement.k401.VestingStep
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
    /** False keeps the goal on this device instead of sharing it with everyone on the budget. */
    val shared: Boolean = true,
) {
    fun toJson(): JsonObject =
        jsonOf(
            PRIVATE to privateFlag(shared),
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
            shared = !isPrivate(body),
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
    val shared: Boolean = true,
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
            PRIVATE to privateFlag(shared),
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
            shared = !isPrivate(body),
        )
    }
}

/** Planning choices that apply to the whole budget, such as which countries' accounts and rules to use. */
data class PlanningSettings(
    val regions: List<String> = emptyList(),
    /** Budget checkup findings marked as fine, as `KIND:id`. */
    val ignoredChecks: List<String> = emptyList(),
) {
    fun toJson(): JsonObject =
        jsonOf(
            "regions" to JsonArray(regions.map(::JsonPrimitive)),
            "ignoredChecks" to (ignoredChecks.takeIf { it.isNotEmpty() }?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull),
        )

    companion object {
        const val TYPE = "planning.settings"
        const val ID = "settings"

        fun fromJson(body: JsonObject) =
            PlanningSettings(
                (body["regions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                (body["ignoredChecks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            )
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
    /** The last day to file the claim, as with an FSA's run-out period. */
    val claimBy: String? = null,
) {
    val received get() = receivedOn != null

    /** Pending with a deadline on or before [date]. */
    fun claimDueBy(date: String): Boolean = !received && claimBy != null && claimBy <= date

    fun toJson(): JsonObject =
        jsonOf(
            "amountMinor" to JsonPrimitive(amountMinor),
            "from" to from.json(),
            "description" to description.json(),
            "date" to date.json(),
            "receivedOn" to receivedOn.json(),
            "claimBy" to claimBy.json(),
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
            claimBy = body.text("claimBy"),
        )

        /** Plans that pay back expenses through claims. */
        val claimPlans = listOf("us-health-fsa", "us-dependent-care-fsa", "us-hsa")

        /** The usual claim deadline for an expense on [date]: an FSA's run-out ends March 31 of the next year; an HSA has none. */
        fun claimDeadline(
            planId: String,
            date: String,
        ): String? = if (planId == "us-hsa") null else "${date.take(4).toInt() + 1}-03-31"
    }
}

/** What a person expects from a public pension: a monthly amount in today's money if it starts at [startAge]. */
data class PensionEstimate(
    val pensionId: String,
    val monthlyMinor: Long,
    val startAge: Int,
) {
    fun toJson(): JsonObject = jsonOf("monthlyMinor" to JsonPrimitive(monthlyMinor), "startAge" to JsonPrimitive(startAge))

    companion object {
        const val TYPE = "planning.pension_estimate"

        fun fromJson(
            pensionId: String,
            body: JsonObject,
        ): PensionEstimate? {
            val monthly = body.long("monthlyMinor") ?: return null
            val age = body.int("startAge") ?: return null
            return PensionEstimate(pensionId, monthly, age)
        }
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

/**
 * One exchange rate the person entered: one [from] is worth [rate] of [to], as of [asOf]. Each date is its own record;
 * [recordId] is set only for a record stored under an older id, such as the undated `fx:FROM:TO`.
 */
data class ExchangeRate(
    val from: String,
    val to: String,
    val rate: String,
    val asOf: String,
    val recordId: String? = null,
) {
    val id: String get() = recordId ?: "fx:$from:$to:$asOf"

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

        fun fromJson(
            recordId: String,
            body: JsonObject,
        ): ExchangeRate = fromJson(body).let { if (it.id == recordId) it else it.copy(recordId = recordId) }
    }
}

/** A name for one device's changes, so change history can say who made an edit. Shared with the household. */
data class DeviceName(
    val node: String,
    val name: String,
) {
    val id: String get() = "device:$node"

    fun toJson(): JsonObject = jsonOf("node" to JsonPrimitive(node), "name" to JsonPrimitive(name))

    companion object {
        const val TYPE = "planning.device_name"

        fun fromJson(body: JsonObject) = DeviceName(body.text("node").orEmpty(), body.text("name").orEmpty())
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
    /** Limits entered by hand, by year, for when the rules change before Tandem's published figures do. */
    val limitOverrides: Map<Int, Long> = emptyMap(),
    /** Last year's earned income, pension adjustment and unused room, for working out RRSP room. */
    val rrspInputs: RrspInputs? = null,
) {
    /** The year's limit: the one entered by hand, or the plan's published one with catch-ups. */
    fun allowance(
        plan: RetirementPlan,
        year: Int,
    ): Long = limitOverrides[year] ?: plan.allowance(year, age, family)

    /** RRSP room worked out from [rrspInputs], or null when they aren't entered. */
    fun rrspRoom(year: Int): Long? =
        rrspInputs?.let { Rrsp.personalRoom(year, it.earnedIncomeMinor, it.pensionAdjustmentMinor, it.carryForwardMinor) }

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
            "limitOverrides" to
                if (limitOverrides.isEmpty()) {
                    JsonNull
                } else {
                    JsonObject(
                        limitOverrides.entries.associate { (y, v) ->
                            y.toString() to
                                JsonPrimitive(v)
                        },
                    )
                },
            "rrsp" to (rrspInputs?.toJson() ?: JsonNull),
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
            limitOverrides =
                (body["limitOverrides"] as? JsonObject)
                    ?.entries
                    ?.mapNotNull { (year, value) ->
                        year.toIntOrNull()?.let { y -> (value as? JsonPrimitive)?.longOrNull?.let { y to it } }
                    }?.toMap()
                    .orEmpty(),
            rrspInputs = (body["rrsp"] as? JsonObject)?.let(RrspInputs::fromJson),
        )
    }
}

/** The figures from last year's notice of assessment that set this year's RRSP room. */
data class RrspInputs(
    val earnedIncomeMinor: Long,
    val pensionAdjustmentMinor: Long = 0,
    val carryForwardMinor: Long = 0,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "earnedIncomeMinor" to JsonPrimitive(earnedIncomeMinor),
            "pensionAdjustmentMinor" to JsonPrimitive(pensionAdjustmentMinor),
            "carryForwardMinor" to JsonPrimitive(carryForwardMinor),
        )

    companion object {
        fun fromJson(body: JsonObject): RrspInputs? =
            body.long("earnedIncomeMinor")?.let {
                RrspInputs(it, body.long("pensionAdjustmentMinor") ?: 0, body.long("carryForwardMinor") ?: 0)
            }
    }
}

/** One saved filter on the Activity search, kept as the field's name so new fields never break older records. */
data class SavedSearchFilter(
    val field: String,
    val value: String,
    val label: String,
)

/** A named Activity search: the typed text plus its filters. */
data class SavedSearch(
    val id: String,
    val name: String,
    val query: String = "",
    val filters: List<SavedSearchFilter> = emptyList(),
) {
    fun toJson(): JsonObject =
        jsonOf(
            "name" to JsonPrimitive(name),
            "query" to JsonPrimitive(query),
            "filters" to
                JsonArray(
                    filters.map {
                        jsonOf("field" to JsonPrimitive(it.field), "value" to JsonPrimitive(it.value), "label" to JsonPrimitive(it.label))
                    },
                ),
        )

    companion object {
        const val TYPE = "planning.saved_search"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = SavedSearch(
            id = id,
            name = body.text("name").orEmpty(),
            query = body.text("query").orEmpty(),
            filters =
                (body["filters"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val filter = element as? JsonObject ?: return@mapNotNull null
                    val field = filter.text("field") ?: return@mapNotNull null
                    SavedSearchFilter(field, filter.text("value").orEmpty(), filter.text("label").orEmpty())
                },
        )
    }
}

private fun matchJson(match: EmployerMatch): JsonObject =
    jsonOf(
        "capMinor" to match.capMinor.json(),
        "vesting" to
            if (match.vesting == Vesting.IMMEDIATE) {
                JsonNull
            } else {
                JsonArray(match.vesting.steps.map { jsonOf("years" to JsonPrimitive(it.years), "share" to JsonPrimitive(it.basisPoints)) })
            },
        "joinedYear" to match.joinedYear.json(),
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
        Vesting(
            (body["vesting"] as? JsonArray).orEmpty().mapNotNull { element ->
                val step = element as? JsonObject ?: return@mapNotNull null
                VestingStep(step.int("years") ?: return@mapNotNull null, step.int("share") ?: return@mapNotNull null)
            },
        ),
        body.int("joinedYear"),
    )

/** The body field that keeps a planning record on this device; [PlanningSync] never uploads such records. */
const val PRIVATE = "private"

fun isPrivate(body: JsonObject): Boolean = (body[PRIVATE] as? JsonPrimitive)?.contentOrNull == "true"

private fun privateFlag(shared: Boolean): JsonElement = if (shared) JsonNull else JsonPrimitive(true)

internal fun jsonOf(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.filter { it.second != JsonNull }.toMap())

internal fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

internal fun Number?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

internal fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
