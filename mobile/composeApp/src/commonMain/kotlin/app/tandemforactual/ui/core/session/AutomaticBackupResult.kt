package app.tandemforactual.ui.core.session

import kotlinx.coroutines.CancellationException

enum class BackupDestination { FOLDER, WEBDAV }

enum class BackupProblem(
    val text: String,
) {
    SIGNED_OUT("sign in to back up"),
    NO_BUDGET("open a budget to back it up"),
    BUDGET_GONE("the budget is no longer on the server"),
    NO_BACKGROUND_KEY("allow background sync for this encrypted budget"),
    BACKUP("Tandem couldn’t make the backup"),
    FOLDER("Tandem can’t reach the backup folder"),
    WEBDAV("the WebDAV upload failed"),
    WEBDAV_PASSWORD("Tandem can’t read the WebDAV password. Set up WebDAV again."),
}

/** Thrown by a backup step whose failure has a more specific reason than its destination. */
class BackupProblemException(
    val problem: BackupProblem,
) : Exception(problem.name)

/** What the last automatic backup run did, saved next to [AutomaticBackupSettings.LAST_RUN]. */
data class AutomaticBackupResult(
    val outcome: Outcome,
    val problem: BackupProblem? = null,
    val savedTo: Set<BackupDestination> = emptySet(),
) {
    enum class Outcome { SAVED, SKIPPED, FAILED }

    fun encode(): String = listOf(outcome.name, savedTo.joinToString(",") { it.name }, problem?.name.orEmpty()).joinToString(";")

    /** The preferences to write for this run; the last-run time moves only when a destination has the backup. */
    fun preferences(now: Long): Map<String, String> =
        mapOf(KEY to encode()) + if (savedTo.isEmpty()) emptyMap() else mapOf(AutomaticBackupSettings.LAST_RUN to now.toString())

    companion object {
        const val KEY = "internal:auto-backup-last-result"

        fun skipped(problem: BackupProblem) = AutomaticBackupResult(Outcome.SKIPPED, problem)

        fun failed(problem: BackupProblem) = AutomaticBackupResult(Outcome.FAILED, problem)

        fun decode(value: String?): AutomaticBackupResult? =
            runCatching {
                val (outcome, saved, problem) = value!!.split(";")
                AutomaticBackupResult(
                    Outcome.valueOf(outcome),
                    problem.takeIf(String::isNotEmpty)?.let(BackupProblem::valueOf),
                    saved
                        .split(",")
                        .filter(String::isNotEmpty)
                        .map(BackupDestination::valueOf)
                        .toSet(),
                )
            }.getOrNull()

        /** Saves to each destination that's set up; a failure in one doesn't stop the other. */
        suspend fun deliver(
            toFolder: (suspend () -> Unit)?,
            toWebDav: (suspend () -> Unit)?,
        ): AutomaticBackupResult {
            val saved = mutableSetOf<BackupDestination>()
            val problems = mutableListOf<BackupProblem>()
            listOf(BackupDestination.FOLDER to toFolder, BackupDestination.WEBDAV to toWebDav).forEach { (destination, step) ->
                step ?: return@forEach
                try {
                    step()
                    saved += destination
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    problems += (error as? BackupProblemException)?.problem ?: BackupProblem.valueOf(destination.name)
                }
            }
            val outcome = if (saved.isEmpty()) Outcome.FAILED else Outcome.SAVED
            return AutomaticBackupResult(outcome, problems.firstOrNull(), saved)
        }
    }
}

/** The card's one-line summary of automatic backups. */
fun automaticBackupSummary(
    settings: AutomaticBackupSettings,
    lastRun: String?,
    result: AutomaticBackupResult?,
): String {
    val problem = result?.problem
    return when {
        !settings.enabled -> "Off"
        settings.folder == null && settings.webDavUrl == null -> "Choose a folder or WebDAV to start"
        result?.outcome == AutomaticBackupResult.Outcome.SKIPPED && problem != null -> "Skipped: ${problem.text}"
        result?.outcome == AutomaticBackupResult.Outcome.FAILED && problem != null -> "Last backup failed: ${problem.text}"
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
