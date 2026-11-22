package app.tandemforactual.ui

import androidx.compose.runtime.Composable

/** Hands a file to the system share sheet so it can be saved or sent anywhere. */
@Composable
internal expect fun rememberFileSharer(): (name: String, mimeType: String, contents: String) -> Unit
