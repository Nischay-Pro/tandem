package app.tandemforactual.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.AutomaticBackupResult
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.session.BackupDestination
import app.tandemforactual.ui.core.session.BackupProblem
import app.tandemforactual.ui.core.session.SyncNetwork
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal const val BACKUP_PASSPHRASE_STORE = "automatic_backup_passphrase"
internal const val BACKUP_WEBDAV_STORE = "automatic_backup_webdav"

/**
 * Checks hourly whether an automatic backup is due. Backups are made from the device's copy and saved to the folder
 * without a network; a WebDAV copy is queued for [AutomaticBackupUploadWorker], which waits for the network "Back up
 * using" allows.
 */
internal object AutomaticBackupScheduler {
    private const val UNIQUE_WORK_NAME = "tandem-automatic-backup"
    internal const val UPLOAD_WORK_NAME = "tandem-automatic-backup-upload"

    fun ensureScheduled(context: Context) {
        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodicRequest())
    }

    /** Moves a queued WebDAV upload onto the newly chosen network. */
    fun update(
        context: Context,
        network: SyncNetwork,
    ) {
        if (PendingWebDavUpload(context).read() != null) queueUpload(context, network)
    }

    fun queueUpload(
        context: Context,
        network: SyncNetwork,
    ) {
        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(UPLOAD_WORK_NAME, ExistingWorkPolicy.REPLACE, uploadRequest(network))
    }

    internal fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(1, TimeUnit.HOURS).build()

    internal fun uploadRequest(network: SyncNetwork): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<AutomaticBackupUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType(network)).build())
            .build()

    internal fun networkType(network: SyncNetwork): NetworkType =
        if (network == SyncNetwork.WIFI) NetworkType.UNMETERED else NetworkType.CONNECTED
}

class AutomaticBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = runCatching { openTandemDatabase(tandemDatabaseBuilder(applicationContext)) }.getOrElse { return Result.retry() }
        val progress = BackupProgress(BackgroundWorkProgress(applicationContext, BackgroundWork.BACKUP))
        val store = AppPreferencesStore(database)
        val now = System.currentTimeMillis()
        return try {
            val preferences = store.read()
            val settings = AutomaticBackupSettings.from(preferences)
            val lastMade =
                listOf(AutomaticBackupSettings.LAST_RUN, AutomaticBackupSettings.LAST_MADE)
                    .mapNotNull { preferences[it]?.toLongOrNull() }
                    .maxOrNull()
            if (!AutomaticBackups.due(settings, lastMade, now)) return Result.success()
            val result = attempt(settings, preferences, database, progress, now)
            record(store, result, now)
            if (BackupDestination.WEBDAV in result.waiting) AutomaticBackupScheduler.queueUpload(applicationContext, settings.network)
            if (result.outcome == AutomaticBackupResult.Outcome.FAILED) Result.retry() else Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runCatching { record(store, AutomaticBackupResult.failed(BackupProblem.BACKUP), now) }
            Result.retry()
        } finally {
            progress.clear()
            database.close()
        }
    }

    private suspend fun attempt(
        settings: AutomaticBackupSettings,
        preferences: Map<String, String>,
        database: TandemDatabase,
        progress: BackupProgress,
        now: Long,
    ): AutomaticBackupResult {
        val budgetId =
            preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() } ?: return AutomaticBackupResult.skipped(BackupProblem.NO_BUDGET)
        val storedSession =
            AndroidDeviceCredentialStore(applicationContext).read() ?: return AutomaticBackupResult.skipped(BackupProblem.SIGNED_OUT)
        val name = preferences["selectedBudget"]?.takeIf { it.isNotBlank() } ?: "Budget"
        val budget = BackupBudget(budgetId, name, preferences["selectedBudgetEncrypted"] == "true")
        val client = TandemApiClient(allowInsecurePrivateNetwork = true, budgetFiles = sealedBudgetFiles(applicationContext))
        return client.use { attemptWith(it, settings, budget, storedSession, database, progress, now) }
    }

    private suspend fun attemptWith(
        client: TandemApiClient,
        settings: AutomaticBackupSettings,
        selected: BackupBudget,
        storedSession: String,
        database: TandemDatabase,
        progress: BackupProgress,
        now: Long,
    ): AutomaticBackupResult {
        progress.show(0)
        client.restoreStoredSession(storedSession)
        val copyEncrypted = client.storedCopyEncrypted(selected.id) ?: return AutomaticBackupResult.skipped(BackupProblem.NO_DEVICE_COPY)
        val budget = selected.copy(encrypted = selected.encrypted || copyEncrypted)
        val password =
            if (budget.encrypted) {
                backgroundPassword(budget.id) ?: return AutomaticBackupResult.skipped(BackupProblem.NO_BACKGROUND_KEY)
            } else {
                null
            }
        val passphrase = AndroidDeviceCredentialStore(applicationContext, BACKUP_PASSPHRASE_STORE).read()
        if (AutomaticBackups.needsPassphrase(budget.encrypted, passphrase != null)) {
            return AutomaticBackupResult.skipped(BackupProblem.NEEDS_PASSPHRASE)
        }
        progress.show(25)
        val (bytes, syncedAt) =
            try {
                backup(database, client, budget, password, passphrase)
            } catch (error: TandemApiException) {
                if (error.errorCode == "no_device_copy") return AutomaticBackupResult.skipped(BackupProblem.NO_DEVICE_COPY)
                throw error
            }
        if (!AutomaticBackups.mayWrite(budget.encrypted, BudgetBackupFile.isSealed(bytes))) {
            return AutomaticBackupResult.skipped(BackupProblem.NEEDS_PASSPHRASE)
        }
        progress.show(70)
        return deliver(settings, budget, bytes, AutomaticBackups.staleSince(syncedAt, now), progress).also { progress.show(100) }
    }

    private suspend fun record(
        store: AppPreferencesStore,
        result: AutomaticBackupResult,
        now: Long,
    ) = result.preferences(now).forEach { (name, value) -> store.putValue(name, value) }

    /**
     * The backup bytes from the device's copy, with planning records from the encrypted budget's vault when the budget
     * has one, and when the copy last synced.
     */
    private suspend fun backup(
        database: TandemDatabase,
        client: TandemApiClient,
        budget: BackupBudget,
        password: String?,
        passphrase: String?,
    ): Pair<ByteArray, Long?> {
        val vault = password?.let { AndroidEncryptedBudgetVault(applicationContext).open(budget.id, it) }
        try {
            val keys = AndroidDeviceCredentialStore(applicationContext, PLANNING_KEY_STORE)
            val bytes =
                AutomaticBackups.build(client, vault ?: database, keys, budget.id, password, budget.name, passphrase) {
                    System.currentTimeMillis()
                }
            val syncedAt = runCatching { (vault ?: database).budgetSyncStateDao().find(budget.id)?.lastSyncedAtEpochMs }.getOrNull()
            return bytes to syncedAt
        } finally {
            vault?.close()
        }
    }

    /** Saves the backup to the folder, trimming old copies, and queues the WebDAV copy. */
    private suspend fun deliver(
        settings: AutomaticBackupSettings,
        budget: BackupBudget,
        bytes: ByteArray,
        staleSince: Long?,
        progress: BackupProgress,
    ): AutomaticBackupResult {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        val name = AutomaticBackups.fileName(budget.name, budget.id, stamp, BudgetBackupFile.isSealed(bytes))
        val pending = PendingWebDavUpload(applicationContext)
        if (settings.webDavUrl == null) pending.clear()
        return AutomaticBackupResult.deliver(
            toFolder =
                settings.folder?.let { folder ->
                    {
                        BackupFolder(applicationContext, Uri.parse(folder)).apply {
                            save(name, bytes)
                            AutomaticBackups.toDelete(names(), budget.name, budget.id, settings.keep).forEach(::delete)
                        }
                        progress.show(85)
                    }
                },
            queueWebDav = settings.webDavUrl?.let { { pending.write(PendingWebDavUpload.Upload(name, budget.id, budget.name), bytes) } },
            budgetSyncedAt = staleSince,
        )
    }

    private fun backgroundPassword(budgetId: String): String? =
        BackgroundBudgetKey
            .decode(runCatching { AndroidDeviceCredentialStore(applicationContext, BACKGROUND_KEY_STORE).read() }.getOrNull())
            ?.takeIf { it.first == budgetId }
            ?.second
}

private data class BackupBudget(
    val id: String,
    val name: String,
    val encrypted: Boolean,
)

/** The folder the person picked, written through the storage framework so backups show up in their Files app. */
private class BackupFolder(
    private val context: Context,
    tree: Uri,
) {
    private val resolver = context.contentResolver
    private val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    suspend fun save(
        name: String,
        bytes: ByteArray,
    ) {
        var partial: Uri? = null
        AutomaticBackups.saveCompletely(
            name,
            write = { partial = create(it, bytes) },
            rename = { _, to ->
                val document = checkNotNull(partial)
                val renamed = runCatching { DocumentsContract.renameDocument(resolver, document, to) }.getOrNull()
                if (renamed == null) {
                    create(to, bytes)
                    DocumentsContract.deleteDocument(resolver, document)
                }
            },
            delete = { partial?.let { DocumentsContract.deleteDocument(resolver, it) } },
        )
    }

    private fun create(
        name: String,
        bytes: ByteArray,
    ): Uri {
        val document = checkNotNull(DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name))
        try {
            checkNotNull(resolver.openOutputStream(document)).use { it.write(bytes) }
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw error
        }
        return document
    }

    private fun entries(): Map<String, Uri> =
        resolver
            .query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(1)))
                    }
                }
            }.orEmpty()

    fun names(): List<String> = entries().keys.toList()

    fun delete(name: String) {
        entries()[name]?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
    }
}

private class BackupProgress(
    private val notification: BackgroundWorkProgress,
) {
    fun show(percent: Int) = notification.show("Backing up your budget", percent)

    fun clear() = notification.clear()
}
