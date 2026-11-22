@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.isLocalHttpHost
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.LanguageButton
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.templates.HeadlineDetail
import app.tandemforactual.ui.design.theme.TandemBrandMark
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.settings_section_budget
import app.tandemforactual.ui.generated.resources.setup_back
import app.tandemforactual.ui.generated.resources.setup_back_to_previous_setup_step
import app.tandemforactual.ui.generated.resources.setup_done
import app.tandemforactual.ui.generated.resources.setup_first_sync
import app.tandemforactual.ui.generated.resources.setup_opening_your_budget
import app.tandemforactual.ui.generated.resources.setup_optional
import app.tandemforactual.ui.generated.resources.setup_progress_step
import app.tandemforactual.ui.generated.resources.setup_server
import app.tandemforactual.ui.generated.resources.setup_sign_in
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay
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
internal fun SetupStepHeader(
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
