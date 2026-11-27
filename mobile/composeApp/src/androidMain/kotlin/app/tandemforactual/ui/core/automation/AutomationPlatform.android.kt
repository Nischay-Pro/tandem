package app.tandemforactual.ui.core.automation

import android.os.Build

internal actual val automationViaShortcuts: Boolean = false

internal actual val automationViaAssistants: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
