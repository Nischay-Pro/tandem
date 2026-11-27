@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.format

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.language_change
import app.tandemforactual.ui.generated.resources.language_close
import app.tandemforactual.ui.generated.resources.language_device_detail
import app.tandemforactual.ui.generated.resources.settings_language
import app.tandemforactual.ui.generated.resources.settings_language_device
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.jetbrains.compose.resources.stringResource

internal data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
)

/** Languages offered in the picker; a translation lives in composeResources/values-<tag>/strings.xml. */
internal val APP_LANGUAGES =
    listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("te", "తెలుగు", "Telugu"),
    )

/** A saved choice that's no longer offered falls back to the device language. */
internal fun appLanguageTag(saved: String): String? = APP_LANGUAGES.firstOrNull { it.tag == saved }?.tag

internal class AppLanguageController(
    val current: String,
    val choose: (String) -> Unit,
)

internal val LocalAppLanguage = staticCompositionLocalOf<AppLanguageController?> { null }

/** Overrides the language used for resources and formatting; null follows the device. */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
internal fun AppLocaleEnvironment(
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) { content() }
    }
}

@Composable
internal fun LanguageButton() {
    val controller = LocalAppLanguage.current ?: return
    var open by remember { mutableStateOf(false) }
    TandemIconButton(
        Lucide.Languages,
        stringResource(Res.string.settings_language),
        onClickLabel = stringResource(Res.string.language_change),
        iconSizeDp = 22,
    ) { open = true }
    LanguagePickerSheet(open, controller) { open = false }
}

@Composable
internal fun LanguagePickerSheet(
    visible: Boolean,
    controller: AppLanguageController,
    onDismiss: () -> Unit,
) {
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        LanguagePickerContent(controller, onDismiss)
    }
}

@Composable
private fun LanguagePickerContent(
    controller: AppLanguageController,
    onDismiss: () -> Unit,
) {
    val palette = tandemPalette
    SheetCard(
        maxWidth = 460.dp,
        spacing = TandemSpacing.small,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Icon(Lucide.Languages, contentDescription = null, modifier = Modifier.size(24.dp), tint = palette.accent)
                Text(
                    stringResource(Res.string.settings_language),
                    modifier = Modifier.weight(1f),
                    color = palette.text,
                    fontSize = TandemTypography.sectionTitle,
                    fontFamily = tandemAlloFamily(),
                    fontWeight = FontWeight.Bold,
                )
                TandemIconButton(Lucide.X, stringResource(Res.string.language_close), sizeDp = 40, modalClose = true, onClick = onDismiss)
            }
        },
    ) {
        ChoiceRow(
            stringResource(Res.string.settings_language_device),
            stringResource(Res.string.language_device_detail),
            selected = controller.current.isBlank(),
        ) {
            controller.choose("")
            onDismiss()
        }
        APP_LANGUAGES.forEach { language ->
            ChoiceRow(
                language.nativeName,
                language.englishName.takeIf { it != language.nativeName },
                selected = controller.current == language.tag,
            ) {
                controller.choose(language.tag)
                onDismiss()
            }
        }
    }
}
