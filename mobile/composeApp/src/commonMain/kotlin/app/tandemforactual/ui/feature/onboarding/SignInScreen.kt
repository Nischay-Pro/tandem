@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.clearActualLoginMethodState
import app.tandemforactual.ui.core.session.loadActualLoginMethods
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_actual_password
import app.tandemforactual.ui.generated.resources.setup_add_your_actual_budget_server
import app.tandemforactual.ui.generated.resources.setup_change
import app.tandemforactual.ui.generated.resources.setup_change_server_address
import app.tandemforactual.ui.generated.resources.setup_checking_sign_in_options
import app.tandemforactual.ui.generated.resources.setup_connection_verified
import app.tandemforactual.ui.generated.resources.setup_continue_with_openid
import app.tandemforactual.ui.generated.resources.setup_create_a_budget_in_actual
import app.tandemforactual.ui.generated.resources.setup_not_set
import app.tandemforactual.ui.generated.resources.setup_opening_sign_in
import app.tandemforactual.ui.generated.resources.setup_password
import app.tandemforactual.ui.generated.resources.setup_server_2
import app.tandemforactual.ui.generated.resources.setup_server_is_reachable
import app.tandemforactual.ui.generated.resources.setup_set_server_address
import app.tandemforactual.ui.generated.resources.setup_sign_in
import app.tandemforactual.ui.generated.resources.setup_sign_in_with_your_existing
import app.tandemforactual.ui.generated.resources.setup_signing_in
import app.tandemforactual.ui.generated.resources.setup_tandem_can_t_use_this
import app.tandemforactual.ui.generated.resources.setup_welcome_back
import app.tandemforactual.ui.generated.resources.setup_your_password_is_only_used
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SignInScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onSignIn: () -> Unit,
    onStartOpenIdSignIn: () -> Unit,
) {
    LaunchedEffect(state.serverUrl) {
        state.actualPassword = ""
        mobileSession.clearActualLoginMethodState()
        if (shouldCheckActualLoginMethods(state.serverUrl, serverUrlFocused = false)) {
            delay(300)
            mobileSession.loadActualLoginMethods(state.serverUrl)
        }
    }
    val loginMethods = mobileSession.actualLoginMethods
    val passwordActive =
        loginMethods?.availableLoginMethods?.any { it.method == "password" && it.active } ?: (loginMethods?.loginMethod == "password")
    val openIdActive =
        loginMethods?.availableLoginMethods?.any { it.method == "openid" && it.active } ?: (loginMethods?.loginMethod == "openid")
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupStepHeader(step = 2, navigation = navigation)
            SetupHeadline(stringResource(Res.string.setup_welcome_back), stringResource(Res.string.setup_sign_in_with_your_existing))
            SignInServerRow(state, navigation)
            if (serverAddressIsValid(state.serverUrl)) SignInTransportWarning(state.serverUrl)
            if (loginMethods != null && mobileSession.signInError == null) ServerReachableNote()
            SignInOptions(state, mobileSession, passwordActive, openIdActive)
        },
        actions = { SignInActions(state, mobileSession, navigation, passwordActive, openIdActive, onSignIn, onStartOpenIdSignIn) },
    )
}

/** The server being signed in to, with a link to change it. */
@Composable
private fun SignInServerRow(
    state: AppState,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            LabelText(stringResource(Res.string.setup_server_2))
            Text(
                state.serverUrl.takeIf { serverAddressIsValid(it) } ?: stringResource(Res.string.setup_not_set),
                color = palette.text,
                fontSize = TandemTypography.compactBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (serverAddressIsValid(state.serverUrl)) {
            Text(
                stringResource(Res.string.setup_change),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(TandemShapes.small))
                        .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.setup_change_server_address)) {
                            navigation.open(AppRoute.SERVER_ADDRESS)
                        }.padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
                color = palette.accent,
                fontSize = TandemTypography.label,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Confirms the server answered and its sign-in options are known. */
@Composable
private fun ServerReachableNote() {
    val palette = tandemPalette
    Row(
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.positive)
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text(
                stringResource(Res.string.setup_server_is_reachable),
                color = palette.text,
                fontSize = TandemTypography.compactBody,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.setup_connection_verified),
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
        }
    }
}

