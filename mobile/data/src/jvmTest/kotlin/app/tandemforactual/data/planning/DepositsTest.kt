package app.tandemforactual.data.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject

class DepositsTest {
    private val monthly = DepositTerms("d", "fd", 1_200, Compounding.MONTHLY, 0, "2027-11-28")

    @Test
    fun keepsDepositTermsAsJson() {
        assertEquals(monthly, DepositTerms.fromJson("d", monthly.toJson()))
        val bare = DepositTerms.fromJson("e", JsonObject(emptyMap()))
        assertEquals(DepositTerms("e", "", 0, Compounding.MONTHLY, 0, null), bare)
        assertEquals(bare, DepositTerms.fromJson("e", bare.toJson()))
    }

    @Test
    fun monthlyCompoundingAddsInterestEveryMonth() {
        assertEquals(listOf(101_000L, 102_010L), DepositMath.growth(100_000, monthly, 2))
        assertEquals(emptyList(), DepositMath.growth(100_000, monthly, 0))
    }

    @Test
    fun quarterlyCompoundingHoldsInterestUntilThePeriodEnds() {
        val quarterly = monthly.copy(compounding = Compounding.QUARTERLY, monthlyContributionMinor = 10_000)
        val growth = DepositMath.growth(100_000, quarterly, 3)
        assertEquals(listOf(110_000L, 120_000L), growth.take(2))
        assertEquals(130_000L + 1_100 + 1_200 + 1_300, growth[2])
    }
}
