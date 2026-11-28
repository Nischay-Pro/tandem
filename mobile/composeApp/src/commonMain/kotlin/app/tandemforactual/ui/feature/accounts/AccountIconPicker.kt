@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.ui.accountIconChoices
import app.tandemforactual.ui.core.ui.accountIconFor
import app.tandemforactual.ui.core.ui.accountTypeIcon
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.launch

/** The account's icon in its options, and a sheet to pick another symbol or go back to the usual one. */
@Composable
internal fun AccountIconPicker(
    account: LiveAccountSummary,
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val picked = mobileSession.planning.accountIcons[account.id]
    var choosing by remember(account.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun choose(icon: String?) {
        choosing = false
        scope.launch { mobileSession.planning.saveAccountIcon(state.selectedBudgetId, account.id, icon) }
    }
    SurfaceCard {
        ListRow(
            "Icon",
            if (picked == null) "Automatic" else "Chosen",
            leading = RowLeading.Icon(accountIconFor(picked) ?: accountTypeIcon(account.name, account.kind)),
            onClick = { choosing = true }.takeUnless { state.readOnlyMode },
        )
    }
    TandemTaskSheet(visible = choosing, onDismissRequest = { choosing = false }, onDismissed = {}) {
        SheetCard(
            header = { TandemModalHeader("Account icon", onClose = { choosing = false }) },
            actions = {
                TandemActionButton("Use the automatic icon", TandemActionButtonTone.NEUTRAL, enabled = picked != null) { choose(null) }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                accountIconChoices.chunked(ICONS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        row.forEach { (name, icon) ->
                            TandemIconButton(
                                icon,
                                name.replace('-', ' '),
                                containerColor = if (name == picked) tandemPalette.accent.copy(alpha = SELECTED_ALPHA) else null,
                            ) { choose(name) }
                        }
                    }
                }
            }
        }
    }
}

private const val ICONS_PER_ROW = 5
private const val SELECTED_ALPHA = 0.18f
