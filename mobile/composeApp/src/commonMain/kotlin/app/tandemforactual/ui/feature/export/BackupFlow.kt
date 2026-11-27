package app.tandemforactual.ui.feature.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.BackupArchive
import app.tandemforactual.data.planning.BackupFileProblem
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.planning.OpenedBackup
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.updateBackupLog
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Backing up the open budget with its Tandem extras, and opening a backup file to restore from it. */
internal class BackupFlow(
    private val state: AppState,
    private val session: TandemMobileSession,
    private val scope: CoroutineScope,
    private val encryption: BackupEncryption,
    private val share: (String, String, ByteArray) -> Unit,
    private val report: (Pair<String, InlineMessageKind>) -> Unit,
    private val build: suspend (passphrase: String?) -> ByteArray = { buildBackup(session, state, platformTodayIsoDate(), it) },
) {
    var backingUp by mutableStateOf(false)
        private set
    var askingPassphrase by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pendingRestore by mutableStateOf<OpenedBackup.Contents?>(null)
        private set
    private var picked: ByteArray? = null
    internal var pick: () -> Unit = {}

    /** Makes a backup and hands it to the share sheet; an encrypted budget gets a passphrase first if it has none. */
    fun backUpNow() {
        if (backingUp) return
        if (!encryption.ready) {
            encryption.askForPassphrase(::backUpNow)
            return
        }
        backingUp = true
        scope.launch {
            try {
                val passphrase = encryption.passphrase()
                val bytes = build(passphrase)
                val sealed = BudgetBackupFile.isSealed(bytes)
                check(AutomaticBackups.mayWrite(state.selectedBudgetEncrypted, sealed))
                val today = platformTodayIsoDate()
                share(
                    AutomaticBackups.fileName(state.selectedBudget, state.selectedBudgetId, today, sealed),
                    if (sealed) "application/octet-stream" else "application/zip",
                    bytes,
                )
                state.updateBackupLog { it.copy(sharedOn = today) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                report("Couldn’t back up. Open the budget and try again." to InlineMessageKind.ERROR)
            } finally {
                backingUp = false
            }
        }
    }

    fun startRestore() = pick()

    fun onPicked(bytes: ByteArray?) {
        picked = bytes
        if (bytes == null) {
            report("Couldn’t read that file. Choose a Tandem backup." to InlineMessageKind.ERROR)
            return
        }
        when (val opened = BackupArchive.open(bytes, null)) {
            OpenedBackup.NeedsPassphrase -> {
                error = null
                askingPassphrase = true
            }
            else -> opened(opened)
        }
    }

    fun submit(passphrase: String) {
        when (val opened = BackupArchive.open(picked ?: return, passphrase)) {
            OpenedBackup.WrongPassphrase -> error = "That passphrase doesn’t open this backup."
            else -> {
                dismiss()
                opened(opened)
            }
        }
    }

    fun dismiss() {
        askingPassphrase = false
        error = null
    }

    private fun opened(opened: OpenedBackup) {
        picked = null
        val contents = opened as? OpenedBackup.Contents
        when {
            contents == null -> report(rejection(opened) to InlineMessageKind.ERROR)
            contents.extras.isEmpty() ->
                report(
                    describeBackup(contents, state.selectedBudgetId) + " Tandem can’t restore a budget yet." to InlineMessageKind.INFO,
                )
            else -> pendingRestore = contents
        }
    }

    private fun rejection(opened: OpenedBackup): String =
        when ((opened as? OpenedBackup.Rejected)?.problem) {
            BackupFileProblem.DAMAGED -> "This backup is damaged and can’t be opened."
            else -> "That file isn’t a Tandem backup."
        }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun confirmRestore() {
        val backup = pendingRestore ?: return
        pendingRestore = null
        scope.launch {
            val count = session.planning.restore(state.selectedBudgetId, backup.extras)
            report(
                if (count == 0) {
                    "Nothing was restored. Open the budget and try again." to InlineMessageKind.ERROR
                } else {
                    "Restored ${formatInteger(count)} Tandem extras." to InlineMessageKind.SUCCESS
                },
            )
        }
    }

    /** What restoring [backup] will do, for the confirmation before anything changes. */
    fun restoreMessage(backup: OpenedBackup.Contents): String {
        val budgetNote = if (backup.database != null) " Tandem can’t restore the Actual budget itself yet." else ""
        return describeBackup(backup, state.selectedBudgetId) +
            " Restoring brings its Tandem extras into “${state.selectedBudget}” and replaces the matching ones on every device " +
            "that shares this budget.$budgetNote"
    }
}

@Composable
internal fun rememberBackupFlow(
    state: AppState,
    session: TandemMobileSession,
    encryption: BackupEncryption,
    share: (String, String, ByteArray) -> Unit,
    report: (Pair<String, InlineMessageKind>) -> Unit,
): BackupFlow {
    val scope = rememberCoroutineScope()
    val flow = remember(state, session, encryption) { BackupFlow(state, session, scope, encryption, share, report) }
    flow.pick = rememberStatementFilePicker(binary = true) { file -> flow.onPicked(file.bytes) }
    return flow
}
