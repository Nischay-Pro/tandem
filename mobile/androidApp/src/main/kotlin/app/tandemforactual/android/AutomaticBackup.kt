package app.tandemforactual.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.AndroidActualBudgetFiles
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
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

/** Whether an activity is on screen, so background work only notifies when Tandem isn't open. */
internal object AppVisibility {
    @Volatile var visible = false
}

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
        val progress = BackupProgress(applicationContext)
        return try {
            val store = AppPreferencesStore(database)
            val preferences = store.read()
            val settings = AutomaticBackupSettings.from(preferences)
            val now = System.currentTimeMillis()
            val lastRun = preferences[AutomaticBackupSettings.LAST_RUN]?.toLongOrNull()
            val budgetId = preferences["selectedBudgetId"]?.takeIf { it.isNotBlank() }
            val folder = settings.folder
            if (!AutomaticBackups.due(settings, lastRun, now) || budgetId == null || folder == null) return Result.success()
            if (!networkAllows(settings.network)) return Result.success()
            val storedSession = AndroidDeviceCredentialStore(applicationContext).read() ?: return Result.success()
            val client = TandemApiClient(allowInsecurePrivateNetwork = true, budgetFiles = AndroidActualBudgetFiles(applicationContext))
            progress.show(0)
            client.restoreStoredSession(storedSession)
            val budget = client.listBudgets().firstOrNull { it.budgetId == budgetId } ?: return Result.success()
            val password = if (budget.isEncrypted) backgroundPassword(budgetId) ?: return Result.success() else null
            val planningDatabase =
                if (password ==
                    null
                ) {
                    database
                } else {
                    AndroidEncryptedBudgetVault(applicationContext).open(budgetId, password)
                }
            progress.show(25)
            val bytes =
                try {
                    val passphrase =
                        runCatching {
                            AndroidDeviceCredentialStore(
                                applicationContext,
                                BACKUP_PASSPHRASE_STORE,
                            ).read()
                        }.getOrNull()
                    AutomaticBackups.build(
                        client,
                        planningDatabase ?: database,
                        AndroidDeviceCredentialStore(applicationContext, "planning_keys"),
                        budgetId,
                        password,
                        budget.name,
                        passphrase,
                    ) { System.currentTimeMillis() }
                } finally {
                    if (planningDatabase != null && planningDatabase !== database) planningDatabase.close()
                }
            progress.show(70)
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
            val sealed =
                app.tandemforactual.data.planning.BudgetBackupFile
                    .isSealed(bytes)
            BackupFolder(applicationContext, Uri.parse(folder)).apply {
                write(AutomaticBackups.fileName(budget.name, stamp, sealed), bytes)
                AutomaticBackups.toDelete(names(), budget.name, settings.keep).forEach(::delete)
            }
            store.putValue(AutomaticBackupSettings.LAST_RUN, now.toString())
            progress.show(100)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: TandemApiException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            progress.clear()
            database.close()
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

/** A progress notification for a backup running while Tandem isn't open; nothing shows when the app is on screen. */
private class BackupProgress(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(percent: Int) {
        val manager = manager ?: return
        if (AppVisibility.visible || !manager.areNotificationsEnabled()) return clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while an automatic backup runs in the background."
            },
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tandem_notification)
                .setContentTitle("Backing up your budget")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build(),
        )
    }

    fun clear() {
        manager?.cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_ID = "tandem-backups"
        const val NOTIFICATION_ID = 4_201
    }
}
