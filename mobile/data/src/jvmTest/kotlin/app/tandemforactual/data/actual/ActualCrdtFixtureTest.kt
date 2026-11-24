package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

class ActualCrdtFixtureTest {
    private val fixtures: JsonObject =
        Json
            .parseToJsonElement(
                checkNotNull(javaClass.getResource("/actual/crdt-fixtures.json")).readText(),
            ).jsonObject

    private fun list(name: String): List<JsonObject> = fixtures.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

    @Test
    fun timestampsParseFormatAndHashLikeActual() {
        list("parsing").forEach { case ->
            val timestamp = checkNotNull(ActualTimestamp.parse(case.str("input")))
            assertEquals(case.getValue("millis").jsonPrimitive.long, timestamp.millis)
            assertEquals(case.getValue("counter").jsonPrimitive.int, timestamp.counter)
            assertEquals(case.str("node"), timestamp.node)
            assertEquals(case.str("string"), timestamp.toString())
            assertEquals(case.getValue("hash").jsonPrimitive.long, timestamp.hash())
        }
        list("invalid").forEach { case ->
            assertEquals(case.getValue("valid").jsonPrimitive.boolean, ActualTimestamp.parse(case.str("input")) != null, case.str("input"))
        }
    }

    @Test
    fun clockSendAndReceiveMatchActual() {
        val clock = HybridLogicalClock(ActualTimestamp(0, 0, "A219E7A71CC18912"))
        list("clock").forEach { step ->
            val phys = step.getValue("phys").jsonPrimitive.long
            val result =
                if (step.str("kind") == "send") {
                    clock.send(phys)
                } else {
                    clock.receive(checkNotNull(ActualTimestamp.parse(step.str("remote"))), phys)
                }
            assertEquals(step.str("result"), result.toString())
        }
    }

    @Test
    fun merkleTriesMatchActual() {
        val tries =
            list("tries").map { case ->
                val trie =
                    case.getValue("timestamps").jsonArray.fold(MerkleTrie()) { trie, value ->
                        trie.insert(checkNotNull(ActualTimestamp.parse(value.jsonPrimitive.content)))
                    }
                assertEquals(case.getValue("trie"), trie.toJson())
                assertEquals(case.getValue("pruned"), trie.prune().toJson())
                assertEquals(trie, MerkleTrie.fromJson(case.getValue("trie").toString()))
                trie
            }
        list("diffs").forEach { case ->
            val first = tries[case.getValue("a").jsonPrimitive.int]
            val b = case.getValue("b").jsonPrimitive
            val second = if (b.isString) tries[1].prune() else tries[b.int]
            assertEquals(case.getValue("result").jsonPrimitive.longOrNull, MerkleTrie.diff(first, second))
        }
        list("keyToTimestamp").forEach { case ->
            assertEquals(case.getValue("millis").jsonPrimitive.long, MerkleTrie.keyToTimestamp(case.str("key")))
        }
    }

    @Test
    fun protobufBytesMatchActual() {
        val proto = fixtures.getValue("proto").jsonObject

        fun bytes(name: String) =
            proto
                .getValue(name)
                .jsonPrimitive.content
                .hexToByteArray()

        val message = ActualMessage("transactions", "tx-1", "amount", "N:-5000")
        assertEquals(bytes("message").toList(), SyncProtocol.encode(message).toList())
        assertEquals(message, SyncProtocol.decodeMessage(bytes("message")))
        val unicode = ActualMessage("payees", "p-1", "name", "S:Café ☕")
        assertEquals(bytes("messageUnicode").toList(), SyncProtocol.encode(unicode).toList())

        val encrypted = EncryptedData(byteArrayOf(1, 2, 3), byteArrayOf(4, 5), byteArrayOf(6, 7, 8, 9))
        assertEquals(bytes("encrypted").toList(), SyncProtocol.encode(encrypted).toList())
        assertEquals(encrypted, SyncProtocol.decodeEncryptedData(bytes("encrypted")))

        val timestamps = list("parsing").map { it.str("input") }
        val envelope = MessageEnvelope(timestamps[1], false, SyncProtocol.encode(message))
        assertEquals(bytes("envelope").toList(), SyncProtocol.encode(envelope).toList())
        assertEquals(envelope, SyncProtocol.decodeEnvelope(bytes("envelope")))

        val request =
            SyncRequest(
                messages = listOf(envelope, MessageEnvelope(timestamps[2], true, SyncProtocol.encode(encrypted))),
                fileId = "file-1",
                groupId = "group-1",
                keyId = "key-1",
                since = "2026-11-20T00:00:00.000Z-0000-0000000000000000",
            )
        assertEquals(bytes("request").toList(), SyncProtocol.encode(request).toList())
        assertEquals(request, SyncProtocol.decodeRequest(bytes("request")))
        assertEquals(bytes("emptyRequest").toList(), SyncProtocol.encode(SyncRequest()).toList())

        val pruned = list("tries")[0].getValue("pruned")
        val response = SyncProtocol.decodeResponse(bytes("response"))
        assertEquals(listOf(envelope), response.messages)
        assertEquals(pruned, Json.parseToJsonElement(response.merkle))
        assertEquals(bytes("response").toList(), SyncProtocol.encode(response).toList())
    }

    @Test
    fun emptyTriesAndMissingHashesStayEmpty() {
        assertEquals(MerkleTrie(), MerkleTrie().prune())
        assertEquals(MerkleTrie(), MerkleTrie.fromJson("{}"))
        assertEquals(MerkleTrie(), MerkleTrie.fromJson(JsonObject(mapOf("hash" to JsonArray(emptyList()), "x" to JsonPrimitive(1)))))
        assertNull(MerkleTrie.diff(MerkleTrie(), MerkleTrie()))
    }
}
