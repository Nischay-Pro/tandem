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
import app.tandemforactual.api.client.WebDavException
import app.tandemforactual.data.database.AndroidActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.planning.BudgetBackupFile
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
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
        return try {
            val store = AppPreferencesStore(database)
            val preferences = store.read()
            val settings = AutomaticBackupSettings.from(preferences)
            val now = System.currentTimeMillis()
            val lastRun = preferences[AutomaticBackupSettings.LAST_RUN]?.toLongOrNull()
            val budgetId = preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() }
            if (!AutomaticBackups.due(settings, lastRun, now) || budgetId == null) return Result.success()
            if (!networkAllows(settings.network)) return Result.success()
            val storedSession = AndroidDeviceCredentialStore(applicationContext).read() ?: return Result.success()
            val client = TandemApiClient(allowInsecurePrivateNetwork = true, budgetFiles = AndroidActualBudgetFiles(applicationContext))
            progress.show(0)
            client.restoreStoredSession(storedSession)
            val budget = client.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return Result.success()
            val password = if (budget.isEncrypted) backgroundPassword(budgetId) ?: return Result.success() else null
            progress.show(25)
            val bytes = backup(database, client, budgetId, budget.name, password)
            progress.show(70)
            deliver(store, settings, budget.name, bytes, now, progress)
            progress.show(100)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        } finally {
            progress.clear()
            database.close()
        }
    }

    /** The backup bytes, with planning records from the encrypted budget's vault when the budget has one. */
    private suspend fun backup(
        database: TandemDatabase,
        client: TandemApiClient,
        budgetId: String,
        budgetName: String,
        password: String?,
    ): ByteArray {
        val vault = password?.let { AndroidEncryptedBudgetVault(applicationContext).open(budgetId, it) }
        try {
            val passphrase = runCatching { AndroidDeviceCredentialStore(applicationContext, BACKUP_PASSPHRASE_STORE).read() }.getOrNull()
            val keys = AndroidDeviceCredentialStore(applicationContext, "planning_keys")
            return AutomaticBackups.build(client, vault ?: database, keys, budgetId, password, budgetName, passphrase) {
                System.currentTimeMillis()
            }
        } finally {
            vault?.close()
        }
    }

    /**
     * Saves the backup to the folder and WebDAV, trimming old copies in each. Once the folder has it the run counts, so a
     * failed upload waits for the next backup instead of writing duplicates.
     */
    private suspend fun deliver(
        store: AppPreferencesStore,
        settings: AutomaticBackupSettings,
        budgetName: String,
        bytes: ByteArray,
        now: Long,
        progress: BackupProgress,
    ) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        val name = AutomaticBackups.fileName(budgetName, stamp, BudgetBackupFile.isSealed(bytes))
        settings.folder?.let { folder ->
            BackupFolder(applicationContext, Uri.parse(folder)).apply {
                write(name, bytes)
                AutomaticBackups.toDelete(names(), budgetName, settings.keep).forEach(::delete)
            }
            store.putValue(AutomaticBackupSettings.LAST_RUN, now.toString())
        }
        progress.show(85)
        try {
            uploadToWebDav(settings, name, bytes, budgetName)
        } catch (error: WebDavException) {
            if (settings.folder == null) throw error
        }
        store.putValue(AutomaticBackupSettings.LAST_RUN, now.toString())
    }

    private suspend fun uploadToWebDav(
        settings: AutomaticBackupSettings,
        name: String,
        bytes: ByteArray,
        budgetName: String,
    ) {
        val url = settings.webDavUrl ?: return
        val password = runCatching { AndroidDeviceCredentialStore(applicationContext, BACKUP_WEBDAV_STORE).read() }.getOrNull() ?: return
        val dav = WebDavClient.connect(url, settings.webDavUser.orEmpty(), password)
        dav.check()
        dav.upload(name, bytes)
        AutomaticBackups.toDelete(dav.list(), budgetName, settings.keep).forEach { dav.delete(it) }
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

    fun write(
        name: String,
        bytes: ByteArray,
    ) {
        val document = checkNotNull(DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name))
        checkNotNull(resolver.openOutputStream(document)).use { it.write(bytes) }
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
