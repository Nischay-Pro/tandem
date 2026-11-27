@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.keyboard.Shortcut
import app.tandemforactual.ui.core.keyboard.ShortcutKey
import app.tandemforactual.ui.core.keyboard.commandKeyLabel
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

/** The in-app cheat sheet: every row of the shortcut table, grouped by where it works. */
@Composable
internal fun ShortcutSheet(
    visible: Boolean,
    shortcuts: List<Shortcut>,
    onClose: () -> Unit,
) {
    TandemTaskSheet(visible = visible, onDismissRequest = onClose, onDismissed = {}) {
        SheetCard(
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Keyboard shortcuts",
                        Modifier.weight(1f).semantics { heading() },
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontFamily = tandemAlloFamily(),
                        fontWeight = FontWeight.Bold,
                    )
                    TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, onClick = onClose)
                }
            },
        ) {
            shortcutSections(shortcuts).forEach { (title, rows) ->
                ShortcutSection(title, rows)
            }
        }
    }
}

/** The table's rows by section title, keeping table order and showing a row listed in two scopes once per section. */
internal fun shortcutSections(shortcuts: List<Shortcut>): List<Pair<String, List<Pair<String, List<String>>>>> =
    shortcuts
        .groupBy { it.scope.title }
        .map { (title, rows) ->
            title to rows.map { it.label to it.keys.map(::shortcutKeyText).distinct() }
        }

internal fun shortcutKeyText(key: ShortcutKey): String =
    buildList {
        if (key.command) add(commandKeyLabel)
        if (key.shift && !key.printable) add("Shift")
        add(if (key.printable) key.symbol.uppercase() else key.symbol)
    }.joinToString(" ")

@Composable
private fun ShortcutSection(
    title: String,
    rows: List<Pair<String, List<String>>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(
            title,
            Modifier.semantics { heading() },
            color = tandemPalette.muted,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
        rows.forEach { (label, keys) -> ShortcutRow(label, keys) }
    }
}

@Composable
private fun ShortcutRow(
    label: String,
    keys: List<String>,
) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label, ${keys.joinToString(" or ")}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text(label, Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
        keys.forEach { KeyCap(it) }
    }
}

@Composable
private fun KeyCap(text: String) {
    val shape = RoundedCornerShape(TandemShapes.small)
    Text(
        text,
        Modifier
            .background(tandemPalette.raised, shape)
            .border(1.dp, tandemPalette.border, shape)
            .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.xxSmall),
        color = tandemPalette.text,
        fontSize = TandemTypography.label,
        fontWeight = FontWeight.Medium,
    )
}
