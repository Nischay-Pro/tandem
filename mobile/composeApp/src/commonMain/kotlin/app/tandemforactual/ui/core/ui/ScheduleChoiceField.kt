@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.FieldFrame
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide

@Composable
internal fun ScheduleChoiceField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    FieldFrame(label, onClickLabel = "Choose $label", onClick = { expanded = true }) {
        Text(
            selectedLabel,
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
    }
    TandemChoiceDialog(visible = expanded, onDismissRequest = { expanded = false }) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(TandemSpacing.medium)) {
                TandemModalHeader(
                    title = "Choose $label",
                    titleSize = TandemTypography.cardTitle,
                    onClose = { expanded = false },
                )
                options.forEach { (id, name) ->
                    ListRow(
                        name,
                        trailing = if (name == selectedLabel) "✓" else null,
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
