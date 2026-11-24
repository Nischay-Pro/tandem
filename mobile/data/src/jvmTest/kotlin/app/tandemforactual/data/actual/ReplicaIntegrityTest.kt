package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Several devices edit one budget through a shared Actual server that drops requests and responses. Afterwards every
 * device must hold the same log and the same rows, and every cell must equal the newest message for it.
 */
class ReplicaIntegrityTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/actual/parity-envelope/$name")).readBytes()

    private inner class Server {
        val messages = sortedMapOf<String, MessageEnvelope>()
        var merkle = MerkleTrie()
        var down = false
        var loseResponse = false

        init {
            SyncProtocol.decodeResponse(fixture("sync-response.bin")).messages.forEach(::store)
        }

        private fun store(message: MessageEnvelope) {
            if (messages.putIfAbsent(message.timestamp, message) == null) {
                merkle = merkle.insert(checkNotNull(ActualTimestamp.parse(message.timestamp)))
            }
        }

        fun sync(request: SyncRequest): SyncResponse {
            if (down) throw IllegalStateException("offline")
            request.messages.forEach(::store)
            if (loseResponse) throw IllegalStateException("response lost")
            val sent = request.messages.map { it.timestamp }.toSet()
            return SyncResponse(
                messages.values.filter { it.timestamp > request.since && it.timestamp !in sent },
                merkle.prune().toJson().toString(),
            )
        }
    }

    private inner class Device(
        server: Server,
        now: () -> Long,
    ) {
        private val file = File.createTempFile("replica", ".sqlite").apply { deleteOnExit() }
        val db: ActualDatabase
        val store: ActualMessageStore
        val clock: StoredClock
        val writer: ActualWriter
        val operations: ActualTransactionOperations
        val reader: ActualBudgetReader
        private val engine: ActualSyncEngine
        private var lastSynced: String? = null

        init {
            file.writeBytes(ZipArchive.read(fixture("budget.zip")).getValue("db.sqlite"))
            db = ActualDatabase(BundledSQLiteDriver().open(file.path))
            store = ActualMessageStore(db)
            clock = store.loadClock().also(store::resetNode)
            engine = ActualSyncEngine(store, { server.sync(it) }, "group", "file", now = now)
            writer = ActualWriter(store, clock, now) { UUID.randomUUID().toString() }
            operations = ActualTransactionOperations(writer)
            reader = ActualBudgetReader(db)
        }

        fun sync() = runBlocking { lastSynced = engine.fullSync(clock, lastSynced).lastSyncedTimestamp }

        fun count(sql: String) = (db.first(sql)?.values?.single() as Number).toLong()

        fun close() {
            db.close()
            file.delete()
        }
    }

    private data class Submitted(
        val operationId: String,
        val transactionId: String,
        val action: String,
        val patch: JsonObject,
        val base: JsonObject?,
        val outcome: OperationOutcome,
    )

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun randomEdits(
        seed: Int,
        devices: Int,
        steps: Int,
    ) {
        val random = Random(seed)
        var time = 1_795_255_200_000L
        val server = Server()
        val replicas = List(devices) { Device(server) { time } }
        try {
            replicas.forEach { it.sync() }
            val first = replicas.first()

            fun account(name: String) = first.db.first("SELECT id FROM accounts WHERE name = ?", name)?.get("id") as String
            val accounts = listOf(account("Checking"), account("Savings"))
            val transferPayees = accounts.map { first.db.first("SELECT id FROM payees WHERE transfer_acct = ?", it)?.get("id") as String }
            val market = first.db.first("SELECT id FROM payees WHERE name = 'Market' AND tombstone = 0")?.get("id") as String
            val groceries = first.db.first("SELECT id FROM categories WHERE name = 'Groceries'")?.get("id") as String
            val payees = listOf(null, market) + transferPayees
            val created = mutableListOf<String>()
            val deleted = mutableSetOf<String>()
            val history = replicas.associateWith { mutableListOf<Submitted>() }

            fun Device.submit(
                id: String,
                action: String,
                patch: JsonObject,
                base: JsonObject?,
            ) {
                val operationId = UUID.randomUUID().toString()
                val outcome = operations.submit(operationId, id, action, patch, base)
                history.getValue(this) += Submitted(operationId, id, action, patch, base, outcome)
                if (outcome.status == OperationStatus.COMMITTED) {
                    if (action == "create") created += id
                    if (action == "delete") deleted += id
                }
            }

            fun date() = "2026-11-${(1 + random.nextInt(28)).toString().padStart(2, '0')}"

            fun amount() = random.nextLong(-500_000, 500_000)

            repeat(steps) {
                time += random.nextLong(0, 3)
                val device = replicas.random(random)
                when (random.nextInt(100)) {
                    in 0..24 -> {
                        val payee = payees.random(random)
                        device.submit(
                            "tandem:${UUID.randomUUID()}",
                            "create",
                            buildJsonObject {
                                put("account", accounts.random(random))
                                put("amount", amount())
                                put("date", date())
                                put("payee", payee?.let(::JsonPrimitive) ?: JsonNull)
                                put("category", if (payee in transferPayees || random.nextBoolean()) JsonNull else JsonPrimitive(groceries))
                                put("notes", if (random.nextBoolean()) JsonNull else JsonPrimitive("n${random.nextInt(9)}"))
                                put("cleared", random.nextBoolean())
                            },
                            null,
                        )
                    }
                    in 25..44 -> {
                        val id = created.randomOrNull(random) ?: return@repeat
                        val base = device.reader.transaction(id) ?: return@repeat
                        val patch =
                            when (random.nextInt(5)) {
                                0 -> buildJsonObject { put("amount", amount()) }
                                1 -> buildJsonObject { put("notes", "edit ${random.nextInt(99)}") }
                                2 -> buildJsonObject { put("date", date()) }
                                3 -> buildJsonObject { put("cleared", random.nextBoolean()) }
                                else -> buildJsonObject { put("payee", payees.random(random)?.let(::JsonPrimitive) ?: JsonNull) }
                            }
                        device.submit(id, "update", patch, base)
                    }
                    in 45..52 -> {
                        val id = created.randomOrNull(random) ?: return@repeat
                        val base = device.reader.transaction(id) ?: return@repeat
                        device.submit(id, "delete", JsonObject(emptyMap()), base)
                    }
                    in 53..60 -> {
                        val previous = history.getValue(device).randomOrNull(random) ?: return@repeat
                        val again =
                            device.operations.submit(
                                previous.operationId,
                                previous.transactionId,
                                previous.action,
                                previous.patch,
                                previous.base,
                            )
                        assertEquals(previous.outcome, again, "seed $seed: a retried operation must return its first outcome")
                    }
                    in 61..66 -> {
                        val messages = device.count("SELECT count(*) FROM messages_crdt")
                        val stamp = device.clock.clock.timestamp
                        val merkle = device.clock.merkle
                        assertFailsWith<IllegalStateException> {
                            device.writer.batch {
                                device.writer.insert("payees", mapOf("name" to "Half written"))
                                error("crashed mid-edit")
                            }
                        }
                        assertEquals(
                            messages,
                            device.count("SELECT count(*) FROM messages_crdt"),
                            "seed $seed: a failed edit left messages",
                        )
                        assertEquals(0, device.count("SELECT count(*) FROM payees WHERE name = 'Half written'"))
                        assertEquals(stamp, device.clock.clock.timestamp)
                        assertEquals(merkle, device.clock.merkle)
                    }
                    in 67..74 -> server.down = !server.down
                    in 75..79 -> server.loseResponse = !server.loseResponse
                    else -> runCatching { device.sync() }.onFailure { if (!server.down && !server.loseResponse) throw it }
                }
            }

            server.down = false
            server.loseResponse = false
            repeat(2) { replicas.forEach { it.sync() } }
            assertConverged(seed, server, replicas)
            replicas.forEach { device ->
                created.filter { it !in deleted }.forEach { id ->
                    assertNotNull(device.reader.transaction(id), "seed $seed: committed transaction $id is missing")
                }
                deleted.forEach { id ->
                    assertEquals(null, device.reader.transaction(id), "seed $seed: deleted transaction $id came back")
                }
                assertTransfersBalance(seed, device)
            }
        } finally {
            replicas.forEach { it.close() }
        }
    }

    private fun assertConverged(
        seed: Int,
        server: Server,
        replicas: List<Device>,
    ) {
        val serverLog = server.messages.keys.toList()
        for (device in replicas) {
            val log = device.db.query("SELECT timestamp FROM messages_crdt ORDER BY timestamp").map { it["timestamp"] as String }
            assertEquals(serverLog, log, "seed $seed: a device's log differs from the server's")
            assertEquals(server.merkle.hash, device.clock.merkle.hash, "seed $seed: the device's trie doesn't match its log")
            assertEquals(
                device.clock.merkle.hash,
                device.store
                    .loadClock()
                    .merkle.hash,
                "seed $seed: the saved trie is stale",
            )
            assertNewestValuesApplied(seed, device)
        }
        val tables =
            first(replicas)
                .db
                .query("SELECT DISTINCT dataset FROM messages_crdt")
                .map { it["dataset"] as String }
                .filter { first(replicas).db.first("SELECT 1 AS found FROM sqlite_master WHERE type = 'table' AND name = ?", it) != null }
        for (table in tables) {
            val expected = first(replicas).db.query("SELECT * FROM \"$table\" ORDER BY id")
            replicas.drop(1).forEach { device ->
                assertEquals(
                    expected,
                    device.db.query("SELECT * FROM \"$table\" ORDER BY id"),
                    "seed $seed: $table differs between devices",
                )
            }
        }
    }

    private fun first(replicas: List<Device>) = replicas.first()

    /** Every cell holds the value of the newest message written to it, as Actual's last-writer-wins requires. */
    private fun assertNewestValuesApplied(
        seed: Int,
        device: Device,
    ) {
        val newest =
            device.db.query(
                "SELECT dataset, row, column, value FROM messages_crdt m WHERE timestamp = " +
                    "(SELECT max(timestamp) FROM messages_crdt WHERE dataset = m.dataset AND row = m.row AND column = m.column)",
            )
        for (message in newest) {
            val dataset = message["dataset"] as String
            if (dataset == "prefs" || dataset == "spreadsheet_cells") continue
            val column = message["column"] as String
            val cell =
                runCatching {
                    device.db.first("SELECT \"$column\" AS v FROM \"$dataset\" WHERE id = ?", message["row"])
                }.getOrNull() ?: continue
            val actual = cell["v"]
            val matches =
                when (val value = CrdtValue.deserialize(message["value"] as String)) {
                    CrdtValue.Null -> actual == null
                    is CrdtValue.Number -> (actual as? Number)?.toDouble() == value.value || actual?.toString() == formatNumber(value.value)
                    is CrdtValue.Text -> actual?.toString() == value.value
                    is CrdtValue.Unknown -> true
                }
            if (!matches) fail("seed $seed: $dataset.${message["row"]}.$column is $actual but the newest message says ${message["value"]}")
        }
    }

    private fun assertTransfersBalance(
        seed: Int,
        device: Device,
    ) {
        val alive = device.db.query("SELECT * FROM v_transactions_internal_alive").associateBy { it["id"] as String }
        for (row in alive.values) {
            val partnerId = row["transfer_id"] as String? ?: continue
            val partner = alive[partnerId] ?: fail("seed $seed: transfer ${row["id"]} lost its other half")
            assertEquals(row["id"], partner["transfer_id"], "seed $seed: transfer halves don't point at each other")
            assertEquals(-(row["amount"] as Long), partner["amount"], "seed $seed: transfer halves don't balance")
            val payeeAccount = device.db.first("SELECT transfer_acct FROM payees WHERE id = ?", row["payee"])?.get("transfer_acct")
            assertEquals(partner["account"], payeeAccount, "seed $seed: a transfer points at the wrong account")
        }
        assertTrue(alive.values.all { it["amount"] is Long? }, "seed $seed: an amount isn't whole cents")
    }

    @Test
    fun twoDevicesConvergeUnderRandomEditsAndOutages() {
        for (seed in 1..6) randomEdits(seed, devices = 2, steps = 250)
    }

    @Test
    fun fourDevicesConvergeUnderRandomEditsAndOutages() {
        for (seed in 100..103) randomEdits(seed, devices = 4, steps = 400)
    }

    @Test
    fun aDueScheduleIsPostedOnceAcrossDevicesAndDays() {
        var time = 1_795_255_200_000L
        val server = Server()
        val (phone, tablet) = List(2) { Device(server) { time++ } }
        try {
            phone.sync()
            tablet.sync()
            val id = phone.db.first("SELECT id FROM schedules WHERE tombstone = 0")?.get("id") as String
            val checking = phone.db.first("SELECT id FROM accounts WHERE name = 'Checking'")?.get("id") as String
            ActualBudgetEdits(phone.writer, { "2026-11-21" }).schedules.edit(id, buildJsonObject { put("accountId", checking) })
            phone.writer.update("schedules", id, mapOf("posts_transaction" to 1L))
            phone.sync()
            tablet.sync()

            fun posted(device: Device) = device.count("SELECT count(*) FROM v_transactions_internal_alive WHERE schedule = '$id'")

            fun run(
                device: Device,
                today: String,
            ) {
                device.sync()
                ActualBudgetEdits(device.writer, { today }).schedules.runService(syncSuccess = true)
                device.sync()
            }
            for (today in listOf("2026-12-02", "2026-12-02", "2026-12-03", "2027-02-10", "2027-02-10")) {
                run(phone, today)
                val afterPhone = posted(phone)
                run(tablet, today)
                run(phone, today)
                assertEquals(afterPhone, posted(tablet), "$today: the tablet posted a schedule the phone already posted")
                assertEquals(afterPhone, posted(phone), "$today: running the schedule again posted it twice")
            }
            assertTrue(posted(phone) >= 3, "missed occurrences should each be posted once")
            val dates = phone.db.query("SELECT date FROM v_transactions_internal_alive WHERE schedule = '$id'").map { it["date"] }
            assertEquals(dates.distinct(), dates, "one occurrence was posted twice")
            assertConverged(0, server, listOf(phone, tablet))
        } finally {
            phone.close()
            tablet.close()
        }
    }
}
