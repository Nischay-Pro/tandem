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
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SegmentedSelector

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
    onSyncNow: () -> Unit,
    backgroundSyncSupported: Boolean,
) {
    val hasSelectedBudget = state.selectedBudgetId.isNotBlank()
    val syncState = mobileSession.localSyncState
    val syncStatus =
        when {
            !hasSelectedBudget -> "No budget selected"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            syncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Never synced"
        }
    val syncDescription =
        when {
            !hasSelectedBudget -> "Sign in and choose an Actual budget to sync"
            mobileSession.isSyncing -> "Reading changes from Actual"
            mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
            syncState?.lastSyncedAtEpochMs != null -> "Last successful sync"
            else -> "Open a budget and sync to save it on this device"
        }
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
        SectionCard("Current budget", if (hasSelectedBudget) null else "Choose an Actual budget", animateDetail = true) {
            ListRow(
                "Sync now",
                "Read changes and check pending edits",
                when {
                    mobileSession.isSyncing -> "Syncing"
                    hasSelectedBudget -> "Sync"
                    else -> "Unavailable"
                },
                options = ListRowOptions(animateChanges = true),
                onClick = onSyncNow.takeIf { hasSelectedBudget && !mobileSession.isSyncing },
            )
            DividerLine()
            ListRow(
                "Sync status",
                syncDescription,
                syncStatus,
                options =
                    ListRowOptions(
                        trailingColor =
                            if (mobileSession.syncError !=
                                null
                            ) {
                                tandemPalette.warning
                            } else {
                                null
                            },
                        animateChanges = true,
                    ),
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
