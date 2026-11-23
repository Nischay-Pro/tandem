package app.tandemforactual.data.regions

import app.tandemforactual.data.regions.ca.retirement.fhsa.Fhsa
import app.tandemforactual.data.regions.global.pension.PublicPensions
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.india.retirement.eps.Eps
import app.tandemforactual.data.regions.india.retirement.vpf.Vpf
import app.tandemforactual.data.regions.us.retirement.dcfsa.DependentCareFsa
import app.tandemforactual.data.regions.us.retirement.hsa.Hsa
import app.tandemforactual.data.regions.us.retirement.ira.Ira
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchResult
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
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
        val match = EmployerMatch.simple(5_000, 600)
        assertEquals(MatchResult(400_000, 200_000, 100_000), K401.match(2026, 35, 10_000_000, 400_000, match))
        assertEquals(MatchResult(2_450_000, 300_000, 0), K401.match(2026, 35, 10_000_000, 9_000_000, match))
        val tiered = EmployerMatch(listOf(MatchTier(10_000, MatchBand.AMOUNT, 100_000), MatchTier(5_000, MatchBand.AMOUNT, 100_000)))
        assertEquals(50_000, tiered.on(50_000, 10_000_000))
        assertEquals(125_000, tiered.on(150_000, 10_000_000))
        assertEquals(150_000, tiered.on(900_000, 10_000_000))
        assertEquals(200_000, tiered.fullMatchAt(10_000_000))
        assertEquals(100_000, tiered.copy(capMinor = 100_000).on(900_000, 0))
        val safeHarbor = EmployerMatch.presets[1].second
        assertEquals(400_000, safeHarbor.on(500_000, 10_000_000))
        assertEquals(4, EmployerMatch.presets.size)
        assertEquals(500_000L to 400_000L, K401.splitDeposits(900_000, 10_000_000, safeHarbor))
        assertEquals(0L to 0L, K401.splitDeposits(0, 10_000_000, safeHarbor))
    }

    @Test
    fun everyPlanIsRegisteredWithSourcesAndHistory() {
        assertEquals(listOf(Region.US, Region.CA, Region.IN), RetirementPlans.regions)
        assertEquals(5, RetirementPlans.forRegion(Region.US).size)
        assertEquals(K401, RetirementPlans.byId("us-401k"))
        assertFalse(K401.matchesAccount("Checking"))
        RetirementPlans.all.forEach { plan ->
            assertTrue(
                plan.source.isNotBlank() && plan.description.isNotBlank() && plan.displayName.isNotBlank() && plan.currency.length == 3,
            )
            val years = plan.limits.map { it.year }
            assertEquals((years.first()..2026).toList(), years)
            assertTrue(plan.limitFor(2026).published)
            assertTrue(plan.matchesAccount("My ${plan.accountKeywords.first()} account"))
            assertTrue(plan.allowance(2026, 70) >= plan.allowance(2026, 30))
        }
    }

    @Test
    fun advancedPlansKeepTheirOwnLimits() {
        assertEquals(800_000, Fhsa.allowance(2026, 30))
        assertEquals(750_000, DependentCareFsa.allowance(2026, 40))
        assertEquals(500_000, DependentCareFsa.allowance(2025, 40))
        assertEquals(1_500_000, Eps.allowance(2026, 30))
        assertEquals(25_000_000, Vpf.allowance(2026, 30))
        assertEquals(listOf("in-epf", "in-eps", "in-vpf"), RetirementPlans.all.filter { it.matchesAccount("EPF EPS VPF") }.map { it.id })
        assertEquals(
            setOf("ca-fhsa", "in-eps", "in-vpf", "us-dependent-care-fsa"),
            RetirementPlans.all
                .filter {
                    it.advanced
                }.map { it.id }
                .toSet(),
        )
    }

    @Test
    fun publicPensionsListTheirStartAges() {
        assertEquals(listOf(62, 67, 70), PublicPensions.byId("us-social-security")!!.startAges)
        assertEquals(listOf(65, 70), PublicPensions.byId("ca-oas")!!.startAges)
        assertEquals(null, PublicPensions.byId("nope"))
        assertEquals(listOf("ca-cpp", "ca-qpp", "ca-oas"), PublicPensions.forRegions(listOf(Region.CA)).map { it.id })
        PublicPensions.all.forEach { assertTrue(it.source.isNotBlank() && it.description.isNotBlank() && it.currency.length == 3) }
    }
}
