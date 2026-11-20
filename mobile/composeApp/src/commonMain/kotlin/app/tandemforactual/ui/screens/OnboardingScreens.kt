@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.isLocalHttpHost
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AnimatedStatusText
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemLoadingIndicator
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay

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
                Text("Opening your budget", color = palette.muted, fontSize = TandemTypography.body)
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
                SetupBrandWordmark()
                Spacer(Modifier.height(TandemSpacing.extraLarge))
                WelcomeOrbitIllustration(Modifier.align(Alignment.CenterHorizontally))
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    SetupHeadline(
                        "Your budget.\nEverywhere.",
                        "Access your Actual Budget anywhere. Make changes offline. Sync when ready.",
                    )
                    signOutNotice?.let { InfoBubble(it) }
                }
            }
        },
        actions = {
            SetupPrimaryButton("Get started", modifier = Modifier.fillMaxWidth()) {
                navigation.open(AppRoute.SERVER_ADDRESS)
            }
            Text(
                "Need help setting up the API Gateway?",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Open API Gateway setup guide", onClick = onOpenSetupGuide)
                        .padding(vertical = TandemSpacing.extraSmall),
                color = tandemPalette.accent,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun SetupBrandWordmark() {
    val palette = tandemPalette
    Column(
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(palette.accent))
            Text(
                "tandem",
                color = palette.text,
                fontSize = TandemTypography.setupBrandWordmark,
                fontWeight = FontWeight.Bold,
                fontFamily = tandemAlloFamily(),
            )
        }
        Text("FOR ACTUAL BUDGET", color = palette.muted, fontSize = TandemTypography.micro, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WelcomeOrbitIllustration(modifier: Modifier = Modifier) {
    val palette = tandemPalette
    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(168.dp)
                .border(2.dp, palette.accent.copy(alpha = 0.2f), CircleShape),
        )
        Box(
            Modifier
                .size(136.dp)
                .border(5.dp, palette.accent.copy(alpha = 0.85f), CircleShape),
        )
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.accentSurface),
            contentAlignment = Alignment.Center,
        ) {
            TandemBrandMark(Modifier.size(42.dp))
        }
    }
}

