package app.tandemforactual.ui.core.format

/** [text] with its spaces made non-breaking, so a date like "Nov 22, 2026" never wraps partway through. */
internal fun keepTogether(text: String): String = text.replace(' ', ' ')
