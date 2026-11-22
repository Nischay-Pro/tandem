@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.core.security.LocalDeviceAuthenticator
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.restorePendingBudgetSelectionOrigin
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.feature.auth.BiometricPasswordCheckbox
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_budget_encryption_password
import app.tandemforactual.ui.generated.resources.setup_budget_is_encrypted
import app.tandemforactual.ui.generated.resources.setup_password
import app.tandemforactual.ui.generated.resources.setup_this_is_not_your_actual
import app.tandemforactual.ui.generated.resources.setup_unlock_budget
import app.tandemforactual.ui.generated.resources.setup_unlock_your_budget
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BudgetUnlockScreen(
    state: AppState,
    navigation: AppNavigation,
    onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
) {
    OnboardingLayout(
        content = {
            SetupStepHeader(
                step = 3,
                navigation = navigation,
                onBack = {
                    state.restorePendingBudgetSelectionOrigin()
                    navigation.back()
                },
            )
            SetupHeadline(
                stringResource(Res.string.setup_unlock_your_budget),
                stringResource(Res.string.setup_budget_is_encrypted, state.selectedBudget),
            )
            TextFieldCard(
                stringResource(Res.string.setup_budget_encryption_password),
                state.encryptedPassword,
                { state.encryptedPassword = it },
                TextFieldOptions(placeholder = stringResource(Res.string.setup_password), isSecret = true),
            )
            InfoBubble(stringResource(Res.string.setup_this_is_not_your_actual))
            if (LocalDeviceAuthenticator.current?.canStoreSecrets() == true) {
                BiometricPasswordCheckbox(state.rememberBudgetPasswordWithBiometrics) {
                    state.rememberBudgetPasswordWithBiometrics = it
                }
            }
        },
        actions = {
            SetupPrimaryButton(
                stringResource(Res.string.setup_unlock_budget),
                modifier = Modifier.fillMaxWidth(),
                enabled = state.encryptedPassword.isNotBlank(),
                onClick = {
                    onOpenSelectedBudget(
                        BudgetCatalogItem(
                            budgetId = state.selectedBudgetId,
                            name = state.selectedBudget,
                            isEncrypted = state.selectedBudgetEncrypted,
                        ),
                    )
                },
            )
        },
    )
}
