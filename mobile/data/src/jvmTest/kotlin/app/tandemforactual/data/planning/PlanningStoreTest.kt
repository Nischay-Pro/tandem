package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanningStoreTest {
    @Test
    fun savesRetirementProfiles(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(db.planningRecordDao(), "budget-1", PlanningCipher(PlanningCipher.newKey())) { 1_000L }
                val profile = RetirementProfile("us-401k", 41, true, 500_000, 2026, 9_000_000, 600, 5_000, 600)
                store.save(profile)
                store.save(RetirementProfile("us-ira"))
                assertEquals(setOf(profile, RetirementProfile("us-ira")), store.retirementProfiles().first().toSet())
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
                dao.upsert(sealed.copy(revision = 7))
                assertEquals(emptyList(), store.savingsGoals().first())

                store.delete("debt-1", DebtTerms.TYPE)
                assertEquals(emptyList(), store.debtTerms().first())
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
    fun cipherBindsTheRecordIdentity() {
        val cipher = PlanningCipher(ByteArray(32) { it.toByte() })
        val (iv, sealed) = cipher.seal("secret".encodeToByteArray(), "a")
        assertContentEquals("secret".encodeToByteArray(), cipher.open(iv, sealed, "a"))
        assertFailsWith<Exception> { cipher.open(iv, sealed, "b") }
        assertFailsWith<IllegalArgumentException> { PlanningCipher(ByteArray(16)) }
        assertEquals("tandem-planning/v1|b|r|1|2", PlanningCipher.identity("b", "r", 1, 2))
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
