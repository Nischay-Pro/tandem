package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ActualEngineEdgeCaseTest {
    private fun memoryDb() = ActualDatabase(BundledSQLiteDriver().open(":memory:"))

    private fun zip(
        entries: Map<String, ByteArray>,
        method: Int = ZipEntry.DEFLATED,
        level: Int = Deflater.DEFAULT_COMPRESSION,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(level)
            entries.forEach { (name, data) ->
                val entry = ZipEntry(name)
                entry.method = method
                if (method == ZipEntry.STORED) {
                    entry.size = data.size.toLong()
                    entry.crc =
                        java.util.zip
                            .CRC32()
                            .apply { update(data) }
                            .value
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun zipReaderHandlesEveryDeflateBlockType() {
        val text = (1..4000).joinToString(",") { (it * 7919 % 1000).toString() }.encodeToByteArray()
        val random = ByteArray(70_000).also { kotlin.random.Random(7).nextBytes(it) }
        for (level in listOf(Deflater.NO_COMPRESSION, Deflater.BEST_SPEED, Deflater.BEST_COMPRESSION)) {
            val entries =
                ZipArchive.read(
                    zip(
                        mapOf(
                            "dir/" to ByteArray(0),
                            "db.sqlite" to text,
                            "random" to random,
                            "tiny" to "ab".encodeToByteArray(),
                        ),
                        level = level,
                    ),
                )
            assertContentEquals(text, entries["db.sqlite"])
            assertContentEquals(random, entries["random"])
            assertEquals("ab", entries.getValue("tiny").decodeToString())
            assertFalse("dir/" in entries)
        }
        assertContentEquals(text, ZipArchive.read(zip(mapOf("a" to text), method = ZipEntry.STORED)).getValue("a"))
    }

    @Test
    fun zipReaderRejectsDamagedFiles() {
        val good = zip(mapOf("a" to "hello hello hello".encodeToByteArray()))
        val centralOffset = good.size - 22 - (good.size - 22 - indexOfCentral(good))

        fun damaged(block: (ByteArray) -> Unit) = good.copyOf().also(block)
        listOf(
            ByteArray(10),
            ByteArray(30),
            damaged { it[centralOffset] = 0 },
            damaged { it[0] = 0 },
            damaged { it[centralOffset + 10] = 99 },
            damaged { it[centralOffset + 24] = (it[centralOffset + 24] + 1).toByte() },
            damaged { it[centralOffset + 20] = 0x7f },
            damaged { it[38] = 0xff.toByte() },
        ).forEachIndexed { i, bytes -> assertFailsWith<ZipFormatException>("case $i") { ZipArchive.read(bytes) } }
        assertFailsWith<ZipFormatException> { ZipArchive.read(good, maxTotalBytes = 2) }
        assertFailsWith<ZipFormatException>("inflate 1") { Inflater.inflate(byteArrayOf(0x07), 1) }
        assertFailsWith<ZipFormatException>("inflate 2") { Inflater.inflate(byteArrayOf(0x01, 0x05, 0x00, 0x00, 0x00), 5) }
        assertFailsWith<ZipFormatException>(
            "inflate 3",
        ) { Inflater.inflate(byteArrayOf(0x01, 0x05, 0x00, 0xfa.toByte(), 0xff.toByte(), 1), 5) }
        assertFailsWith<ZipFormatException>("inflate 4") { Inflater.inflate(byteArrayOf(0x01), 5) }
        assertFailsWith<ZipFormatException>("inflate 5") { Inflater.inflate(ByteArray(7).also { it[0] = 0x05 }, 5) }
        assertFailsWith<ZipFormatException> {
            Inflater.inflate(
                byteArrayOf(
                    0x05,
                    0xe0.toByte(),
                    0x81.toByte(),
                    0x91.toByte(),
                    0x24,
                    0xcb.toByte(),
                    0xb2.toByte(),
                    0x2c,
                    0x49,
                    0xe2.toByte(),
                    0x0f,
                    0x2e,
                    0x8b.toByte(),
                    0x9a.toByte(),
                    0x47,
                    0x56,
                    0x9f.toByte(),
                    0xfb.toByte(),
                    0xfe.toByte(),
                    0xec.toByte(),
                    0xd2.toByte(),
                    0xfe.toByte(),
                    0x32,
                ),
                5,
            )
        }
        val builder = ByteArrayBuilder(1)
        builder.append('a'.code.toByte())
        assertFailsWith<ZipFormatException> { builder.copyBack(2, 1) }
    }

    private fun indexOfCentral(bytes: ByteArray): Int {
        for (i in bytes.indices) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 1.toByte() &&
                bytes[i + 3] == 2.toByte()
            ) {
                return i
            }
        }
        error("no central directory")
    }

    @Test
    fun sha256MatchesPublishedVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun valuesRoundTripLikeActual() {
        assertEquals(CrdtValue.Null, CrdtValue.of(null))
        assertEquals("N:1", CrdtValue.of(true).serialize())
        assertEquals("N:0", CrdtValue.of(false).serialize())
        assertEquals("N:-5000", CrdtValue.of(-5000).serialize())
        assertEquals("N:1.5", CrdtValue.of(1.5).serialize())
        assertEquals("S:hi", CrdtValue.of("hi").serialize())
        assertFailsWith<IllegalArgumentException> { CrdtValue.of(listOf(1)) }
        assertEquals(CrdtValue.Null, CrdtValue.deserialize("0:"))
        assertEquals(CrdtValue.Number(0.5), CrdtValue.deserialize("N:.5"))
        assertEquals(CrdtValue.Number(1000.0), CrdtValue.deserialize("N:1e3x"))
        assertEquals(CrdtValue.Number(Double.NEGATIVE_INFINITY), CrdtValue.deserialize("N:-Infinity"))
        assertEquals(CrdtValue.Number(Double.POSITIVE_INFINITY), CrdtValue.deserialize("N:Infinity"))
        assertEquals(CrdtValue.Unknown("N:abc"), CrdtValue.deserialize("N:abc"))
        assertEquals(CrdtValue.Unknown("X:1"), CrdtValue.deserialize("X:1"))
        assertEquals(CrdtValue.Unknown("?"), CrdtValue.deserialize("?"))
        assertEquals("X:1", CrdtValue.Unknown("X:1").serialize())
        assertEquals("1e+21", formatNumber(1e21).replace("E", "e+").replace("1.0", "1"))
    }

    @Test
    fun jsonHelpersMatchJavaScript() {
        assertEquals(JsonNull, jsonNumber(null))
        assertEquals(JsonPrimitive(3L), jsonNumber(3.0))
        assertEquals(JsonPrimitive(2.5), jsonNumber(2.5))
        assertEquals(JsonPrimitive("x"), jsonNumber("x"))
        assertTrue(truthy(1.5))
        assertFalse(truthy(0.0))
        assertTrue(truthy("a"))
        assertFalse(truthy(""))
        assertTrue(truthy(listOf<Int>()))
        val nested =
            JsonObject(
                mapOf(
                    "b" to JsonArray(listOf(JsonObject(mapOf("d" to JsonPrimitive(1), "c" to JsonNull)))),
                    "a" to JsonPrimitive(true),
                ),
            )
        assertEquals("""{"a":true,"b":[{"c":null,"d":1}]}""", canonicalJson(nested))
        assertEquals("""{"b":[{"d":1,"c":null}],"a":true}""", stringifyJson(nested))
        assertEquals("[1.5,2]", stringifyJson(JsonArray(listOf(JsonPrimitive(1.5), JsonPrimitive(2.0)))))
        assertEquals("\"q\\\"\\\\\\b\\f\\n\\r\\t\\u0001\\udc00😀\\ud800x\"", quoteJson("q\"\\\b\u000C\n\r\t\u0001\uDC00😀\uD800x"))
    }

    @Test
    fun databaseBindsEveryTypeAndRollsBack() {
        val db = memoryDb()
        db.execute("CREATE TABLE t (id TEXT PRIMARY KEY, v)")
        db.execute("INSERT INTO t VALUES (?, ?)", "bool", true)
        db.execute("INSERT INTO t VALUES (?, ?)", "int", 7)
        db.execute("INSERT INTO t VALUES (?, ?)", "blob", byteArrayOf(1, 2))
        assertEquals(1L, db.first("SELECT v FROM t WHERE id = 'bool'")?.get("v"))
        assertEquals(7L, db.first("SELECT v FROM t WHERE id = 'int'")?.get("v"))
        assertContentEquals(byteArrayOf(1, 2), db.first("SELECT v FROM t WHERE id = 'blob'")?.get("v") as ByteArray)
        assertFailsWith<IllegalArgumentException> { db.execute("INSERT INTO t VALUES (?, ?)", "x", listOf(1)) }
        assertFailsWith<IllegalStateException> {
            db.transaction {
                db.transaction { db.execute("INSERT INTO t VALUES ('nested', 1)") }
                error("boom")
            }
        }
        assertNull(db.first("SELECT v FROM t WHERE id = 'nested'"))
        val ended =
            assertFailsWith<IllegalStateException> {
                db.transaction {
                    db.execute("ROLLBACK")
                    error("already rolled back")
                }
            }
        assertEquals("already rolled back", ended.message)
        db.close()
    }

    @Test
    fun aClockCatchesUpWithAnotherConnection() {
        val file =
            kotlin.io.path
                .createTempFile(suffix = ".sqlite")
                .toFile()
        try {
            val first = ActualMessageStore(ActualDatabase(BundledSQLiteDriver().open(file.path)))
            first.db.execute(
                "CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT, row TEXT, column TEXT, value BLOB)",
            )
            first.db.execute("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            first.db.execute("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, tombstone INTEGER DEFAULT 0)")
            val second = ActualMessageStore(ActualDatabase(BundledSQLiteDriver().open(file.path)))
            val mine = first.loadClock()
            val theirs = second.loadClock()
            theirs.clock.timestamp = mine.clock.timestamp
            second.apply(listOf(msg(5_000, "payees", "name", CrdtValue.Text("Other"))), theirs, deferUnknownSchema = false)
            first.reload(mine)
            assertEquals(theirs.merkle.toJson(), mine.merkle.toJson())
            mine.clock.send(9_000)
            val ahead = mine.clock.timestamp
            first.reload(mine)
            assertEquals(ahead, mine.clock.timestamp)
            first.db.close()
            second.db.close()
        } finally {
            file.delete()
        }
    }

    private fun crdtDb(): ActualMessageStore {
        val db = memoryDb()
        db.execute(
            "CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT, row TEXT, column TEXT, value BLOB)",
        )
        db.execute("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
        db.execute("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, tombstone INTEGER DEFAULT 0)")
        return ActualMessageStore(db)
    }

    private fun msg(
        ms: Long,
        dataset: String,
        column: String,
        value: CrdtValue,
        row: String = "p1",
    ) = CrdtMessage(ActualTimestamp(ms, 0, "0000000000000001"), dataset, row, column, value)

    @Test
    fun messagesApplyWithLastWriterWins() {
        val store = crdtDb()
        val clock = store.loadClock()
        assertEquals(16, clock.clock.timestamp.node.length)
        val applied =
            store.apply(
                listOf(
                    msg(2000, "payees", "name", CrdtValue.Text("New")),
                    msg(1000, "payees", "name", CrdtValue.Text("Old")),
                    msg(1500, "prefs", "budgetName", CrdtValue.Text("x")),
                    msg(1600, "spreadsheet_cells", "expr", CrdtValue.Text("x")),
                ),
                clock,
                deferUnknownSchema = true,
            )
        assertEquals(4, applied.size)
        assertEquals("New", store.db.first("SELECT name FROM payees WHERE id = 'p1'")?.get("name"))
        val replay =
            store.apply(
                listOf(msg(2000, "payees", "name", CrdtValue.Text("New")), msg(1200, "payees", "name", CrdtValue.Text("Older"))),
                clock,
                true,
            )
        assertTrue(replay.isEmpty())
        assertEquals("New", store.db.first("SELECT name FROM payees WHERE id = 'p1'")?.get("name"))
        assertEquals(5, store.messagesSince(ActualTimestamp.zero.toString()).size)
        assertEquals(clock.merkle, store.loadClock().merkle)
        store.apply(
            listOf(msg(3000, "payees", "tombstone", CrdtValue.Number(1.0)), msg(3100, "payees", "name", CrdtValue.Null, row = "p2")),
            clock,
            true,
        )
        assertEquals(1L, store.db.first("SELECT tombstone FROM payees WHERE id = 'p1'")?.get("tombstone"))
        assertNull(store.db.first("SELECT name FROM payees WHERE id = 'p2'")?.get("name"))
    }

    @Test
    fun unknownSchemaIsDeferredOrRejected() {
        val store = crdtDb()
        val clock = store.loadClock()
        val future =
            listOf(
                msg(1000, "future_table", "x", CrdtValue.Text("a")),
                msg(1100, "payees", "future_column", CrdtValue.Text("b")),
                msg(1200, "payees", "name", CrdtValue.Unknown("Z:new")),
                msg(1300, "future_table", "x", CrdtValue.Text("c")),
            )
        assertTrue(store.apply(future, clock, deferUnknownSchema = true).isEmpty())
        assertEquals(3L, store.db.first("SELECT count(*) AS n FROM messages_pending")?.get("n"))
        assertEquals("S:c", store.db.first("SELECT value FROM messages_pending WHERE dataset = 'future_table'")?.get("value"))
        assertFailsWith<ActualSchemaException> { store.apply(listOf(msg(2000, "nope", "x", CrdtValue.Text("a"))), clock, false) }
        assertFailsWith<ActualSchemaException> { store.apply(listOf(msg(2100, "payees", "name", CrdtValue.Unknown("Z:1"))), clock, false) }
        assertFailsWith<ActualSchemaException> {
            store.apply(listOf(msg(2200, "payees", "nope", CrdtValue.Text("a"))), clock, false)
        }
    }

    @Test
    fun clockSurvivesBadStoredValues() {
        val store = crdtDb()
        store.db.execute("INSERT INTO messages_clock VALUES (1, 'not json')")
        assertEquals(
            0,
            store
                .loadClock()
                .clock.timestamp.millis,
        )
        store.db.execute(
            "UPDATE messages_clock SET clock = '{\"timestamp\":\"1970-01-01T00:00:00.005Z-0000-00000000000000aa\",\"merkle\":{}}'",
        )
        val clock = store.loadClock()
        assertEquals(5, clock.clock.timestamp.millis)
        store.resetNode(clock)
        assertEquals(
            5,
            store
                .loadClock()
                .clock.timestamp.millis,
        )
        assertTrue(
            store
                .loadClock()
                .clock.timestamp.node != "00000000000000aa",
        )
    }

    private class XorCipher : ActualMessageCipher {
        override val keyId = "key-1"

        override fun encrypt(plaintext: ByteArray) =
            EncryptedData(
                byteArrayOf(1),
                byteArrayOf(2),
                plaintext
                    .map {
                        (it.toInt() xor 0x5a).toByte()
                    }.toByteArray(),
            )

        override fun decrypt(data: EncryptedData) = data.data.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }

    private class FakeServer(
        val cipher: ActualMessageCipher?,
    ) {
        val store = mutableListOf<MessageEnvelope>()
        var merkle = MerkleTrie()
        val requests = mutableListOf<SyncRequest>()

        val transport =
            ActualSyncTransport { request ->
                requests += request
                request.messages.forEach { envelope ->
                    if (store.none { it.timestamp == envelope.timestamp }) {
                        store += envelope
                        merkle = merkle.insert(checkNotNull(ActualTimestamp.parse(envelope.timestamp)))
                    }
                }
                SyncResponse(
                    store.filter {
                        it.timestamp > request.since &&
                            request.messages.none { m ->
                                m.timestamp == it.timestamp
                            }
                    },
                    merkle.prune().toJson().toString(),
                )
            }
    }

    @Test
    fun aLargeUploadGoesUpInChunksUnderTheLimit() {
        val server = FakeServer(null)
        val now = 1_795_254_060_000L
        val store = crdtDb()
        try {
            val clock = store.loadClock()
            store.apply(
                (1..30).map { CrdtMessage(clock.clock.send(now), "payees", "p$it", "name", CrdtValue.Text("Payee $it")) },
                clock,
                false,
            )
            runBlocking {
                ActualSyncEngine(
                    store,
                    server.transport,
                    "group",
                    "file",
                    null,
                    maxUploadBytes = 400,
                ) { now }.fullSync(clock, null)
            }

            assertEquals(30, server.store.size)
            val uploads = server.requests.filter { it.messages.isNotEmpty() }
            assertTrue(uploads.size > 1)
            assertTrue(uploads.all { request -> request.messages.sumOf { it.content.size + it.timestamp.length + 16 } <= 400 })
        } finally {
            store.db.close()
        }
    }

    @Test
    fun twoDevicesConvergeThroughEncryptedSync() {
        val server = FakeServer(XorCipher())
        val now = 1_795_254_060_000L
        val devices =
            List(2) {
                val store = crdtDb()
                val clock = store.loadClock()
                Triple(store, clock, ActualSyncEngine(store, server.transport, "group", "file", XorCipher()) { now })
            }
        val (storeA, clockA, engineA) = devices[0]
        val (storeB, clockB, engineB) = devices[1]
        storeA.apply(listOf(CrdtMessage(clockA.clock.send(now), "payees", "p1", "name", CrdtValue.Text("From A"))), clockA, false)
        val first = runBlocking { engineA.fullSync(clockA, null) }
        assertTrue(first.received.isEmpty())
        assertTrue(server.store.single().isEncrypted)
        assertEquals("key-1", server.requests.first().keyId)
        val second = runBlocking { engineB.fullSync(clockB, null) }
        assertEquals(1, second.received.size)
        assertEquals("From A", storeB.db.first("SELECT name FROM payees WHERE id = 'p1'")?.get("name"))
        assertEquals(clockA.merkle.hash, clockB.merkle.hash)
    }

    @Test
    fun writerRollbackRestoresTheClockAndMessageLog() {
        val store = crdtDb()
        try {
            val clock = store.loadClock()
            val timestamp = clock.clock.timestamp
            val merkleHash = clock.merkle.hash
            val writer = ActualWriter(store, clock, { 1_795_254_060_000L }) { "p1" }

            assertFailsWith<IllegalStateException> {
                writer.batch {
                    writer.insert("payees", mapOf("name" to "Unsaved"))
                    error("Operation journal was not saved")
                }
            }
            assertEquals(timestamp, clock.clock.timestamp)
            assertEquals(merkleHash, clock.merkle.hash)
            assertTrue(store.messagesSince(timestamp.toString()).isEmpty())
            assertNull(store.db.first("SELECT id FROM payees WHERE id = 'p1'"))
        } finally {
            store.db.close()
        }
    }

    @Test
    fun receiveOnlySyncNeverUploadsPendingChanges() {
        val server = FakeServer(XorCipher())
        val now = 1_795_254_060_000L
        val store = crdtDb()
        try {
            val clock = store.loadClock()
            val engine = ActualSyncEngine(store, server.transport, "group", "file", XorCipher()) { now }
            store.apply(listOf(CrdtMessage(clock.clock.send(now), "payees", "p1", "name", CrdtValue.Text("Pending"))), clock, false)

            val error =
                assertFailsWith<ActualSyncException> {
                    runBlocking { engine.fullSync(clock, null, receiveOnly = true) }
                }
            assertEquals("out-of-sync", error.reason)
            assertTrue(server.requests.isNotEmpty())
            assertTrue(server.requests.all { it.messages.isEmpty() })
            assertTrue(server.store.isEmpty())

            runBlocking { engine.fullSync(clock, null) }
            assertEquals(1, server.store.size)
        } finally {
            store.db.close()
        }
    }

    @Test
    fun aServerThatNeverAgreesStopsTheSyncEvenWhileSendingNewMessages() {
        val now = 1_000_000_000L
        val store = crdtDb()
        try {
            val clock = store.loadClock()
            var rounds = 0
            val error =
                assertFailsWith<ActualSyncException> {
                    runBlocking {
                        ActualSyncEngine(store, { _: SyncRequest ->
                            check(++rounds < 200) { "sync never gave up" }
                            val message =
                                MessageEnvelope(
                                    ActualTimestamp(now + rounds, 0, "0000000000000002").toString(),
                                    false,
                                    SyncProtocol.encode(ActualMessage("payees", "p$rounds", "name", "S:x")),
                                )
                            SyncResponse(listOf(message), """{"hash":5,"0":{"hash":5}}""")
                        }, "g", "f") { now }.fullSync(clock, "x")
                    }
                }
            assertEquals("out-of-sync", error.reason)
            assertTrue(rounds <= 101)
        } finally {
            store.db.close()
        }
    }

    @Test
    fun syncReportsActualErrorReasons() {
        val now = 1_000_000_000L
        val store = crdtDb()
        val clock = store.loadClock()

        fun reason(
            cipher: ActualMessageCipher? = null,
            response: (SyncRequest) -> SyncResponse,
        ) = assertFailsWith<ActualSyncException> {
            runBlocking { ActualSyncEngine(store, response::invoke, "g", "f", cipher) { now }.fullSync(clock, "x") }
        }.reason
        val plain =
            MessageEnvelope(
                "1970-01-01T00:00:00.000Z-0000-0000000000000002",
                false,
                SyncProtocol.encode(ActualMessage("payees", "p9", "name", "S:x")),
            )
        val encrypted =
            MessageEnvelope(
                "1970-01-01T00:00:00.000Z-0000-0000000000000003",
                true,
                SyncProtocol.encode(EncryptedData(byteArrayOf(), byteArrayOf(), byteArrayOf())),
            )
        assertEquals("invalid-merkle", reason { SyncResponse(emptyList(), "nope") })
        assertEquals("decrypt-failure", reason { SyncResponse(listOf(encrypted), "{}") })
        val failing =
            object : ActualMessageCipher {
                override val keyId = "k"

                override fun encrypt(plaintext: ByteArray): EncryptedData = error("no key")

                override fun decrypt(data: EncryptedData): ByteArray = error("no key")
            }
        assertEquals("decrypt-failure", reason(failing) { SyncResponse(listOf(encrypted), "{}") })
        assertEquals("invalid-timestamp", reason { SyncResponse(listOf(plain.copy(timestamp = "bad")), "{}") })
        assertEquals(
            "clock-drift",
            reason {
                SyncResponse(listOf(plain.copy(timestamp = ActualTimestamp(now + 3_600_000, 0, "2").toString())), "{}")
            },
        )
        assertEquals("out-of-sync", reason { SyncResponse(emptyList(), """{"hash":5,"0":{"hash":5}}""") })
        store.apply(listOf(CrdtMessage(clock.clock.send(now), "payees", "p1", "name", CrdtValue.Text("A"))), clock, false)
        assertEquals("encrypt-failure", reason(failing) { SyncResponse(emptyList(), "{}") })
    }
}
