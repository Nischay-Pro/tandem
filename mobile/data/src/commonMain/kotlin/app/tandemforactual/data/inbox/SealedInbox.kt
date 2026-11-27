package app.tandemforactual.data.inbox

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A budget's inbox on this device: one line per sealed entry or withdrawal, each appended whole. */
interface InboxFile {
    suspend fun append(line: String)

    suspend fun lines(): List<String>

    /** Empties the file once everything in it has been handled. */
    suspend fun clear()
}

/** What opening the inbox did: entries added, and entries that couldn't be opened and were dropped. */
data class InboxDrain(
    val applied: Int,
    val unreadable: Int,
)

/**
 * Automation writes for a locked budget, sealed to its inbox key. Anyone can add an entry or take one back by its id;
 * only the unlocked vault's keyring can read them. Every call is serialized, so appends never interleave with a drain.
 */
@OptIn(ExperimentalEncodingApi::class)
class SealedInbox(
    private val file: InboxFile,
    private val budgetId: String,
) {
    suspend fun seal(
        publicKey: ByteArray,
        payload: ByteArray,
    ): String =
        lock.withLock {
            val entryId = SealedBox.newEntryId()
            file.append(ENTRY + Base64.encode(SealedBox.seal(publicKey, budgetId, entryId, payload)))
            entryId
        }

    /** Takes back [entryId] before it's added; false when it isn't waiting any more. */
    suspend fun withdraw(entryId: String): Boolean =
        lock.withLock {
            val lines = file.lines()
            val waiting = lines.mapNotNull(::headerOf).any { it.entryId == entryId }
            if (!waiting || WITHDRAWN + entryId in lines) return@withLock false
            file.append(WITHDRAWN + entryId)
            true
        }

    /**
     * Opens every waiting entry with [keyring] and hands it to [apply], oldest first, each exactly once: an entry is
     * marked only after [apply] returns, and [apply] must be idempotent by entry id, so a crash in between can't add it
     * twice. If [apply] throws, the inbox is left as it is for the next unlock.
     */
    suspend fun drain(
        keyring: InboxKeyring,
        apply: suspend (OpenedEntry) -> Unit,
    ): InboxDrain =
        lock.withLock {
            val lines = file.lines()
            val withdrawn = lines.filter { it.startsWith(WITHDRAWN) }.map { it.removePrefix(WITHDRAWN) }.toSet()
            val keys = keyring.keys().associateBy { it.keyId }
            var applied = 0
            var unreadable = 0
            lines.filter { it.startsWith(ENTRY) }.forEach { line ->
                val sealed = decode(line)
                val header = sealed?.let(SealedBox::header)
                if (header != null && (header.entryId in withdrawn || keyring.isApplied(header.entryId))) return@forEach
                val opened = sealed?.let { open(keys[header?.keyId], it) }
                if (opened == null) {
                    unreadable++
                } else {
                    apply(opened)
                    keyring.markApplied(opened.entryId)
                    applied++
                }
            }
            file.clear()
            InboxDrain(applied, unreadable)
        }

    private fun open(
        pair: InboxKeyPair?,
        sealed: ByteArray,
    ): OpenedEntry? = pair?.let { runCatching { SealedBox.open(it, budgetId, sealed) }.getOrNull() }

    private fun headerOf(line: String): SealedHeader? = decode(line)?.let(SealedBox::header)

    private fun decode(line: String): ByteArray? =
        line.takeIf { it.startsWith(ENTRY) }?.let { runCatching { Base64.decode(it.removePrefix(ENTRY)) }.getOrNull() }
}

private const val ENTRY = "e "
private const val WITHDRAWN = "x "
private val lock = Mutex()
