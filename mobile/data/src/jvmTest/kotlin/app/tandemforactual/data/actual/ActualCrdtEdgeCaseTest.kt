package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActualCrdtEdgeCaseTest {
    @Test
    fun clockRejectsDriftAndOverflow() {
        val node = "0000000000000001"
        val drift = 1000L
        assertFailsWith<ClockDriftException> { HybridLogicalClock(ActualTimestamp(5000, 0, node), drift).send(0) }
        assertFailsWith<ClockDriftException> {
            HybridLogicalClock(
                ActualTimestamp(0, 0, node),
                drift,
            ).receive(ActualTimestamp(5000, 0, node), 0)
        }
        assertFailsWith<ClockDriftException> {
            HybridLogicalClock(
                ActualTimestamp(5000, 0, node),
                drift,
            ).receive(ActualTimestamp(0, 0, node), 0)
        }
        val full = ActualTimestamp(10, ActualTimestamp.MAX_COUNTER, node)
        assertFailsWith<ClockOverflowException> { HybridLogicalClock(full).send(10) }
        assertFailsWith<ClockOverflowException> { HybridLogicalClock(full).receive(full, 10) }
        assertEquals(
            ActualTimestamp(20, 0, node),
            HybridLogicalClock(ActualTimestamp(10, 3, node)).receive(ActualTimestamp(15, 9, node), 20),
        )
    }

    @Test
    fun timestampParsingRejectsMalformedParts() {
        listOf(
            "2026-13-01T00:00:00.000Z-0000-0000000000000000",
            "2026-02-30T00:00:00.000Z-0000-0000000000000000",
            "2025-02-29T00:00:00.000Z-0000-0000000000000000",
            "2026-04-31T00:00:00.000Z-0000-0000000000000000",
            "2026-01-01T24:00:00.000Z-0000-0000000000000000",
            "2026-01-01T00:60:00.000Z-0000-0000000000000000",
            "2026-01-01T00:00:60.000Z-0000-0000000000000000",
            "2026-01-01T00:00:00Z-0000-0000000000000000",
            "2026-01-01T00:00:00.000Z-zzzz-0000000000000000",
            "2026-01-01T00:00:00.000Z-FFFFFFFFFFFF-0000000000000000",
        ).forEach { assertNull(ActualTimestamp.parse(it), it) }
        assertEquals(1, ActualTimestamp.parse("2026-01-01T00:00:00.000Z-1x-0000000000000000")?.counter)
        assertEquals(
            "2000-02-29T12:00:00.000Z-0000-0000000000000000",
            ActualTimestamp.parse("2000-02-29T12:00:00.000Z-0000-0000000000000000").toString(),
        )
        assertEquals(
            "1900-03-01T00:00:00.000Z-0000-0000000000000000",
            ActualTimestamp(-2_203_891_200_000, 0, "0").toString(),
        )
        assertEquals("2026-11-21T00:00:00.000Z-0000-0000000000000000", ActualTimestamp.since("2026-11-21T00:00:00.000Z"))
        assertEquals("1970-01-01T00:00:00.000Z-0000-0000000000000000", ActualTimestamp.zero.toString())
        assertTrue(ActualTimestamp(1, 0, "a") < ActualTimestamp(2, 0, "a"))
    }

    @Test
    fun murmurHashCoversEveryTailLength() {
        assertEquals(0L, murmurHash3(""))
        assertEquals(1009084850L, murmurHash3("a"))
        assertEquals(2613040991L, murmurHash3("ab"))
        assertEquals(3017643002L, murmurHash3("abc"))
        assertEquals(1139631978L, murmurHash3("abcd"))
    }

    @Test
    fun decodersSkipUnknownFieldsOfEveryWireType() {
        val unknown =
            byteArrayOf(
                (9 shl 3).toByte(),
                1,
                ((10 shl 3) or 1).toByte(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                ((11 shl 3) or 2).toByte(),
                1,
                7,
                ((12 shl 3) or 5).toByte(),
                0,
                0,
                0,
                0,
            )
        assertEquals(ActualMessage("", "", "", ""), SyncProtocol.decodeMessage(unknown))
        assertEquals(MessageEnvelope("", false, ByteArray(0)), SyncProtocol.decodeEnvelope(unknown))
        assertEquals(EncryptedData(ByteArray(0), ByteArray(0), ByteArray(0)), SyncProtocol.decodeEncryptedData(unknown))
        assertEquals(SyncRequest(), SyncProtocol.decodeRequest(unknown))
        assertEquals(SyncResponse(emptyList(), ""), SyncProtocol.decodeResponse(unknown))
    }

    @Test
    fun decodersRejectMalformedBytes() {
        listOf(
            byteArrayOf(0x80.toByte()),
            ByteArray(11) { 0x80.toByte() },
            byteArrayOf(((1 shl 3) or 0).toByte(), 1),
            byteArrayOf(((1 shl 3) or 2).toByte(), 5, 1),
            byteArrayOf(((9 shl 3) or 3).toByte()),
            byteArrayOf(((9 shl 3) or 1).toByte(), 1),
        ).forEach { bytes -> assertFailsWith<ProtocolException> { SyncProtocol.decodeMessage(bytes) } }
    }

    @Test
    fun byteHoldersCompareByContent() {
        val envelope = MessageEnvelope("t", true, byteArrayOf(1))
        assertEquals(envelope.hashCode(), MessageEnvelope("t", true, byteArrayOf(1)).hashCode())
        assertNotEquals<Any>(envelope, "t")
        assertNotEquals(envelope, MessageEnvelope("t", false, byteArrayOf(1)))
        val data = EncryptedData(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
        assertEquals(data.hashCode(), EncryptedData(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)).hashCode())
        assertNotEquals<Any>(data, "d")
        assertNotEquals(data, EncryptedData(byteArrayOf(1), byteArrayOf(2), byteArrayOf(4)))
    }
}
