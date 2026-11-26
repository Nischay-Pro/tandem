package app.tandemforactual.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.WebDavClient
import app.tandemforactual.data.database.AndroidActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.AutomaticBackupResult
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.session.BackupProblem
import app.tandemforactual.ui.core.session.BackupProblemException
import app.tandemforactual.ui.core.session.SyncNetwork
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal const val BACKUP_PASSPHRASE_STORE = "automatic_backup_passphrase"
internal const val BACKUP_WEBDAV_STORE = "automatic_backup_webdav"

/** Checks hourly whether an automatic backup is due; the worker itself reads the schedule, folder and network choice. */
internal object AutomaticBackupScheduler {
    private const val UNIQUE_WORK_NAME = "tandem-automatic-backup"

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutomaticBackupWorker>(1, TimeUnit.HOURS).build(),
        )
    }
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
            val lastRun = preferences[AutomaticBackupSettings.LAST_RUN]?.toLongOrNull()
            if (!AutomaticBackups.due(settings, lastRun, now) || !networkAllows(settings.network)) return Result.success()
            val result = attempt(settings, preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() }, database, progress)
            record(store, result, now)
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
        budgetId: String?,
        database: TandemDatabase,
        progress: BackupProgress,
    ): AutomaticBackupResult {
        budgetId ?: return AutomaticBackupResult.skipped(BackupProblem.NO_BUDGET)
        val storedSession =
            AndroidDeviceCredentialStore(applicationContext).read() ?: return AutomaticBackupResult.skipped(BackupProblem.SIGNED_OUT)
        val client = TandemApiClient(allowInsecurePrivateNetwork = true, budgetFiles = AndroidActualBudgetFiles(applicationContext))
        return client.use { attemptWith(it, settings, budgetId, storedSession, database, progress) }
    }

    private suspend fun attemptWith(
        client: TandemApiClient,
        settings: AutomaticBackupSettings,
        budgetId: String,
        storedSession: String,
        database: TandemDatabase,
        progress: BackupProgress,
    ): AutomaticBackupResult {
        progress.show(0)
        client.restoreStoredSession(storedSession)
        val budget =
            client.listBudgets().firstOrNull { it.budgetId == budgetId }
                ?: return AutomaticBackupResult.skipped(BackupProblem.BUDGET_GONE)
        val password =
            if (budget.isEncrypted) {
                backgroundPassword(budgetId) ?: return AutomaticBackupResult.skipped(BackupProblem.NO_BACKGROUND_KEY)
            } else {
                null
            }
        val passphrase = AndroidDeviceCredentialStore(applicationContext, BACKUP_PASSPHRASE_STORE).read()
        if (AutomaticBackups.needsPassphrase(budget.isEncrypted, passphrase != null)) {
            return AutomaticBackupResult.skipped(BackupProblem.NEEDS_PASSPHRASE)
        }
        progress.show(25)
        val bytes = backup(database, client, budgetId, budget.name, password, passphrase)
        if (!AutomaticBackups.mayWrite(budget.isEncrypted, BudgetBackupFile.isSealed(bytes))) {
            return AutomaticBackupResult.skipped(BackupProblem.NEEDS_PASSPHRASE)
        }
        progress.show(70)
        return deliver(settings, budgetId, budget.name, bytes, progress).also { progress.show(100) }
    }

    private suspend fun record(
        store: AppPreferencesStore,
        result: AutomaticBackupResult,
        now: Long,
    ) = result.preferences(now).forEach { (name, value) -> store.putValue(name, value) }

    /** The backup bytes, with planning records from the encrypted budget's vault when the budget has one. */
    private suspend fun backup(
        database: TandemDatabase,
        client: TandemApiClient,
        budgetId: String,
        budgetName: String,
        password: String?,
        passphrase: String?,
    ): ByteArray {
        val vault = password?.let { AndroidEncryptedBudgetVault(applicationContext).open(budgetId, it) }
        try {
            val keys = AndroidDeviceCredentialStore(applicationContext, "planning_keys")
            return AutomaticBackups.build(client, vault ?: database, keys, budgetId, password, budgetName, passphrase) {
                System.currentTimeMillis()
            }
        } finally {
            vault?.close()
        }
    }

    /** Saves the backup to the folder and WebDAV, trimming old copies in each. */
    private suspend fun deliver(
        settings: AutomaticBackupSettings,
        budgetId: String,
        budgetName: String,
        bytes: ByteArray,
        progress: BackupProgress,
    ): AutomaticBackupResult {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        val name = AutomaticBackups.fileName(budgetName, budgetId, stamp, BudgetBackupFile.isSealed(bytes))
        return AutomaticBackupResult.deliver(
            toFolder =
                settings.folder?.let { folder ->
                    {
                        BackupFolder(applicationContext, Uri.parse(folder)).apply {
                            save(name, bytes)
                            AutomaticBackups.toDelete(names(), budgetName, budgetId, settings.keep).forEach(::delete)
                        }
                        progress.show(85)
                    }
                },
            toWebDav = settings.webDavUrl?.let { url -> { uploadToWebDav(settings, url, name, bytes, budgetId, budgetName) } },
        )
    }

    private suspend fun uploadToWebDav(
        settings: AutomaticBackupSettings,
        url: String,
        name: String,
        bytes: ByteArray,
        budgetId: String,
        budgetName: String,
    ) {
        val password =
            runCatching { AndroidDeviceCredentialStore(applicationContext, BACKUP_WEBDAV_STORE).read() }.getOrNull()
                ?: throw BackupProblemException(BackupProblem.WEBDAV_PASSWORD)
        WebDavClient.connect(url, settings.webDavUser.orEmpty(), password).use { dav ->
            dav.check()
            AutomaticBackups.saveCompletely(name, { dav.upload(it, bytes) }, dav::move, dav::delete)
            AutomaticBackups.toDelete(dav.list(), budgetName, budgetId, settings.keep).forEach { dav.delete(it) }
        }
    }

    private fun networkAllows(network: SyncNetwork): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        val unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return when (network) {
            SyncNetwork.WIFI -> unmetered
            SyncNetwork.MOBILE_DATA -> !unmetered
            SyncNetwork.ANY -> true
        }
    }

    private fun backgroundPassword(budgetId: String): String? =
        BackgroundBudgetKey
            .decode(runCatching { AndroidDeviceCredentialStore(applicationContext, BACKGROUND_KEY_STORE).read() }.getOrNull())
            ?.takeIf { it.first == budgetId }
            ?.second
}

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
