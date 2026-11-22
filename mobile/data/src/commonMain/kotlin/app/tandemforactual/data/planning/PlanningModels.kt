package app.tandemforactual.data.planning

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
        )
    }
}

private fun jsonOf(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.filter { it.second != JsonNull }.toMap())

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

private fun Number?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
