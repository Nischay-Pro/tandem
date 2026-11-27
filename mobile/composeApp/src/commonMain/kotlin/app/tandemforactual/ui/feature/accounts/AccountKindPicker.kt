@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.CardNetwork
import app.tandemforactual.ui.core.ui.accountKindIcon
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.LocalRowLogos
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide

/** Row with a menu of account kinds; a kind Tandem guessed is marked as a suggestion until the person picks one. */
@Composable
internal fun AccountKindPicker(
    kind: AccountKind,
    suggested: Boolean,
    onKindChange: (AccountKind) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ListRow(
            "Type",
            subtitle = accountKindPickerHint(kind, suggested),
            trailing = kind.label,
            leading = RowLeading.Icon(accountKindIcon(kind)),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AccountKind.entries.forEach { option ->
                DropdownMenuItem(onClick = {
                    onKindChange(option)
                    expanded = false
                }) {
                    Text(option.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}

internal fun accountKindPickerHint(
    kind: AccountKind,
    suggested: Boolean,
): String? =
    when {
        !suggested -> null
        kind == AccountKind.OTHER -> "Choose what this account is"
        else -> "Suggested from the name"
    }

/** A credit card's network, picked by the person; Tandem never reads it from card numbers. */
@Composable
internal fun CardNetworkPicker(
    network: CardNetwork?,
    onNetworkChange: (CardNetwork?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val mark = network?.let { LocalRowLogos.current?.bySlug?.invoke(it.iconSlug) }
    Box {
        ListRow(
            "Card network",
            trailing = network?.label ?: "None",
            leading = RowLeading.Merchant(mark, Lucide.CreditCard),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf<CardNetwork?>(null) + CardNetwork.entries).forEach { option ->
                DropdownMenuItem(onClick = {
                    onNetworkChange(option)
                    expanded = false
                }) {
                    Text(option?.label ?: "None", color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
