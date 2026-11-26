package app.tandemforactual.data.regions

import app.tandemforactual.data.planning.AccountMovement
import app.tandemforactual.data.planning.MovementKind
import app.tandemforactual.data.planning.PlanYearRoom
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.data.planning.RetirementContributions
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.RetirementRoom
import app.tandemforactual.data.planning.RoomStart
import app.tandemforactual.data.planning.YearMovements
import app.tandemforactual.data.regions.ca.retirement.fhsa.Fhsa
import app.tandemforactual.data.regions.ca.retirement.tfsa.Tfsa
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import app.tandemforactual.data.regions.india.retirement.epf.Epf
import app.tandemforactual.data.regions.india.retirement.ppf.Ppf
import app.tandemforactual.data.regions.india.retirement.vpf.Vpf
import app.tandemforactual.data.regions.us.retirement.ira.Ira
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.K401
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
import app.tandemforactual.data.regions.us.retirement.k401.Vesting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class RetirementAccuracyTest {
    @Test
    fun indianPlansRunAprilToMarch() {
        RetirementPlans.forRegion(Region.IN).forEach { assertEquals(4, it.yearStartMonth, it.id) }
        assertEquals(2025, Ppf.planYear("2026-03-31"))
        assertEquals(2026, Ppf.planYear("2026-04-01"))
        assertEquals(2026, Ppf.planYear("2027-01-15"))
        assertEquals(2026, K401.planYear("2026-03-31"))
        assertEquals(2026, K401.planYear("2026-12-31"))
        val deposits =
            listOf(
                AccountMovement("2026-03-10", 10_000_000),
                AccountMovement("2026-04-10", 4_000_000),
                AccountMovement("2027-02-10", 5_000_000),
            )
        val years = RetirementContributions.byPlanYear(Ppf, deposits, emptySet())
        assertEquals(10_000_000, years.getValue(2025).ownMinor)
        assertEquals(9_000_000, years.getValue(2026).ownMinor)
        assertEquals(setOf(2026), RetirementContributions.byPlanYear(K401, deposits.drop(1).dropLast(1), emptySet()).keys)
    }

    @Test
    fun vpfSharesEpfsTaxFreeInterestThreshold() {
        assertEquals(Epf.id, Vpf.sharesRoomWith)
        assertEquals(Epf.limits, Vpf.limits)
        assertEquals(25_000_000, Epf.allowance(2026, 30))
    }

    @Test
    fun onlyYourOwnMoneyCountsAsContributions() {
        val linked = setOf("ira", "k401")

        fun kind(
            amount: Long,
            payee: String? = null,
            notes: String? = null,
            other: String? = null,
        ) = RetirementContributions.kind(AccountMovement("2026-05-01", amount, payee, notes, other), linked)
        assertEquals(MovementKind.OWN, kind(50_000, "Payroll"))
        assertEquals(MovementKind.OWN, kind(50_000, other = "checking"))
        assertEquals(MovementKind.GROWTH, kind(1_200, "Vanguard", "Dividend reinvested"))
        assertEquals(MovementKind.GROWTH, kind(800, "Interest"))
        assertEquals(MovementKind.GROWTH, kind(-900, "Account fee"))
        assertEquals(MovementKind.GROWTH, kind(30_000, "Market gain"))
        assertEquals(MovementKind.EMPLOYER, kind(25_000, "Acme", "Employer match"))
        assertEquals(MovementKind.EMPLOYER, kind(25_000, "Safe harbor contribution"))
        assertEquals(MovementKind.ROLLOVER, kind(1_000_000, other = "ira"))
        assertEquals(MovementKind.WITHDRAWAL, kind(-40_000, other = "checking"))
        assertEquals(MovementKind.OWN, kind(50_000, "Again Corp"))
        val totals =
            RetirementContributions.byPlanYear(
                Ira,
                listOf(
                    AccountMovement("2026-01-05", 300_000, "Payroll"),
                    AccountMovement("2026-02-05", 2_000, "Dividend"),
                    AccountMovement("2026-03-05", 100_000, "Employer match"),
                    AccountMovement("2026-04-05", 500_000, otherAccountId = "k401"),
                    AccountMovement("2026-05-05", -50_000, otherAccountId = "checking"),
                    AccountMovement("2026-06-05", 200_000, otherAccountId = "checking"),
                ),
                linked,
            )
        assertEquals(YearMovements(500_000, 100_000, 2_000, 500_000, 50_000, "2026-06-05"), totals.getValue(2026))
    }

    @Test
    fun the401kSplitMatchesPayToDateNotTheWholeYear() {
        val match = EmployerMatch.simple(10_000, 300)
        assertEquals(219_600L to 109_800L, K401.splitDeposits(2026, 329_400, 7_300_000, match, "2026-07-02"))
        assertEquals(164_700L to 164_700L, K401.splitDeposits(2026, 329_400, 7_300_000, match))
        assertEquals(164_700L to 164_700L, K401.splitDeposits(2025, 329_400, 7_300_000, match, "2026-07-02"))
        val dollars = EmployerMatch(listOf(MatchTier(10_000, MatchBand.AMOUNT, 100_000)))
        assertEquals(100_000L to 100_000L, K401.splitDeposits(2026, 200_000, 10_000_000, dollars, "2026-02-01"))
    }

    @Test
    fun matchUsesPayOnlyUpTo401a17() {
        assertEquals(36_000_000, K401.matchablePay(2026, 50_000_000))
        assertEquals(37_500_000, K401.matchablePay(2027, 50_000_000))
        assertEquals(35_000_000, K401.matchablePay(2025, 50_000_000))
        assertEquals(10_000_000, K401.matchablePay(2026, 10_000_000))
        assertEquals(37_500_000, K401.matchablePay(2031, 90_000_000))
        val match = EmployerMatch.simple(10_000, 600)
        val result = K401.match(2026, 40, 50_000_000, 2_450_000, match)
        assertEquals(2_160_000, result.employerMinor)
        assertEquals(0, result.missedMinor)
    }

    @Test
    fun employerMoneyStaysWithin415c() {
        assertEquals(4_750_000, K401.withinAdditionsLimit(2026, 2_450_000, 9_000_000))
        assertEquals(4_750_000, K401.withinAdditionsLimit(2026, 3_250_000, 9_000_000))
        assertEquals(100_000, K401.withinAdditionsLimit(2026, 2_450_000, 100_000))
        val generous = EmployerMatch.simple(30_000, 2_000)
        val result = K401.match(2026, 40, 36_000_000, 2_450_000, generous)
        assertEquals(4_750_000, result.employerMinor)
        assertEquals(1_000_000, K401.roomLeft(2026, 2_450_000, 1_450_000, 0))
        assertEquals(300_000, K401.roomLeft(2026, 2_450_000, 1_450_000, 5_450_000))
        assertEquals(1_100_000, K401.roomLeft(2026, 3_250_000, 1_450_000, 5_450_000))
        assertEquals(0, K401.roomLeft(2026, 2_450_000, 2_450_000, 0))
    }

    @Test
    fun vestingCountsServiceFromTheMonthYouJoinedAcrossAllYears() {
        val match = EmployerMatch.simple(5_000, 600).copy(vesting = Vesting.cliff(3), joinedYear = 2023, joinedMonth = 9)
        assertEquals(2, match.serviceYears("2026-08-31"))
        assertEquals(3, match.serviceYears("2026-09-01"))
        assertEquals(0, match.vestedBasisPoints("2026-08"))
        assertEquals(10_000, match.vestedBasisPoints("2026-09"))
        val unknownMonth = match.copy(joinedMonth = null)
        assertEquals(2, unknownMonth.serviceYears("2026-11-26"))
        assertEquals(3, unknownMonth.serviceYears("2026-12-01"))
        assertNull(match.copy(joinedYear = null).serviceYears("2026-01"))
        assertEquals(0, match.serviceYears("2020-01"))
        val profile = RetirementProfile("us-401k", match = match)
        assertEquals(profile, RetirementProfile.fromJson("us-401k", profile.toJson()))
        val bad = Json.parseToJsonElement("""{"match":{"tiers":[],"joinedYear":2023,"joinedMonth":13}}""") as JsonObject
        assertNull(RetirementProfile.fromJson("us-401k", bad).match?.joinedMonth)
    }

    @Test
    fun birthYearKeepsCatchUpEligibilityCurrent() {
        val profile = RetirementProfile(K401.id, birthYear = 1976)
        assertEquals(49, profile.ageIn(2025))
        assertEquals(50, profile.ageIn(2026))
        assertEquals(2_350_000, profile.allowance(K401, 2025))
        assertEquals(3_250_000, profile.allowance(K401, 2026))
        assertEquals(3_575_000, RetirementProfile(K401.id, birthYear = 1966).allowance(K401, 2026))
        assertEquals(profile, RetirementProfile.fromJson(K401.id, profile.toJson()))
        assertFalse(profile.needsBirthYear)
        assertNull(RetirementProfile(K401.id).ageIn(2026))
    }

    @Test
    fun anAgeSavedByAnOlderVersionIsDatedFromWhenItWasSaved() {
        val legacy = Json.parseToJsonElement("""{"age":49,"family":false}""") as JsonObject
        val dated = RetirementProfile.fromJson(K401.id, legacy, savedYear = 2025)
        assertEquals(49, dated.ageIn(2025))
        assertEquals(50, dated.ageIn(2026))
        assertTrue(dated.needsBirthYear)
        assertEquals(1976, dated.estimatedBirthYear(2026))
        assertEquals(dated, RetirementProfile.fromJson(K401.id, dated.toJson()))
        val undated = RetirementProfile.fromJson(K401.id, legacy)
        assertEquals(49, undated.ageIn(2026))
        assertEquals(1977, undated.estimatedBirthYear(2026))
        val confirmed = dated.copy(birthYear = 1976, legacyAge = null, legacyAgeYear = null)
        assertFalse("age" in confirmed.toJson())
        assertEquals(confirmed, RetirementProfile.fromJson(K401.id, confirmed.toJson(), savedYear = 2030))
        val both = Json.parseToJsonElement("""{"age":30,"birthYear":1980}""") as JsonObject
        assertEquals(46, RetirementProfile.fromJson(K401.id, both, 2020).ageIn(2026))
        assertEquals(2026, PlanningStore.savedYear(1_790_000_000_000))
        assertNull(PlanningStore.savedYear(42))
    }

    @Test
    fun tfsaRoomCarriesForwardAndGivesBackWithdrawalsNextYear() {
        assertEquals(500_000, Tfsa.limitFor(2009).limit.standardMinor)
        assertEquals(550_000, Tfsa.limitFor(2014).limit.standardMinor)
        assertEquals(10_900_000, (2009..2026).sumOf { Tfsa.limitFor(it).limit.standardMinor })
        assertEquals(1_500_000, Tfsa.roomLeft(2026, 2026, 2_000_000, mapOf(2026 to 500_000), emptyMap()))
        assertEquals(
            2_500_000,
            Tfsa.roomLeft(2027, 2026, 2_000_000, mapOf(2026 to 500_000), mapOf(2026 to 300_000)),
        )
        assertEquals(-100_000, Tfsa.roomLeft(2026, 2026, 400_000, mapOf(2026 to 500_000), emptyMap()))
        assertNull(Tfsa.roomLeft(2025, 2026, 400_000, emptyMap(), emptyMap()))
        val profile = RetirementProfile(Tfsa.id, roomStart = RoomStart(2026, 2_000_000))
        assertEquals(profile, RetirementProfile.fromJson(Tfsa.id, profile.toJson()))
    }

    @Test
    fun fhsaCarriesForwardOneYearAndStopsAtTheLifetimeLimit() {
        assertEquals(800_000, Fhsa.roomLeft(2023, 2023, emptyMap()))
        assertEquals(1_600_000, Fhsa.roomLeft(2024, 2023, emptyMap()))
        assertEquals(1_600_000, Fhsa.roomLeft(2025, 2023, emptyMap()))
        assertEquals(1_300_000, Fhsa.roomLeft(2024, 2023, mapOf(2023 to 300_000)))
        assertEquals(0, Fhsa.roomLeft(2022, 2023, emptyMap()))
        val everyYear = (2023..2027).associateWith { 800_000L }
        assertEquals(0, Fhsa.roomLeft(2027, 2023, everyYear))
        assertEquals(0, Fhsa.roomLeft(2028, 2023, everyYear))
        val big = mapOf(2023 to 800_000L, 2024 to 1_600_000L, 2025 to 800_000L)
        assertEquals(800_000, Fhsa.roomLeft(2026, 2023, big))
        assertEquals(0, Fhsa.roomLeft(2027, 2023, big + (2026 to 800_000L)))
        assertEquals(500_000, Fhsa.roomLeft(2026, 2024, emptyMap(), startYear = 2026, startRoomMinor = 500_000))
        assertEquals(1_300_000, Fhsa.roomLeft(2027, 2024, emptyMap(), startYear = 2026, startRoomMinor = 500_000))
    }

    @Test
    fun planRoomCountsYourMoneyAndCarriesForwardWhereTheRulesDo() {
        val match = EmployerMatch.simple(10_000, 300)
        val k401 = RetirementProfile(K401.id, birthYear = 1990, salaryMinor = 7_300_000, match = match)
        val split = mapOf(2026 to YearMovements(ownMinor = 329_400, lastOwnDate = "2026-07-02"))
        val room = RetirementRoom.forYear(K401, k401, 2026, split, 2026)
        assertEquals(PlanYearRoom(2_450_000, 219_600, 109_800, 2_230_400), room)
        val named = mapOf(2026 to YearMovements(ownMinor = 300_000, employerMinor = 50_000))
        assertEquals(300_000L to 50_000L, RetirementRoom.split(K401, k401, 2026, named.getValue(2026)))
        assertEquals(109_800 + 50_000, RetirementRoom.employerThrough(K401, k401, split + (2025 to named.getValue(2026)), 2026))
        assertEquals(50_000, RetirementRoom.employerThrough(K401, k401, split + (2025 to named.getValue(2026)), 2025))
        val ira = RetirementRoom.forYear(Ira, RetirementProfile(Ira.id), 2026, named, 2026)
        assertEquals(PlanYearRoom(750_000, 300_000, 50_000, 450_000), ira)
        val tfsa = RetirementProfile(Tfsa.id, roomStart = RoomStart(2026, 2_000_000))
        val tfsaYears = mapOf(2026 to YearMovements(ownMinor = 500_000, withdrawnMinor = 300_000))
        assertEquals(PlanYearRoom(2_000_000, 500_000, 0, 1_500_000, true), RetirementRoom.forYear(Tfsa, tfsa, 2026, tfsaYears, 2026))
        assertEquals(2_500_000, RetirementRoom.forYear(Tfsa, tfsa, 2027, tfsaYears, 2027).roomMinor)
        assertEquals(700_000, RetirementRoom.forYear(Tfsa, RetirementProfile(Tfsa.id), 2026, emptyMap(), 2026).roomMinor)
        val fhsaYears = mapOf(2024 to YearMovements(ownMinor = 300_000))
        assertEquals(1_300_000, RetirementRoom.forYear(Fhsa, RetirementProfile(Fhsa.id), 2025, fhsaYears, 2025).roomMinor)
        val custom = RetirementProfile(Tfsa.id, roomStart = RoomStart(2026, 2_000_000), limitOverrides = mapOf(2026 to 900_000L))
        assertEquals(400_000, RetirementRoom.forYear(Tfsa, custom, 2026, tfsaYears, 2026).roomMinor)
        val merged = RetirementRoom.merge(listOf(named, split))
        assertEquals(YearMovements(629_400, 50_000, lastOwnDate = "2026-07-02"), merged.getValue(2026))
    }
}
