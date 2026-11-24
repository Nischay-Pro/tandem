@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable

/** Opens the system folder picker for automatic backups, or null where automatic backups aren't supported yet. */
@Composable
internal expect fun rememberBackupFolderPicker(onPicked: (folder: String, label: String) -> Unit): (() -> Unit)?
