package app.tandemforactual.android

import android.content.Context
import app.tandemforactual.data.inbox.InboxFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A budget's sealed inbox in no-backup storage; each line is written whole and synced before the call returns. */
internal class AndroidInboxFile(
    private val file: File,
) : InboxFile {
    override suspend fun append(line: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val torn =
                file.length() > 0 &&
                    RandomAccessFile(file, "r").use {
                        it.seek(it.length() - 1)
                        it.read() != '\n'.code
                    }
            FileOutputStream(file, true).use { out ->
                out.write(((if (torn) "\n" else "") + line + "\n").encodeToByteArray())
                out.fd.sync()
            }
        }
    }

    override suspend fun lines(): List<String> =
        withContext(Dispatchers.IO) { if (file.isFile) file.readLines().filter(String::isNotBlank) else emptyList() }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
    }

    companion object {
        private fun directory(context: Context) = File(context.applicationContext.noBackupFilesDir, "inbox")

        private fun path(
            context: Context,
            budgetId: String,
        ) = File(directory(context), budgetId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".inbox")

        fun forBudget(
            context: Context,
            budgetId: String,
        ) = AndroidInboxFile(path(context, budgetId))

        fun delete(
            context: Context,
            budgetId: String,
        ) {
            path(context, budgetId).delete()
        }

        fun deleteAll(context: Context) {
            directory(context).deleteRecursively()
        }
    }
}
