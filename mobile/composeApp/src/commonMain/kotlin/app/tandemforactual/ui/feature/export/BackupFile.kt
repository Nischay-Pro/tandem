package app.tandemforactual.ui.feature.export

import app.tandemforactual.api.client.budgetBackup
import app.tandemforactual.data.planning.OpenedBackup
import app.tandemforactual.data.planning.PlanningStore
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformFormatDateAtEpochMilliseconds
import app.tandemforactual.ui.core.session.PlanningBackupSource
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.planningBackupJson
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatLiveDate

/**
 * A backup of the open budget: Actual's database with the Tandem extras inside, encrypted with [passphrase] when one
 * is set.
 */
internal suspend fun buildBackup(
    session: TandemMobileSession,
    state: AppState,
    today: String,
    passphrase: String?,
): ByteArray {
    val source = PlanningBackupSource(state.selectedBudgetId, state.selectedBudget, today)
    val extras = session.planning.backupRecords(source.budgetId)?.let { planningBackupJson(it, source) }
    return session.apiClient.budgetBackup(
        source.budgetId,
        state.encryptedPassword.takeIf { it.isNotBlank() },
        source.budgetName,
        extras,
        passphrase,
    )
}

/** The Tandem extras a backup holds that this version of Tandem can restore. */
internal val OpenedBackup.Contents.extras: List<Triple<String, String, kotlinx.serialization.json.JsonObject>>
    get() = planningRecords.orEmpty().filter { (_, type, _) -> PlanningStore.isBackedUp(type) }

/** What an opened backup holds, when it was made and from which budget, in one sentence. */
internal fun describeBackup(
    backup: OpenedBackup.Contents,
    selectedBudgetId: String,
): String {
    val parts =
        listOfNotNull(
            "your Actual budget".takeIf { backup.database != null },
            backup.extras.takeIf { it.isNotEmpty() }?.let {
                "Tandem extras (${formatInteger(
                    it.size,
                )} ${if (it.size == 1) "item" else "items"})"
            },
        ).joinToString(" and ").ifEmpty { "nothing Tandem can restore" }
    val made =
        (backup.createdOn?.let(::formatLiveDate) ?: backup.exportedAt?.let(::platformFormatDateAtEpochMilliseconds))
            ?.let { ", made on $it" }
            .orEmpty()
    val from =
        backup.budgetId
            ?.takeIf { it != selectedBudgetId }
            ?.let { " from ${backup.budgetName?.let { name -> "“$name”" } ?: "a different budget"}" }
            .orEmpty()
    return "This backup opens correctly. It holds $parts$made$from."
}
