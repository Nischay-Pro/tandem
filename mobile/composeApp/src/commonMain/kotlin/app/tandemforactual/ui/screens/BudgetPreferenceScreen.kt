@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SegmentedSelector
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide

@Composable
internal fun BudgetPreferencesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val currencyCode = state.budgetDisplaySettings?.currencyCode ?: platformCurrencyCode()
    PageColumn {
        SectionCard(
            "Number format",
            if (state.budgetDisplaySettings != null) "Uses Actual budget settings" else "Device locale fallback",
        ) {
            ListRow(
                "Currency",
                if (mobileSession.budgetDisplaySettings != null) "Selected Actual budget" else "Follows the device locale",
                currencyCode,
            )
            DividerLine()
            ListRow("Start of week", "Follows the device locale", formatFirstWeekday())
        }
        SectionCard("Planning") {
            ListRow(
                "Available category amounts",
                "From the Actual budget",
            )
            DividerLine()
            ListRow("Carry balances forward", "Change this in Actual", "Read-only")
        }
        PreviewNote("Tandem displays planning rules set in Actual. To change them, update the rules on the Actual website.")
    }
}

@Composable
internal fun SyncPreferencesScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    backgroundSyncSupported: Boolean,
) {
    PageColumn {
        SectionCard("When to sync") {
            ToggleRow("Sync when opening a budget", "Check for server changes when returning to a budget", state.syncOnOpen) {
                state.syncOnOpen =
                    it
            }
            if (backgroundSyncSupported) {
                DividerLine()
                ToggleRow(
                    "Schedule background sync",
                    if (state.selectedBudgetEncrypted) {
                        "Encrypted budgets sync only while unlocked."
                    } else {
                        "About every six hours, even when Tandem is closed"
                    },
                    state.backgroundSyncEnabled && !state.selectedBudgetEncrypted,
                    enabled = !state.selectedBudgetEncrypted,
                ) { state.backgroundSyncEnabled = it }
            } else {
                InfoBubble("Background sync isn’t available on this device.")
            }
        }
        SectionCard("Sync over", "Automatic syncs use this. Sync now always works.") {
            Column(Modifier.padding(TandemSpacing.large)) {
                SyncNetworkChoices(state.syncNetwork) { state.syncNetwork = it }
            }
        }
        SurfaceCard {
            ListRow(
                "Sync Center",
                "Sync now, see status, and review changes",
                leading = RowLeading.Icon(Lucide.CloudSync),
                onClick = { navigation.open(AppRoute.SYNC_CENTER) },
            )
        }
        AnimatedPreviewNote(mobileSession.syncError)
    }
}

@Composable
internal fun SyncNetworkChoices(
    selected: SyncNetwork,
    onSelect: (SyncNetwork) -> Unit,
) {
    val order = listOf(SyncNetwork.MOBILE_DATA, SyncNetwork.WIFI, SyncNetwork.ANY)
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        SegmentedSelector(order.map { it.shortLabel to (it == selected) }) { onSelect(order[it]) }
        Text(selected.detail, color = tandemPalette.muted, fontSize = TandemTypography.caption)
    }
}
