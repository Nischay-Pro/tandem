package app.tandemforactual.data.regions

import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.us.retirement.hsa.Hsa
import app.tandemforactual.data.regions.us.retirement.ira.Ira
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.data.regions.us.retirement.k401.MatchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetirementPlansTest {
    @Test
    fun limitsFollowTheYearAndFallBackWhenNotPublished() {
        assertEquals(2_450_000, K401.limitFor(2026).limit.standardMinor)
        assertEquals(1_800_000, K401.limitFor(2015).limit.standardMinor)
        val future = K401.limitFor(2031)
        assertFalse(future.published)
        assertEquals(2026, future.limit.year)
        assertEquals(2015, K401.limitFor(2001).limit.year)
        assertTrue(K401.limitFor(2024).published)
    }

    @Test
    fun allowancesIncludeCatchUpsAndFamilyCoverage() {
        assertEquals(2_450_000, K401.allowance(2026, 40))
        assertEquals(3_250_000, K401.allowance(2026, 52))
        assertEquals(3_575_000, K401.allowance(2026, 61))
        assertEquals(3_050_000, K401.allowance(2024, 61))
        assertEquals(2_450_000, K401.allowance(2026, null))
        assertEquals(860_000, Ira.allowance(2026, 50))
        assertEquals(null, Ira.superCatchUpAges)
        assertEquals(875_000, Hsa.allowance(2026, 40, family = true))
        assertEquals(540_000, Hsa.allowance(2026, 56))
        assertEquals(1_450_000, K401.room(2026, 40, 1_000_000))
        assertEquals(0, Ira.room(2026, 30, 9_000_000))
    }

    @Test
    fun employerMatchShowsWhatIsLeftOnTheTable() {
        val match = EmployerMatch(rateBasisPoints = 5_000, upToBasisPoints = 600)
        assertEquals(MatchResult(400_000, 200_000, 100_000), K401.match(2026, 35, 10_000_000, 400, match))
        assertEquals(MatchResult(2_450_000, 300_000, 0), K401.match(2026, 35, 10_000_000, 9_000, match))
    }

    @Test
    fun everyPlanIsRegisteredWithSourcesAndHistory() {
        assertEquals(listOf(Region.US, Region.CA, Region.IN), RetirementPlans.regions)
        assertEquals(4, RetirementPlans.forRegion(Region.US).size)
        assertEquals(K401, RetirementPlans.byId("us-401k"))
        RetirementPlans.all.forEach { plan ->
            assertTrue(
                plan.source.isNotBlank() && plan.description.isNotBlank() && plan.displayName.isNotBlank() && plan.currency.length == 3,
            )
            assertEquals((2015..2026).toList(), plan.limits.map { it.year })
            assertTrue(plan.limitFor(2026).published)
            assertTrue(plan.allowance(2026, 70) >= plan.allowance(2026, 30))
        }
    }
}
