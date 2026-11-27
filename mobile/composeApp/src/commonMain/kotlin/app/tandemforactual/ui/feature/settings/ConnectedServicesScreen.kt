@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.externalConnections
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.connections_all_services
import app.tandemforactual.ui.generated.resources.connections_all_services_detail
import app.tandemforactual.ui.generated.resources.connections_page_intro
import app.tandemforactual.ui.generated.resources.connections_what_is_shared
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Network
import org.jetbrains.compose.resources.stringResource

/** Each opt-in network feature with what it sends, and a link to every service Tandem can contact. */
@Composable
internal fun ConnectedServicesScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        BodyText(stringResource(Res.string.connections_page_intro))
        externalConnections.forEach { connection ->
            OptInCard(
                stringResource(connection.title),
                connection.isOn(state),
                { connection.turn(state, it) },
                stringResource(Res.string.connections_what_is_shared),
                stringResource(connection.detail),
            ) { connection.advanced?.let { advanced -> AnimatedReveal(visible = connection.isOn(state)) { advanced(state) } } }
        }
        SurfaceCard {
            ListRow(
                stringResource(Res.string.connections_all_services),
                stringResource(Res.string.connections_all_services_detail),
                leading = RowLeading.Icon(Lucide.Network),
                onClick = { navigation.open(AppRoute.ABOUT) },
            )
        }
    }
}
