@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.isLocalHttpHost
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AnimatedStatusText
import app.tandemforactual.ui.components.Badge
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemLoadingIndicator
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
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
        verticalSpacing = TandemSpacing.large,
        horizontalPadding = TandemSpacing.extraLarge,
        content = {
            TandemBrandMark(Modifier.size(72.dp))
            Heading("Tandem for Actual Budget", "Connect your Actual Budget server.")
            SetupProgressIndicator()
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    WelcomeBenefit(Lucide.Cloud, "Open your saved budget offline")
                    WelcomeBenefit(Lucide.CloudSync, "Edits wait here until they sync")
                    WelcomeBenefit(Lucide.ShieldCheck, "Changes marked “Needs review” stay on this device")
                }
            }
            SecondaryButton("How to set up a server", modifier = Modifier.fillMaxWidth(), onClick = onOpenSetupGuide)
            signOutNotice?.let { InfoBubble(it) }
        },
        actions = {
            PrimaryButton("Get started", modifier = Modifier.fillMaxWidth()) {
                navigation.open(AppRoute.SIGN_IN)
            }
        },
    )
}

@Composable
private fun SetupProgressIndicator() {
    val palette = tandemPalette
    val steps = listOf("Server", "Sign in", "Budget", "First sync")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Setup progress. Step 1 of 4: Server. Next: Sign in, Budget, First sync."
                },
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
        verticalAlignment = Alignment.Top,
    ) {
        steps.forEachIndexed { index, step ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (index == 0) palette.accent else palette.surface)
                        .border(1.dp, if (index == 0) palette.accent else palette.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color = if (index == 0) palette.accentContent else palette.muted,
                        fontSize = TandemTypography.micro,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    step,
                    color = if (index == 0) palette.text else palette.muted,
                    fontSize = TandemTypography.label,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WelcomeBenefit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tandemPalette.accent)
        Text(
            text,
            color = tandemPalette.text,
            fontSize = TandemTypography.compactBody,
            lineHeight = TandemTypography.bodyLineHeight,
        )
    }
}

