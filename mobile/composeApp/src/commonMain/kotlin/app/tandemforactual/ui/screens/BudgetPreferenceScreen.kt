@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.SectionCard

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
            mobileSession.isSyncing && mobileSession.syncError != null -> "Retrying"
            mobileSession.isSyncing -> "Syncing"
            mobileSession.syncError != null -> "Needs attention"
            syncState?.lastSyncedAtEpochMs != null -> "Ready"
            else -> "Never synced"
        }
    val syncDescription =
        when {
            !hasSelectedBudget -> "Pair this device and choose an Actual budget to sync"
            mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
            mobileSession.isSyncing -> "Reading changes from Actual"
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
                        "Encrypted budgets can’t sync in the background."
                    } else {
                        "Refresh the selected budget about every six hours"
                    },
                    state.backgroundSyncEnabled && !state.selectedBudgetEncrypted,
                    enabled = !state.selectedBudgetEncrypted,
                ) { state.backgroundSyncEnabled = it }
                if (!state.selectedBudgetEncrypted) {
                    DividerLine()
                    ToggleRow(
                        "Sync on mobile data",
                        if (state.syncOnWifi) "Off pauses sync on metered connections" else "On allows sync on mobile data",
                        !state.syncOnWifi,
                    ) { state.syncOnWifi = !it }
                }
            } else {
                InfoBubble("Background sync isn’t available on this device.")
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
