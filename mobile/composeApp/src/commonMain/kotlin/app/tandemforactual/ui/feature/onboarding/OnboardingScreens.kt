@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.isLocalHttpHost
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.LanguageButton
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.budgetChoicesForSelection
import app.tandemforactual.ui.core.state.cachedBudgetsWithLocalSnapshots
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.restorePendingBudgetSelectionOrigin
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.AnimatedStatusText
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ChoiceRow
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.templates.HeadlineDetail
import app.tandemforactual.ui.design.templates.InfoBubble
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemBrandMark
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.auth.BiometricPasswordCheckbox
import app.tandemforactual.ui.feature.auth.LocalDeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.settings_section_budget
import app.tandemforactual.ui.generated.resources.setup_access_your_actual_budget_anywhere
import app.tandemforactual.ui.generated.resources.setup_actual_password
import app.tandemforactual.ui.generated.resources.setup_add_your_actual_budget_server
import app.tandemforactual.ui.generated.resources.setup_ask_the_budget_owner_to
import app.tandemforactual.ui.generated.resources.setup_available_budgets
import app.tandemforactual.ui.generated.resources.setup_available_on_this_server
import app.tandemforactual.ui.generated.resources.setup_back
import app.tandemforactual.ui.generated.resources.setup_back_to_previous_setup_step
import app.tandemforactual.ui.generated.resources.setup_budget_encryption_password
import app.tandemforactual.ui.generated.resources.setup_budget_is_encrypted
import app.tandemforactual.ui.generated.resources.setup_budget_is_ready
import app.tandemforactual.ui.generated.resources.setup_budgets
import app.tandemforactual.ui.generated.resources.setup_cancel
import app.tandemforactual.ui.generated.resources.setup_change
import app.tandemforactual.ui.generated.resources.setup_change_server_address
import app.tandemforactual.ui.generated.resources.setup_checking_sign_in_options
import app.tandemforactual.ui.generated.resources.setup_choose_a_budget
import app.tandemforactual.ui.generated.resources.setup_choose_a_saved_budget
import app.tandemforactual.ui.generated.resources.setup_choose_a_server_budget_or
import app.tandemforactual.ui.generated.resources.setup_complete_offline_copy_saved
import app.tandemforactual.ui.generated.resources.setup_connect_this_device_to_your
import app.tandemforactual.ui.generated.resources.setup_connect_to_your_server
import app.tandemforactual.ui.generated.resources.setup_connection_verified
import app.tandemforactual.ui.generated.resources.setup_continue
import app.tandemforactual.ui.generated.resources.setup_continue_with_openid
import app.tandemforactual.ui.generated.resources.setup_couldn_t_save_a_complete
import app.tandemforactual.ui.generated.resources.setup_create_a_budget_in_actual
import app.tandemforactual.ui.generated.resources.setup_done
import app.tandemforactual.ui.generated.resources.setup_downloading_a_local_copy_so
import app.tandemforactual.ui.generated.resources.setup_encrypted
import app.tandemforactual.ui.generated.resources.setup_encrypted_actual_budget
import app.tandemforactual.ui.generated.resources.setup_encrypted_budget_saved_on_this
import app.tandemforactual.ui.generated.resources.setup_encrypted_saved_on_this_device
import app.tandemforactual.ui.generated.resources.setup_enter_the_address_you_use
import app.tandemforactual.ui.generated.resources.setup_first_sync
import app.tandemforactual.ui.generated.resources.setup_first_sync_complete
import app.tandemforactual.ui.generated.resources.setup_first_sync_needs_attention
import app.tandemforactual.ui.generated.resources.setup_for_actual_budget
import app.tandemforactual.ui.generated.resources.setup_get_started
import app.tandemforactual.ui.generated.resources.setup_keep_tandem_open_for_the
import app.tandemforactual.ui.generated.resources.setup_loading_budgets
import app.tandemforactual.ui.generated.resources.setup_making_it_yours
import app.tandemforactual.ui.generated.resources.setup_need_help_setting_up_the
import app.tandemforactual.ui.generated.resources.setup_no_budgets_available
import app.tandemforactual.ui.generated.resources.setup_not_secure
import app.tandemforactual.ui.generated.resources.setup_not_set
import app.tandemforactual.ui.generated.resources.setup_on_this_server
import app.tandemforactual.ui.generated.resources.setup_open
import app.tandemforactual.ui.generated.resources.setup_open_api_gateway_setup_guide
import app.tandemforactual.ui.generated.resources.setup_open_budget
import app.tandemforactual.ui.generated.resources.setup_open_saved_copy
import app.tandemforactual.ui.generated.resources.setup_opening_sign_in
import app.tandemforactual.ui.generated.resources.setup_opening_your_budget
import app.tandemforactual.ui.generated.resources.setup_optional
import app.tandemforactual.ui.generated.resources.setup_password
import app.tandemforactual.ui.generated.resources.setup_preparing_your_offline_copy
import app.tandemforactual.ui.generated.resources.setup_progress_step
import app.tandemforactual.ui.generated.resources.setup_saved_on_this_device
import app.tandemforactual.ui.generated.resources.setup_saving_your_budget_for_offline
import app.tandemforactual.ui.generated.resources.setup_secure_connection
import app.tandemforactual.ui.generated.resources.setup_select_the_actual_budget_to
import app.tandemforactual.ui.generated.resources.setup_select_the_budget_saved_on
import app.tandemforactual.ui.generated.resources.setup_server
import app.tandemforactual.ui.generated.resources.setup_server_2
import app.tandemforactual.ui.generated.resources.setup_server_is_reachable
import app.tandemforactual.ui.generated.resources.setup_server_url
import app.tandemforactual.ui.generated.resources.setup_set_server_address
import app.tandemforactual.ui.generated.resources.setup_sign_in
import app.tandemforactual.ui.generated.resources.setup_sign_in_first
import app.tandemforactual.ui.generated.resources.setup_sign_in_to_this_server
import app.tandemforactual.ui.generated.resources.setup_sign_in_with_your_existing
import app.tandemforactual.ui.generated.resources.setup_signing_in
import app.tandemforactual.ui.generated.resources.setup_sync_again_whenever_you_need
import app.tandemforactual.ui.generated.resources.setup_tandem
import app.tandemforactual.ui.generated.resources.setup_tandem_can_t_use_this
import app.tandemforactual.ui.generated.resources.setup_tandem_checks_for_the_actual
import app.tandemforactual.ui.generated.resources.setup_this_is_not_your_actual
import app.tandemforactual.ui.generated.resources.setup_transactions_saved
import app.tandemforactual.ui.generated.resources.setup_try_again
import app.tandemforactual.ui.generated.resources.setup_unlock_budget
import app.tandemforactual.ui.generated.resources.setup_unlock_your_budget
import app.tandemforactual.ui.generated.resources.setup_use_https_to_protect_budget
import app.tandemforactual.ui.generated.resources.setup_welcome_back
import app.tandemforactual.ui.generated.resources.setup_you_can_manage_syncing_in
import app.tandemforactual.ui.generated.resources.setup_you_re_all_set
import app.tandemforactual.ui.generated.resources.setup_your_budget
import app.tandemforactual.ui.generated.resources.setup_your_budget_everywhere
import app.tandemforactual.ui.generated.resources.setup_your_last_saved_copy_is
import app.tandemforactual.ui.generated.resources.setup_your_password_is_only_used
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StartupScreen() {
    val palette = tandemPalette
    var showLoadingIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        showLoadingIndicator = true
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(TandemSpacing.extraLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TandemBrandMark(Modifier.size(96.dp))
        AnimatedReveal(showLoadingIndicator) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                TandemLoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.setup_opening_your_budget), color = palette.muted, fontSize = TandemTypography.body)
            }
        }
    }
}

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

