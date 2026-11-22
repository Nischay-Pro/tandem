package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The extension sync rules in memory: next-revision writes, sequence-ordered changes, one guarded key. */
private class FakeServer {
    val records = linkedMapOf<String, RemotePlanningRecord>()
    var key: WrappedPlanningKey? = null
    var seq = 0L
    var pageSize = 1
    var raceKey: WrappedPlanningKey? = null
    var alwaysConflict = false

    fun transport() =
        object : PlanningTransport {
            override suspend fun key() = key

            override suspend fun putKey(
                key: WrappedPlanningKey,
                expectedKeyId: String?,
            ): Boolean {
                raceKey?.let {
                    this@FakeServer.key = it
                    raceKey = null
                }
                if (this@FakeServer.key?.keyId != expectedKeyId) return false
                this@FakeServer.key = key
                return true
            }

            override suspend fun changes(after: Long): RemotePlanningPage {
                val all = records.values.filter { it.seq > after }.sortedBy { it.seq }
                val page = all.take(pageSize)
                return RemotePlanningPage(page, page.lastOrNull()?.seq ?: after, all.size > pageSize)
            }

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
                val current = records[recordId]
                if (alwaysConflict) return RemoteWrite.Conflict(current)
                if (baseRevision != current?.revision || revision != (current?.revision ?: 0) + 1) return RemoteWrite.Conflict(current)
                val stored = RemotePlanningRecord(recordId, recordType, schemaVersion, revision, deleted, iv, ciphertext, ++seq)
                records[recordId] = stored
                return RemoteWrite.Stored(stored.seq)
            }
        }
}

class PlanningSyncTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun hkdfMatchesRfc5869() {
        val okm = PlanningKeys.hkdf(ByteArray(22) { 0x0b }, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertContentEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
        assertEquals(32, PlanningKeys.hkdf(ByteArray(8), ByteArray(0), ByteArray(0)).size)
    }

    @Test
    fun wrapsKeysSoOnlyMembersCanUnwrapThem() {
        val key = PlanningCipher.newKey()
        val secret = ByteArray(32) { 7 }
        val wrapped = PlanningKeys.wrap(key, secret, "budget")
        assertContentEquals(key, PlanningKeys.unwrap(wrapped, secret, "budget"))
        assertFailsWith<PlanningKeyMismatchException> { PlanningKeys.unwrap(wrapped, ByteArray(32), "budget") }
        assertFailsWith<PlanningKeyMismatchException> { PlanningKeys.unwrap(wrapped, secret, "other") }
        assertFailsWith<PlanningKeyMismatchException> { PlanningKeys.unwrap(wrapped.copy(keyCheck = ByteArray(16)), secret, "budget") }
        assertEquals(wrapped, wrapped.copy())
        assertFalse(wrapped.equals("x"))
        assertEquals(wrapped.hashCode(), wrapped.copy().hashCode())
    }