/** The password field or a note about how this server signs people in. */
@Composable
private fun SignInOptions(
    state: AppState,
    mobileSession: TandemMobileSession,
    passwordActive: Boolean,
    openIdActive: Boolean,
) {
    val palette = tandemPalette
    when {
        !serverAddressIsValid(state.serverUrl) -> {
            InlineMessage(stringResource(Res.string.setup_add_your_actual_budget_server), InlineMessageKind.INFO)
        }
        mobileSession.isLoadingLoginMethods || (mobileSession.actualLoginMethods == null && mobileSession.signInError == null) -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemLoadingIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
                Text(
                    stringResource(Res.string.setup_checking_sign_in_options),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
        }
        mobileSession.actualLoginMethods?.bootstrapped == false -> {
            InlineMessage(stringResource(Res.string.setup_create_a_budget_in_actual), InlineMessageKind.INFO)
        }
        mobileSession.actualLoginMethods != null -> {
            if (passwordActive) {
                TextFieldCard(
                    stringResource(Res.string.setup_actual_password),
                    state.actualPassword,
                    { state.actualPassword = it },
                    TextFieldOptions(placeholder = stringResource(Res.string.setup_password), isSecret = true),
                )
                Text(
                    stringResource(Res.string.setup_your_password_is_only_used),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
            mobileSession.signInError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
            mobileSession.credentialStorageError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
            if (!passwordActive && !openIdActive) {
                InlineMessage(
                    stringResource(Res.string.setup_tandem_can_t_use_this),
                    InlineMessageKind.INFO,
                )
            }
        }
        mobileSession.signInError != null ->
            InlineMessage(mobileSession.signInError.orEmpty(), InlineMessageKind.ERROR)
        else ->
            InlineMessage(
                stringResource(Res.string.setup_tandem_can_t_use_this),
                InlineMessageKind.INFO,
            )
    }
}

/** Sign in with a password or OpenID, or set the server address first. */
@Composable
private fun SignInActions(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    passwordActive: Boolean,
    openIdActive: Boolean,
    onSignIn: () -> Unit,
    onStartOpenIdSignIn: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    if (!serverAddressIsValid(state.serverUrl)) {
        SetupPrimaryButton(stringResource(Res.string.setup_set_server_address), modifier = Modifier.fillMaxWidth()) {
            navigation.open(AppRoute.SERVER_ADDRESS)
        }
    }
    if (serverAddressIsValid(state.serverUrl) && passwordActive) {
        SetupPrimaryButton(
            if (mobileSession.isSigningIn) {
                stringResource(
                    Res.string.setup_signing_in,
                )
            } else {
                stringResource(Res.string.setup_sign_in)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !mobileSession.isSigningIn && state.actualPassword.isNotBlank(),
            loading = mobileSession.isSigningIn,
            onClick = {
                keyboard?.hide()
                onSignIn()
            },
        )
    }
    if (serverAddressIsValid(state.serverUrl) && openIdActive) {
        if (passwordActive) {
            SecondaryButton(
                if (mobileSession.isSigningIn) {
                    stringResource(
                        Res.string.setup_opening_sign_in,
                    )
                } else {
                    stringResource(Res.string.setup_continue_with_openid)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !mobileSession.isSigningIn,
                onClick = onStartOpenIdSignIn,
            )
        } else {
            SetupPrimaryButton(
                if (mobileSession.isSigningIn) {
                    stringResource(
                        Res.string.setup_opening_sign_in,
                    )
                } else {
                    stringResource(Res.string.setup_continue_with_openid)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !mobileSession.isSigningIn,
                loading = mobileSession.isSigningIn,
                onClick = onStartOpenIdSignIn,
            )
        }
    }
}
