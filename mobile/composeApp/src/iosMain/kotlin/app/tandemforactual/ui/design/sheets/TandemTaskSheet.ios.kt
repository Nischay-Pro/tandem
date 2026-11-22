package app.tandemforactual.ui.design.sheets

import androidx.compose.ui.window.DialogProperties

@Suppress("UNUSED_PARAMETER")
internal actual fun edgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean,
    secure: Boolean,
) = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissOnBackPress)