@Composable
private fun SetupHeadline(
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
    val stepName = listOf("Server", "Sign in", "Budget", "First sync")[activeStep - 1]
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TandemIconButton(
                Lucide.ChevronLeft,
                contentDescription = "Back",
                onClickLabel = "Back to previous setup step",
                sizeDp = 44,
                iconSizeDp = 24,
                onClick = onBack ?: navigation.back,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (optional) "OPTIONAL" else "${activeStep.toString().padStart(2, '0')} / 04",
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
                        contentDescription = "Setup progress. Step $activeStep of 4: $stepName."
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
private fun SetupPrimaryButton(
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
    OnboardingLayout(
        content = {
            SetupStepHeader(step = 1, navigation = navigation)
            SetupHeadline("Connect to your server.", "Enter the address you use to access your Actual Budget server.")
            TextFieldCard(
                "Server URL",
                state.serverUrl,
                { state.serverUrl = it },
                TextFieldOptions(placeholder = "https://"),
            )
            InfoBubble("Tandem checks for the Actual API Gateway at /api/v1/.")
            if (serverAddressMayBeInsecure(state.serverUrl)) {
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
                        "Use HTTPS to protect budget data on public networks.",
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        lineHeight = TandemTypography.bodyLineHeight,
                    )
                }
            }
        },
        actions = {
            SetupPrimaryButton("Continue", modifier = Modifier.fillMaxWidth(), enabled = serverAddressIsValid(state.serverUrl)) {
                navigation.open(AppRoute.SIGN_IN)
            }
        },
    )
}

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
            SetupHeadline("Welcome back.", "Sign in with your existing Actual Budget account.")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    LabelText("SERVER")
                    Text(
                        state.serverUrl.takeIf { serverAddressIsValid(it) } ?: "Not set",
                        color = palette.text,
                        fontSize = TandemTypography.compactBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (serverAddressIsValid(state.serverUrl)) {
                    Text(
                        "Change",
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(TandemShapes.small))
                                .clickable(role = Role.Button, onClickLabel = "Change server address") {
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
                            "Server is reachable",
                            color = palette.text,
                            fontSize = TandemTypography.compactBody,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Connection verified", color = palette.muted, fontSize = TandemTypography.caption)
                    }
                }
            }
            when {
                !serverAddressIsValid(state.serverUrl) -> {
                    InlineMessage("Add your Actual Budget server address to continue.", InlineMessageKind.INFO)
                }
                mobileSession.isLoadingLoginMethods || (mobileSession.actualLoginMethods == null && mobileSession.signInError == null) -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        TandemLoadingIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
                        Text("Checking sign-in options…", color = palette.muted, fontSize = TandemTypography.caption)
                    }
                }
                mobileSession.actualLoginMethods?.bootstrapped == false -> {
                    InlineMessage("Create a budget in Actual, then return here to sign in.", InlineMessageKind.INFO)
                }
                mobileSession.actualLoginMethods != null -> {
                    if (passwordActive) {
                        TextFieldCard(
                            "Actual password",
                            state.actualPassword,
                            { state.actualPassword = it },
                            TextFieldOptions(placeholder = "Password", isSecret = true),
                        )
                        Text(
                            "Your password is only used to sign in and isn’t saved on this device.",
                            color = palette.muted,
                            fontSize = TandemTypography.caption,
                        )
                    }
                    mobileSession.signInError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
                    mobileSession.credentialStorageError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
                    if (!passwordActive && !openIdActive) {
                        InlineMessage(
                            "Tandem can’t use this sign-in method. Change Actual to password or OpenID.",
                            InlineMessageKind.INFO,
                        )
                    }
                }
                mobileSession.signInError != null ->
                    InlineMessage(mobileSession.signInError.orEmpty(), InlineMessageKind.ERROR)
                else ->
                    InlineMessage(
                        "Tandem can’t use this sign-in method. Change Actual to password or OpenID.",
                        InlineMessageKind.INFO,
                    )
            }
        },
        actions = {
            if (!serverAddressIsValid(state.serverUrl)) {
                SetupPrimaryButton("Set server address", modifier = Modifier.fillMaxWidth()) {
                    navigation.open(AppRoute.SERVER_ADDRESS)
                }
            }
            if (serverAddressIsValid(state.serverUrl) && passwordActive) {
                SetupPrimaryButton(
                    if (mobileSession.isSigningIn) "Signing in" else "Sign in",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !mobileSession.isSigningIn && state.actualPassword.isNotBlank(),
                    loading = mobileSession.isSigningIn,
                    onClick = onSignIn,
                )
            }
            if (serverAddressIsValid(state.serverUrl) && openIdActive) {
                if (passwordActive) {
                    SecondaryButton(
                        if (mobileSession.isSigningIn) "Opening sign-in" else "Continue with OpenID",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !mobileSession.isSigningIn,
                        onClick = onStartOpenIdSignIn,
                    )
                } else {
                    SetupPrimaryButton(
                        if (mobileSession.isSigningIn) "Opening sign-in" else "Continue with OpenID",
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
                    "Choose a saved budget."
                } else {
                    "Choose a budget."
                },
                if (usingCachedCatalog) {
                    "Select the budget saved on this device."
                } else if (hasCachedOnlyBudgets) {
                    "Choose a server budget or a saved copy."
                } else {
                    "Select the Actual budget to use on this device."
                },
            )
            LabelText("AVAILABLE BUDGETS")
            when {
                usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                    selectableBudgets.forEach { budget ->
                        ChoiceRow(
                            budget.name,
                            if (budget.isEncrypted) "Encrypted budget · saved on this device" else "Saved on this device",
                            selected = chooserSelectedBudgetId == budget.budgetId,
                        ) { chooseBudget(budget) }
                    }
                }
                selectableBudgets.isNotEmpty() -> {
                    selectableBudgets.forEach { budget ->
                        val isOnServer = mobileSession.budgets.any { it.budgetId == budget.budgetId }
                        val detail =
                            when {
                                !isOnServer && budget.isEncrypted -> "Encrypted budget · saved on this device"
                                !isOnServer -> "Saved on this device"
                                budget.isEncrypted -> "Encrypted Actual budget"
                                else -> "Available on this server"
                            }
                        ChoiceRow(budget.name, detail, selected = chooserSelectedBudgetId == budget.budgetId) { chooseBudget(budget) }
                    }
                }
                mobileSession.isLoadingBudgets -> BodyText("Loading budgets")
                mobileSession.budgets.isEmpty() && mobileSession.isAuthenticated -> {
                    mobileSession.budgetError?.let { error ->
                        InlineMessage(error, InlineMessageKind.ERROR)
                    } ?: EmptyState("No budgets available", "Ask the budget owner to grant access.")
                    SecondaryButton("Try again", onClick = onRefreshBudgets)
                }
                mobileSession.budgets.isEmpty() -> {
                    SurfaceCard {
                        Column(
                            Modifier.padding(TandemSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        ) {
                            LabelText("Sign in first")
                            BodyText("Connect this device to your Tandem server to choose a budget.")
                        }
                    }
                    SecondaryButton("Sign in") { navigation.open(AppRoute.SIGN_IN) }
                }
            }
            mobileSession.budgetError?.takeIf { selectableBudgets.isNotEmpty() }?.let { error ->
                InlineMessage(error, InlineMessageKind.ERROR)
            }
        },
        actions = {
            SetupPrimaryButton(
                "Continue",
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
                "Unlock your budget.",
                "${state.selectedBudget} is encrypted. Enter the separate password you use to open it in Actual.",
            )
            TextFieldCard(
                "Budget encryption password",
                state.encryptedPassword,
                { state.encryptedPassword = it },
                TextFieldOptions(placeholder = "Password", isSecret = true),
            )
            InfoBubble("This is not your Actual sign-in password. Tandem keeps it only while the app is unlocked.")
            if (LocalDeviceAuthenticator.current?.canStoreSecrets() == true) {
                BiometricPasswordCheckbox(state.rememberBudgetPasswordWithBiometrics) {
                    state.rememberBudgetPasswordWithBiometrics = it
                }
            }
        },
        actions = {
            SetupPrimaryButton(
                "Unlock budget",
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
            SetupHeadline("Making it yours.", "Downloading a local copy so your budget is ready when you’re offline.")
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
                            contentDescription = "First sync complete",
                            modifier = Modifier.size(42.dp),
                            tint = palette.positive,
                        )
                    mobileSession.syncError != null && !mobileSession.isSyncing ->
                        Icon(
                            Lucide.CircleAlert,
                            contentDescription = "First sync needs attention",
                            modifier = Modifier.size(42.dp),
                            tint = palette.warning,
                        )
                    else -> TandemLoadingIndicator(Modifier.size(42.dp), palette.accent, 3.dp)
                }
            }
            val syncMessage =
                when {
                    mobileSession.isSyncing -> "Saving your budget for offline use."
                    mobileSession.syncComplete -> "Complete offline copy saved"
                    mobileSession.syncError != null -> "Couldn’t save a complete offline copy."
                    else -> "Preparing your offline copy."
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
                    "${mobileSession.syncedTransactionCount} transactions saved. Your budget is now available on this device.",
                )
            }
            if (mobileSession.syncError != null && !mobileSession.isSyncing) {
                InlineMessage(mobileSession.syncError.orEmpty(), InlineMessageKind.ERROR)
                if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId &&
                    mobileSession.hasCachedSnapshot &&
                    !state.selectedBudgetEncrypted
                ) {
                    BodyText("Your last saved copy is still available on this device.")
                    SecondaryButton("Open saved copy") {
                        state.pendingBudgetSelectionOrigin = null
                        selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        navigation.selectTab(AppRoute.BUDGET)
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
                                "Continue"
                            } else {
                                "Open budget"
                            },
                        onComplete = {
                            selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        },
                    )
                mobileSession.syncError != null && !mobileSession.isSyncing ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        SecondaryButton("Cancel", modifier = Modifier.weight(1f), onClick = navigation.back)
                        SetupPrimaryButton("Try again", modifier = Modifier.weight(1f), onClick = onRetrySync)
                    }
                else ->
                    Text(
                        "Keep Tandem open for the first download.",
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
    actionLabel: String = "Done",
    onComplete: () -> Unit,
) {
    SetupPrimaryButton(actionLabel, modifier = Modifier.fillMaxWidth(), enabled = syncComplete) {
        state.pendingBudgetSelectionOrigin = null
        onComplete()
        if (shouldShowNotificationOnboarding(state.hasCompletedNotificationOnboarding, notificationPermissionEnabled)) {
            navigation.open(AppRoute.NOTIFICATION_ONBOARDING)
        } else {
            state.hasCompletedNotificationOnboarding = true
            navigation.open(AppRoute.SETUP_COMPLETE)
        }
    }
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
internal fun NotificationOnboardingScreen(
    state: AppState,
    navigation: AppNavigation,
    onRequestNotificationPermission: (() -> Unit)?,
) {
    OnboardingLayout(
        verticalSpacing = TandemSpacing.medium,
        content = {
            SetupStepHeader(step = 4, navigation = navigation, optional = true)
            SetupHeadline("Stay in the loop.", "Get helpful alerts when your budget needs attention. You can change this later.")
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("notification-onboarding-info"),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
            ) {
                listOf(
                    "Sync issues" to "Know when your Actual server needs attention.",
                    "Needs review" to "These changes need your decision.",
                ).forEach { (title, detail) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.accent)
                        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                            Text(
                                title,
                                color = tandemPalette.text,
                                fontSize = TandemTypography.compactBody,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                detail,
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                lineHeight = TandemTypography.bodyLineHeight,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            SetupPrimaryButton("Allow notifications", modifier = Modifier.fillMaxWidth()) {
                finishNotificationOnboarding(state, navigation, onRequestNotificationPermission)
            }
            SecondaryButton("Not now", modifier = Modifier.fillMaxWidth()) {
                finishNotificationOnboarding(state, navigation, onRequestPermission = null)
            }
        },
    )
}

internal fun finishNotificationOnboarding(
    state: AppState,
    navigation: AppNavigation,
    onRequestPermission: (() -> Unit)?,
) {
    state.hasCompletedNotificationOnboarding = true
    onRequestPermission?.invoke()
    navigation.open(AppRoute.SETUP_COMPLETE)
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
                "You’re all set.",
                "${state.selectedBudget.ifBlank { "Your budget" }} is ready. Your first offline copy is saved on this device.",
            )
            Text(
                "Sync again whenever you need.",
                color = palette.muted,
                fontSize = TandemTypography.body,
            )
        },
        actions = {
            SetupPrimaryButton("Open budget", modifier = Modifier.fillMaxWidth()) {
                state.pendingBudgetSelectionOrigin = null
                navigation.selectTab(AppRoute.BUDGET)
            }
            Text(
                "You can manage syncing in Settings.",
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
