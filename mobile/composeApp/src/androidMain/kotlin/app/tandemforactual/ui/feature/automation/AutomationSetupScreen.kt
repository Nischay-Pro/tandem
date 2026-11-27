@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette

/** One setting an automation action takes: typed text or an automation app's variable, with names to pick from. */
data class AutomationSetupField(
    val key: String,
    val label: String,
    val hint: String,
    val choices: List<String> = emptyList(),
)

data class AutomationSetupContent(
    val title: String,
    val intro: String,
    val fields: List<AutomationSetupField>,
    val initial: Map<String, String>,
    val notice: String? = null,
)

private const val MAX_CHOICES = 12

/** An automation action's settings page in the app's own theme. */
@Composable
fun AutomationSetupScreen(
    preferences: Map<String, String>,
    content: AutomationSetupContent,
    onClose: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    TandemTheme(
        ThemeMode.entries.firstOrNull { it.name == preferences["theme"] } ?: ThemeMode.SYSTEM,
        AccentChoice.entries.firstOrNull { it.name == preferences["accent"] } ?: AccentChoice.ACTUAL_PURPLE,
        reduceMotion = preferences["reduceMotionOverride"] == "true",
        animateThemeChanges = false,
    ) {
        AutomationSetupPage(content, onClose, onSave)
    }
}

@Composable
private fun AutomationSetupPage(
    content: AutomationSetupContent,
    onClose: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
) {
    val palette = tandemPalette
    val values = remember(content) { mutableStateMapOf<String, String>().apply { putAll(content.initial) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        TandemModalHeader(content.title, onClose = onClose)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Text(content.intro, color = palette.muted, fontSize = TandemTypography.body)
            content.notice?.let { Text(it, color = palette.warning, fontSize = TandemTypography.body) }
            content.fields.forEach { field ->
                val value = values[field.key].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TextFieldCard(field.label, value, { values[field.key] = it }, TextFieldOptions(placeholder = field.hint))
                    val shown = field.choices.filter { it.contains(value.trim(), ignoreCase = true) || value == it }.take(MAX_CHOICES)
                    if (shown.isNotEmpty()) {
                        FilterPills(shown.map { it to it }, value, { values[field.key] = it })
                    }
                }
            }
        }
        PrimaryButton("Save", modifier = Modifier.fillMaxWidth()) {
            onSave(content.fields.associate { it.key to values[it.key].orEmpty().trim() })
        }
    }
}
