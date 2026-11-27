package app.tandemforactual.data.inbox

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalEncodingApi::class)
class SealedInboxTest {
    private val vault = database()
    private val main = database()
    private val file = MemoryInboxFile()
    private val inbox = SealedInbox(file, BUDGET)
    private val keyring = InboxKeyring(vault, BUDGET)

    @AfterTest
    fun close() {
        vault.close()
        main.close()
    }

    @Test
    fun lockedWritesApplyExactlyOnceAfterUnlock() =
        runBlocking {
            val pair = keyring.ensure(PASSWORD)
            InboxPublicKeys.publish(main, BUDGET, pair)
            val publicKey = checkNotNull(InboxPublicKeys.read(main, BUDGET))
            val first = inbox.seal(publicKey, "first".encodeToByteArray())
            val second = inbox.seal(publicKey, "second".encodeToByteArray())
            assertFalse(file.lines.joinToString().contains("first"))

            val seen = mutableListOf<String>()
            assertEquals(InboxDrain(2, 0), inbox.drain(keyring) { seen += it.payload.decodeToString() })
            assertEquals(listOf("first", "second"), seen)
            assertTrue(keyring.isApplied(first) && keyring.isApplied(second))
            assertEquals(InboxDrain(0, 0), inbox.drain(keyring) { seen += "again" })
            assertEquals(2, seen.size)
        }

    @Test
    fun aKillBetweenApplyAndMarkHandsTheSameEntryBackOnce() =
        runBlocking {
            val entry = inbox.seal(keyring.ensure(PASSWORD).publicKey, "kept".encodeToByteArray())
            assertFailsWith<IllegalStateException> { inbox.drain(keyring) { error("killed") } }
            assertEquals(1, file.lines.size)

            val delivered = mutableListOf<String>()
            inbox.drain(keyring) { delivered += it.entryId }
            assertEquals(listOf(entry), delivered)

            val marked = inbox.seal(keyring.ensure(PASSWORD).publicKey, "marked".encodeToByteArray())
            keyring.markApplied(marked)
            assertEquals(InboxDrain(0, 0), inbox.drain(keyring) { delivered += it.entryId })
            assertEquals(listOf(entry), delivered)
            assertTrue(file.lines.isEmpty())
        }

    @Test
    fun undoTakesAnEntryBackBeforeItsApplied() =
        runBlocking {
            val publicKey = keyring.ensure(PASSWORD).publicKey
            val undone = inbox.seal(publicKey, "undone".encodeToByteArray())
            val kept = inbox.seal(publicKey, "kept".encodeToByteArray())

            assertTrue(inbox.withdraw(undone))
            assertFalse(inbox.withdraw(undone))
            assertFalse(inbox.withdraw(SealedBox.newEntryId()))
            val seen = mutableListOf<String>()
            assertEquals(InboxDrain(1, 0), inbox.drain(keyring) { seen += it.entryId })
            assertEquals(listOf(kept), seen)
            assertFalse(inbox.withdraw(kept))
        }

    @Test
    fun entriesSealedBeforeAPasswordChangeStillOpenFirst() =
        runBlocking {
            val before = keyring.ensure(PASSWORD)
            assertEquals(before.keyId, keyring.ensure(PASSWORD).keyId)
            inbox.seal(before.publicKey, "old".encodeToByteArray())
            val after = keyring.ensure("new password")
            assertNotEquals(before.keyId, after.keyId)
            inbox.seal(after.publicKey, "new".encodeToByteArray())

            val seen = mutableListOf<String>()
            inbox.drain(keyring) { seen += it.payload.decodeToString() }
            assertEquals(listOf("old", "new"), seen)
            repeat(5) { keyring.ensure("password $it") }
            assertEquals(4, keyring.keys().size)
            assertFalse(before.keyId in keyring.keys().map { it.keyId })
        }

    @Test
    fun tamperedAndForeignEntriesAreDroppedUnread() =
        runBlocking {
            val publicKey = keyring.ensure(PASSWORD).publicKey
            inbox.seal(publicKey, "good".encodeToByteArray())
            inbox.seal(publicKey, "bad".encodeToByteArray())
            val sealed = Base64.decode(file.lines.removeAt(file.lines.lastIndex).removePrefix("e "))
            sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
            file.append("e " + Base64.encode(sealed))
            file.append("e not base64!")
            file.append("e " + Base64.encode(byteArrayOf(9, 9, 9)))
            inbox.seal(SealedBox.newKeyPair().publicKey, "foreign".encodeToByteArray())
            file.append("something else")

            val seen = mutableListOf<String>()
            assertEquals(InboxDrain(1, 4), inbox.drain(keyring) { seen += it.payload.decodeToString() })
            assertEquals(listOf("good"), seen)
        }

    @Test
    fun publicKeysAreKeptInTheClearAndForgotten() =
        runBlocking {
            assertNull(InboxPublicKeys.read(main, BUDGET))
            val pair = keyring.ensure(PASSWORD)
            InboxPublicKeys.publish(main, BUDGET, pair)
            InboxPublicKeys.publish(main, BUDGET, pair)
            assertContentEquals(pair.publicKey, InboxPublicKeys.read(main, BUDGET))
            assertFalse(AppPreferencesStore(main).read().values.any { it.contains(Base64.encode(pair.privateKey)) })
            InboxPublicKeys.forget(main, BUDGET)
            assertNull(InboxPublicKeys.read(main, BUDGET))
            AppPreferencesStore(main).putValue("internal:inbox-public-key-v1:$BUDGET", "%%%")
            assertNull(InboxPublicKeys.read(main, BUDGET))
            InboxPublicKeys.publish(main, BUDGET, pair)
            InboxPublicKeys.publish(main, "budget-2", pair)
            InboxPublicKeys.forget(main, "budget-3")
            InboxPublicKeys.forgetAll(main)
            assertNull(InboxPublicKeys.read(main, BUDGET))
            assertNull(InboxPublicKeys.read(main, "budget-2"))
        }

    @Test
    fun damagedKeyringRecordsAreIgnored() =
        runBlocking {
            val store = AppPreferencesStore(vault)
            store.putValue("internal:inbox-keys-v1:$BUDGET", "not json")
            assertTrue(keyring.keys().isEmpty())
            val key = Base64.encode(SealedBox.newKeyPair().publicKey)
            store.putValue(
                "internal:inbox-keys-v1:$BUDGET",
                """[1,{"public":"%%"},{"public":"$key"},{"public":"$key","private":"$key"}]""",
            )
            assertTrue(keyring.keys().isEmpty())
            store.putValue("internal:inbox-applied-v1:$BUDGET", """[{"id":1}]""")
            assertFalse(keyring.isApplied("1"))
        }

    private class MemoryInboxFile : InboxFile {
        val lines = mutableListOf<String>()

        override suspend fun append(line: String) {
            lines += line
        }

        override suspend fun lines(): List<String> = lines.toList()

        override suspend fun clear() {
            lines.clear()
        }
    }

    private companion object {
        const val BUDGET = "budget-1"
        const val PASSWORD = "correct horse"

        fun database(): TandemDatabase =
            openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
    }
}
