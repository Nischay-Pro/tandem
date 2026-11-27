@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.tandemListItemModifier
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

data class WidgetSetupOption(
    val id: String,
    val title: String,
    val subtitle: String,
)

/** What a widget's settings page offers; [options] is null until they've loaded and empty when there's nothing to pick. */
data class WidgetSetupContent(
    val title: String,
    val intro: String,
    val options: List<WidgetSetupOption>?,
    val emptyMessage: String,
    val multiple: Boolean,
    val initial: Set<String>,
    val confirmLabel: String,
)

/** A widget's settings page in the app's own theme: someone picks one option, or several when [WidgetSetupContent.multiple]. */
@Composable
fun WidgetSetupScreen(
    preferences: Map<String, String>,
    content: WidgetSetupContent,
    onClose: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    TandemTheme(
        ThemeMode.entries.firstOrNull { it.name == preferences["theme"] } ?: ThemeMode.SYSTEM,
        AccentChoice.entries.firstOrNull { it.name == preferences["accent"] } ?: AccentChoice.ACTUAL_PURPLE,
        reduceMotion = preferences["reduceMotionMode"] == "on" || preferences["reduceMotion"] == "true",
        animateThemeChanges = false,
    ) {
        WidgetSetupPage(content, onClose, onConfirm)
    }
}

@Composable
private fun WidgetSetupPage(
    content: WidgetSetupContent,
    onClose: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val palette = tandemPalette
    val options = content.options
    var chosen by remember(options) {
        val known = options.orEmpty().map { it.id }.toSet()
        mutableStateOf(content.initial.filter { it in known }.toSet())
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding()
            .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        TandemModalHeader(content.title, onClose = onClose)
        val message = if (options?.isEmpty() == true) content.emptyMessage else content.intro
        Text(message, color = palette.muted, fontSize = TandemTypography.body)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = TandemSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            items(options.orEmpty(), key = { it.id }) { option ->
                Column(tandemListItemModifier(tandemReduceMotion)) {
                    WidgetSetupRow(option, option.id in chosen, content.multiple) { selected ->
                        chosen =
                            when {
                                !content.multiple -> setOf(option.id)
                                selected -> chosen + option.id
                                else -> chosen - option.id
                            }
                    }
                }
            }
        }
        if (!options.isNullOrEmpty()) {
            PrimaryButton(content.confirmLabel, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                onConfirm(options.map { it.id }.filter { it in chosen })
            }
        }
    }
}

@Composable
private fun WidgetSetupRow(
    option: WidgetSetupOption,
    selected: Boolean,
    multiple: Boolean,
    onChange: (Boolean) -> Unit,
) {
    if (multiple) {
        CheckBoxRow(option.title, option.subtitle, selected, enabled = true, onChange = onChange)
    } else {
        ChoiceRow(option.title, option.subtitle, selected) { onChange(true) }
    }
}
