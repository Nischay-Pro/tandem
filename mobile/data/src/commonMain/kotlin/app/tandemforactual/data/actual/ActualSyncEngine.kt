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
            val currentTime = clock.clock.timestamp.toString()
            val outgoing = if (receiveOnly) emptyList() else store.messagesSince(since)
            val response =
                transport.sync(
                    SyncRequest(
                        messages = outgoing.map(::envelope),
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
            val localTimeChanged = clock.clock.timestamp.toString() != currentTime
            val incoming = response.messages.map(::decode)
            if (incoming.isNotEmpty()) {
                receive(clock, incoming)
                received += store.apply(incoming, clock, deferUnknownSchema = true)
            }
            val diff = MerkleTrie.diff(serverMerkle, clock.merkle) ?: return ActualSyncResult(received, clock.clock.timestamp.toString())
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
