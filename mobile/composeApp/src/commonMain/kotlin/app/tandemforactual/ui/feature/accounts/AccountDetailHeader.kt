@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt

/** Prompt to choose a budget when none is open. */
@Composable
internal fun AccountDetailChooseBudget(navigation: AppNavigation) {
    EmptyStateCard(
        "Choose a budget",
        "Open an Actual budget to view its accounts.",
        actionLabel = "Choose budget",
        onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
    )
}

/** Budget status, edit button and balance hero at the top of the account detail screen. */
@Composable
internal fun AccountDetailHeader(
    accountName: String,
    description: String,
    balance: String,
    converted: String?,
    showEdit: Boolean,
    editEnabled: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Box(Modifier.weight(1f)) {
            if (description.isNotBlank()) Badge(description, tandemPalette.muted)
        }
        if (showEdit) {
            TandemIconButton(
                Lucide.Pencil,
                "Edit $accountName",
                enabled = editEnabled,
                onClick = onEdit,
            )
        }
    }
    AmountHero("Available balance", balance, listOfNotNull(converted, accountName, description).joinToString(" · "))
}

/** Add, reconcile and import shortcuts for the account. */
@Composable
internal fun AccountQuickActions(
    showAccountActions: Boolean,
    addEnabled: Boolean,
    reconcileEnabled: Boolean,
    importEnabled: Boolean,
    onAdd: () -> Unit,
    onReconcile: () -> Unit,
    onImport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        AccountQuickAction(
            Lucide.Plus,
            "Add",
            "Add transaction",
            enabled = addEnabled,
            emphasized = true,
            modifier = Modifier.weight(1f),
            onClick = onAdd,
        )
        if (showAccountActions) {
            AccountQuickAction(
                Lucide.Check,
                "Reconcile",
                "Reconcile account",
                enabled = reconcileEnabled,
                modifier = Modifier.weight(1f),
                onClick = onReconcile,
            )
            AccountQuickAction(
                Lucide.Receipt,
                "Import",
                "Import statement",
                enabled = importEnabled,
                modifier = Modifier.weight(1f),
                onClick = onImport,
            )
        }
    }
}

@Composable
private fun AccountQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accessibilityLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val content = if (emphasized) palette.accentContent else palette.text
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(if (emphasized) palette.accent else palette.raised)
            .then(
                if (emphasized) {
                    Modifier
                } else {
                    Modifier.border(1.dp, palette.border, RoundedCornerShape(TandemShapes.large))
                },
            ).clickable(enabled = enabled, role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick)
            .semantics { contentDescription = accessibilityLabel }
            .padding(vertical = TandemSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = content)
        Text(label, color = content, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
    }
}
