package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** One transaction in a retirement account. [otherAccountId] is the account on the other side of a transfer. */
data class AccountMovement(
    val date: String,
    val amountMinor: Long,
    val payee: String? = null,
    val notes: String? = null,
    val otherAccountId: String? = null,
)

/** What a movement in a retirement account is, for counting only the money that uses up contribution room. */
enum class MovementKind { OWN, EMPLOYER, GROWTH, ROLLOVER, WITHDRAWAL }

/** One plan year's money in and out of a retirement account, split by where it came from. */
data class YearMovements(
    val ownMinor: Long = 0,
    val employerMinor: Long = 0,
    val growthMinor: Long = 0,
    val rolloverMinor: Long = 0,
    val withdrawnMinor: Long = 0,
    /** The latest date your own money went in, for working out how much of the year's pay has been matched. */
    val lastOwnDate: String? = null,
)

/**
 * Sorts a retirement account's transactions into your own contributions, employer money, investment growth, rollovers
 * between your own plans, and withdrawals. Payees and notes name the employer and growth entries; anything else that goes
 * in counts as yours.
 */
object RetirementContributions {
    private val growthWords =
        words(
            "dividend",
            "interest",
            "capital gain",
            "distribution",
            "reinvest",
            "gain",
            "market",
            "valuation",
            "revaluation",
            "fee",
            "reconciliation",
        )
    private val employerWords = words("employer", "match", "company contribution", "profit sharing", "safe harbor", "nonelective")

    private fun words(vararg starts: String) = Regex("\\b(" + starts.joinToString("|") + ")")

    fun kind(
        movement: AccountMovement,
        planAccountIds: Set<String>,
    ): MovementKind {
        val text = listOfNotNull(movement.payee, movement.notes).joinToString(" ").lowercase()
        return when {
            movement.otherAccountId != null && movement.otherAccountId in planAccountIds -> MovementKind.ROLLOVER
            movement.otherAccountId == null && growthWords.containsMatchIn(text) -> MovementKind.GROWTH
            movement.amountMinor < 0 -> MovementKind.WITHDRAWAL
            movement.otherAccountId == null && employerWords.containsMatchIn(text) -> MovementKind.EMPLOYER
            else -> MovementKind.OWN
        }
    }

    /**
     * Totals by [plan]'s years, so an April-to-March plan sums its own financial year. [planAccountIds] are the accounts
     * linked to any retirement plan, whose transfers between each other are rollovers rather than new contributions.
     */
    fun byPlanYear(
        plan: RetirementPlan,
        movements: List<AccountMovement>,
        planAccountIds: Set<String>,
    ): Map<Int, YearMovements> =
        movements
            .sortedBy { it.date }
            .groupBy { plan.planYear(it.date) }
            .mapValues { (_, list) ->
                list.fold(YearMovements()) { total, movement ->
                    val amount = movement.amountMinor
                    when (kind(movement, planAccountIds)) {
                        MovementKind.OWN -> total.copy(ownMinor = total.ownMinor + amount, lastOwnDate = movement.date)
                        MovementKind.EMPLOYER -> total.copy(employerMinor = total.employerMinor + amount)
                        MovementKind.GROWTH -> total.copy(growthMinor = total.growthMinor + amount)
                        MovementKind.ROLLOVER -> total.copy(rolloverMinor = total.rolloverMinor + amount)
                        MovementKind.WITHDRAWAL -> total.copy(withdrawnMinor = total.withdrawnMinor - amount)
                    }
                }
            }
}
