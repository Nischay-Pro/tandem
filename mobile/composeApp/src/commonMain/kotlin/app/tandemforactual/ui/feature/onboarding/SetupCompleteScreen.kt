@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SetupPrimaryButton
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.SetupHeadline
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_budget_is_ready
import app.tandemforactual.ui.generated.resources.setup_open_budget
import app.tandemforactual.ui.generated.resources.setup_sync_again_whenever_you_need
import app.tandemforactual.ui.generated.resources.setup_you_can_manage_syncing_in
import app.tandemforactual.ui.generated.resources.setup_you_re_all_set
import app.tandemforactual.ui.generated.resources.setup_your_budget
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SetupCompleteScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    OnboardingLayout(
        content = {
            Spacer(Modifier.height(TandemSpacing.extraLarge))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(38.dp), tint = palette.positive)
            }
            SetupHeadline(
                stringResource(Res.string.setup_you_re_all_set),
                stringResource(
                    Res.string.setup_budget_is_ready,
                    state.selectedBudget.ifBlank { stringResource(Res.string.setup_your_budget) },
                ),
            )
            Text(
                stringResource(Res.string.setup_sync_again_whenever_you_need),
                color = palette.muted,
                fontSize = TandemTypography.body,
            )
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.setup_open_budget), modifier = Modifier.fillMaxWidth()) {
                state.pendingBudgetSelectionOrigin = null
                navigation.selectTab(AppRoute.HOME)
            }
            Text(
                stringResource(Res.string.setup_you_can_manage_syncing_in),
                modifier = Modifier.fillMaxWidth(),
                color = palette.muted,
                fontSize = TandemTypography.caption,
                textAlign = TextAlign.Center,
            )
        },
    )
}
