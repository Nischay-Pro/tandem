@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.budgetChoicesForSelection
import app.tandemforactual.ui.core.state.cachedBudgetsWithLocalSnapshots
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_ask_the_budget_owner_to
import app.tandemforactual.ui.generated.resources.setup_available_budgets
import app.tandemforactual.ui.generated.resources.setup_available_on_this_server
import app.tandemforactual.ui.generated.resources.setup_choose_a_budget
import app.tandemforactual.ui.generated.resources.setup_choose_a_saved_budget
import app.tandemforactual.ui.generated.resources.setup_choose_a_server_budget_or
import app.tandemforactual.ui.generated.resources.setup_connect_this_device_to_your
import app.tandemforactual.ui.generated.resources.setup_continue
import app.tandemforactual.ui.generated.resources.setup_encrypted_actual_budget
import app.tandemforactual.ui.generated.resources.setup_encrypted_budget_saved_on_this
import app.tandemforactual.ui.generated.resources.setup_loading_budgets
import app.tandemforactual.ui.generated.resources.setup_no_budgets_available
import app.tandemforactual.ui.generated.resources.setup_saved_on_this_device
import app.tandemforactual.ui.generated.resources.setup_select_the_actual_budget_to
import app.tandemforactual.ui.generated.resources.setup_select_the_budget_saved_on
import app.tandemforactual.ui.generated.resources.setup_sign_in
import app.tandemforactual.ui.generated.resources.setup_sign_in_first
import app.tandemforactual.ui.generated.resources.setup_try_again
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BudgetChooserScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRefreshBudgets: () -> Unit,
    onPrepareBudgetUnlock: (BudgetCatalogItem) -> Unit,
    onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
) {
    val cachedCatalog =
        remember(state.cachedBudgetCatalogJson, mobileSession.cachedBudgets) {
            cachedBudgetsWithLocalSnapshots(
                decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                mobileSession.cachedBudgets,
            )
        }
    val selectableBudgets =
        budgetChoicesForSelection(mobileSession.budgets, cachedCatalog, mobileSession.budgetCatalogLoaded)
    val usingCachedCatalog = !mobileSession.budgetCatalogLoaded && cachedCatalog.isNotEmpty()
    val hasCachedOnlyBudgets = cachedCatalog.any { cached -> mobileSession.budgets.none { it.budgetId == cached.budgetId } }
    var chooserSelectedBudgetId by rememberSaveable(state.selectedBudgetId) { mutableStateOf(state.selectedBudgetId) }
    LaunchedEffect(selectableBudgets, state.selectedBudgetId) {
        if (selectableBudgets.none { it.budgetId == chooserSelectedBudgetId }) {
            chooserSelectedBudgetId =
                selectableBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }?.budgetId
                    ?: selectableBudgets.firstOrNull()?.budgetId.orEmpty()
        }
    }
    val selectedBudget = selectableBudgets.firstOrNull { it.budgetId == chooserSelectedBudgetId }
    val cachedBudgetSelected = selectedBudget?.let { budget -> cachedCatalog.any { it.budgetId == budget.budgetId } } == true
    val selectedBudgetIsAvailable = selectedBudget != null
    val vaultAvailable = LocalEncryptedBudgetVault.current != null
    val needsEncryptionPassword =
        budgetPasswordRequired(
            isEncrypted = selectedBudget?.isEncrypted == true,
            hasUnlockedPassword = selectedBudget?.budgetId == state.selectedBudgetId && state.encryptedPassword.isNotBlank(),
        )

    fun chooseBudget(budget: BudgetCatalogItem) {
        if (chooserSelectedBudgetId != budget.budgetId) state.encryptedPassword = ""
        chooserSelectedBudgetId = budget.budgetId
    }

    OnboardingLayout(
        content = {
            SetupStepHeader(step = 3, navigation = navigation)
            SetupHeadline(
                if (usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty())) {
                    stringResource(Res.string.setup_choose_a_saved_budget)
                } else {
                    stringResource(Res.string.setup_choose_a_budget)
                },
                if (usingCachedCatalog) {
                    stringResource(Res.string.setup_select_the_budget_saved_on)
                } else if (hasCachedOnlyBudgets) {
                    stringResource(Res.string.setup_choose_a_server_budget_or)
                } else {
                    stringResource(Res.string.setup_select_the_actual_budget_to)
                },
            )
            LabelText(stringResource(Res.string.setup_available_budgets))
            when {
                usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                    selectableBudgets.forEach { budget ->
                        ChoiceRow(
                            budget.name,
                            if (budget.isEncrypted) {
                                stringResource(
                                    Res.string.setup_encrypted_budget_saved_on_this,
                                )
                            } else {
                                stringResource(Res.string.setup_saved_on_this_device)
                            },
                            selected = chooserSelectedBudgetId == budget.budgetId,
                        ) { chooseBudget(budget) }
                    }
                }
                selectableBudgets.isNotEmpty() -> {
                    selectableBudgets.forEach { budget ->
                        val isOnServer = mobileSession.budgets.any { it.budgetId == budget.budgetId }
                        val detail =
                            when {
                                !isOnServer && budget.isEncrypted -> stringResource(Res.string.setup_encrypted_budget_saved_on_this)
                                !isOnServer -> stringResource(Res.string.setup_saved_on_this_device)
                                budget.isEncrypted -> stringResource(Res.string.setup_encrypted_actual_budget)
                                else -> stringResource(Res.string.setup_available_on_this_server)
                            }
                        ChoiceRow(budget.name, detail, selected = chooserSelectedBudgetId == budget.budgetId) { chooseBudget(budget) }
                    }
                }
                mobileSession.isLoadingBudgets -> BodyText(stringResource(Res.string.setup_loading_budgets))
                mobileSession.budgets.isEmpty() && mobileSession.isAuthenticated -> {
                    mobileSession.budgetError?.let { error ->
                        InlineMessage(error, InlineMessageKind.ERROR)
                    }
                        ?: EmptyState(
                            stringResource(Res.string.setup_no_budgets_available),
                            stringResource(Res.string.setup_ask_the_budget_owner_to),
                        )
                    SecondaryButton(stringResource(Res.string.setup_try_again), onClick = onRefreshBudgets)
                }
                mobileSession.budgets.isEmpty() -> {
                    SurfaceCard {
                        Column(
                            Modifier.padding(TandemSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            LabelText(stringResource(Res.string.setup_sign_in_first))
                            BodyText(stringResource(Res.string.setup_connect_this_device_to_your))
                        }
                    }
                    SecondaryButton(stringResource(Res.string.setup_sign_in)) { navigation.open(AppRoute.SIGN_IN) }
                }
            }
            mobileSession.budgetError?.takeIf { selectableBudgets.isNotEmpty() }?.let { error ->
                InlineMessage(error, InlineMessageKind.ERROR)
            }
        },
        actions = {
            SetupPrimaryButton(
                stringResource(Res.string.setup_continue),
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedBudgetIsAvailable && (!mobileSession.isLoadingBudgets || cachedBudgetSelected),
                onClick = {
                    selectedBudget?.let { budget ->
                        if (needsEncryptionPassword && !vaultAvailable) onPrepareBudgetUnlock(budget) else onOpenSelectedBudget(budget)
                    }
                },
            )
        },
    )
}
