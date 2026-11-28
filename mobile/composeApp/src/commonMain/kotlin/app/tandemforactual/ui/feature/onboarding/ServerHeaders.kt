@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow

private val HEADER_NAME = Regex("""[A-Za-z0-9!#$%&'*+.^_`|~-]+""")

/** Headers written one per line as `Name: value`, or the first line that can't be read. Blank lines are skipped. */
internal fun parseServerHeaders(text: String): Result<Map<String, String>> {
    val headers = linkedMapOf<String, String>()
    val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
    for (line in lines) {
        val name = line.substringBefore(':', "").trim()
        if (!HEADER_NAME.matches(name)) {
            return Result.failure(IllegalArgumentException("Write each header as Name: value. “$line” isn’t."))
        }
        headers[name] = line.substringAfter(':').trim()
    }
    return Result.success(headers)
}

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
