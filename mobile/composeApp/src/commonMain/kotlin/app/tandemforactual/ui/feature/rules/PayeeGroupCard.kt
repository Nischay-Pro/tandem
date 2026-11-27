@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.data.store.PayeeDuplicateGroup
import app.tandemforactual.data.store.PayeeUsage
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Merge
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

/** Changes made to a suggested group before merging: payees left out or added, the one kept and the final name. */
internal data class PayeeGroupEdit(
    val left: Set<String> = emptySet(),
    val added: List<PayeeUsage> = emptyList(),
    val keepId: String? = null,
    val name: String? = null,
)

/** The group as edited, or null once fewer than two payees are left to merge. */
internal fun PayeeDuplicateGroup.edited(edit: PayeeGroupEdit): PayeeDuplicateGroup? {
    val included = (members + edit.added).distinctBy { it.id }.filter { it.id !in edit.left }
    if (included.size < 2) return null
    val ids = included.map { it.id }
    val keep = listOfNotNull(edit.keepId, keepId).firstOrNull { it in ids } ?: included.maxBy { it.transactions }.id
    val newName = edit.name?.trim()?.takeIf { it.isNotEmpty() } ?: name
    return copy(name = newName, keepId = keep, members = included)
}

@Composable
internal fun PayeeGroupCard(
    group: PayeeDuplicateGroup,
    payees: List<PayeeUsage>,
    enabled: Boolean,
    onMerge: (PayeeDuplicateGroup) -> Unit,
    onNotDuplicates: () -> Unit,
) {
    var edit by remember(group) { mutableStateOf(PayeeGroupEdit()) }
    val result = group.edited(edit)
    val shown = (group.members + edit.added).distinctBy { it.id }
    val detail = group.stripped.takeIf { it.isNotEmpty() }?.let { "Set aside: ${it.take(MAX_STRIPPED_SHOWN).joinToString(", ")}" }
    SectionCard(result?.name ?: group.name, detail = detail) {
        shown.forEachIndexed { index, member ->
            if (index > 0) DividerLine()
            val kept = result?.keepId == member.id
            ToggleRow(
                member.name,
                (if (kept) "Kept · " else "") + transactionCount(member.transactions),
                checked = member.id !in edit.left,
                enabled = enabled,
            ) { on -> edit = edit.copy(left = if (on) edit.left - member.id else edit.left + member.id) }
        }
        TextFieldCard("Name", edit.name ?: group.name, { edit = edit.copy(name = it) })
        result?.let { merged ->
            val keptName = merged.members.first { it.id == merged.keepId }.name
            PayeeChoice("Keep", Lucide.CircleCheck, keptName, merged.members) { id -> edit = edit.copy(keepId = id) }
        }
        val addable = payees.filter { payee -> shown.none { it.id == payee.id } }.sortedBy { it.name.lowercase() }
        PayeeChoice("Add a payee", Lucide.Plus, "", addable) { id ->
            payees.firstOrNull { it.id == id }?.let { edit = edit.copy(added = edit.added + it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            TandemActionButton(
                "Merge into ${result?.name ?: group.name}",
                TandemActionButtonTone.PRIMARY,
                modifier = Modifier.weight(1f),
                icon = Lucide.Merge,
                enabled = enabled && result != null,
                onClick = { result?.let(onMerge) },
            )
            TandemActionButton(
                "Not the same",
                TandemActionButtonTone.NEUTRAL,
                modifier = Modifier.weight(1f),
                icon = Lucide.X,
                enabled = enabled,
                onClick = onNotDuplicates,
            )
        }
    }
}

@Composable
private fun PayeeChoice(
    label: String,
    icon: ImageVector,
    selected: String,
    candidates: List<PayeeUsage>,
    onSelect: (String) -> Unit,
) {
    if (candidates.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListRow(label, trailing = selected, leading = RowLeading.Icon(icon), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { candidate ->
                DropdownMenuItem(onClick = {
                    onSelect(candidate.id)
                    expanded = false
                }) {
                    Text(candidate.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}

private fun transactionCount(count: Int): String = if (count == 1) "1 transaction" else "$count transactions"

private const val MAX_STRIPPED_SHOWN = 4
