package app.tandemforactual.ui.core.session

import kotlinx.coroutines.CancellationException

enum class BackupDestination { FOLDER, WEBDAV }

enum class BackupProblem(
    val text: String,
) {
    SIGNED_OUT("sign in to back up"),
    NO_BUDGET("open a budget to back it up"),
    NO_DEVICE_COPY("open the budget in Tandem once so there’s a copy to back up"),
    NO_BACKGROUND_KEY("allow background sync for this encrypted budget"),
    NEEDS_PASSPHRASE("set a backup passphrase for this encrypted budget"),
    BACKUP("Tandem couldn’t make the backup"),
    FOLDER("Tandem can’t reach the backup folder"),
    WEBDAV("the WebDAV upload failed"),
    WEBDAV_PASSWORD("Tandem can’t read the WebDAV password. Set up WebDAV again."),
}

/** Thrown by a backup step whose failure has a more specific reason than its destination. */
class BackupProblemException(
    val problem: BackupProblem,
) : Exception(problem.name)

/**
 * What the last automatic backup run did, saved next to [AutomaticBackupSettings.LAST_RUN]. [waiting] holds the
 * destinations whose copy is queued for the network, and [budgetSyncedAt] is set when the backup came from a copy of
 * the budget that hadn't synced for a while.
 */
data class AutomaticBackupResult(
    val outcome: Outcome,
    val problem: BackupProblem? = null,
    val savedTo: Set<BackupDestination> = emptySet(),
    val waiting: Set<BackupDestination> = emptySet(),
    val budgetSyncedAt: Long? = null,
) {
    enum class Outcome { SAVED, SKIPPED, FAILED }

    fun encode(): String =
        listOf(outcome.name, savedTo.encode(), problem?.name.orEmpty(), waiting.encode(), budgetSyncedAt?.toString().orEmpty())
            .joinToString(";")

    /**
     * The preferences to write for this run. The last-run time moves only when a destination has the backup; the
     * last-made time also moves when a copy is queued, so the schedule doesn't make another while it waits.
     */
    fun preferences(now: Long): Map<String, String> =
        mapOf(KEY to encode()) +
            (if (savedTo.isEmpty()) emptyMap() else mapOf(AutomaticBackupSettings.LAST_RUN to now.toString())) +
            (if (savedTo.isEmpty() && waiting.isEmpty()) emptyMap() else mapOf(AutomaticBackupSettings.LAST_MADE to now.toString()))

    /** This run once the queued WebDAV copy has been uploaded. */
    fun uploaded(): AutomaticBackupResult =
        copy(
            outcome = Outcome.SAVED,
            problem = problem?.takeUnless { it == BackupProblem.WEBDAV || it == BackupProblem.WEBDAV_PASSWORD },
            savedTo = savedTo + BackupDestination.WEBDAV,
            waiting = waiting - BackupDestination.WEBDAV,
        )

    /** This run once the queued WebDAV upload has failed with [reason]. */
    fun uploadFailed(reason: BackupProblem): AutomaticBackupResult =
        copy(
            outcome = if (savedTo.isEmpty()) Outcome.FAILED else Outcome.SAVED,
            problem = reason,
            waiting = waiting - BackupDestination.WEBDAV,
        )

    companion object {
        const val KEY = "internal:auto-backup-last-result"

        fun skipped(problem: BackupProblem) = AutomaticBackupResult(Outcome.SKIPPED, problem)

        fun failed(problem: BackupProblem) = AutomaticBackupResult(Outcome.FAILED, problem)

        fun decode(value: String?): AutomaticBackupResult? =
            runCatching {
                val parts = value!!.split(";")
                AutomaticBackupResult(
                    Outcome.valueOf(parts[0]),
                    parts[2].takeIf(String::isNotEmpty)?.let(BackupProblem::valueOf),
                    destinations(parts[1]),
                    destinations(parts.getOrElse(3) { "" }),
                    parts.getOrNull(4)?.toLongOrNull(),
                )
            }.getOrNull()

        /**
         * Saves to the folder now and queues the WebDAV upload for the network "Back up using" allows; a failure in
         * one doesn't stop the other.
         */
        suspend fun deliver(
            toFolder: (suspend () -> Unit)?,
            queueWebDav: (suspend () -> Unit)?,
            budgetSyncedAt: Long? = null,
        ): AutomaticBackupResult {
            val saved = mutableSetOf<BackupDestination>()
            val waiting = mutableSetOf<BackupDestination>()
            val problems = mutableListOf<BackupProblem>()
            listOf(BackupDestination.FOLDER to toFolder, BackupDestination.WEBDAV to queueWebDav).forEach { (destination, step) ->
                step ?: return@forEach
                try {
                    step()
                    if (destination == BackupDestination.WEBDAV) waiting += destination else saved += destination
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    problems += (error as? BackupProblemException)?.problem ?: BackupProblem.valueOf(destination.name)
                }
            }
            val outcome = if (saved.isEmpty() && waiting.isEmpty()) Outcome.FAILED else Outcome.SAVED
            return AutomaticBackupResult(outcome, problems.firstOrNull(), saved, waiting, budgetSyncedAt)
        }

        private fun destinations(value: String): Set<BackupDestination> =
            value
                .split(",")
                .filter(String::isNotEmpty)
                .map(BackupDestination::valueOf)
                .toSet()
    }
}

private fun Set<BackupDestination>.encode() = joinToString(",") { it.name }

/** The card's one-line summary of automatic backups. */
fun automaticBackupSummary(
    settings: AutomaticBackupSettings,
    lastRun: String?,
    result: AutomaticBackupResult?,
): String {
    val problem = result?.problem
    val waitingFor = if (settings.network == SyncNetwork.WIFI) "Wi-Fi" else "a connection"
    return when {
        !settings.enabled -> "Off"
        settings.folder == null && settings.webDavUrl == null -> "Choose a folder or WebDAV to start"
        result?.outcome == AutomaticBackupResult.Outcome.SKIPPED && problem != null -> "Skipped: ${problem.text}"
        result?.outcome == AutomaticBackupResult.Outcome.FAILED && problem != null -> "Last backup failed: ${problem.text}"
        result != null && BackupDestination.WEBDAV in result.waiting ->
            when {
                BackupDestination.FOLDER in result.savedTo -> "Saved to the folder; WebDAV upload waiting for $waitingFor"
                problem != null -> "WebDAV upload waiting for $waitingFor; saving to the folder failed"
                else -> "WebDAV upload waiting for $waitingFor"
            }
        result?.outcome == AutomaticBackupResult.Outcome.SAVED && problem != null ->
            if (BackupDestination.FOLDER in result.savedTo) {
                "Saved to the folder; WebDAV upload failed"
            } else {
                "Uploaded to WebDAV; saving to the folder failed"
            }
        lastRun != null -> "Last backup $lastRun"
        else -> "Waiting for the first backup"
    }
}

/** Says the last backup came from the budget as it last synced, when the device's copy hadn't synced for a while. */
fun staleBackupNote(
    result: AutomaticBackupResult?,
    format: (Long) -> String,
): String? {
    if (result?.outcome != AutomaticBackupResult.Outcome.SAVED) return null
    val syncedAt = result.budgetSyncedAt ?: return null
    return "Backed up the budget as last synced ${format(syncedAt)}"
}
