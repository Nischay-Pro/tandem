@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.ui.ActivitySearchInput
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
internal fun RuleChoiceField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    selectedIds: Set<String>,
    multiple: Boolean = false,
    onSelect: (List<String>) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    var query by rememberSaveable(label) { mutableStateOf("") }
    var picked by remember(expanded, selectedIds) { mutableStateOf(selectedIds) }
    ListRow(
        label,
        trailing = selectedLabel,
        leading = RowLeading.None,
        options = ListRowOptions(dense = true),
        onClick = { expanded = true },
    )
    val close = {
        expanded = false
        query = ""
    }
    TandemChoiceDialog(visible = expanded, onDismissRequest = close) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemModalHeader(title = "Choose ${label.lowercase()}", titleSize = TandemTypography.cardTitle, onClose = close)
                if (options.size > SEARCH_THRESHOLD) {
                    ActivitySearchInput(query, onValueChange = { query = it }, placeholder = "Find ${label.lowercase()}")
                }
                val shown =
                    remember(options, query) {
                        val needle = query.trim().lowercase()
                        if (needle.isEmpty()) options else options.filter { it.second.lowercase().contains(needle) }
                    }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (id, name) ->
                        val selected = id in picked
                        ListRow(
                            name,
                            leading = RowLeading.None,
                            options = ListRowOptions(dense = true),
                            trailingContent =
                                if (selected) {
                                    { Icon(Lucide.Check, contentDescription = "Selected", tint = tandemPalette.accent) }
                                } else {
                                    null
                                },
                            onClick = {
                                if (multiple) {
                                    picked = if (selected) picked - id else picked + id
                                } else {
                                    onSelect(listOf(id))
                                    close()
                                }
                            },
                        )
                    }
                }
                if (multiple) {
                    PrimaryButton("Done", enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        onSelect(options.map { it.first }.filter { it in picked })
                        close()
                    }
                }
            }
        }
    }
}

private const val SEARCH_THRESHOLD = 8
