package app.tandemforactual.data.planning

/** A sealed record as extension sync stores it. */
data class RemotePlanningRecord(
    val recordId: String,
    val recordType: String,
    val schemaVersion: Int,
    val revision: Long,
    val deleted: Boolean,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val seq: Long,
)

sealed interface RemoteWrite {
    data class Stored(
        val seq: Long,
    ) : RemoteWrite

    data class Conflict(
        val current: RemotePlanningRecord?,
    ) : RemoteWrite
}

data class RemotePlanningPage(
    val records: List<RemotePlanningRecord>,
    val cursor: Long,
    val hasMore: Boolean,
)

/** The extension sync service for one budget. */
interface PlanningTransport {
    suspend fun key(): WrappedPlanningKey?

    suspend fun putKey(
        key: WrappedPlanningKey,
        expectedKeyId: String?,
    ): Boolean

    suspend fun changes(after: Long): RemotePlanningPage

    suspend fun put(
        recordId: String,
        recordType: String,
        schemaVersion: Int,
        revision: Long,
        baseRevision: Long?,
        deleted: Boolean,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): RemoteWrite
}

data class PlanningSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val cursor: Long,
)

/**
 * Exchanges one budget's sealed planning records with extension sync. Local edits go up as the next server revision; if
 * another device got there first, the edit is re-based on the newest revision so the latest writer wins. The cursor only
 * advances after received records are stored, and a failure leaves local edits pending for the next attempt.
 */
class PlanningSync(
    private val dao: PlanningRecordDao,
    private val budgetId: String,
    private val transport: PlanningTransport,
) {
    /** Agrees on one planning key: shares this device's key if none exists yet, else adopts the shared one. */
    suspend fun agreeOnKey(
        localKey: ByteArray,
        secret: ByteArray,
    ): ByteArray {
        val shared = transport.key()
        if (shared == null) {
            if (transport.putKey(PlanningKeys.wrap(localKey, secret, budgetId), null)) return localKey
            return PlanningKeys.unwrap(checkNotNull(transport.key()), secret, budgetId)
        }
        return PlanningKeys.unwrap(shared, secret, budgetId)
    }

    /** Re-seals every local record under [to], after another device's key has been adopted. */
    suspend fun rekey(
        from: PlanningCipher,
        to: PlanningCipher,
    ) {
        for (record in dao.all(budgetId)) {
            val identity = PlanningCipher.identity(budgetId, record.recordId, record.schemaVersion, record.revision)
            val plaintext = runCatching { from.open(record.iv, record.ciphertext, identity) }.getOrNull() ?: continue
            val (iv, sealed) = to.seal(plaintext, identity)
            dao.upsert(record.copy(iv = iv, ciphertext = sealed))
        }
    }

    suspend fun sync(
        cipher: PlanningCipher,
        cursor: Long,
    ): PlanningSyncResult {
        var uploaded = 0
        for (record in dao.pending(budgetId)) {
            val plaintext =
                cipher.open(
                    record.iv,
                    record.ciphertext,
                    PlanningCipher.identity(budgetId, record.recordId, record.schemaVersion, record.revision),
                )
            var base = record.syncedRevision.takeIf { it > 0 }
            var attempts = 0
            while (attempts++ < 2) {
                val revision = (base ?: 0) + 1
                val (iv, sealed) =
                    cipher.seal(
                        plaintext,
                        PlanningCipher.identity(budgetId, record.recordId, record.schemaVersion, revision),
                    )
                val result =
                    transport.put(
                        record.recordId,
                        record.recordType,
                        record.schemaVersion,
                        revision,
                        base,
                        record.deleted,
                        iv,
                        sealed,
                    )
                if (result is RemoteWrite.Stored) {
                    dao.upsert(
                        record.copy(revision = revision, syncedRevision = revision, iv = iv, ciphertext = sealed, pendingUpload = false),
                    )
                    uploaded++
                    break
                }
                base = (result as RemoteWrite.Conflict).current?.revision
            }
        }
        var after = cursor
        var downloaded = 0
        do {
            val page = transport.changes(after)
            for (remote in page.records) {
                val local = dao.find(budgetId, remote.recordId)
                if (local?.pendingUpload == true || (local != null && local.syncedRevision >= remote.revision)) continue
                dao.upsert(
                    PlanningRecordEntity(
                        budgetId,
                        remote.recordId,
                        remote.recordType,
                        remote.schemaVersion,
                        remote.revision,
                        remote.seq,
                        remote.deleted,
                        remote.iv,
                        remote.ciphertext,
                        pendingUpload = false,
                        syncedRevision = remote.revision,
                    ),
                )
                downloaded++
            }
            after = page.cursor
        } while (page.hasMore)
        return PlanningSyncResult(uploaded, downloaded, after)
    }
}
