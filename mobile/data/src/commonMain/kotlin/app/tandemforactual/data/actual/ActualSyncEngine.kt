package app.tandemforactual.data.actual

/** Sends a sync request to an Actual server's `/sync/sync` endpoint. */
fun interface ActualSyncTransport {
    suspend fun sync(request: SyncRequest): SyncResponse
}

/** End-to-end encryption for message contents, keyed by Actual's budget key id. */
interface ActualMessageCipher {
    val keyId: String

    fun encrypt(plaintext: ByteArray): EncryptedData

    fun decrypt(data: EncryptedData): ByteArray
}

/** Guards the local database between network calls, so a sync never holds it while waiting on the server. */
interface ActualStoreGuard {
    suspend fun <T> locked(block: suspend () -> T): T

    object None : ActualStoreGuard {
        override suspend fun <T> locked(block: suspend () -> T): T = block()
    }
}

class ActualSyncException(
    val reason: String,
    message: String = reason,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

data class ActualSyncResult(
    val received: List<CrdtMessage>,
    val lastSyncedTimestamp: String,
)

/**
 * Exchanges messages with an Actual server until both Merkle tries agree, following Actual's own `fullSync`: send
 * local messages since the last sync, apply what comes back, then walk back to wherever the tries still differ.
 */
class ActualSyncEngine(
    private val store: ActualMessageStore,
    private val transport: ActualSyncTransport,
    private val groupId: String,
    private val fileId: String,
    private val cipher: ActualMessageCipher? = null,
    private val maxUploadBytes: Int = MAX_UPLOAD_BYTES,
    private val guard: ActualStoreGuard = ActualStoreGuard.None,
    private val now: () -> Long,
) {
    suspend fun fullSync(
        clock: StoredClock,
        lastSyncedTimestamp: String?,
        receiveOnly: Boolean = false,
    ): ActualSyncResult {
        val received = mutableListOf<CrdtMessage>()
        var since = lastSyncedTimestamp ?: ActualTimestamp(now() - 5 * 60 * 1000, 0, "0").toString()
        var count = 0
        var prevDiff: Long? = null
        while (true) {
            val (currentTime, local) =
                guard.locked { clock.clock.timestamp.toString() to if (receiveOnly) emptyList() else store.messagesSince(since) }
            val outgoing = uploadAllButLast(local.map(::envelope), since)
            val response =
                transport.sync(
                    SyncRequest(
                        messages = outgoing,
                        fileId = fileId,
                        groupId = groupId,
                        keyId = cipher?.keyId.orEmpty(),
                        since = since,
                    ),
                )
            val serverMerkle =
                runCatching {
                    MerkleTrie.fromJson(response.merkle)
                }.getOrElse { throw ActualSyncException("invalid-merkle", cause = it) }
            val incoming = response.messages.map(::decode)
            var localTimeChanged = false
            var syncedTimestamp = ""
            val diff =
                guard.locked {
                    localTimeChanged = clock.clock.timestamp.toString() != currentTime
                    if (incoming.isNotEmpty()) {
                        receive(clock, incoming)
                        received += store.apply(incoming, clock, deferUnknownSchema = true)
                    }
                    syncedTimestamp = clock.clock.timestamp.toString()
                    MerkleTrie.diff(serverMerkle, clock.merkle)
                } ?: return ActualSyncResult(received, syncedTimestamp)
            if ((count >= 10 && diff == prevDiff) || count >= 100) throw ActualSyncException("out-of-sync")
            since = ActualTimestamp(diff, 0, "0").toString()
            count = if (localTimeChanged) 0 else count + 1
            prevDiff = diff
        }
    }

    private fun receive(
        clock: StoredClock,
        messages: List<CrdtMessage>,
    ) {
        val latest = messages.maxWith(compareBy<CrdtMessage> { it.timestamp.millis }.thenBy { it.timestamp.counter }).timestamp
        try {
            clock.clock.receive(latest, now())
        } catch (error: ClockDriftException) {
            throw ActualSyncException("clock-drift", cause = error)
        }
    }

    /**
     * Sends [envelopes] in requests of at most [maxUploadBytes], as Actual's own client does, so a large upload stays
     * under the server's request limit. Returns the last chunk, which the sync loop sends with its usual request; the
     * server repeats everything since [since] in that reply, so the replies to earlier chunks can be skipped.
     */
    private suspend fun uploadAllButLast(
        envelopes: List<MessageEnvelope>,
        since: String,
    ): List<MessageEnvelope> {
        val chunks = mutableListOf(mutableListOf<MessageEnvelope>())
        var size = 0
        for (envelope in envelopes) {
            val bytes = envelope.content.size + envelope.timestamp.length + ENVELOPE_OVERHEAD_BYTES
            if (size + bytes > maxUploadBytes && chunks.last().isNotEmpty()) {
                chunks += mutableListOf<MessageEnvelope>()
                size = 0
            }
            chunks.last() += envelope
            size += bytes
        }
        for (chunk in chunks.dropLast(1)) {
            transport.sync(SyncRequest(chunk, fileId, groupId, cipher?.keyId.orEmpty(), since))
        }
        return chunks.last()
    }

    private fun envelope(message: CrdtMessage): MessageEnvelope {
        val binary = SyncProtocol.encode(ActualMessage(message.dataset, message.row, message.column, message.value.serialize()))
        val active = cipher ?: return MessageEnvelope(message.timestamp.toString(), false, binary)
        return run {
            val encrypted = runCatching { active.encrypt(binary) }.getOrElse { throw ActualSyncException("encrypt-failure", cause = it) }
            MessageEnvelope(message.timestamp.toString(), true, SyncProtocol.encode(encrypted))
        }
    }

    private fun decode(envelope: MessageEnvelope): CrdtMessage {
        val bytes =
            if (envelope.isEncrypted) {
                val active = cipher ?: throw ActualSyncException("decrypt-failure", "missing-key")
                runCatching { active.decrypt(SyncProtocol.decodeEncryptedData(envelope.content)) }
                    .getOrElse { throw ActualSyncException("decrypt-failure", cause = it) }
            } else {
                envelope.content
            }
        val message = SyncProtocol.decodeMessage(bytes)
        val timestamp = ActualTimestamp.parse(envelope.timestamp) ?: throw ActualSyncException("invalid-timestamp")
        return CrdtMessage(timestamp, message.dataset, message.row, message.column, CrdtValue.deserialize(message.value))
    }
}

private const val MAX_UPLOAD_BYTES = 4 * 1024 * 1024
private const val ENVELOPE_OVERHEAD_BYTES = 16
