@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AccountSort
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ChartNoAxesColumn
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Hash
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Overflow menu on the budget header: lock, switch budget, amount style and privacy. */
@Composable
internal fun BudgetHeaderMenu(
    expanded: Boolean,
    state: AppState,
    onDismiss: () -> Unit,
    onSwitchBudget: () -> Unit,
) {
    TandemHeaderMenu(expanded = expanded, onDismiss = onDismiss) {
        if (state.selectedBudgetEncrypted && state.encryptedPassword.isNotBlank()) {
            HeaderMenuAction(Lucide.Lock, "Lock budget", onClick = {
                onDismiss()
                state.encryptedPassword = ""
            })
        }
        HeaderMenuAction(Lucide.ArrowLeftRight, "Switch budget", onClick = onSwitchBudget)
        HeaderMenuDivider()
        HeaderMenuLabel("Category amounts")
        HeaderMenuChoice(Lucide.Hash, "Numbers", selected = !state.budgetShowProgressBars, onClick = {
            state.budgetShowProgressBars = false
            onDismiss()
        })
        HeaderMenuChoice(Lucide.ChartNoAxesColumn, "Progress bars", selected = state.budgetShowProgressBars, onClick = {
            state.budgetShowProgressBars = true
            onDismiss()
        })
        HeaderMenuDivider()
        HeaderMenuLabel("Privacy")
        HeaderMenuChoice(Lucide.EyeOff, "Hide amounts", selected = state.privacyModeEnabled, onClick = {
            state.privacyModeEnabled = !state.privacyModeEnabled
            onDismiss()
        })
    }
}

/** Overflow menu on the accounts header. */
@Composable
internal fun AccountHeaderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
    onManageBanks: () -> Unit,
    onSwitchBudget: () -> Unit,
    onOpenSync: () -> Unit,
    activeSort: AccountSort?,
    onSort: (AccountSort) -> Unit,
) {
    TandemHeaderMenu(expanded = expanded, onDismiss = onDismiss) {
        HeaderMenuLabel("Account actions")
        HeaderMenuAction(Lucide.Plus, "Add account", onClick = onAddAccount)
        HeaderMenuAction(Lucide.Landmark, "Manage bank connections", onClick = onManageBanks)
        HeaderMenuDivider()
        HeaderMenuLabel("Sort accounts")
        HeaderMenuChoice(Lucide.GripVertical, "Custom order", selected = activeSort == null, onClick = onDismiss)
        AccountSort.entries.forEach { option ->
            val icon = if (option.ascending) Lucide.ArrowUp else Lucide.ArrowDown
            HeaderMenuChoice(icon, option.label, selected = activeSort == option, onClick = { onSort(option) })
        }
        HeaderMenuDivider()
        HeaderMenuAction(Lucide.ArrowLeftRight, "Switch budget", onClick = onSwitchBudget)
        HeaderMenuAction(Lucide.CloudSync, "Sync Center", onClick = onOpenSync)
    }
}

@Composable
private fun HeaderMenuLabel(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HeaderMenuDivider() {
    androidx.compose.material.Divider(
        modifier = Modifier.padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = tandemPalette.border,
        thickness = 1.dp,
    )
}

@Composable
private fun HeaderMenuChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) palette.accent else palette.muted)
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (selected) palette.accent else palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
        )
        if (selected) Icon(Lucide.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp), tint = palette.accent)
    }
}
