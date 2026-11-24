package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class JurisdictionTimelineTest {
    private val india = JurisdictionPeriod("a", PeriodKind.RESIDENCE, Region.IN, "2018-01", "2023-06")
    private val us = JurisdictionPeriod("b", PeriodKind.RESIDENCE, Region.US, "2023-07")
    private val job = JurisdictionPeriod("c", PeriodKind.EMPLOYMENT, Region.US, "2023-08")

    @Test
    fun roundTripsThroughJson() {
        assertEquals(india, JurisdictionPeriod.fromJson("a", india.toJson()))
        assertEquals(us, JurisdictionPeriod.fromJson("b", us.toJson()))
        assertNull(JurisdictionPeriod.fromJson("x", JsonObject(mapOf("kind" to JsonPrimitive("RESIDENCE")))))
        assertNull(
            JurisdictionPeriod.fromJson("x", JsonObject(mapOf("kind" to JsonPrimitive("RESIDENCE"), "region" to JsonPrimitive("US")))),
        )
        assertNull(JurisdictionPeriod.fromJson("x", JsonObject(emptyMap())))
        assertEquals("Lived in", PeriodKind.RESIDENCE.label)
    }

    @Test
    fun findsWhereYouWereEachMonth() {
        val periods = listOf(india, us, job)
        assertTrue(india.covers("2023-06"))
        assertFalse(india.covers("2023-07"))
        assertTrue(us.covers("2030-01"))
        assertFalse(us.isCurrent.not())
        assertEquals(listOf(Region.IN), JurisdictionTimeline.regionsIn(periods, PeriodKind.RESIDENCE, "2020-05"))
        assertEquals(listOf(Region.US), JurisdictionTimeline.regionsIn(periods, PeriodKind.EMPLOYMENT, "2024-01"))
        assertEquals(emptyList(), JurisdictionTimeline.overlaps(periods))
        val dual = JurisdictionPeriod("d", PeriodKind.TAX_RESIDENCE, Region.IN, "2023-01", "2023-12")
        val usTax = JurisdictionPeriod("e", PeriodKind.TAX_RESIDENCE, Region.US, "2023-07")
        assertEquals(listOf(dual to usTax), JurisdictionTimeline.overlaps(periods + dual + usTax))
    }

    @Test
    fun movesWithoutTouchingTaxResidence() {
        var next = 0
        val tax = JurisdictionPeriod("t", PeriodKind.TAX_RESIDENCE, Region.US, "2023-07")
        val moved = JurisdictionTimeline.move(listOf(india, us, job, tax), Region.CA, "2026-12", newJob = true) { "n${next++}" }
        assertEquals(
            listOf(
                us.copy(through = "2026-11"),
                JurisdictionPeriod("n0", PeriodKind.RESIDENCE, Region.CA, "2026-12"),
                job.copy(through = "2026-11"),
                JurisdictionPeriod("n1", PeriodKind.EMPLOYMENT, Region.CA, "2026-12"),
            ),
            moved,
        )
        assertEquals(
            listOf(us.copy(through = "2023-07")),
            JurisdictionTimeline.move(listOf(us), Region.CA, "2023-07", false) { "z" }.take(1),
        )
        assertEquals(emptyList(), JurisdictionTimeline.move(listOf(us), Region.US, "2026-12", false) { "z" })
    }

    @Test
    fun reviewsPlansAfterAMove() {
        val steps = JurisdictionTimeline.review(Region.US, Region.IN, setOf("us-401k", "us-ira", "in-ppf", "gone"))
        assertEquals(
            listOf(
                RelocationStep.EmployerPlanEnds("us-401k"),
                RelocationStep.ReviewContributions("us-ira"),
                RelocationStep.ConsiderPlan("in-epf"),
                RelocationStep.ConsiderPlan("in-nps"),
                RelocationStep.SplitTaxYear(Region.US, Region.IN),
                RelocationStep.NewCurrency("INR"),
            ),
            steps,
        )
        assertEquals(
            listOf(RelocationStep.ConsiderPlan("ca-rrsp"), RelocationStep.ConsiderPlan("ca-tfsa"), RelocationStep.NewCurrency("CAD")),
            JurisdictionTimeline.review(null, Region.CA, emptySet()),
        )
        assertEquals(
            emptyList(),
            JurisdictionTimeline.review(Region.EU, Region.EU, emptySet()).filterIsInstance<RelocationStep.NewCurrency>(),
        )
        assertNull(JurisdictionTimeline.currencyOf(Region.GLOBAL))
    }
}
