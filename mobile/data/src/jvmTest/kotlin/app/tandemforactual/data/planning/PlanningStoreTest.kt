package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.us.retirement.k401.EmployerMatch
import app.tandemforactual.data.regions.us.retirement.k401.MatchBand
import app.tandemforactual.data.regions.us.retirement.k401.MatchTier
import app.tandemforactual.data.regions.us.retirement.k401.Vesting
import app.tandemforactual.data.regions.us.retirement.k401.VestingStep
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class PlanningStoreTest {
    @Test
    fun countsOnlyLivePlanningRecordsThatNeverSynced(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val dao = db.planningRecordDao()
                val cipher = PlanningCipher(PlanningCipher.newKey())
                val first = PlanningStore(dao, "budget-1", cipher) { 1L }
                val second = PlanningStore(dao, "budget-2", cipher) { 1L }
                first.put("goal-1", SavingsGoal.TYPE, JsonObject(emptyMap()))
                first.put("goal-2", SavingsGoal.TYPE, JsonObject(emptyMap()))
                second.put("goal-3", SavingsGoal.TYPE, JsonObject(emptyMap()))
                second.put("bill-1", SharedBill.TYPE, JsonObject(emptyMap()))
                first.put("gone", SharedBill.TYPE, JsonObject(emptyMap()))
                first.delete("gone", SharedBill.TYPE)
                first.put("other", "internal.state", JsonObject(emptyMap()))
                dao.upsert(checkNotNull(dao.find("budget-1", "goal-2")).copy(pendingUpload = false))

                assertEquals(
                    setOf(
                        UnsyncedPlanningRecord("budget-1", SavingsGoal.TYPE),
                        UnsyncedPlanningRecord("budget-2", SavingsGoal.TYPE),
                        UnsyncedPlanningRecord("budget-2", SharedBill.TYPE),
                    ),
                    dao.unsyncedPlanningRecords().toSet(),
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun anAgeFromAnOlderVersionIsDatedByWhenThisDeviceSavedIt(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val cipher = PlanningCipher(PlanningCipher.newKey())
                val in2025 = PlanningStore(db.planningRecordDao(), "budget-1", cipher) { 1_750_000_000_000 }
                in2025.put("us-401k", RetirementProfile.TYPE, JsonObject(mapOf("age" to JsonPrimitive(49))))
                val profile = in2025.retirementProfiles().first().single()
                assertEquals(2025, profile.legacyAgeYear)
                assertEquals(50, profile.ageIn(2026))
                val synced = PlanningStore(db.planningRecordDao(), "budget-1", cipher) { 7 }
                synced.put("us-ira", RetirementProfile.TYPE, JsonObject(mapOf("age" to JsonPrimitive(49))))
                val undated = synced.retirementProfiles().first().single { it.planId == "us-ira" }
                assertEquals(null, undated.legacyAgeYear)
                assertTrue(undated.needsBirthYear)
            } finally {
                db.close()
            }
        }

    @Test
    fun savesRetirementProfiles(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(db.planningRecordDao(), "budget-1", PlanningCipher(PlanningCipher.newKey())) { 1_000L }
                val profile =
                    RetirementProfile(
                        "us-401k",
                        "acct-1",
                        1985,
                        true,
                        500_000,
                        2026,
                        9_000_000,
                        600,
                        EmployerMatch(
                            listOf(MatchTier(10_000, MatchBand.AMOUNT, 100_000)),
                            capMinor = 50_000,
                            vesting = Vesting(listOf(VestingStep(1, 5_000), VestingStep(2, 10_000))),
                            joinedYear = 2022,
                            joinedMonth = 4,
                        ),
                        mapOf(2027 to 2_500_000L),
                        roomStart = RoomStart(2026, 700_000),
                    )
                store.save(profile)
                store.save(RetirementProfile("us-ira"))
                val card = DebtTerms("debt-2", "card", kind = DebtKind.CARD, paysInFull = true)
                store.save(card)
                assertEquals(card, store.debtTerms().first().first { it.id == "debt-2" })
                store.put(
                    "debt-3",
                    DebtTerms.TYPE,
                    kotlinx.serialization.json.buildJsonObject {
                        put("kind", kotlinx.serialization.json.JsonPrimitive("WEIRD"))
                    },
                )
                assertEquals(
                    DebtKind.UNSPECIFIED,
                    store
                        .debtTerms()
                        .first()
                        .first { it.id == "debt-3" }
                        .kind,
                )
                store.delete("debt-2", DebtTerms.TYPE)
                store.delete("debt-3", DebtTerms.TYPE)
                store.put(
                    "broken",
                    RetirementProfile.TYPE,
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(
                            """{"match":{"tiers":[1,{"band":"NOPE"},{"band":"AMOUNT"}]}}""",
                        ).jsonObject,
                )
                assertEquals(
                    listOf(MatchTier(0, MatchBand.AMOUNT, 0)),
                    store
                        .retirementProfiles()
                        .first()
                        .first { it.planId == "broken" }
                        .match!!
                        .tiers,
                )
                store.deleteRetirementProfile("broken")
                assertEquals(setOf(profile, RetirementProfile("us-ira")), store.retirementProfiles().first().toSet())
                val owed = Reimbursement("tx-1", 4_200, "Sam", "Dinner", "2026-11-20", claimBy = "2027-03-31")
                store.save(owed)
                store.save(Reimbursement("tx-2", 900))
                assertFalse(owed.received)
                assertTrue(owed.claimDueBy("2027-03-31"))
                assertFalse(owed.claimDueBy("2027-03-30"))
                assertFalse(owed.copy(receivedOn = "2026-12-01").claimDueBy("2027-04-01"))
                assertFalse(Reimbursement("tx-2", 900).claimDueBy("2099-01-01"))
                assertEquals("2027-03-31", Reimbursement.claimDeadline("us-health-fsa", "2026-11-20"))
                assertEquals(null, Reimbursement.claimDeadline("us-hsa", "2026-11-20"))
                assertTrue("us-health-fsa" in Reimbursement.claimPlans)
                store.save(owed.copy(receivedOn = "2026-11-25"))
                store.deleteReimbursement("tx-2")
                assertEquals(listOf(owed.copy(receivedOn = "2026-11-25")), store.reimbursements().first())
                assertTrue(
                    store
                        .reimbursements()
                        .first()
                        .single()
                        .received,
                )
                val home = JurisdictionPeriod("p1", PeriodKind.RESIDENCE, Region.IN, "2018-01", "2023-06")
                store.save(home)
                store.save(JurisdictionPeriod("p2", PeriodKind.RESIDENCE, Region.US, "2023-07"))
                store.put("p3", JurisdictionPeriod.TYPE, JsonObject(mapOf("kind" to JsonPrimitive("NOPE"))))
                store.deleteJurisdictionPeriod("p2")
                assertEquals(listOf(home), store.jurisdictionPeriods().first())
                val bill = SharedBill("b1", "Phone plan", "phone-plan", listOf(SharedBillMember("Alex", "alex", 4_000, "Zelle from Alex")))
                store.save(bill)
                store.save(bill.copy(id = "b2"))
                store.deleteSharedBill("b2")
                assertEquals(listOf(bill), store.sharedBills().first())
                assertEquals(PlanningSettings(), store.settings().first())
                store.save(PlanningSettings(listOf("US", "IN")))
                assertEquals(listOf("US", "IN"), store.settings().first().regions)
                store.save(PlanningSettings(listOf("US"), listOf("UNUSED_PAYEES:p1")))
                assertEquals(listOf("UNUSED_PAYEES:p1"), store.settings().first().ignoredChecks)
                store.ignoreChecks(listOf("UNUSED_PAYEES:p2", "UNUSED_PAYEES:p1", "UNUSED_PAYEES:p2"))
                store.save(PlanningSettings(listOf("US", "CA")))
                assertEquals(PlanningSettings(listOf("US", "CA"), listOf("UNUSED_PAYEES:p1", "UNUSED_PAYEES:p2")), store.settings().first())
                assertEquals(null, IgnoredCheck.fromJson(JsonObject(emptyMap())))
                assertTrue(PlanningStore.isBackedUp(IgnoredCheck.TYPE))
                store.deleteRetirementProfile("us-ira")
                assertEquals(listOf("us-401k"), store.retirementProfiles().first().map { it.planId })
                val trips = CustomReport("r-2", "trips", grouping = ReportGrouping.PAYEE)
                val food = CustomReport("r-1", "Food", categoryIds = listOf("cat-1"))
                store.save(trips)
                store.save(food)
                assertEquals(listOf(food, trips), store.customReports().first())
                store.deleteCustomReport("r-1")
                assertEquals(listOf(trips), store.customReports().first())
                val coffee = SavedSearch("s-1", "coffee", "cafe", listOf(SavedSearchFilter("TAG", "treat", "#treat")))
                val big = SavedSearch("s-2", "Big", filters = listOf(SavedSearchFilter("OUTFLOW_AT_LEAST", "10000", "Over 100")))
                store.save(coffee)
                store.save(big)
                assertEquals(listOf(big, coffee), store.savedSearches().first())
                store.deleteSavedSearch("s-2")
                assertEquals(listOf(coffee), store.savedSearches().first())
                val upi = CustomPaymentFormat("f-1", "upi", "(?<amount>\\d+)", "Paid 12")
                val bank = CustomPaymentFormat("f-2", "Bank", "spent (?<amount>\\d+)", "spent 4")
                store.save(upi)
                store.save(bank)
                assertEquals(listOf(bank, upi), store.paymentFormats().first())
                store.deletePaymentFormat("f-2")
                assertEquals(listOf(upi), store.paymentFormats().first())
                val cpp = PensionEstimate("ca-cpp", 90_000, 65)
                store.save(cpp)
                store.save(PensionEstimate("ca-oas", 70_000, 70))
                store.put("broken", PensionEstimate.TYPE, JsonObject(mapOf("monthlyMinor" to JsonPrimitive(1))))
                store.put("no-amount", PensionEstimate.TYPE, JsonObject(emptyMap()))
                store.deletePensionEstimate("ca-oas")
                assertEquals(listOf(cpp), store.pensionEstimates().first())
            } finally {
                db.close()
            }
        }

    @Test
    fun sealsRecordsAndReadsThemBack(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val dao = db.planningRecordDao()
                val key = PlanningCipher.newKey()
                val store = PlanningStore(dao, "budget-1", PlanningCipher(key)) { 1_000L }
                val goal = SavingsGoal("goal-1", "Holiday", 250_000, "USD", "2027-06-01", categoryId = "cat-1")
                store.save(goal)
                store.save(goal.copy(targetMinor = 300_000))
                val terms = DebtTerms("debt-1", "card", aprBasisPoints = 2_399, minimumPaymentMinor = 3_500, dueDay = 12)
                store.save(terms)
                assertEquals(listOf(goal.copy(targetMinor = 300_000)), store.savingsGoals().first())
                assertEquals(listOf(terms), store.debtTerms().first())

                val sealed = checkNotNull(dao.find("budget-1", "goal-1"))
                assertEquals(2, sealed.revision)
                assertTrue(sealed.pendingUpload)
                assertFalse(sealed.ciphertext.decodeToString().contains("Holiday"))
                assertEquals(2, dao.pending("budget-1").size)

                val wrongKey = PlanningStore(dao, "budget-1", PlanningCipher(PlanningCipher.newKey())) { 1_000L }
                assertEquals(emptyList(), wrongKey.savingsGoals().first())
                dao.upsert(sealed.copy(budgetId = "budget-2"))
                assertEquals(emptyList(), PlanningStore(dao, "budget-2", PlanningCipher(key)) { 1L }.savingsGoals().first())
                assertEquals(listOf(DebtTerms.TYPE, SavingsGoal.TYPE), store.backupRecords().map { it.second })
                assertEquals(goal.copy(targetMinor = 300_000).toJson(), store.backupRecords().last().third)
                dao.upsert(sealed.copy(recordId = "other", recordType = "tandem.internal"))
                assertEquals(2, store.backupRecords().size)
                dao.upsert(sealed.copy(revision = 7))
                assertEquals(emptyList(), store.savingsGoals().first())
                assertEquals(listOf(DebtTerms.TYPE), store.backupRecords().map { it.second })

                store.delete("debt-1", DebtTerms.TYPE)
                assertEquals(emptyList(), store.debtTerms().first())
                assertEquals(emptyList(), store.backupRecords())
                assertTrue(checkNotNull(dao.find("budget-1", "debt-1")).deleted)
                assertEquals(sealed, sealed.copy())
                assertFalse(sealed.equals("x"))
                assertEquals(sealed.hashCode(), sealed.copy().hashCode())
                dao.deleteBudget("budget-1")
                assertNull(dao.find("budget-1", "goal-1"))
            } finally {
                db.closeDatabase()
            }
        }

    @Test
    fun backupRecordsIncludeCurrenciesRatesAndResidencePeriods(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(db.planningRecordDao(), "budget-1", PlanningCipher(PlanningCipher.newKey())) { 1_000L }
                val period = JurisdictionPeriod("period-1", PeriodKind.RESIDENCE, Region.CA, "2024-01")
                store.save(AccountCurrency("acct-1", "EUR"))
                store.save(ExchangeRate("EUR", "USD", "1.08", "2026-11-01"))
                store.put("rate-from-an-older-version", ExchangeRate.TYPE, ExchangeRate("GBP", "USD", "1.27", "2026-10-01").toJson())
                store.save(period)

                val records = store.backupRecords()

                assertEquals(
                    listOf(AccountCurrency.TYPE, ExchangeRate.TYPE, ExchangeRate.TYPE, JurisdictionPeriod.TYPE),
                    records.map { it.second },
                )
                assertEquals(
                    setOf("fx:EUR:USD:2026-11-01", "rate-from-an-older-version"),
                    records.filter { it.second == ExchangeRate.TYPE }.map { it.first }.toSet(),
                )
                assertEquals(period.toJson(), records.last().third)
            } finally {
                db.closeDatabase()
            }
        }

    @Test
    fun cipherBindsTheRecordIdentity() {
        val cipher = PlanningCipher(ByteArray(32) { it.toByte() })
        val (iv, sealed) = cipher.seal("secret".encodeToByteArray(), "a")
        assertContentEquals("secret".encodeToByteArray(), cipher.open(iv, sealed, "a"))
        assertFailsWith<Exception> { cipher.open(iv, sealed, "b") }
        assertFailsWith<IllegalArgumentException> { PlanningCipher(ByteArray(16)) }
        assertEquals("tandem-planning/v1|b|r|goal|1|2|0", PlanningCipher.identity("b", "r", "goal", 1, 2, false))
        val live = PlanningCipher.identity("b", "r", "goal", 1, 2, false)
        val (liveIv, liveSealed) = cipher.seal("{}".encodeToByteArray(), live)
        assertFailsWith<Exception> { cipher.open(liveIv, liveSealed, PlanningCipher.identity("b", "r", "debt", 1, 2, false)) }
        val tombstone = PlanningCipher.identity("b", "r", "goal", 1, 2, true)
        val (deadIv, deadSealed) = cipher.seal(ByteArray(0), tombstone)
        assertFailsWith<Exception> { cipher.open(deadIv, deadSealed, live) }
    }

    @Test
    fun modelsKeepUnknownValuesUnknown() {
        val bare = DebtTerms("d", "acct")
        assertEquals(Provenance.UNKNOWN, bare.aprProvenance)
        assertEquals(Provenance.USER_SUPPLIED, bare.copy(aprBasisPoints = 1_999).aprProvenance)
        assertEquals(JsonObject(mapOf("accountId" to JsonPrimitive("acct"))), bare.toJson())
        assertEquals(bare, DebtTerms.fromJson("d", bare.toJson()))
        val full = DebtTerms("d", "acct", 1_999, 2_500, 3, 0, "2027-01-31")
        assertEquals(full, DebtTerms.fromJson("d", full.toJson()))
        assertEquals(SavingsGoal("g", "", 0, ""), SavingsGoal.fromJson("g", JsonObject(emptyMap())))
        assertEquals(DebtTerms("d", ""), DebtTerms.fromJson("d", JsonObject(emptyMap())))
    }

    @Test
    fun payoffAndSavingsMath() {
        assertEquals(PayoffEstimate(0, 0), PlanningMath.payoff(0, 2_000, 100))
        assertNull(PlanningMath.payoff(100_000, 2_400, 0))
        assertNull(PlanningMath.payoff(100_000, 2_400, 2_000))
        assertNull(PlanningMath.payoff(10_000_000, 100, 10_001))
        assertEquals(PayoffEstimate(10, 0), PlanningMath.payoff(100_000, 0, 10_000))
        val card = checkNotNull(PlanningMath.payoff(500_000, 1_999, 20_000))
        assertEquals(33, card.months)
        assertTrue(card.totalInterestMinor in 148_000..156_000, "${card.totalInterestMinor}")
        assertEquals(0, PlanningMath.monthlyToTarget(100, 150, 3))
        assertEquals(34, PlanningMath.monthlyToTarget(100, 0, 3))
        assertEquals(100, PlanningMath.monthlyToTarget(100, 0, 0))
        assertEquals(7, PlanningMath.monthsBetween("2026-11-21", "2027-06-01"))
        assertEquals(7, PlanningMath.monthsBetween("2026-11-21", "2027-06-21"))
        assertEquals(8, PlanningMath.monthsBetween("2026-11-21", "2027-06-22"))
        assertEquals(0, PlanningMath.monthsBetween("2027-01-01", "2026-01-01"))
    }
}
