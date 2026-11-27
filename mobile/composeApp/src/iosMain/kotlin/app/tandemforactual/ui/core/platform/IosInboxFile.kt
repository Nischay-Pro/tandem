package app.tandemforactual.ui.core.platform

import app.tandemforactual.data.inbox.InboxFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fsync
import platform.posix.ftell
import platform.posix.fwrite

/** A budget's sealed inbox in Application Support; each line is appended whole and synced before the call returns. */
@OptIn(ExperimentalForeignApi::class)
internal class IosInboxFile(
    private val path: String,
) : InboxFile {
    override suspend fun append(line: String) {
        withContext(Dispatchers.IO) {
            val existing = read()
            val torn = existing.isNotEmpty() && existing.last() != '\n'.code.toByte()
            val bytes = ((if (torn) "\n" else "") + line + "\n").encodeToByteArray()
            val output = checkNotNull(fopen(path, "ab")) { "Couldn’t open the inbox." }
            try {
                val length = bytes.size.toULong()
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1uL, length, output) }
                check(written == length && fflush(output) == 0 && fsync(fileno(output)) == 0) { "Couldn’t save to the inbox." }
            } finally {
                fclose(output)
            }
        }
    }

    override suspend fun lines(): List<String> =
        withContext(Dispatchers.IO) { read().decodeToString().split('\n').filter(String::isNotBlank) }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { remove(path) }
    }

    private fun read(): ByteArray {
        val input = fopen(path, "rb") ?: return ByteArray(0)
        try {
            fseek(input, 0, SEEK_END)
            val size = ftell(input)
            fseek(input, 0, SEEK_SET)
            val bytes = ByteArray(size.toInt())
            if (size > 0) {
                val read = bytes.usePinned { fread(it.addressOf(0), 1uL, size.toULong(), input) }
                check(read == size.toULong()) { "Couldn’t read the inbox." }
            }
            return bytes
        } finally {
            fclose(input)
        }
    }

    companion object {
        private val files get() = NSFileManager.defaultManager

        private fun directory(): String {
            val support =
                requireNotNull(
                    NSFileManager.defaultManager
                        .URLForDirectory(
                            directory = NSApplicationSupportDirectory,
                            inDomain = NSUserDomainMask,
                            appropriateForURL = null,
                            create = true,
                            error = null,
                        )?.path,
                )
            val directory = "$support/inbox"
            files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
            return directory
        }

        private fun pathFor(budgetId: String) = "${directory()}/${budgetId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.inbox"

        private fun remove(path: String) {
            if (files.fileExistsAtPath(path)) files.removeItemAtPath(path, error = null)
        }

        fun forBudget(budgetId: String) = IosInboxFile(pathFor(budgetId))

        fun delete(budgetId: String) = remove(pathFor(budgetId))

        fun deleteAll() = remove(directory())
    }
}
