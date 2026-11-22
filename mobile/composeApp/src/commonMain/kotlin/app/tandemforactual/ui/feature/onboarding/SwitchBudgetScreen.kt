@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.budgetChoicesForSelection
import app.tandemforactual.ui.core.state.cachedBudgetsWithLocalSnapshots
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_budgets
import app.tandemforactual.ui.generated.resources.setup_encrypted
import app.tandemforactual.ui.generated.resources.setup_encrypted_saved_on_this_device
import app.tandemforactual.ui.generated.resources.setup_loading_budgets
import app.tandemforactual.ui.generated.resources.setup_no_budgets_available
import app.tandemforactual.ui.generated.resources.setup_on_this_server
import app.tandemforactual.ui.generated.resources.setup_open
import app.tandemforactual.ui.generated.resources.setup_saved_on_this_device
import app.tandemforactual.ui.generated.resources.setup_sign_in
import app.tandemforactual.ui.generated.resources.setup_sign_in_to_this_server
import app.tandemforactual.ui.generated.resources.setup_try_again
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SwitchBudgetScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRefreshBudgets: () -> Unit,
    onPrepareBudgetUnlock: (BudgetCatalogItem) -> Unit,
    onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
) {
    val cachedCatalog =
        remember(state.cachedBudgetCatalogJson, mobileSession.cachedBudgets) {
            cachedBudgetsWithLocalSnapshots(decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson), mobileSession.cachedBudgets)
        }
    val budgets = budgetChoicesForSelection(mobileSession.budgets, cachedCatalog, mobileSession.budgetCatalogLoaded)
    val vaultAvailable = LocalEncryptedBudgetVault.current != null
    LaunchedEffect(Unit) { if (mobileSession.isAuthenticated) onRefreshBudgets() }
    PageColumn {
        if (budgets.isEmpty()) {
            if (mobileSession.isLoadingBudgets) {
                BodyText(stringResource(Res.string.setup_loading_budgets))
            } else {
                EmptyState(
                    stringResource(Res.string.setup_no_budgets_available),
                    mobileSession.budgetError ?: stringResource(Res.string.setup_sign_in_to_this_server),
                    actionLabel =
                        if (mobileSession.isAuthenticated) {
                            stringResource(
                                Res.string.setup_try_again,
                            )
                        } else {
                            stringResource(Res.string.setup_sign_in)
                        },
                    onAction = { if (mobileSession.isAuthenticated) onRefreshBudgets() else navigation.open(AppRoute.SIGN_IN) },
                )
            }
            return@PageColumn
        }
        SectionCard(stringResource(Res.string.setup_budgets)) {
            budgets.forEachIndexed { index, budget ->
                if (index > 0) DividerLine()
                val current = budget.budgetId == state.selectedBudgetId
                val onServer = mobileSession.budgets.any { it.budgetId == budget.budgetId }
                ListRow(
                    budget.name,
                    when {
                        budget.isEncrypted && !onServer -> stringResource(Res.string.setup_encrypted_saved_on_this_device)
                        budget.isEncrypted -> stringResource(Res.string.setup_encrypted)
                        !onServer -> stringResource(Res.string.setup_saved_on_this_device)
                        else -> stringResource(Res.string.setup_on_this_server)
                    },
                    if (current) stringResource(Res.string.setup_open) else null,
                    leading = RowLeading.Icon(if (budget.isEncrypted) Lucide.LockKeyhole else Lucide.WalletCards),
                    options = ListRowOptions(trailingColor = tandemPalette.accent),
                    onClick = {
                        when {
                            current && !(budget.isEncrypted && state.encryptedPassword.isBlank()) -> navigation.back()
                            budget.isEncrypted && !vaultAvailable && !(current && state.encryptedPassword.isNotBlank()) ->
                                onPrepareBudgetUnlock(budget)
                            else -> onOpenSelectedBudget(budget)
                        }
                    },
                )
            }
        }
        mobileSession.budgetError?.let {
            InlineMessage(
                it,
                InlineMessageKind.ERROR,
                actionLabel =
                    when {
                        !mobileSession.isAuthenticated -> stringResource(Res.string.setup_sign_in)
                        mobileSession.isLoadingBudgets -> null
                        else -> stringResource(Res.string.setup_try_again)
                    },
                onAction = { if (mobileSession.isAuthenticated) onRefreshBudgets() else navigation.open(AppRoute.SIGN_IN) },
            )
        }
    }
}