@Composable
internal fun SetupHeadline(
    title: String,
    supportingText: String,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        DisplayHeadline(title)
        HeadlineDetail(supportingText)
    }
}

@Composable
private fun SetupStepHeader(
    step: Int,
    navigation: AppNavigation,
    optional: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    val activeStep = step.coerceIn(1, 4)
    val stepNames =
        listOf(
            stringResource(Res.string.setup_server),
            stringResource(Res.string.setup_sign_in),
            stringResource(Res.string.settings_section_budget),
            stringResource(Res.string.setup_first_sync),
        )
    val progressDescription = stringResource(Res.string.setup_progress_step, activeStep, stepNames[activeStep - 1])
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TandemIconButton(
                Lucide.ChevronLeft,
                contentDescription = stringResource(Res.string.setup_back),
                onClickLabel = stringResource(Res.string.setup_back_to_previous_setup_step),
                sizeDp = 44,
                iconSizeDp = 24,
                onClick = onBack ?: navigation.back,
            )
            Spacer(Modifier.weight(1f))
            LanguageButton()
            Text(
                if (optional) stringResource(Res.string.setup_optional) else "${activeStep.toString().padStart(2, '0')} / 04",
                color = palette.muted,
                fontSize = TandemTypography.label,
                fontWeight = FontWeight.Medium,
            )
        }
        if (!optional) {
            Row(
                Modifier
                    .padding(start = 12.dp)
                    .clearAndSetSemantics {
                        contentDescription = progressDescription
                    },
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .width(17.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (index < activeStep) palette.accent else palette.border),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SetupPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val light = palette.background.luminance() >= 0.5f
    val shape = RoundedCornerShape(TandemShapes.medium)
    val background =
        if (enabled) {
            if (light) {
                palette.text
            } else {
                palette.accent
            }
        } else {
            palette.raised
        }
    val foreground =
        when {
            !enabled -> palette.muted
            light -> palette.background
            else -> palette.accentContent
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = TandemSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) TandemLoadingIndicator(Modifier.size(18.dp), foreground, 2.dp)
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = if (loading) TandemSpacing.small else 0.dp),
            color = foreground,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(21.dp), tint = foreground)
    }
}

