package app.tandemforactual.ui.core.importing

internal enum class StatementFormat {
    DELIMITED,
    QIF,
    OFX,
}

private val ofxMarker = Regex("(?i)OFXHEADER\\s*:|<\\?OFX|<OFX>|<STMTTRN>")
private val qifHeader = Regex("(?i)^!(?:Type|Account|Option|Clear)\\b")

internal fun detectStatementFormat(contents: String): StatementFormat {
    val content = contents.removePrefix("﻿").trimStart()
    if (content.startsWith("%PDF")) {
        throw StatementImportParseException("PDF statements can’t be read. Export a CSV, QIF, OFX, or QFX file from your bank.")
    }
    if (content.take(BINARY_SAMPLE_CHARS).any { it == '\u0000' || (it < ' ' && it !in "\t\r\n\u000C") }) {
        throw StatementImportParseException("Choose a CSV, TSV, QIF, OFX, or QFX statement.")
    }
    if (ofxMarker.containsMatchIn(content.take(OFX_SAMPLE_CHARS))) return StatementFormat.OFX
    val lines = content.lineSequence().map(String::trim).filter(String::isNotEmpty).take(QIF_SAMPLE_LINES).toList()
    if (lines.firstOrNull()?.let(qifHeader::containsMatchIn) == true) return StatementFormat.QIF
    val looksLikeQif =
        lines.any { it == "^" } &&
            lines.any { it.startsWith('D') && it.length > 1 } &&
            lines.any { (it.startsWith('T') || it.startsWith('U')) && it.length > 1 }
    return if (looksLikeQif) StatementFormat.QIF else StatementFormat.DELIMITED
}

private const val BINARY_SAMPLE_CHARS = 2_048
private const val OFX_SAMPLE_CHARS = 65_536
private const val QIF_SAMPLE_LINES = 200
