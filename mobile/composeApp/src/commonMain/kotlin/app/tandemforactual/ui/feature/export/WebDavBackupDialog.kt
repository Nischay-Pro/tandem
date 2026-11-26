@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.WebDavClient
import app.tandemforactual.api.client.WebDavException
import app.tandemforactual.ui.core.session.LocalLanAccess
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

/** Where automatic backups are copied on a WebDAV server, checked before it's saved. */
@Composable
internal fun WebDavBackupDialog(
    visible: Boolean,
    url: String?,
    user: String?,
    onDismiss: () -> Unit,
    onSave: (url: String, user: String, password: String) -> Boolean,
    onRemove: (() -> Unit)?,
) {
    var address by remember(visible) { mutableStateOf(url.orEmpty()) }
    var name by remember(visible) { mutableStateOf(user.orEmpty()) }
    var password by remember(visible) { mutableStateOf("") }
    var checking by remember(visible) { mutableStateOf(false) }
    var problem by remember(visible) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lanAccess = LocalLanAccess.current
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Upload to WebDAV", onClose = onDismiss, leadingIcon = Lucide.Cloud)
                BodyText("Each backup is also copied to this folder, such as one on Nextcloud or a NAS. Old copies are removed too.")
                TextFieldCard("Folder address", address, {
                    address = it
                }, TextFieldOptions(placeholder = "https://cloud.example/remote.php/dav/files/sam/Tandem"))
                TextFieldCard("Username", name, { name = it })
                TextFieldCard("Password", password, { password = it }, TextFieldOptions(isSecret = true))
                FieldErrorMessage(problem)
                TandemActionButton(
                    "Check and save",
                    TandemActionButtonTone.PRIMARY,
                    Modifier.fillMaxWidth(),
                    enabled = address.isNotBlank() && name.isNotBlank() && password.isNotEmpty(),
                    loading = checking,
                ) {
                    checking = true
                    problem = null
                    scope.launch {
                        problem =
                            try {
                                lanAccess?.request(address.trim())
                                WebDavClient.connect(address.trim(), name.trim(), password).use { it.check() }
                                null
                            } catch (error: WebDavException) {
                                error.message
                            }
                        checking = false
                        if (problem == null && !onSave(address.trim(), name.trim(), password)) {
                            problem = "Couldn’t save the password on this device. Try again."
                        }
                    }
                }
                if (onRemove != null) {
                    TandemActionButton("Stop uploading", TandemActionButtonTone.NEUTRAL, Modifier.fillMaxWidth(), onClick = onRemove)
                }
            }
        }
    }
}
