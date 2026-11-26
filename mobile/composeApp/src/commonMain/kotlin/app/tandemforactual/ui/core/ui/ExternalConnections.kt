@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.connections_bank_logos
import app.tandemforactual.ui.generated.resources.connections_bank_logos_detail
import app.tandemforactual.ui.generated.resources.connections_detail
import app.tandemforactual.ui.generated.resources.connections_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A feature that contacts a service other than the user's Actual server; off until the user turns it on. */
internal class ExternalConnection(
    val title: StringResource,
    val detail: StringResource,
    val isOn: (AppState) -> Boolean,
    val turn: (AppState, Boolean) -> Unit,
)

internal val externalConnections =
    listOf(
        ExternalConnection(
            Res.string.connections_bank_logos,
            Res.string.connections_bank_logos_detail,
            isOn = { it.bankIconsEnabled },
            turn = { state, on -> state.bankIconsEnabled = on },
        ),
    )

@Composable
internal fun ExternalConnectionsSection(state: AppState) {
    SectionCard(stringResource(Res.string.connections_title), stringResource(Res.string.connections_detail)) {
        externalConnections.forEachIndexed { index, connection ->
            if (index > 0) DividerLine()
            ToggleRow(stringResource(connection.title), stringResource(connection.detail), connection.isOn(state)) {
                connection.turn(state, it)
            }
        }
    }
}
