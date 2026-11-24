package app.tandemforactual.api.client

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString

/** Keeps bank icons as files named after the bank's domain, which [bankIconHost] has already limited to safe characters. */
class FileBankIconStore(
    directory: String,
) : BankIconStore {
    private val root = Path(directory)

    override suspend fun icon(host: String): BankIcon? =
        BankIconFormat.entries.firstNotNullOfOrNull { format ->
            file(host, format.name.lowercase())
                .takeIf { SystemFileSystem.exists(it) }
                ?.let { BankIcon(SystemFileSystem.source(it).buffered().use { source -> source.readByteArray() }, format) }
        }

    override suspend fun saveIcon(
        host: String,
        icon: BankIcon,
    ) {
        write(file(host, icon.format.name.lowercase())) { write(icon.bytes) }
        SystemFileSystem.delete(file(host, MISSING), mustExist = false)
    }

    override suspend fun missingSince(host: String): Long? =
        file(host, MISSING)
            .takeIf { SystemFileSystem.exists(it) }
            ?.let {
                SystemFileSystem
                    .source(it)
                    .buffered()
                    .use { source -> source.readString() }
                    .toLongOrNull()
            }

    override suspend fun saveMissing(
        host: String,
        at: Long,
    ) = write(file(host, MISSING)) { writeString(at.toString()) }

    private fun file(
        host: String,
        extension: String,
    ) = Path(root, "$host.$extension")

    private fun write(
        path: Path,
        body: kotlinx.io.Sink.() -> Unit,
    ) {
        SystemFileSystem.createDirectories(root)
        val partial = Path(root, "${path.name}.partial")
        SystemFileSystem.sink(partial).buffered().use { it.body() }
        SystemFileSystem.atomicMove(partial, path)
    }

    private companion object {
        const val MISSING = "missing"
    }
}
