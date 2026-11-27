package app.tandemforactual.ui.app.routes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.ListDetailPanes
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.accountDetailKey
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.accounts.AccountDetailScreen
import app.tandemforactual.ui.feature.accounts.AccountOptionsScreen
import app.tandemforactual.ui.feature.accounts.AccountsScreen
import com.composables.icons.lucide.FileUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards

/** Screens for the accounts routes. */
internal val accountsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.ACCOUNTS to
            @Composable { ctx ->
                with(ctx) {
                    ListDetailPanes(
                        detailKey = accountDetailKey(state, liveAccounts),
                        emptyTitle = "Choose an account",
                        emptyMessage = "Its balance and activity appear here.",
                        list = { detailInPane ->
                            AccountsScreen(state, navigation, liveAccounts, merchantIconMatcher, mobileSession, detailInPane)
                        },
                        detail = {
                            AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher, isForegroundRoute)
                        },
                    )
                }
            },
        AppRoute.ACCOUNT_DETAIL to
            @Composable { ctx ->
                with(ctx) {
                    AccountDetailScreen(state, navigation, liveAccounts, mobileSession, merchantIconMatcher, isForegroundRoute)
                }
            },
        AppRoute.ACCOUNT_OPTIONS to
            @Composable { ctx ->
                with(ctx) {
                    AccountOptionsScreen(state, liveAccounts, mobileSession, navigation)
                }
            },
        AppRoute.CLOSED_ACCOUNTS to
            @Composable { ctx ->
                with(ctx) {
                    AccountsScreen(state, navigation, liveAccounts, merchantIconMatcher, mobileSession, closedOnly = true)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val accountsMenu: List<MenuEntry> =
    listOf(
        MenuEntry(
            50,
        ) { ctx -> with(ctx) { if (!advanced) AppMenuItem(Lucide.WalletCards, "Accounts") { open(AppRoute.ACCOUNTS) } else null } },
        MenuEntry(
            70,
        ) { ctx -> with(ctx) { AppMenuItem(Lucide.FileUp, "Import a statement", "CSV, OFX, QIF") { open(AppRoute.ACCOUNTS) } } },
        MenuEntry(270) { ctx -> with(ctx) { AppMenuItem(Lucide.WalletCards, "Closed accounts") { open(AppRoute.CLOSED_ACCOUNTS) } } },
    )
