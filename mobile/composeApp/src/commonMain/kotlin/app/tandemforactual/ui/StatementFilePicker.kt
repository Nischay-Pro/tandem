package app.tandemforactual.ui

import androidx.compose.runtime.Composable

internal data class PickedStatementFile(
    val name: String,
    val contents: String?,
    val errorMessage: String? = null,
)

internal const val MAX_STATEMENT_IMPORT_CHARS = 10_000_000
internal const val MAX_STATEMENT_IMPORT_BYTES = 10_000_000

internal fun decodeStatementBytes(bytes: ByteArray): String =
    runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
        .getOrElse {
            val windows1252 =
                mapOf(
                    0x80 to '€',
                    0x82 to '‚',
                    0x83 to 'ƒ',
                    0x84 to '„',
                    0x85 to '…',
                    0x86 to '†',
                    0x87 to '‡',
                    0x88 to 'ˆ',
                    0x89 to '‰',
                    0x8A to 'Š',
                    0x8B to '‹',
                    0x8C to 'Œ',
                    0x8E to 'Ž',
                    0x91 to '‘',
                    0x92 to '’',
                    0x93 to '“',
                    0x94 to '”',
                    0x95 to '•',
                    0x96 to '–',
                    0x97 to '—',
                    0x98 to '˜',
                    0x99 to '™',
                    0x9A to 'š',
                    0x9B to '›',
                    0x9C to 'œ',
                    0x9E to 'ž',
                    0x9F to 'Ÿ',
                )
            buildString(bytes.size) {
                bytes.forEach { byte ->
                    val value = byte.toInt() and 0xFF
                    append(windows1252[value] ?: value.toChar())
                }
            }
        }

@Composable
internal expect fun rememberStatementFilePicker(onPicked: (PickedStatementFile) -> Unit): () -> Unit
