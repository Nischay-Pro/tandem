package app.tandemforactual.data.inbox

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SealedBoxTest {
    private val pair = SealedBox.newKeyPair()
    private val entryId = SealedBox.newEntryId()
    private val payload = """{"amount":"-12.40","payee":"Corner Café"}""".encodeToByteArray()

    @Test
    fun opensOnlyWithTheBudgetsPrivateKey() {
        val sealed = SealedBox.seal(pair.publicKey, BUDGET, entryId, payload)

        assertEquals(SealedHeader(pair.keyId, entryId), SealedBox.header(sealed))
        val opened = SealedBox.open(pair, BUDGET, sealed)
        assertEquals(entryId, opened.entryId)
        assertContentEquals(payload, opened.payload)
        assertFalse(sealed.decodeToString().contains("Corner"))
        assertFailsWith<SealedEntryException> { SealedBox.open(SealedBox.newKeyPair(), BUDGET, sealed) }
        assertFailsWith<SealedEntryException> { SealedBox.open(pair, "other-budget", sealed) }
    }

    @Test
    fun everyEntryHasItsOwnEphemeralKeyAndNonce() {
        val first = SealedBox.seal(pair.publicKey, BUDGET, entryId, payload)
        val second = SealedBox.seal(pair.publicKey, BUDGET, entryId, payload)

        assertContentEquals(first.copyOf(33), second.copyOf(33))
        assertNotEquals(first.copyOfRange(33, 98).toList(), second.copyOfRange(33, 98).toList())
        assertNotEquals(first.copyOfRange(98, 110).toList(), second.copyOfRange(98, 110).toList())
        assertEquals(pair.keyId, SealedBox.keyId(pair.publicKey))
        assertEquals(32, SealedBox.newEntryId().length)
    }

    @Test
    fun rejectsTamperedOrForeignEntries() {
        val sealed = SealedBox.seal(pair.publicKey, BUDGET, entryId, payload)

        listOf(0, 1, 17, 40, 100, 120, sealed.size - 1).forEach { index ->
            val tampered = sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertFailsWith<SealedEntryException> { SealedBox.open(pair, BUDGET, tampered) }
        }
        assertNull(SealedBox.header(sealed.copyOf(100)))
        assertNull(SealedBox.header(byteArrayOf(2) + sealed.copyOfRange(1, sealed.size)))
        val movedEntry = sealed.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertNotEquals(entryId, SealedBox.header(movedEntry)?.entryId)
        assertFailsWith<SealedEntryException> { SealedBox.open(pair, BUDGET, movedEntry) }
    }

    @Test
    fun refusesEntryIdsThatArentSixteenHexBytes() {
        assertFailsWith<IllegalArgumentException> { SealedBox.seal(pair.publicKey, BUDGET, "not-an-id", payload) }
        assertFailsWith<IllegalArgumentException> { SealedBox.seal(pair.publicKey, BUDGET, "G".repeat(32), payload) }
    }

    private companion object {
        const val BUDGET = "budget-1"
    }
}
