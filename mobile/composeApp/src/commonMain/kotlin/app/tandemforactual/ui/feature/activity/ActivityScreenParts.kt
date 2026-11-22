@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide

internal fun shouldShowActivityDateHeader(
    currentDateLabel: String,
    previousDateLabel: String?,
): Boolean = previousDateLabel != currentDateLabel

@Composable
internal fun ActivitySortControl(
    selected: ActivitySortOrder,
    onSelect: (ActivitySortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${selected.label}", color = tandemPalette.text, fontSize = TandemTypography.caption)
            Icon(
                Lucide.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(start = 2.dp),
                tint = tandemPalette.muted,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ActivitySortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                ) {
                    Text(option.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
