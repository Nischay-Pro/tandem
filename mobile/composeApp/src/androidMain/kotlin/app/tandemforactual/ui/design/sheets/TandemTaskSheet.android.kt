package app.tandemforactual.ui.design.sheets

import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

internal actual fun edgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean,
    secure: Boolean,
) = DialogProperties(
    usePlatformDefaultWidth = false,
    dismissOnBackPress = dismissOnBackPress,
    decorFitsSystemWindows = false,
    securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
)
