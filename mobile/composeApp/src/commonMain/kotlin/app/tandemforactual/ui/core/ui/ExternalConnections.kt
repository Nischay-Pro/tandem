@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.FrankfurterRateSource
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.connections_bank_logos
import app.tandemforactual.ui.generated.resources.connections_bank_logos_detail
import app.tandemforactual.ui.generated.resources.connections_bank_logos_shared_limit
import app.tandemforactual.ui.generated.resources.connections_bank_logos_shared_source
import app.tandemforactual.ui.generated.resources.connections_bank_logos_short
import app.tandemforactual.ui.generated.resources.connections_bank_logos_subtitle
import app.tandemforactual.ui.generated.resources.connections_detail
import app.tandemforactual.ui.generated.resources.connections_exchange_rates
import app.tandemforactual.ui.generated.resources.connections_exchange_rates_detail
import app.tandemforactual.ui.generated.resources.connections_exchange_rates_shared_limit
import app.tandemforactual.ui.generated.resources.connections_exchange_rates_shared_source
import app.tandemforactual.ui.generated.resources.connections_exchange_rates_short
import app.tandemforactual.ui.generated.resources.connections_exchange_rates_subtitle
import app.tandemforactual.ui.generated.resources.connections_rate_server
import app.tandemforactual.ui.generated.resources.connections_rate_server_invalid
import app.tandemforactual.ui.generated.resources.connections_rate_server_save
import app.tandemforactual.ui.generated.resources.connections_rate_server_saved
import app.tandemforactual.ui.generated.resources.connections_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A feature that contacts a service other than the user's Actual server; off until the user turns it on. */
internal class ExternalConnection(
    val title: StringResource,
    val detail: StringResource,
    val shortTitle: StringResource,
    val subtitle: StringResource,
    val shared: List<StringResource>,
    val isOn: (AppState) -> Boolean,
    val turn: (AppState, Boolean) -> Unit,
    val advanced: (@Composable (AppState) -> Unit)? = null,
)

internal val externalConnections =
    listOf(
        ExternalConnection(
            Res.string.connections_bank_logos,
            Res.string.connections_bank_logos_detail,
            Res.string.connections_bank_logos_short,
            Res.string.connections_bank_logos_subtitle,
            listOf(Res.string.connections_bank_logos_shared_source, Res.string.connections_bank_logos_shared_limit),
            isOn = { it.bankIconsEnabled },
            turn = { state, on -> state.bankIconsEnabled = on },
        ),
        ExternalConnection(
            Res.string.connections_exchange_rates,
            Res.string.connections_exchange_rates_detail,
            Res.string.connections_exchange_rates_short,
            Res.string.connections_exchange_rates_subtitle,
            listOf(Res.string.connections_exchange_rates_shared_source, Res.string.connections_exchange_rates_shared_limit),
            isOn = { it.exchangeRateFetching.enabled },
            turn = { state, on -> state.exchangeRateFetching = state.exchangeRateFetching.copy(enabled = on) },
            advanced = { ExchangeRateServerField(it) },
        ),
    )

/** The opt-in list; [showAdvanced] adds each connection's extra settings under its row while it's on. */
@Composable
internal fun ExternalConnectionsSection(
    state: AppState,
    showAdvanced: Boolean = false,
) {
    SectionCard(stringResource(Res.string.connections_title), stringResource(Res.string.connections_detail)) {
        externalConnections.forEachIndexed { index, connection ->
            if (index > 0) DividerLine()
            ToggleRow(stringResource(connection.title), stringResource(connection.detail), connection.isOn(state)) {
                connection.turn(state, it)
            }
            val advanced = connection.advanced
            if (showAdvanced && advanced != null) {
                AnimatedReveal(visible = connection.isOn(state)) { advanced(state) }
            }
        }
    }
}

/** One short line naming which connections are on, or "Off" when none are. */
@Composable
internal fun externalConnectionsStatus(state: AppState): String =
    settingsSwitchStatus(externalConnections.map { it.shortTitle to it.isOn(state) })

/** A self-hosted Frankfurter server to fetch rates from instead of the public one; blank uses the public one. */
@Composable
private fun ExchangeRateServerField(state: AppState) {
    var address by rememberSaveable { mutableStateOf(state.exchangeRateFetching.server) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.padding(
            start = TandemLayout.listRowHorizontalInset,
            end = TandemLayout.listRowHorizontalInset,
            bottom = TandemSpacing.medium,
        ),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        TextFieldCard(
            stringResource(Res.string.connections_rate_server),
            address,
            {
                address = it
                invalid = false
            },
            TextFieldOptions(placeholder = FrankfurterRateSource.DEFAULT_URL),
        )
        if (invalid) FieldErrorMessage(stringResource(Res.string.connections_rate_server_invalid))
        val saved = address.trim() == state.exchangeRateFetching.server
        SecondaryButton(
            stringResource(if (saved) Res.string.connections_rate_server_saved else Res.string.connections_rate_server_save),
            enabled = !saved,
        ) {
            val server = address.trim().takeIf { it.isEmpty() || FrankfurterRateSource.address(it) != null }
            invalid = server == null
            if (server != null) state.exchangeRateFetching = state.exchangeRateFetching.copy(server = server)
        }
    }
}
