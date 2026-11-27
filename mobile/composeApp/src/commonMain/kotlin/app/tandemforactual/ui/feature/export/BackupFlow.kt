package app.tandemforactual.ui.feature.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.RestoredBudget
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.api.client.restoreBudgetAsNew
import app.tandemforactual.data.planning.BackupArchive
import app.tandemforactual.data.planning.BackupFileProblem
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.planning.OpenedBackup
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.updateBackupLog
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where a backup is restored to. */
internal enum class RestoreTarget {
    NEW_BUDGET,
    EXTRAS_ONLY,
}

/**
 * Backing up the open budget with its Tandem extras, and restoring from a backup file. A backup's budget is restored
 * as a new budget on the Actual server, as Actual's own import does, so the open budget is never overwritten.
 */
internal class BackupFlow(
    private val state: AppState,
    private val session: TandemMobileSession,
    private val scope: CoroutineScope,
    private val encryption: BackupEncryption,
    private val share: (String, String, ByteArray) -> Unit,
    private val report: (Pair<String, InlineMessageKind>) -> Unit,
    private val restoreBudget: suspend (OpenedBackup.Contents, String, String?) -> RestoredBudget = { backup, name, password ->
        session.apiClient.restoreBudgetAsNew(checkNotNull(backup.database), backup.metadataJson, name, password).also {
            session.loadBudgets()
        }
    },
    private val build: suspend (passphrase: String?) -> ByteArray = { buildBackup(session, state, platformTodayIsoDate(), it) },
) {
    internal var openBudget: (BudgetCatalogItem) -> Unit = {}
    var backingUp by mutableStateOf(false)
        private set
    var askingPassphrase by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pendingRestore by mutableStateOf<OpenedBackup.Contents?>(null)
        private set
    var restoreTarget by mutableStateOf(RestoreTarget.NEW_BUDGET)
    var restoring by mutableStateOf(false)
        private set
    var restoreError by mutableStateOf<String?>(null)
        private set
    var needsBudgetPassword by mutableStateOf(false)
        private set
    var openedWithPassphrase by mutableStateOf(false)
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
        openedWithPassphrase = false
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
                openedWithPassphrase = true
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
            contents.database == null && contents.extras.isEmpty() ->
                report(describeBackup(contents, state.selectedBudgetId) to InlineMessageKind.INFO)
            else -> {
                restoreTarget = if (contents.database != null) RestoreTarget.NEW_BUDGET else RestoreTarget.EXTRAS_ONLY
                restoreError = null
                needsBudgetPassword = false
                pendingRestore = contents
            }
        }
    }

    private fun rejection(opened: OpenedBackup): String =
        when ((opened as? OpenedBackup.Rejected)?.problem) {
            BackupFileProblem.DAMAGED -> "This backup is damaged and can’t be opened."
            else -> "That file isn’t a Tandem backup."
        }

    fun cancelRestore() {
        if (restoring) return
        pendingRestore = null
        restoreError = null
        needsBudgetPassword = false
    }

    /** Restores the pending backup to [restoreTarget]; nothing changes until this is called. */
    fun confirmRestore(budgetPassword: String = "") {
        val backup = pendingRestore ?: return
        if (restoring) return
        if (restoreTarget == RestoreTarget.EXTRAS_ONLY || backup.database == null) {
            pendingRestore = null
            restoreExtras(backup)
            return
        }
        restoring = true
        restoreError = null
        scope.launch {
            try {
                val restored = restoreBudget(backup, restoredName(backup), budgetPassword.ifEmpty { null })
                if (backup.extras.isNotEmpty()) session.planning.restore(restored.budgetId, backup.extras)
                pendingRestore = null
                needsBudgetPassword = false
                report("Restored “${restored.name}” as a new budget." to InlineMessageKind.SUCCESS)
                openBudget(BudgetCatalogItem(restored.budgetId, restored.name, restored.encrypted))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TandemApiException) {
                if (error.errorCode in PASSWORD_ERRORS) needsBudgetPassword = true
                restoreError = restoreFailure(error.errorCode)
            } catch (_: Exception) {
                restoreError = restoreFailure(null)
            } finally {
                restoring = false
            }
        }
    }

    private fun restoreExtras(backup: OpenedBackup.Contents) {
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

    /** The name the restored budget gets on the server, so it isn't mistaken for the budget it came from. */
    fun restoredName(backup: OpenedBackup.Contents): String = "${backup.budgetName?.takeIf { it.isNotBlank() } ?: "Budget"} (restored)"

    /** What restoring [backup] to [restoreTarget] will change, shown beside the button that does it. */
    fun restoreMessage(backup: OpenedBackup.Contents): String =
        if (restoreTarget == RestoreTarget.NEW_BUDGET && backup.database != null) {
            "Adds “${restoredName(backup)}” to your Actual server and opens it here. “${state.selectedBudget}” stays as it is."
        } else {
            "Brings the Tandem extras into “${state.selectedBudget}” and replaces the matching ones on every device " +
                "that shares this budget. The budget itself isn’t changed."
        }

    private fun restoreFailure(code: String?): String =
        when (code) {
            "budget_password_required" -> "This budget is end-to-end encrypted. Enter the password you set for it in Actual."
            "budget_password_incorrect" -> "That password doesn’t open this budget."
            "backup_unreadable" -> "The budget in this backup can’t be read."
            "sign_in_required", "sign_in_expired" -> "Sign in to your Actual server, then try again."
            else -> "Couldn’t restore. Nothing changed. Check the connection and try again."
        }

    private companion object {
        val PASSWORD_ERRORS = setOf("budget_password_required", "budget_password_incorrect")
    }
}

@Composable
internal fun rememberBackupFlow(
    state: AppState,
    session: TandemMobileSession,
    encryption: BackupEncryption,
    share: (String, String, ByteArray) -> Unit,
    report: (Pair<String, InlineMessageKind>) -> Unit,
    openBudget: (BudgetCatalogItem) -> Unit,
): BackupFlow {
    val scope = rememberCoroutineScope()
    val flow = remember(state, session, encryption) { BackupFlow(state, session, scope, encryption, share, report) }
    flow.openBudget = openBudget
    flow.pick = rememberStatementFilePicker(binary = true) { file -> flow.onPicked(file.bytes) }
    return flow
}