@Composable
internal fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    OnboardingLayout(
        content = {
            TextFieldCard(
                "Server URL",
                state.serverUrl,
                { state.serverUrl = it },
                TextFieldOptions(placeholder = "https://"),
            )
            InfoBubble("Enter your Actual Budget address. Tandem checks for its API Gateway at /api/v1/.")
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
            PrimaryButton("Continue", modifier = Modifier.fillMaxWidth(), enabled = serverAddressIsValid(state.serverUrl)) {
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
    onSignIn: () -> Unit,
    onStartOpenIdSignIn: () -> Unit,
) {
    var serverUrlFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.serverUrl, serverUrlFocused) {
        state.actualPassword = ""
        mobileSession.clearActualLoginMethodState()
        if (shouldCheckActualLoginMethods(state.serverUrl, serverUrlFocused)) {
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
        content = {
            Heading("Sign in to Actual")
            TextFieldCard(
                "Server URL",
                state.serverUrl,
                { state.serverUrl = it },
                TextFieldOptions(
                    placeholder = "https://",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onFocusedChange = { serverUrlFocused = it },
                ),
            )
            if (serverAddressIsValid(state.serverUrl)) SignInTransportWarning(state.serverUrl)
            when {
                !serverAddressIsValid(state.serverUrl) -> {
                    InfoBubble("Enter your server address to see sign-in options.")
                }
                serverUrlFocused -> Unit
                mobileSession.isLoadingLoginMethods -> {
                    TandemLoadingIndicator(Modifier.size(24.dp), color = tandemPalette.accent, strokeWidth = 2.dp)
                }
                mobileSession.actualLoginMethods == null && mobileSession.signInError == null -> {
                    TandemLoadingIndicator(Modifier.size(24.dp), color = tandemPalette.accent, strokeWidth = 2.dp)
                }
                mobileSession.actualLoginMethods?.bootstrapped == false -> {
                    InlineMessage("Set up your Actual server before signing in.", InlineMessageKind.INFO)
                }
                mobileSession.actualLoginMethods != null -> {
                    if (passwordActive) {
                        TextFieldCard(
                            "Actual password",
                            state.actualPassword,
                            { state.actualPassword = it },
                            TextFieldOptions(placeholder = "Password", isSecret = true),
                        )
                        InfoBubble("Your password is used to sign in and isn’t saved on this device.")
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
            if (serverAddressIsValid(state.serverUrl) && passwordActive) {
                PrimaryButton(
                    if (mobileSession.isSigningIn) "Signing in" else "Sign in",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !mobileSession.isSigningIn && state.actualPassword.isNotBlank(),
                    onClick = onSignIn,
                )
            }
            if (serverAddressIsValid(state.serverUrl) && openIdActive) {
                SecondaryButton(
                    if (mobileSession.isSigningIn) "Opening sign-in" else "Continue with OpenID",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !mobileSession.isSigningIn,
                    onClick = onStartOpenIdSignIn,
                )
            }
        },
    )
}

@Composable
internal fun BudgetChooserScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onSelectBudget: (BudgetCatalogItem) -> Unit,
    onRefreshBudgets: () -> Unit,
    onOpenSelectedBudget: () -> Unit,
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
    val cachedBudgetSelected = cachedCatalog.any { it.budgetId == state.selectedBudgetId }
    val selectedBudgetIsAvailable = selectableBudgets.any { it.budgetId == state.selectedBudgetId }
    val needsEncryptionPassword = budgetPasswordRequired(state.selectedBudgetEncrypted, cachedBudgetSelected)
    PageColumn {
        Heading(
            if (usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty())) {
                "Saved budgets"
            } else {
                "Choose a budget"
            },
            if (usingCachedCatalog) {
                "Choose a saved budget to open on this device."
            } else if (hasCachedOnlyBudgets) {
                "Choose a server or saved budget to open."
            } else {
                "Choose a budget to open."
            },
        )
        when {
            usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                selectableBudgets.forEach { budget ->
                    ChoiceRow(
                        budget.name,
                        if (budget.isEncrypted) "Encrypted budget · saved on this device" else "Saved on this device",
                        selected = state.selectedBudgetId == budget.budgetId,
                    ) { onSelectBudget(budget) }
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
                    ChoiceRow(budget.name, detail, selected = state.selectedBudgetId == budget.budgetId) {
                        onSelectBudget(budget)
                    }
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
                    Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
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
        if (needsEncryptionPassword) {
            TextFieldCard(
                "Budget password",
                state.encryptedPassword,
                { state.encryptedPassword = it },
                TextFieldOptions(placeholder = "Enter password", isSecret = true),
            )
            InfoBubble("Used to unlock this budget. Cleared after the first sync.")
        }
        PrimaryButton(
            "Open budget",
            enabled =
                budgetOpenButtonEnabled(
                    selectedBudgetIsAvailable,
                    mobileSession.isLoadingBudgets,
                    cachedBudgetSelected,
                    needsEncryptionPassword,
                    state.encryptedPassword,
                ),
            onClick = onOpenSelectedBudget,
        )
    }
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
    PageColumn {
        SurfaceCard {
            Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                listOf("Connecting", "Downloading budget", "Saving on this device").forEachIndexed { index, title ->
                    val complete = mobileSession.syncComplete || (!mobileSession.isSyncing && mobileSession.syncError != null && index == 0)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color =
                            if (complete) {
                                palette.positive
                            } else if (mobileSession.syncError !=
                                null
                            ) {
                                palette.warning
                            } else {
                                palette.accent
                            }
                        if (mobileSession.isSyncing && index == 0 && !complete) {
                            TandemLoadingIndicator(Modifier.size(20.dp), color, 2.dp)
                        } else {
                            Badge(if (complete) "✓" else "${index + 1}", color)
                        }
                        Text(title, color = palette.text, fontSize = TandemTypography.compactBody)
                    }
                }
            }
        }
        val syncMessage =
            when {
                mobileSession.isSyncing -> "Syncing the budget."
                mobileSession.syncComplete ->
                    "${mobileSession.syncedTransactionCount} transactions saved on this device."
                mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                else -> "Preparing budget."
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
        AnimatedReveal(visible = mobileSession.syncError != null && !mobileSession.isSyncing) {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SecondaryButton("Try again", onClick = onRetrySync)
                if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId && mobileSession.hasCachedSnapshot) {
                    BodyText("The last saved copy is still available on this device.")
                    SecondaryButton("Open saved copy") {
                        selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                        navigation.selectTab(AppRoute.BUDGET)
                    }
                }
            }
        }
        FirstSyncCompletionAction(
            state = state,
            navigation = navigation,
            syncComplete = mobileSession.syncComplete,
            notificationPermissionEnabled = notificationPermissionEnabled,
        ) {
            selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
        }
    }
}

@Composable
internal fun FirstSyncCompletionAction(
    state: AppState,
    navigation: AppNavigation,
    syncComplete: Boolean,
    notificationPermissionEnabled: Boolean?,
    onComplete: () -> Unit,
) {
    PrimaryButton("Done", enabled = syncComplete) {
        onComplete()
        if (shouldShowNotificationOnboarding(state.hasCompletedNotificationOnboarding, notificationPermissionEnabled)) {
            navigation.open(AppRoute.NOTIFICATION_ONBOARDING)
        } else {
            state.hasCompletedNotificationOnboarding = true
            navigation.selectTab(AppRoute.BUDGET)
        }
    }
}

internal fun shouldShowNotificationOnboarding(
    alreadyCompleted: Boolean,
    permissionEnabled: Boolean?,
): Boolean = !alreadyCompleted && permissionEnabled != true

internal fun budgetPasswordRequired(
    isEncrypted: Boolean,
    hasCachedSnapshot: Boolean,
): Boolean = isEncrypted && !hasCachedSnapshot

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
        horizontalPadding = TandemSpacing.extraLarge,
        content = {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(TandemShapes.large))
                    .background(tandemPalette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Bell, contentDescription = null, modifier = Modifier.size(30.dp), tint = tandemPalette.accent)
            }
            Heading("Stay in the loop", "Get an alert when a change needs your attention.")
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    BodyText("Tandem can alert you when an edit needs review, is rejected, or has an unknown status.")
                    BodyText("You can change this later in Settings.")
                }
            }
        },
        actions = {
            PrimaryButton("Allow notifications", modifier = Modifier.fillMaxWidth()) {
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
    navigation.selectTab(AppRoute.BUDGET)
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
