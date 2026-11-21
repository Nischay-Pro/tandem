package app.tandemforactual.ui

/** Drives a debug build straight to one screen against a demo server, for automated store and README screenshots. */
data class ScreenshotScript(
    val serverUrl: String,
    val password: String,
    val budgetName: String,
    val screen: String,
    val select: String? = null,
) {
    companion object {
        /** Reads `TANDEM_SCREENSHOT_*` values; null unless the server, password and budget are all given. */
        fun from(values: Map<String, String?>): ScreenshotScript? {
            fun value(name: String) = values["TANDEM_SCREENSHOT_$name"]?.trim()?.takeIf(String::isNotEmpty)
            return ScreenshotScript(
                serverUrl = value("SERVER") ?: return null,
                password = value("PASSWORD") ?: return null,
                budgetName = value("BUDGET") ?: return null,
                screen = value("SCREEN") ?: "budget",
                select = value("SELECT"),
            )
        }
    }
}
