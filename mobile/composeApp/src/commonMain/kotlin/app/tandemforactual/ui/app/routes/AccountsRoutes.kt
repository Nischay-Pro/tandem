@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.routes

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.ListDetailPanes
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.PaneContent
import app.tandemforactual.ui.app.PaneHeader
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.app.accountDetailKey
import app.tandemforactual.ui.app.closingPane
import app.tandemforactual.ui.app.openingInPane
import app.tandemforactual.ui.app.paneStackTransform
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.templates.appBackgroundBrush
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.accounts.AccountDetailScreen
import app.tandemforactual.ui.feature.accounts.AccountOptionsScreen
import app.tandemforactual.ui.feature.accounts.AccountsScreen
import app.tandemforactual.ui.feature.transactions.TransactionDetailScreen
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
                        detail = { AccountDetailPane(ctx) },
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

/** The account beside the list; a transaction opened from it replaces it in the same pane, with back returning to it. */
@Composable
private fun AccountDetailPane(ctx: ScreenContext) {
    with(ctx) {
        var transactionOpen by remember { mutableStateOf(false) }
        val closeTransaction: () -> Unit = { transactionOpen = false }
        val reduceMotion = tandemReduceMotion
        val background = appBackgroundBrush(state.backgroundStyle, tandemPalette, state.theme == ThemeMode.PURE_BLACK)
        TandemBackHandler(enabled = transactionOpen, onBack = closeTransaction, onBackProgress = { _, _ -> })
        AnimatedContent(
            targetState = transactionOpen,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transitionSpec = { paneStackTransform(reduceMotion) },
            label = "tandem-account-pane",
        ) { showTransaction ->
            Box(Modifier.fillMaxSize().background(background)) {
                if (showTransaction) {
                    Column(Modifier.fillMaxSize()) {
                        PaneHeader(state.transactionPayee.ifBlank { AppRoute.TRANSACTION_DETAIL.title }, closeTransaction, back = true)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            PaneContent {
                                TransactionDetailScreen(
                                    state,
                                    mobileSession,
                                    navigation.closingPane(closeTransaction),
                                    merchantIconMatcher,
                                    showPayee = true,
                                )
                            }
                        }
                    }
                } else {
                    AccountDetailScreen(
                        state,
                        navigation.openingInPane(setOf(AppRoute.TRANSACTION_DETAIL)) { transactionOpen = true },
                        liveAccounts,
                        mobileSession,
                        merchantIconMatcher,
                        isForegroundRoute,
                    )
                }
            }
        }
    }
}
