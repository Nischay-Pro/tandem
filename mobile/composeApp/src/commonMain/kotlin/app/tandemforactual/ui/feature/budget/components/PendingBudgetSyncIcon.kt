@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide

/** Cloud icon marking a budget amount that is still waiting to sync. */
@Composable
internal fun PendingBudgetSyncIcon() {
    Icon(
        imageVector = Lucide.CloudSync,
        contentDescription = "Waiting to sync",
        modifier = Modifier.size(18.dp),
        tint = tandemPalette.accent,
    )
}
