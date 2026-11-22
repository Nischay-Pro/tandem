@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.configureLocalServer
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import kotlinx.coroutines.launch

@Composable
internal fun LocalServerAddressScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf(state.localServerUrl) }
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf<Boolean?>(null) }
    PageColumn {
        val mainAddress = state.serverUrl.ifBlank { "your server address" }
        InfoBubble(
            "On your home network or VPN, Tandem can talk to your server directly. It uses this address only after " +
                "your server proves it’s yours, and uses $mainAddress everywhere else.",
        )
        TextFieldCard(
            "Local address",
            address,
            {
                address = it
                checked = null
            },
            TextFieldOptions(placeholder = "http://192.168.1.10:19753"),
        )
        FieldErrorMessage(mobileSession.localServerError)
        when (checked) {
            true -> InlineMessage("Your server confirmed this address. Tandem uses it on this network.", InlineMessageKind.SUCCESS)
            false ->
                if (mobileSession.localServerError == null) {
                    InlineMessage(
                        "Tandem couldn’t confirm your server at this address on this network. It will keep using your main address and try again when the network changes.",
                        InlineMessageKind.INFO,
                    )
                }
            null -> Unit
        }
        PrimaryButton(if (checking) "Checking…" else "Save and check", enabled = address.isNotBlank() && !checking, loading = checking) {
            checking = true
            scope.launch {
                val saved = address.trim()
                checked = mobileSession.configureLocalServer(saved)
                if (mobileSession.localServerError == null) state.localServerUrl = saved
                checking = false
            }
        }
        if (state.localServerUrl.isNotBlank()) {
            SecondaryButton("Remove local address") {
                state.localServerUrl = ""
                address = ""
                checked = null
                scope.launch { mobileSession.configureLocalServer(null) }
            }
        }
    }
}
