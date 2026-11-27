package app.tandemforactual.ui.core.automation

/** Whether this platform's automation apps are Shortcuts and Siri rather than apps such as Tasker. */
internal expect val automationViaShortcuts: Boolean

/** Whether assistants such as Gemini can call Tandem here, which Android offers from Android 16. */
internal expect val automationViaAssistants: Boolean
