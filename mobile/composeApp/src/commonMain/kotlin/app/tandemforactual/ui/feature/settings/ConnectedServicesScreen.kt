@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.externalConnections
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.connections_all_services
import app.tandemforactual.ui.generated.resources.connections_page_intro
import app.tandemforactual.ui.generated.resources.connections_what_is_shared
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Network
import org.jetbrains.compose.resources.stringResource

/** Each opt-in network feature as a switch, with what it sends and every service Tandem can contact one tap away. */
@Composable
internal fun ConnectedServicesScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    var sharedOpen by rememberSaveable { mutableStateOf(false) }
    PageColumn {
        BodyText(stringResource(Res.string.connections_page_intro))
        SurfaceCard {
            externalConnections.forEachIndexed { index, connection ->
                if (index > 0) DividerLine()
                ToggleRow(stringResource(connection.title), stringResource(connection.subtitle), connection.isOn(state)) {
                    connection.turn(state, it)
                }
                connection.advanced?.let { advanced -> AnimatedReveal(visible = connection.isOn(state)) { advanced(state) } }
            }
        }
        SurfaceCard {
            ListRow(
                stringResource(Res.string.connections_what_is_shared),
                leading = RowLeading.Icon(Lucide.Eye),
                onClick = { sharedOpen = true },
            )
            DividerLine()
            ListRow(
                stringResource(Res.string.connections_all_services),
                leading = RowLeading.Icon(Lucide.Network),
                onClick = { navigation.open(AppRoute.ABOUT) },
            )
        }
    }
    SettingsInfoSheet(sharedOpen, stringResource(Res.string.connections_what_is_shared), connectionsShared()) { sharedOpen = false }
}

internal fun connectionsShared(): List<InfoGroup> = externalConnections.map { InfoGroup(it.title, it.shared) }
