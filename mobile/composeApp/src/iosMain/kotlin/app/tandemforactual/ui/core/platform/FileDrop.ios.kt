package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun rememberFileDropTarget(
    onStatement: ((PickedStatementFile) -> Unit)?,
    onImage: ((String) -> Unit)?,
): Modifier = Modifier
