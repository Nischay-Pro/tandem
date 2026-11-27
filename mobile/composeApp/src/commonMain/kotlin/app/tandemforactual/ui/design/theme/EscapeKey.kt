package app.tandemforactual.ui.design.theme

/** Android already turns an unhandled Esc into Back; iOS has no back key, so there Compose maps Esc itself. */
internal expect val escapeClosesSheetsInCompose: Boolean

/** Runs the topmost enabled [TandemBackHandler] for Esc; false where the platform handles Esc itself. */
internal expect fun dispatchEscapeAsBack(): Boolean
