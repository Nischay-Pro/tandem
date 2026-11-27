@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.runtime.Composable
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

@Composable
internal actual fun SecureSheetContent(
    secure: Boolean,
    content: @Composable () -> Unit,
) = content()
