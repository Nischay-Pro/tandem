package app.tandemforactual.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tandemforactual.api.client.WebDavClient
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.session.AutomaticBackupResult
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.AutomaticBackups
import app.tandemforactual.ui.core.session.BackupProblem
import app.tandemforactual.ui.core.session.BackupProblemException
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * The newest automatic backup waiting to go to WebDAV, kept in the app's own storage. It holds the same bytes as the
 * folder copy, so an encrypted budget's backup is already sealed here.
 */
internal class PendingWebDavUpload(
    private val directory: File,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "automatic-backup-upload"))

    data class Upload(
        val name: String,
        val budgetId: String,
        val budgetName: String,
    )

    private val bytes get() = File(directory, "backup")
    private val about get() = File(directory, "about")

    /** Replaces any earlier upload; [about] is written last, so a half-written upload is never read. */
    fun write(
        upload: Upload,
        content: ByteArray,
    ) {
        clear()
        directory.mkdirs()
        bytes.writeBytes(content)
        about.writeText(listOf(upload.name, upload.budgetId, upload.budgetName).joinToString("\n"))
    }

    fun read(): Upload? {
        val lines = runCatching { about.readText() }.getOrNull()?.split("\n", limit = 3) ?: return null
        if (lines.size != 3 || !bytes.isFile) return null
        return Upload(lines[0], lines[1], lines[2])
    }

    fun content(): ByteArray = bytes.readBytes()

    fun clear() {
        about.delete()
        bytes.delete()
    }
}

/** Uploads the queued backup to WebDAV once the network "Back up using" allows is there, then trims old copies. */
class AutomaticBackupUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val pending = PendingWebDavUpload(applicationContext)
        val upload = pending.read() ?: return Result.success()
        val database = runCatching { openTandemDatabase(tandemDatabaseBuilder(applicationContext)) }.getOrElse { return Result.retry() }
        val store = AppPreferencesStore(database)
        return try {
            val preferences = store.read()
            val settings = AutomaticBackupSettings.from(preferences)
            val url = settings.webDavUrl?.takeIf { settings.enabled }
            if (url == null) {
                pending.clear()
                return Result.success()
            }
            val last =
                AutomaticBackupResult.decode(preferences[AutomaticBackupResult.KEY])
                    ?: AutomaticBackupResult(AutomaticBackupResult.Outcome.SAVED)
            val problem = upload(settings, url, upload, pending.content())
            if (problem == null) {
                pending.clear()
                last.uploaded().preferences(System.currentTimeMillis()).forEach { (name, value) -> store.putValue(name, value) }
                Result.success()
            } else {
                store.putValue(AutomaticBackupResult.KEY, last.uploadFailed(problem).encode())
                Result.retry()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    private suspend fun upload(
        settings: AutomaticBackupSettings,
        url: String,
        upload: PendingWebDavUpload.Upload,
        bytes: ByteArray,
    ): BackupProblem? =
        try {
            val password =
                runCatching { AndroidDeviceCredentialStore(applicationContext, BACKUP_WEBDAV_STORE).read() }.getOrNull()
                    ?: throw BackupProblemException(BackupProblem.WEBDAV_PASSWORD)
            WebDavClient.connect(url, settings.webDavUser.orEmpty(), password).use { dav ->
                dav.check()
                AutomaticBackups.saveCompletely(upload.name, { dav.upload(it, bytes) }, dav::move, dav::delete)
                AutomaticBackups.toDelete(dav.list(), upload.budgetName, upload.budgetId, settings.keep).forEach { dav.delete(it) }
            }
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            (error as? BackupProblemException)?.problem ?: BackupProblem.WEBDAV
        }
}
