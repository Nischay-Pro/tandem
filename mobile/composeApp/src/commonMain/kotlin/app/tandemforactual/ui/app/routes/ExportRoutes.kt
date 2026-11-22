package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.export.ExportScreen
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Lucide

/** Screens for the export routes. */
internal val exportScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.EXPORT to
            @Composable { ctx ->
                with(ctx) {
                    ExportScreen(state, mobileSession)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val exportMenu: List<MenuEntry> =
    listOf(
        MenuEntry(230) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.FileDown, "Export & backup", "CSV and planning backup") { open(AppRoute.EXPORT) }
            }
        },
    )
