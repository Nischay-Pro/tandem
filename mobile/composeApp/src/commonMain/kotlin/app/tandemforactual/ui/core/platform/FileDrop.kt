package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal enum class DroppedFileKind { STATEMENT, IMAGE }

private val STATEMENT_EXTENSIONS = setOf("csv", "tsv", "ofx", "qfx", "qif")
private val STATEMENT_MIME_TYPES =
    setOf(
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
        "application/csv",
        "application/ofx",
        "application/x-ofx",
        "application/x-qfx",
        "application/vnd.intu.qfx",
        "application/qif",
        "application/x-qif",
    )
private val UNTYPED_MIME_TYPES = setOf("application/octet-stream", "text/plain")

/** A photo goes to the receipt reader and a CSV, TSV, OFX, QFX or QIF file to the statement import; anything else is refused. */
internal fun droppedFileKind(
    mimeType: String?,
    name: String?,
): DroppedFileKind? {
    val mime =
        mimeType
            ?.lowercase()
            ?.substringBefore(';')
            ?.trim()
            .orEmpty()
    val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> DroppedFileKind.IMAGE
        extension in STATEMENT_EXTENSIONS || mime in STATEMENT_MIME_TYPES -> DroppedFileKind.STATEMENT
        else -> null
    }
}

/** Whether a drag carrying [mimeType] might hold a file of a kind the target takes, before its name is known. */
internal fun mayAcceptDrop(
    mimeType: String,
    statements: Boolean,
    images: Boolean,
): Boolean {
    val mime = mimeType.lowercase()
    return when {
        mime.startsWith("image/") -> images
        mime in STATEMENT_MIME_TYPES || mime in UNTYPED_MIME_TYPES -> statements
        else -> false
    }
}

/**
 * Takes files dragged in from another app, tinting the target while one is over it: statements go to [onStatement] and
 * photos to [onImage] as a reference the receipt reader can open. A null callback turns that kind off.
 */
@Composable
internal expect fun rememberFileDropTarget(
    onStatement: ((PickedStatementFile) -> Unit)?,
    onImage: ((String) -> Unit)?,
): Modifier
