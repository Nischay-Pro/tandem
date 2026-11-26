package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualBudgetKey
import app.tandemforactual.data.actual.ZipArchive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** An encrypted budget's key with the salt and test message the server registers for it. */
class ActualUploadKey(
    val key: ActualBudgetKey,
    val salt: String,
    val test: String,
)

data class ActualUploadedBudget(
    val fileId: String,
    val groupId: String,
)

/**
 * Makes [database] the copy every device syncs from, as Actual's `resetSync` and `upload` do. A file the server still
 * holds is reset and uploaded into a new sync group, so other devices get `file-has-reset` and download again. A file
 * it no longer holds is uploaded under [newFileId], as Actual's import does.
 */
suspend fun ActualServerClient.uploadBudget(
    fileId: String?,
    name: String,
    database: ByteArray,
    metadataJson: String?,
    key: ActualUploadKey?,
    newFileId: () -> String,
): ActualUploadedBudget =
    try {
        val live = fileId?.let { id -> listFiles().firstOrNull { it.fileId == id && !it.deleted } }
        val archive = budgetArchive(name, database, metadataJson)
        val (body, meta) = key?.let { seal(it.key, archive) } ?: (archive to null)
        if (live == null) {
            val id = newFileId()
            val groupId = upload(id, name, body, meta)
            key?.let { createKey(id, it.key.keyId, it.salt, it.test) }
            ActualUploadedBudget(id, groupId)
        } else {
            if (keyInfo(live.fileId).id != key?.key?.keyId) throw ActualServerException("file-has-new-key")
            resetFile(live.fileId)
            ActualUploadedBudget(live.fileId, upload(live.fileId, name, body, meta))
        }
    } catch (error: IOException) {
        throw ActualServerException("network-failure", cause = error)
    }

/** Actual's `exportBuffer` zip; `resetClock` gives each device that downloads it its own clock node. */
internal fun budgetArchive(
    name: String,
    database: ByteArray,
    metadataJson: String?,
): ByteArray {
    val metadata = metadataJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }.orEmpty()
    val stamped = JsonObject(metadata + mapOf("budgetName" to JsonPrimitive(name), "resetClock" to JsonPrimitive(true)))
    return ZipArchive.write(mapOf("db.sqlite" to database, "metadata.json" to stamped.toString().encodeToByteArray()))
}

@OptIn(ExperimentalEncodingApi::class)
private fun seal(
    key: ActualBudgetKey,
    archive: ByteArray,
): Pair<ByteArray, JsonObject> {
    val sealed = key.encrypt(archive)
    return sealed.data to
        buildJsonObject {
            put("keyId", key.keyId)
            put("algorithm", "aes-256-gcm")
            put("iv", Base64.encode(sealed.iv))
            put("authTag", Base64.encode(sealed.authTag))
        }
}