@Composable
internal fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val completedAddress = completeServerAddress(state.serverUrl)
    val canContinue = serverAddressIsValid(completedAddress)
    val secureAddress = completedAddress.startsWith("https://", ignoreCase = true)
    val continueToSignIn = {
        if (canContinue) {
            state.serverUrl = completedAddress
            navigation.open(AppRoute.SIGN_IN)
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
            AnimatedReveal(visible = suggestions.isNotEmpty()) {
                SurfaceCard {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) DividerLine()
                        ListRow(
                            suggestion.address,
                            suggestion.detail,
                            leading = RowLeading.Icon(if (suggestion.recent) Lucide.History else Lucide.Globe),
                            options = ListRowOptions(dense = true),
                            onClick = { state.serverUrl = suggestion.address },
                        )
                    }
                }
            }
            InfoBubble(stringResource(Res.string.setup_tandem_checks_for_the_actual))
            if (serverAddressMayBeInsecure(completedAddress)) {
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
        },
        actions = {
            SetupPrimaryButton(stringResource(Res.string.setup_continue), modifier = Modifier.fillMaxWidth(), enabled = canContinue) {
                continueToSignIn()
            }
        },
    )
}

internal data class ServerAddressSuggestion(
    val address: String,
    val detail: String,
    val recent: Boolean,
)

internal fun completeServerAddress(value: String): String {
    val address = value.trim().trimEnd('/')
    if (address.isEmpty() || "://" in address) return address
    val host =
        address
            .substringBefore('/')
            .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBeforeLast(':') }
    return (if (isLocalHttpHost(host)) "http://" else "https://") + address
}

internal fun recentServerAddresses(saved: String): List<String> = saved.lines().map(String::trim).filter(String::isNotEmpty)

internal fun rememberRecentServerAddress(
    saved: String,
    address: String,
): String {
    val cleaned = address.trim().trimEnd('/')
    if (cleaned.isEmpty()) return saved
    return (listOf(cleaned) + recentServerAddresses(saved).filterNot { it.equals(cleaned, ignoreCase = true) })
        .take(MAX_RECENT_SERVER_ADDRESSES)
        .joinToString("\n")
}

