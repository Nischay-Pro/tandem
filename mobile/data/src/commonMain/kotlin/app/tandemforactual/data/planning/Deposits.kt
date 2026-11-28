package app.tandemforactual.data.planning

import kotlin.math.round
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** How often a deposit's interest is added to it. */
enum class Compounding(
    val monthsPerPeriod: Int,
) {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12),
}

/** A savings deposit's yearly rate, how its interest compounds, what is added each month, and when it matures. */
data class DepositTerms(
    val id: String,
    val accountId: String,
    val rateBasisPoints: Int,
    val compounding: Compounding,
    val monthlyContributionMinor: Long,
    val maturesOn: String?,
) {
    fun toJson(): JsonObject =
        jsonOf(
            "accountId" to JsonPrimitive(accountId),
            "rateBasisPoints" to JsonPrimitive(rateBasisPoints),
            "compounding" to JsonPrimitive(compounding.name),
            "monthlyContributionMinor" to JsonPrimitive(monthlyContributionMinor),
            "maturesOn" to maturesOn.json(),
        )

    companion object {
        const val TYPE = "planning.deposit_terms"

        fun fromJson(
            id: String,
            body: JsonObject,
        ) = DepositTerms(
            id = id,
            accountId = body.text("accountId").orEmpty(),
            rateBasisPoints = body.int("rateBasisPoints") ?: 0,
            compounding = Compounding.entries.firstOrNull { it.name == body.text("compounding") } ?: Compounding.MONTHLY,
            monthlyContributionMinor = body.long("monthlyContributionMinor") ?: 0L,
            maturesOn = body.text("maturesOn"),
        )
    }
}

object DepositMath {
    /**
     * The balance at the end of each of the next [months] months: the contribution goes in at the start of a month,
     * interest is earned monthly at the yearly rate and added at the end of each compounding period.
     */
    fun growth(
        balanceMinor: Long,
        terms: DepositTerms,
        months: Int,
    ): List<Long> {
        var balance = balanceMinor
        var accrued = 0.0
        return (1..months).map { month ->
            balance += terms.monthlyContributionMinor
            accrued += balance * terms.rateBasisPoints / MONTHLY_BASIS_POINTS
            if (month % terms.compounding.monthsPerPeriod == 0) {
                balance += round(accrued).toLong()
                accrued = 0.0
            }
            balance
        }
    }

    private const val MONTHLY_BASIS_POINTS = 120_000.0
}
