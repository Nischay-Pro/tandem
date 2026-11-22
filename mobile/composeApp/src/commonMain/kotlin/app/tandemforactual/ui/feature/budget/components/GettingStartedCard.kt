@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.X

/** Dismissible card with first-run tips for the budget screen. */
@Composable
internal fun GettingStartedCard(onDismiss: () -> Unit) {
    val palette = tandemPalette
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Getting started",
                    Modifier.weight(1f),
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
                TandemIconButton(Lucide.X, "Dismiss getting started", sizeDp = 36, iconSizeDp = 16, onClick = onDismiss)
            }
            GettingStartedTip(Lucide.Tags, "Tap a category", "See its activity and change what’s assigned.")
            GettingStartedTip(Lucide.Plus, "Add a transaction", "It’s saved on this device first, then synced.")
            GettingStartedTip(Lucide.RefreshCw, "Sync with Actual", "Pull down here or tap the cloud to sync now.")
            Text(
                "Got it",
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(TandemShapes.capsule))
                    .background(palette.accent.copy(alpha = 0.16f))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                color = palette.accent,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GettingStartedTip(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    val palette = tandemPalette
    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(title, color = palette.text, fontSize = TandemTypography.compactBody, fontWeight = FontWeight.SemiBold)
            Text(detail, color = palette.muted, fontSize = TandemTypography.caption)
        }
    }
}
