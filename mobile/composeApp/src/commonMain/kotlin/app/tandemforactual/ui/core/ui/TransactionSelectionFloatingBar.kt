@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2

/** Select all and delete for selected transactions, as a compact pill on the left so the add button stays reachable. */
@Composable
internal fun TransactionSelectionFloatingBar(
    allSelected: Boolean,
    canDelete: Boolean,
    isWorking: Boolean = false,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        Row(
            Modifier.padding(TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            val label = if (allSelected) "Deselect all" else "Select all"
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(tandemPalette.accentSurface)
                    .clickable(enabled = !isWorking, role = Role.Button, onClickLabel = label, onClick = onToggleAll)
                    .padding(horizontal = TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isWorking) {
                    TandemLoadingIndicator(Modifier.size(18.dp), tandemPalette.accent, 2.dp)
                } else {
                    Icon(
                        if (allSelected) Lucide.Check else Lucide.Square,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tandemPalette.accent,
                    )
                }
                Text(
                    label,
                    modifier = Modifier.padding(start = TandemSpacing.small),
                    color = tandemPalette.accent,
                    fontSize = TandemTypography.label,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TandemIconButton(
                Lucide.Trash2,
                "Delete selected transactions",
                enabled = canDelete && !isWorking,
                tint = tandemPalette.negative,
                containerColor = tandemPalette.negative.copy(alpha = 0.12f),
                onClick = onDelete,
            )
        }
    }
}
