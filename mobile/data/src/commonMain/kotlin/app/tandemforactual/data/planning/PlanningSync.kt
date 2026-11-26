package app.tandemforactual.data.planning

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
    val restored: Boolean = false,
)

/** The extension sync service for one budget. */
interface PlanningTransport {
    suspend fun key(): WrappedPlanningKey?

    suspend fun putKey(
        key: WrappedPlanningKey,
        expectedKeyId: String?,
    ): Boolean

    suspend fun changes(
        after: Long,
        anchor: String? = null,
    ): RemotePlanningPage

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
    val anchor: String? = null,
)

/**
 * Exchanges one budget's sealed planning records with extension sync. Local edits go up as the next server revision; if
 * another device got there first, the edit is re-based on the newest revision so the latest writer wins. A record marked
 * private stays on this device; one that was shared before goes up as a deletion so other devices drop it. The cursor only
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

    /**
     * Replaces the shared planning key, as after losing a device: queues every record to upload again, swaps the wrapped key
     * only if no other device changed it first, then re-seals local records under [to]. Devices that don't know [newSecret]
     * can no longer unwrap the key. Returns false when another device's key change won.
     */
    suspend fun rotate(
        from: ByteArray,
        to: ByteArray,
        newSecret: ByteArray,
    ): Boolean {
        for (record in dao.all(budgetId)) dao.upsert(record.copy(pendingUpload = true))
        if (!transport.putKey(PlanningKeys.wrap(to, newSecret, budgetId), transport.key()?.keyId)) return false
        rekey(PlanningCipher(from), PlanningCipher(to))
        return true
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

    /** [anchor] is the record received at [cursor]; both come from the previous result. */
    suspend fun sync(
        cipher: PlanningCipher,
        cursor: Long,
        anchor: String? = null,
    ): PlanningSyncResult {
        var uploaded = uploadPending(cipher)
        val first = transport.changes(cursor, anchor)
        if (!first.restored) return downloadChanges(first, anchor).copy(uploaded = uploaded)
        val result = recoverFromRestore()
        uploaded += uploadPending(cipher)
        return result.copy(uploaded = uploaded)
    }

    /**
     * The service was restored from a backup older than this device's cursor. Downloads everything again, keeps local
     * records newer than the restored copies and queues them, and anything the service lost, to upload again.
     */
    private suspend fun recoverFromRestore(): PlanningSyncResult {
        val seen = mutableSetOf<String>()
        var after = 0L
        var anchor: String? = null
        var downloaded = 0
        do {
            val page = transport.changes(after)
            for (remote in page.records) {
                seen += remote.recordId
                val local = dao.find(budgetId, remote.recordId)
                when {
                    local?.pendingUpload == true || local?.syncedRevision == remote.revision -> Unit
                    local != null && local.syncedRevision > remote.revision -> dao.upsert(local.copy(pendingUpload = true))
                    else -> {
                        dao.upsert(remote.toEntity())
                        downloaded++
                    }
                }
            }
            after = page.cursor
            anchor = page.records.lastOrNull()?.recordId ?: anchor
        } while (page.hasMore)
        dao.all(budgetId).filter { it.recordId !in seen && !it.pendingUpload }.forEach {
            dao.upsert(it.copy(pendingUpload = true, syncedRevision = 0))
        }
        return PlanningSyncResult(0, downloaded, after, anchor)
    }

    /**
     * Uploads each pending record on its own, so one the service refuses stays queued for next time instead of stopping
     * the rest, and the download that follows still runs.
     */
    private suspend fun uploadPending(cipher: PlanningCipher): Int {
        var uploaded = 0
        for (record in dao.pending(budgetId)) {
            try {
                if (uploadRecord(cipher, record)) uploaded++
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                continue
            }
        }
        return uploaded
    }

    /** Uploads one pending record, true once the service has stored it. */
    private suspend fun uploadRecord(
        cipher: PlanningCipher,
        record: PlanningRecordEntity,
    ): Boolean {
        val plaintext =
            cipher.open(
                record.iv,
                record.ciphertext,
                PlanningCipher.identity(budgetId, record.recordId, record.schemaVersion, record.revision),
            )
        var base = record.syncedRevision.takeIf { it > 0 }
        val private =
            !record.deleted &&
                runCatching { isPrivate(Json.parseToJsonElement(plaintext.decodeToString()).jsonObject) }.getOrDefault(false)
        if (private && base == null) {
            dao.upsert(record.copy(pendingUpload = false))
            return false
        }
        var attempts = 0
        while (attempts++ < 2) {
            val revision = (base ?: 0) + 1
            val identity = PlanningCipher.identity(budgetId, record.recordId, record.schemaVersion, revision)
            val (iv, sealed) = cipher.seal(plaintext, identity)
            val (sentIv, sent) = if (private) cipher.seal(ByteArray(0), identity) else iv to sealed
            val result =
                transport.put(
                    record.recordId,
                    record.recordType,
                    record.schemaVersion,
                    revision,
                    base,
                    record.deleted || private,
                    sentIv,
                    sent,
                )
            if (result is RemoteWrite.Stored) {
                val current = dao.find(budgetId, record.recordId)
                dao.upsert(
                    if (current == null || current == record) {
                        record.copy(revision = revision, syncedRevision = revision, iv = iv, ciphertext = sealed, pendingUpload = false)
                    } else {
                        current.copy(syncedRevision = revision)
                    },
                )
                return true
            }
            base = (result as RemoteWrite.Conflict).current?.revision
        }
        return false
    }

    private suspend fun downloadChanges(
        first: RemotePlanningPage,
        anchor: String?,
    ): PlanningSyncResult {
        var page = first
        var last = anchor
        var downloaded = 0
        while (true) {
            last = page.records.lastOrNull()?.recordId ?: last
            for (remote in page.records) {
                val local = dao.find(budgetId, remote.recordId)
                if (local?.pendingUpload == true || (local != null && local.syncedRevision >= remote.revision)) continue
                dao.upsert(remote.toEntity())
                downloaded++
            }
            if (!page.hasMore) return PlanningSyncResult(0, downloaded, page.cursor, last)
            page = transport.changes(page.cursor, last)
        }
    }

    private fun RemotePlanningRecord.toEntity() =
        PlanningRecordEntity(
            budgetId,
            recordId,
            recordType,
            schemaVersion,
            revision,
            seq,
            deleted,
            iv,
            ciphertext,
            pendingUpload = false,
            syncedRevision = revision,
        )
}
