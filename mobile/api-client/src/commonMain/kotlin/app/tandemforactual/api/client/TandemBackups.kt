package app.tandemforactual.api.client

import app.tandemforactual.data.planning.BudgetBackupFile
import kotlin.time.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A whole-budget backup of the device's copy: Actual's database and metadata, which Actual can import, plus
 * [planningJson] when given, sealed with [passphrase] when one is set.
 */
suspend fun TandemApiClient.budgetBackup(
    budgetId: String,
    encryptionPassword: String?,
    budgetName: String,
    planningJson: String?,
    passphrase: String?,
): ByteArray {
    val files = budgetFiles ?: throw TandemApiException("not_supported")
    val database = call { budget(budgetId, encryptionPassword).snapshot(files, budgetId) }
    val metadata =
        buildJsonObject {
            put("id", budgetId)
            put("budgetName", budgetName)
            put("exportedBy", "Tandem")
            put("exportedAt", Clock.System.now().toEpochMilliseconds())
        }
    return BudgetBackupFile.build(database, metadata.toString(), planningJson, passphrase)
}
