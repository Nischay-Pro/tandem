@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

/** Picks any number of items from a list; choosing none means all of them. */
@Composable
internal fun ChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    val palette = tandemPalette
    var chosen by remember(title) { mutableStateOf(initial) }
    var query by remember(title) { mutableStateOf("") }
    TandemTaskSheet(visible = true, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader(title, onClose = onDismiss)
                if (options.size > 8) TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Filter the list"))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    options.filter { query.isBlank() || it.second.contains(query.trim(), ignoreCase = true) }.forEach { (id, label) ->
                        val selected = id in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET)
                                .toggleable(value = selected, role = Role.Checkbox) { chosen = if (it) chosen + id else chosen - id }
                                .padding(vertical = TandemSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f), color = palette.text, fontSize = TandemTypography.body)
                            if (selected) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = palette.accent)
                        }
                    }
                }
                Text(
                    if (chosen.isEmpty()) "Nothing picked, so all ${title.lowercase()} are included." else "${chosen.size} picked",
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Clear", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = chosen.isNotEmpty()) {
                        chosen = emptySet()
                    }
                    TandemActionButton("Done", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) { onDone(chosen) }
                }
            }
        }
    }
}
