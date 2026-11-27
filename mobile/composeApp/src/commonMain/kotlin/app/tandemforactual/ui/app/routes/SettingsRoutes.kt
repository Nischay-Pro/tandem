package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.RouteListDetailPanes
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.settings.AppearanceScreen
import app.tandemforactual.ui.feature.settings.LocalServerAddressScreen
import app.tandemforactual.ui.feature.settings.NotificationsScreen
import app.tandemforactual.ui.feature.settings.SecurityScreen
import app.tandemforactual.ui.feature.settings.ServerDeviceScreen
import app.tandemforactual.ui.feature.settings.SettingsScreen

/** Settings pages that open beside the settings list when both fit. */
private val settingsPaneRoutes =
    setOf(
        AppRoute.APPEARANCE,
        AppRoute.BUDGET_PREFERENCES,
        AppRoute.SYNC_PREFERENCES,
        AppRoute.SERVER_DEVICE,
        AppRoute.NOTIFICATIONS,
        AppRoute.SECURITY_PRIVACY,
        AppRoute.LOCAL_DATA,
        AppRoute.EXPORT,
        AppRoute.ABOUT,
    )

/** Screens for the settings routes. */
internal val settingsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.LOCAL_SERVER_ADDRESS to
            @Composable { ctx ->
                with(ctx) {
                    LocalServerAddressScreen(state, mobileSession, navigation)
                }
            },
        AppRoute.SETTINGS to
            @Composable { ctx ->
                RouteListDetailPanes(
                    ctx,
                    paneRoutes = settingsPaneRoutes,
                    emptyTitle = "Choose a setting",
                    emptyMessage = "Its options appear here.",
                    list = { navigation -> SettingsScreen(ctx.state, navigation, ctx.mobileSession) },
                )
            },
        AppRoute.APPEARANCE to
            @Composable { ctx ->
                with(ctx) {
                    AppearanceScreen(state)
                }
            },
        AppRoute.SERVER_DEVICE to
            @Composable { ctx ->
                with(ctx) {
                    ServerDeviceScreen(state, deviceName, mobileSession, navigation, onForgetDevice, onForgetDeviceLocally)
                }
            },
        AppRoute.SECURITY_PRIVACY to
            @Composable { ctx ->
                with(ctx) {
                    SecurityScreen(state, mobileSession)
                }
            },
        AppRoute.NOTIFICATIONS to
            @Composable { ctx ->
                with(ctx) {
                    NotificationsScreen(
                        state,
                        onRequestNotificationPermission,
                        onOpenNotificationSettings,
                        notificationPermissionEnabled,
                        onToggleNotificationPermission,
                    )
                }
            },
    )
