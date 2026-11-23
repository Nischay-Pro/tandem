package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.sharedbills.SharedBillEditScreen
import app.tandemforactual.ui.feature.sharedbills.SharedBillPaymentScreen
import app.tandemforactual.ui.feature.sharedbills.SharedBillScreen
import app.tandemforactual.ui.feature.sharedbills.SharedBillsScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

/** Screens for the shared bills routes. */
internal val sharedBillsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.SHARED_BILLS to @Composable { ctx -> with(ctx) { SharedBillsScreen(state, mobileSession, navigation) } },
        AppRoute.SHARED_BILL to @Composable { ctx -> with(ctx) { SharedBillScreen(state, mobileSession, navigation, billTransactions()) } },
        AppRoute.SHARED_BILL_PAYMENT to
            @Composable { ctx ->
                with(ctx) { SharedBillPaymentScreen(state, mobileSession, navigation, billTransactions(), liveAccounts) }
            },
        AppRoute.SHARED_BILL_EDIT to @Composable { ctx -> with(ctx) { SharedBillEditScreen(state, mobileSession, navigation) } },
    )

/** Every transaction the device has, from Activity and the budget, once each. */
private fun ScreenContext.billTransactions() =
    (liveActivityTransactions + liveTransactions).distinctBy {
        it.recordId
            ?: it.hashCode().toString()
    }

/** This feature's entries in the full-screen menu. */
internal val sharedBillsMenu: List<MenuEntry> =
    listOf(
        MenuEntry(195) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Users, "Shared bills", "Who has paid their share") { open(AppRoute.SHARED_BILLS) }
            }
        },
    )
