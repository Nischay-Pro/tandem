package app.tandemforactual.ui

internal fun shouldStackActionButtons(fontScale: Float): Boolean = fontScale >= 1.2f

internal fun budgetHeaderTitleMaxLines(fontScale: Float): Int = if (fontScale >= 1.5f) 4 else 2
