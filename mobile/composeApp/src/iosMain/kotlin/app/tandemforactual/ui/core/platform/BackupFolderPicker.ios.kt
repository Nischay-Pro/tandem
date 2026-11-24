@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberBackupFolderPicker(onPicked: (folder: String, label: String) -> Unit): (() -> Unit)? = null
