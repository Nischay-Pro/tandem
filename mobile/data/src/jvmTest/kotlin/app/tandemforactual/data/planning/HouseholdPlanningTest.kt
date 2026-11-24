package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingTransport : PlanningTransport {
    val records = linkedMapOf<String, RemotePlanningRecord>()
    private var seq = 0L

    override suspend fun key(): WrappedPlanningKey? = null

    override suspend fun putKey(
        key: WrappedPlanningKey,
        expectedKeyId: String?,
    ) = true

    override suspend fun changes(
        after: Long,
        anchor: String?,
    ) = RemotePlanningPage(records.values.filter { it.seq > after }, seq, false)

    override suspend fun put(
        recordId: String,
        recordType: String,
        schemaVersion: Int,
        revision: Long,
        baseRevision: Long?,
        deleted: Boolean,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): RemoteWrite {
        records[recordId] = RemotePlanningRecord(recordId, recordType, schemaVersion, revision, deleted, iv, ciphertext, ++seq)
        return RemoteWrite.Stored(seq)
    }
}

class HouseholdPlanningTest {
    @Test
    fun modelsRoundTripThroughJson() {
        val device = DeviceName("node", "Ana’s phone")
        assertEquals("device:node", device.id)
        assertEquals(device, DeviceName.fromJson(device.toJson()))
        assertEquals(DeviceName("", ""), DeviceName.fromJson(kotlinx.serialization.json.JsonObject(emptyMap())))
        val goal = SavingsGoal("g", "Gift", 100, "USD", shared = false)
        assertTrue(isPrivate(goal.toJson()))
        assertFalse(SavingsGoal.fromJson("g", goal.toJson()).shared)
        assertTrue(SavingsGoal.fromJson("g", goal.copy(shared = true).toJson()).shared)
        val debt = DebtTerms("d", "acc", shared = false)
        assertFalse(DebtTerms.fromJson("d", debt.toJson()).shared)
    }

    @Test
    fun privateRecordsStayOnThisDevice(): Unit =
        runBlocking {
            val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val dao = database.planningRecordDao()
                val cipher = PlanningCipher(PlanningCipher.newKey())
                val store = PlanningStore(dao, "b", cipher) { 1L }
                val transport = RecordingTransport()
                val sync = PlanningSync(dao, "b", transport)

                store.save(SavingsGoal("secret", "Gift", 100, "USD", shared = false))
                store.save(SavingsGoal("open", "Holiday", 100, "USD"))
                assertEquals(1, sync.sync(cipher, 0).uploaded)
                assertEquals(setOf("open"), transport.records.keys)
                assertTrue(dao.pending("b").isEmpty())
                assertEquals(2, store.savingsGoals().first().size)

                store.save(SavingsGoal("open", "Holiday", 100, "USD", shared = false))
                sync.sync(cipher, 0)
                assertTrue(transport.records.getValue("open").deleted)
                assertEquals(listOf(false, false), store.savingsGoals().first().map { it.shared })
                assertTrue(dao.pending("b").isEmpty())

                store.save(SavingsGoal("open", "Holiday", 100, "USD"))
                sync.sync(cipher, 0)
                assertFalse(transport.records.getValue("open").deleted)
                assertEquals(3L, transport.records.getValue("open").revision)

                store.save(DeviceName("node", "Phone"))
                assertEquals(listOf(DeviceName("node", "Phone")), store.deviceNames().first())
            } finally {
                database.close()
            }
        }
}
