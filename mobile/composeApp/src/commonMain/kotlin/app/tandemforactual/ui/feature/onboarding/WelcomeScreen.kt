@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.LanguageButton
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.buttons.SetupPrimaryButton
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.SetupHeadline
import app.tandemforactual.ui.design.theme.TandemBrandMark
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_access_your_actual_budget_anywhere
import app.tandemforactual.ui.generated.resources.setup_for_actual_budget
import app.tandemforactual.ui.generated.resources.setup_get_started
import app.tandemforactual.ui.generated.resources.setup_need_help_setting_up_the
import app.tandemforactual.ui.generated.resources.setup_open_api_gateway_setup_guide
import app.tandemforactual.ui.generated.resources.setup_tandem
import app.tandemforactual.ui.generated.resources.setup_your_budget_everywhere
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WelcomeScreen(
    navigation: AppNavigation,
    signOutNotice: String? = null,
    onOpenSetupGuide: () -> Unit,
) {
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        horizontalPadding = TandemSpacing.extraLarge,
        content = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageButton() }
                WelcomeBrand(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(TandemSpacing.large))
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    SetupHeadline(
                        stringResource(Res.string.setup_your_budget_everywhere),
                        stringResource(Res.string.setup_access_your_actual_budget_anywhere),
                    )
                    signOutNotice?.let { InfoBubble(it) }
                }
            }
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.setup_get_started), modifier = Modifier.fillMaxWidth()) {
                navigation.open(AppRoute.SERVER_ADDRESS)
            }
            Text(
                stringResource(Res.string.setup_need_help_setting_up_the),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(Res.string.setup_open_api_gateway_setup_guide),
                            onClick = onOpenSetupGuide,
                        ).padding(vertical = TandemSpacing.extraSmall),
                color = tandemPalette.accent,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun WelcomeBrand(modifier: Modifier = Modifier) {
    val palette = tandemPalette
    val breathing = !tandemReduceMotion
    val breath by
        rememberInfiniteTransition(label = "welcome-breath").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1_600, easing = TandemMotion.movementEasing), RepeatMode.Reverse),
            label = "welcome-breath-phase",
        )
    val emphasis = if (breathing) breath else 1f
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        TandemBrandMark(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    alpha = 0.8f + 0.2f * emphasis
                    scaleX = 0.94f + 0.06f * emphasis
                    scaleY = 0.94f + 0.06f * emphasis
                },
        )
        Spacer(Modifier.height(TandemSpacing.small))
        Text(
            stringResource(Res.string.setup_tandem),
            color = palette.text,
            fontSize = TandemTypography.welcomeWordmark,
            lineHeight = TandemTypography.welcomeWordmarkLineHeight,
            fontWeight = FontWeight.Bold,
            fontFamily = tandemAlloFamily(),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.setup_for_actual_budget),
            color = palette.muted,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = TandemTypography.captionLetterSpacing,
            textAlign = TextAlign.Center,
        )
    }
}
