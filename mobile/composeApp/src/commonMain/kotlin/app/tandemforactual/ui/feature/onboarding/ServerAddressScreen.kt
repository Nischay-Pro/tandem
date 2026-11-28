@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.parseServerHeaders
import app.tandemforactual.ui.core.session.LocalLanAccess
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SetupPrimaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.SetupHeadline
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_connect_to_your_server
import app.tandemforactual.ui.generated.resources.setup_continue
import app.tandemforactual.ui.generated.resources.setup_enter_the_address_you_use
import app.tandemforactual.ui.generated.resources.setup_not_secure
import app.tandemforactual.ui.generated.resources.setup_secure_connection
import app.tandemforactual.ui.generated.resources.setup_server_url
import app.tandemforactual.ui.generated.resources.setup_tandem_checks_for_the_actual
import app.tandemforactual.ui.generated.resources.setup_use_https_to_protect_budget
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val completedAddress = completeServerAddress(state.serverUrl)
    val canContinue = serverAddressIsValid(completedAddress) && parseServerHeaders(state.serverHeaders).isSuccess
    val secureAddress = completedAddress.startsWith("https://", ignoreCase = true)
    val scope = rememberCoroutineScope()
    val lanAccess = LocalLanAccess.current
    var continuing by remember { mutableStateOf(false) }
    val continueToSignIn = {
        if (canContinue && !continuing) {
            state.serverUrl = completedAddress
            continuing = true
            scope.launch {
                try {
                    lanAccess?.request(completedAddress)
                    navigation.open(AppRoute.SIGN_IN)
                } finally {
                    continuing = false
                }
            }
        }
    }
    val suggestions = serverAddressSuggestions(state.serverUrl, recentServerAddresses(state.recentServerUrls))
    OnboardingLayout(
        content = {
            SetupStepHeader(step = 1, navigation = navigation)
            SetupHeadline(
                stringResource(Res.string.setup_connect_to_your_server),
                stringResource(Res.string.setup_enter_the_address_you_use),
            )
            TextFieldCard(
                stringResource(Res.string.setup_server_url),
                state.serverUrl,
                { state.serverUrl = it },
                TextFieldOptions(
                    placeholder = "budget.example.com",
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { continueToSignIn() }),
                    leadingIcon = if (canContinue) (if (secureAddress) Lucide.Lock else Lucide.LockOpen) else null,
                    leadingIconTint = if (secureAddress) tandemPalette.positive else tandemPalette.negative,
                    leadingIconDescription =
                        if (secureAddress) {
                            stringResource(
                                Res.string.setup_secure_connection,
                            )
                        } else {
                            stringResource(Res.string.setup_not_secure)
                        },
                ),
            )
            ServerAddressSuggestions(suggestions) { state.serverUrl = it }
            ServerHeadersField(state)
            InfoBubble(stringResource(Res.string.setup_tandem_checks_for_the_actual))
            if (serverAddressMayBeInsecure(completedAddress)) InsecureAddressWarning()
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.setup_continue), modifier = Modifier.fillMaxWidth(), enabled = canContinue) {
                continueToSignIn()
            }
        },
    )
}

/** Recent and suggested server addresses matching what's typed. */
@Composable
private fun ServerAddressSuggestions(
    suggestions: List<ServerAddressSuggestion>,
    onPick: (String) -> Unit,
) {
    AnimatedReveal(visible = suggestions.isNotEmpty()) {
        SurfaceCard {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) DividerLine()
                ListRow(
                    suggestion.address,
                    suggestion.detail,
                    leading = RowLeading.Icon(if (suggestion.recent) Lucide.History else Lucide.Globe),
                    options = ListRowOptions(dense = true),
                    onClick = { onPick(suggestion.address) },
                )
            }
        }
    }
}

/** A warning that a plain-HTTP address doesn't protect budget data. */
@Composable
private fun InsecureAddressWarning() {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .background(palette.warning.copy(alpha = 0.12f))
            .border(1.dp, palette.warning.copy(alpha = 0.38f), RoundedCornerShape(TandemShapes.medium))
            .padding(TandemSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.CircleAlert, contentDescription = null, modifier = Modifier.size(23.dp), tint = palette.warning)
        Text(
            stringResource(Res.string.setup_use_https_to_protect_budget),
            color = palette.text,
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.bodyLineHeight,
        )
    }
}
