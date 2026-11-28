@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatServerHeaders
import app.tandemforactual.ui.core.format.parseServerHeaders
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.updateCustomHeaders
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import kotlinx.coroutines.launch

/** The headers sent with every request to this server, editable after signing in. */
@Composable
internal fun CustomHeadersSection(mobileSession: TandemMobileSession) {
    val current = formatServerHeaders(mobileSession.apiClient.customHeaders)
    var text by remember(current) { mutableStateOf(current) }
    val parsed = parseServerHeaders(text)
    val scope = rememberCoroutineScope()
    SectionCard("Custom headers", "Sent with every request to this server, for servers behind an access proxy.") {
        TextFieldCard(
            "Headers, one per line",
            text,
            { text = it },
            TextFieldOptions(placeholder = "CF-Access-Client-Id: …", singleLine = false),
        )
        parsed.exceptionOrNull()?.message?.let { InlineMessage(it, InlineMessageKind.ERROR) }
        TandemActionButton(
            "Save headers",
            TandemActionButtonTone.NEUTRAL,
            enabled = text != current && parsed.isSuccess,
        ) {
            parsed.getOrNull()?.let { headers -> scope.launch { mobileSession.updateCustomHeaders(headers) } }
        }
    }
}