internal fun serverAddressSuggestions(
    input: String,
    recent: List<String>,
): List<ServerAddressSuggestion> {
    val typed = input.trim().trimEnd('/')
    val needle = typed.substringAfter("://").lowercase()
    val recentMatches =
        recent
            .filter { !it.equals(typed, ignoreCase = true) && it.substringAfter("://").lowercase().contains(needle) }
            .map { ServerAddressSuggestion(it, "Used before", recent = true) }
    val completed = completeServerAddress(typed)
    val completion =
        if (typed.isNotEmpty() && "://" !in typed && serverAddressIsValid(completed)) {
            ServerAddressSuggestion(
                completed,
                if (completed.startsWith("http://")) "Home network address" else "Secure connection",
                recent = false,
            )
        } else {
            null
        }
    return (recentMatches + listOfNotNull(completion)).distinctBy { it.address.lowercase() }.take(MAX_SERVER_ADDRESS_SUGGESTIONS)
}

private const val MAX_RECENT_SERVER_ADDRESSES = 5
private const val MAX_SERVER_ADDRESS_SUGGESTIONS = 3

internal fun cleanServerAddress(value: String): String =
    value.trim().takeUnless { it.equals("https://budget.example.com", ignoreCase = true) }.orEmpty()

internal fun serverAddressIsValid(value: String): Boolean {
    val address = value.trim()
    if (address.isBlank() || address.any(Char::isWhitespace)) return false
    val scheme =
        when {
            address.startsWith("https://", ignoreCase = true) -> "https"
            address.startsWith("http://", ignoreCase = true) -> "http"
            else -> return false
        }
    val authority =
        address
            .substring(scheme.length + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if (authority.isBlank() || '@' in authority) return false
    val host: String
    val portText: String?
    if (authority.startsWith("[")) {
        val closingBracket = authority.indexOf(']')
        if (closingBracket <= 1) return false
        host = authority.substring(1, closingBracket)
        val suffix = authority.substring(closingBracket + 1)
        if (suffix.isNotEmpty() && !suffix.startsWith(':')) return false
        portText = suffix.takeIf { it.isNotEmpty() }?.drop(1)
        if (host.any { !(it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.') }) return false
    } else {
        if (authority.count { it == ':' } > 1) return false
        val separator = authority.lastIndexOf(':')
        host = if (separator >= 0) authority.substring(0, separator) else authority
        portText = if (separator >= 0) authority.substring(separator + 1) else null
        if (host.isBlank() || host.startsWith('.') || host.endsWith('.') || ".." in host) return false
        if (host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return false
    }
    if (portText != null && (portText.isEmpty() || portText.toIntOrNull()?.let { it in 1..65535 } != true)) return false
    return host.isNotBlank()
}

internal fun shouldCheckActualLoginMethods(
    serverUrl: String,
    serverUrlFocused: Boolean,
): Boolean = serverAddressIsValid(serverUrl) && !serverUrlFocused

internal fun serverAddressMayBeInsecure(value: String): Boolean {
    val address = value.trim().lowercase()
    if (!address.startsWith("http://")) return false
    val authority =
        address
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    val host =
        (if (authority.startsWith("[")) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':'))
            .removePrefix("www.")
            .trim()
    if (host.isBlank()) return false
    if (isLocalHttpHost(host)) return false
    if (host == "localhost" || host.endsWith(".localhost")) return false
    return true
}

internal fun signInHttpTransportWarning(serverUrl: String): String? =
    if (serverAddressMayBeInsecure(serverUrl)) {
        "Data may not be secure over this public HTTP connection. Use HTTPS."
    } else {
        null
    }

@Composable
internal fun SignInTransportWarning(serverUrl: String) {
    signInHttpTransportWarning(serverUrl)?.let { warning ->
        InlineMessage(warning, InlineMessageKind.WARNING)
    }
}

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
    val keyboard = LocalSoftwareKeyboardController.current
    val loginMethods = mobileSession.actualLoginMethods
    val passwordActive =
        loginMethods?.availableLoginMethods?.any { it.method == "password" && it.active } ?: (loginMethods?.loginMethod == "password")
    val openIdActive =
        loginMethods?.availableLoginMethods?.any { it.method == "openid" && it.active } ?: (loginMethods?.loginMethod == "openid")
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            val palette = tandemPalette
            SetupStepHeader(step = 2, navigation = navigation)
            SetupHeadline(stringResource(Res.string.setup_welcome_back), stringResource(Res.string.setup_sign_in_with_your_existing))
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
            if (serverAddressIsValid(state.serverUrl)) SignInTransportWarning(state.serverUrl)
            if (loginMethods != null && mobileSession.signInError == null) {
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
        },
        actions = {
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
        },
    )
}

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

@Composable
internal fun FirstSyncScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRetrySync: () -> Unit,
    notificationPermissionEnabled: Boolean?,
) {
    val palette = tandemPalette
    OnboardingLayout(
        content = {
            SetupStepHeader(step = 4, navigation = navigation)
            SetupHeadline(stringResource(Res.string.setup_making_it_yours), stringResource(Res.string.setup_downloading_a_local_copy_so))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    mobileSession.syncComplete ->
                        Icon(
                            Lucide.Check,
                            contentDescription = stringResource(Res.string.setup_first_sync_complete),
                            modifier = Modifier.size(42.dp),
                            tint = palette.positive,
                        )
                    mobileSession.syncError != null && !mobileSession.isSyncing ->
                        Icon(
                            Lucide.CircleAlert,
                            contentDescription = stringResource(Res.string.setup_first_sync_needs_attention),
                            modifier = Modifier.size(42.dp),
                            tint = palette.warning,
                        )
                    else -> TandemLoadingIndicator(Modifier.size(42.dp), palette.accent, 3.dp)
                }
            }
            val syncMessage =
                when {
                    mobileSession.isSyncing -> stringResource(Res.string.setup_saving_your_budget_for_offline)
                    mobileSession.syncComplete -> stringResource(Res.string.setup_complete_offline_copy_saved)
                    mobileSession.syncError != null -> stringResource(Res.string.setup_couldn_t_save_a_complete)
                    else -> stringResource(Res.string.setup_preparing_your_offline_copy)
                }
            val syncMessageColor by
                animateColorAsState(
                    targetValue = if (mobileSession.syncError != null && !mobileSession.isSyncing) palette.warning else palette.muted,
                    animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                    label = "first-sync-status-color",
                )
            AnimatedStatusText(
                syncMessage,
                syncMessageColor,
                fontSize = TandemTypography.compactBody,
                lineHeight = TandemTypography.bodyLineHeight,
                animateSizeChanges = true,
            )
            if (mobileSession.syncComplete) {
                BodyText(
                    pluralStringResource(
                        Res.plurals.setup_transactions_saved,
                        mobileSession.syncedTransactionCount,
                        mobileSession.syncedTransactionCount,
                    ),
                )
            }
            if (mobileSession.syncError != null && !mobileSession.isSyncing) {
                InlineMessage(mobileSession.syncError.orEmpty(), InlineMessageKind.ERROR)
                if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId &&
                    mobileSession.hasCachedSnapshot &&
                    !state.selectedBudgetEncrypted
                ) {
                    BodyText(stringResource(Res.string.setup_your_last_saved_copy_is))
                    SecondaryButton(stringResource(Res.string.setup_open_saved_copy)) {
                        state.pendingBudgetSelectionOrigin = null
                        selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        navigation.selectTab(AppRoute.HOME)
                    }
                }
            }
        },
        actions = {
            when {
                mobileSession.syncComplete ->
                    FirstSyncCompletionAction(
                        state = state,
                        navigation = navigation,
                        syncComplete = true,
                        notificationPermissionEnabled = notificationPermissionEnabled,
                        actionLabel =
                            if (shouldShowNotificationOnboarding(
                                    state.hasCompletedNotificationOnboarding,
                                    notificationPermissionEnabled,
                                )
                            ) {
                                stringResource(Res.string.setup_continue)
                            } else {
                                stringResource(Res.string.setup_open_budget)
                            },
                        onComplete = {
                            selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        },
                    )
                mobileSession.syncError != null && !mobileSession.isSyncing ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        SecondaryButton(stringResource(Res.string.setup_cancel), modifier = Modifier.weight(1f), onClick = navigation.back)
                        SetupPrimaryButton(
                            stringResource(Res.string.setup_try_again),
                            modifier = Modifier.weight(1f),
                            onClick = onRetrySync,
                        )
                    }
                else ->
                    Text(
                        stringResource(Res.string.setup_keep_tandem_open_for_the),
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        textAlign = TextAlign.Center,
                    )
            }
        },
    )
}

