package app.tandemforactual.ui.components

import androidx.compose.ui.window.DialogProperties

internal actual fun edgeToEdgeDialogProperties(dismissOnBackPress: Boolean) =
    DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissOnBackPress)
