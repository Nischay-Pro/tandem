@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.parseServerHeaders
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow

/** Extra headers some access proxies need on every request, entered before signing in and kept with the sign-in. */
@Composable
internal fun ServerHeadersField(state: AppState) {
    var open by rememberSaveable { mutableStateOf(state.serverHeaders.isNotBlank()) }
    ToggleRow("Custom headers", "For servers behind an access proxy that needs its own headers", open) { open = it }
    AnimatedReveal(visible = open) {
        TextFieldCard(
            "Headers, one per line",
            state.serverHeaders,
            { state.serverHeaders = it },
            TextFieldOptions(placeholder = "CF-Access-Client-Id: …", singleLine = false, isSecret = false),
        )
    }
    parseServerHeaders(state.serverHeaders).exceptionOrNull()?.message?.let { InlineMessage(it, InlineMessageKind.ERROR) }
}