@Composable
internal fun FirstSyncCompletionAction(
    state: AppState,
    navigation: AppNavigation,
    syncComplete: Boolean,
    notificationPermissionEnabled: Boolean?,
    actionLabel: String = stringResource(Res.string.setup_done),
    onComplete: () -> Unit,
) {
    SetupPrimaryButton(actionLabel, modifier = Modifier.fillMaxWidth(), enabled = syncComplete) {
        state.pendingBudgetSelectionOrigin = null
        onComplete()
        continueSetup(state, navigation, notificationPermissionEnabled)
    }
}

internal fun nextSetupRoute(
    state: AppState,
    notificationPermissionEnabled: Boolean?,
): AppRoute = if (state.experience == null) AppRoute.PERSONALIZE else AppRoute.SETUP_COMPLETE

internal fun continueSetup(
    state: AppState,
    navigation: AppNavigation,
    notificationPermissionEnabled: Boolean?,
) {
    if (notificationPermissionEnabled == true) state.hasCompletedNotificationOnboarding = true
    navigation.open(nextSetupRoute(state, notificationPermissionEnabled))
}

internal fun shouldShowNotificationOnboarding(
    alreadyCompleted: Boolean,
    permissionEnabled: Boolean?,
): Boolean = !alreadyCompleted && permissionEnabled != true

