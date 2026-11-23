package app.tandemforactual.ui.feature.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.updateBackupLog
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Backing up, restoring and checking planning backups, including the passphrase step each one needs. */
internal class BackupFlow(
    private val state: AppState,
    private val session: TandemMobileSession,
    private val scope: CoroutineScope,
    private val share: (String, String, String) -> Unit,
    private val report: (Pair<String, InlineMessageKind>) -> Unit,
) {
    var purpose by mutableStateOf<PassphrasePurpose?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var picked: String? = null
    internal var pick: () -> Unit = {}

    fun startBackup() = ask(PassphrasePurpose.BACK_UP)

    fun startRestore() = choose(PassphrasePurpose.RESTORE)

    fun startCheck() = choose(PassphrasePurpose.CHECK)

    private var pendingPurpose = PassphrasePurpose.RESTORE

    private fun choose(next: PassphrasePurpose) {
        pendingPurpose = next
        pick()
    }

    private fun ask(next: PassphrasePurpose) {
        error = null
        purpose = next
    }

    fun dismiss() {
        purpose = null
        picked = null
    }

    fun onPicked(contents: String?) {
        picked = contents
        when (val read = contents?.let { readBackupFile(it, null) } ?: BackupContents.NotABackup) {
            BackupContents.NeedsPassphrase -> ask(pendingPurpose)
            else -> finish(pendingPurpose, read)
        }
    }

    fun submit(passphrase: String) {
        val current = purpose ?: return
        if (current == PassphrasePurpose.BACK_UP) {
            val today = platformTodayIsoDate()
            share("tandem-planning-$today.json", "application/json", sealedPlanningBackup(session, passphrase))
            state.updateBackupLog { it.copy(backedUpOn = today) }
            dismiss()
            return
        }
        when (val read = readBackupFile(picked.orEmpty(), passphrase)) {
            BackupContents.WrongPassphrase -> error = "That passphrase doesn’t open this backup."
            else -> {
                dismiss()
                finish(current, read)
            }
        }
    }

    private fun finish(
        next: PassphrasePurpose,
        read: BackupContents,
    ) {
        val records = (read as? BackupContents.Records)?.records
        if (records == null) {
            report("That file isn’t a Tandem planning backup." to InlineMessageKind.ERROR)
            return
        }
        state.updateBackupLog { it.copy(checkedOn = platformTodayIsoDate()) }
        if (next == PassphrasePurpose.CHECK) {
            report("This backup opens and holds ${formatInteger(records.size)} planning records." to InlineMessageKind.SUCCESS)
            return
        }
        scope.launch {
            val count = session.planning.restore(state.selectedBudgetId, records)
            report("Restored ${formatInteger(count)} planning records." to InlineMessageKind.SUCCESS)
        }
    }
}

@Composable
internal fun rememberBackupFlow(
    state: AppState,
    session: TandemMobileSession,
    share: (String, String, String) -> Unit,
    report: (Pair<String, InlineMessageKind>) -> Unit,
): BackupFlow {
    val scope = rememberCoroutineScope()
    val flow = remember(state, session) { BackupFlow(state, session, scope, share, report) }
    flow.pick = rememberStatementFilePicker { file -> flow.onPicked(file.contents) }
    return flow
}