    @Test
    fun twoDevicesShareRecordsThroughSealedSync(): Unit =
        runBlocking {
            val phoneDb = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            val tabletDb = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val server = FakeServer()
                val secret = ByteArray(32) { 3 }
                val phoneSync = PlanningSync(phoneDb.planningRecordDao(), "b", server.transport())
                val tabletSync = PlanningSync(tabletDb.planningRecordDao(), "b", server.transport())

                val phoneKey = PlanningCipher.newKey()
                assertContentEquals(phoneKey, phoneSync.agreeOnKey(phoneKey, secret))
                val phone = PlanningStore(phoneDb.planningRecordDao(), "b", PlanningCipher(phoneKey)) { 1L }
                phone.save(SavingsGoal("g", "Holiday", 1_000, "USD"))
                phone.save(SavingsGoal("g", "Holiday", 2_000, "USD"))
                phone.save(DebtTerms("d", "card", 1_999))
                val first = phoneSync.sync(PlanningCipher(phoneKey), 0)
                assertEquals(2, first.uploaded)
                assertEquals(1L, server.records.getValue("g").revision)
                assertTrue(phoneDb.planningRecordDao().pending("b").isEmpty())

                val tabletOwnKey = PlanningCipher.newKey()
                val tabletEarly = PlanningStore(tabletDb.planningRecordDao(), "b", PlanningCipher(tabletOwnKey)) { 1L }
                tabletEarly.save(SavingsGoal("t", "Laptop", 500, "USD"))
                val shared = tabletSync.agreeOnKey(tabletOwnKey, secret)
                assertContentEquals(phoneKey, shared)
                tabletSync.rekey(PlanningCipher(tabletOwnKey), PlanningCipher(shared))
                val tablet = PlanningStore(tabletDb.planningRecordDao(), "b", PlanningCipher(shared)) { 2L }
                val pulled = tabletSync.sync(PlanningCipher(shared), 0)
                assertEquals(PlanningSyncResult(1, 2, 3), pulled)
                assertEquals(
                    setOf("Holiday", "Laptop"),
                    tablet
                        .savingsGoals()
                        .first()
                        .map { it.name }
                        .toSet(),
                )
                assertEquals(listOf(DebtTerms("d", "card", 1_999)), tablet.debtTerms().first())

                tablet.save(SavingsGoal("g", "Holiday", 3_000, "USD"))
                phone.save(SavingsGoal("g", "Holiday", 4_000, "USD"))
                tabletSync.sync(PlanningCipher(shared), pulled.cursor)
                val phoneResult = phoneSync.sync(PlanningCipher(phoneKey), first.cursor)
                assertEquals(1, phoneResult.uploaded)
                assertEquals(3L, server.records.getValue("g").revision)
                tabletSync.sync(PlanningCipher(shared), pulled.cursor)
                assertEquals(
                    4_000,
                    tablet
                        .savingsGoals()
                        .first()
                        .first { it.id == "g" }
                        .targetMinor,
                )

                phone.delete("d", DebtTerms.TYPE)
                phoneSync.sync(PlanningCipher(phoneKey), phoneResult.cursor)
                tabletSync.sync(PlanningCipher(shared), 0)
                assertEquals(emptyList(), tablet.debtTerms().first())

                tabletSync.rekey(PlanningCipher(PlanningCipher.newKey()), PlanningCipher(shared))
                assertEquals(2, tablet.savingsGoals().first().size)
            } finally {
                phoneDb.closeDatabase()
                tabletDb.closeDatabase()
            }
        }

    @Test
    fun agreeingOnAKeyHandlesARace(): Unit =
        runBlocking {
            val db = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val server = FakeServer()
                val secret = ByteArray(32) { 1 }
                val winner = PlanningCipher.newKey()
                server.raceKey = PlanningKeys.wrap(winner, secret, "b")
                val sync = PlanningSync(db.planningRecordDao(), "b", server.transport())
                assertContentEquals(winner, sync.agreeOnKey(PlanningCipher.newKey(), secret))
                assertFailsWith<PlanningKeyMismatchException> { sync.agreeOnKey(PlanningCipher.newKey(), ByteArray(32)) }
                val store = PlanningStore(db.planningRecordDao(), "b", PlanningCipher(winner)) { 1L }
                store.save(SavingsGoal("x", "X", 1, "USD"))
                server.records["x"] = RemotePlanningRecord("x", SavingsGoal.TYPE, 1, 5, false, ByteArray(12), ByteArray(16), 9)
                server.seq = 9
                val conflicted = sync.sync(PlanningCipher(winner), 9)
                assertEquals(1, conflicted.uploaded)
                assertEquals(6L, server.records.getValue("x").revision)
                server.records["y"] = RemotePlanningRecord("y", SavingsGoal.TYPE, 1, 1, false, ByteArray(12), ByteArray(16), 1)
                store.save(SavingsGoal("y", "Y", 1, "USD"))
                server.records["y"] = server.records.getValue("y").copy(revision = 3)
                assertEquals(1, sync.sync(PlanningCipher(winner), 10).uploaded)
                assertEquals(4L, server.records.getValue("y").revision)
                store.save(SavingsGoal("y", "Y", 2, "USD"))
                server.alwaysConflict = true
                val stuck = sync.sync(PlanningCipher(winner), 10)
                assertEquals(0, stuck.uploaded)
                assertTrue(db.planningRecordDao().pending("b").isNotEmpty())
            } finally {
                db.closeDatabase()
            }
        }
}