internal fun budgetPasswordRequired(
    isEncrypted: Boolean,
    hasUnlockedPassword: Boolean,
): Boolean = isEncrypted && !hasUnlockedPassword

internal fun budgetOpenButtonEnabled(
    selectedBudgetAvailable: Boolean,
    loadingBudgets: Boolean,
    hasCachedSnapshot: Boolean,
    passwordRequired: Boolean,
    budgetPassword: String,
): Boolean =
    selectedBudgetAvailable &&
        (!loadingBudgets || hasCachedSnapshot) &&
        (!passwordRequired || budgetPassword.isNotBlank())

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

internal fun selectLatestCachedMonth(
    state: AppState,
    budgetMonthIds: List<String>,
    transactionRecords: List<BudgetRecordEntity>,
) {
    val cachedMonthIds =
        budgetMonthIds
            .mapNotNull { monthId ->
                val parts = monthId.split('-')
                if (parts.size != 2) return@mapNotNull null
                val year = parts[0].toIntOrNull() ?: return@mapNotNull null
                val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                if (year !in 1..9999) return@mapNotNull null
                monthId
            }.distinct()
            .sorted()
    if (state.selectedMonthId in cachedMonthIds) return

    val latestActivityMonth =
        transactionRecords
            .mapNotNull { it.recordDate?.take(7)?.takeIf { monthId -> monthId in cachedMonthIds } }
            .maxOrNull()
    val selectedMonthId =
        latestActivityMonth
            ?: cachedMonthIds.filter { it <= state.selectedMonthId }.maxOrNull()
            ?: cachedMonthIds.firstOrNull()
            ?: return
    val (year, month) = selectedMonthId.split('-').map(String::toInt)
    state.selectedMonthId = selectedMonthId
    state.selectedMonth = formatMonth(year, month)
}
