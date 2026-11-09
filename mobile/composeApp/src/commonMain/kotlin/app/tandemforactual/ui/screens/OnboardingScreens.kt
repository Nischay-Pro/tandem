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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
internal fun WelcomeScreen(navigation: AppNavigation) {
    val palette = tandemPalette
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = TandemSpacing.extraLarge, vertical = TandemSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
    ) {
        Spacer(Modifier.height(14.dp))
        TandemBrandMark(Modifier.size(72.dp))
        Heading("Budget and account overview", "Plan spending, review accounts, and follow recent changes.")
        SurfaceCard {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                Text(
                    "Tandem saves a copy on this device. Actual Budget remains the source of truth.",
                    color = palette.text,
                    fontSize = TandemTypography.action,
                    lineHeight = TandemTypography.actionLineHeight,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        PrimaryButton("Get started") { navigation.open(AppRoute.SERVER_ADDRESS) }
        SecondaryButton("View sample budget") { navigation.selectTab(AppRoute.BUDGET) }
        PreviewNote("Names and amounts are sample data. This screen isn’t connected to a server.")
    }
}

@Composable
internal fun ServerAddressScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        TextFieldCard(
            "Server URL",
            state.serverUrl,
            { state.serverUrl = it },
            TextFieldOptions(placeholder = "https://"),
        )
        InfoBubble("Enter your Tandem server address. Use HTTPS over the internet; use HTTP only on a trusted local network.")
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
                    "This address may not be secure on a public network. Use HTTPS over the internet.",
                    color = palette.text,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
        PrimaryButton("Continue", enabled = serverAddressIsValid(state.serverUrl)) { navigation.open(AppRoute.PAIRING_CODE) }
    }
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

@Composable
internal fun PairingCodeScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onPairDevice: () -> Unit,
    onRetryStoredSession: () -> Unit,
    pairingCompleted: Boolean,
) {
    val palette = tandemPalette
    PageColumn {
        if (pairingCompleted) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(60.dp).clip(CircleShape).background(palette.accentSurface), contentAlignment = Alignment.Center) {
                Text("✓", color = palette.accent, fontSize = TandemTypography.heroTitle, fontWeight = FontWeight.Bold)
            }
            Heading("Device paired")
            mobileSession.pairedDevice?.let { device ->
                SurfaceCard {
                    Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        LabelText("Device access")
                        BodyText(
                            "This device can open ${device.budgetIds.size} budget${if (device.budgetIds.size == 1) "" else "s"}.",
                        )
                    }
                }
            }
            mobileSession.budgetError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
            PrimaryButton("Choose a budget", enabled = mobileSession.pairedDevice != null) {
                navigation.reset(AppRoute.BUDGET_CHOOSER)
            }
        } else {
            TextFieldCard(
                "Pairing code",
                state.pairingCode,
                { state.pairingCode = sanitizePairingCodeInput(it) },
                TextFieldOptions(
                    placeholder = "For example, 4DF7-J1M9",
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                    textAlign = TextAlign.Center,
                ),
            )
            InfoBubble("This pairing code expires after five minutes. Tandem doesn’t save it or your budget password.")
            mobileSession.pairingError?.let { error -> InlineMessage(error, InlineMessageKind.ERROR) }
            mobileSession.credentialStorageError?.let { error ->
                InlineMessage(error, InlineMessageKind.ERROR)
                SecondaryButton(
                    if (mobileSession.isRestoringStoredSession) "Checking storage" else "Try again",
                    enabled = !mobileSession.isRestoringStoredSession,
                    onClick = onRetryStoredSession,
                )
            }
            PrimaryButton(
                if (mobileSession.isPairing) "Pairing" else "Pair this device",
                enabled = !mobileSession.isPairing && state.pairingCode.filter(Char::isLetterOrDigit).length == 8,
                onClick = onPairDevice,
            )
        }
    }
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
    PageColumn {
        Heading(
            if (usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty())) {
                "Saved budgets"
            } else {
                "Choose a budget"
            },
            if (usingCachedCatalog) {
                "Open a budget already saved on this device. You can sync again when the server is available."
            } else if (hasCachedOnlyBudgets) {
                "Choose a server budget or open one of the saved copies on this device."
            } else {
                "Choose a budget this device can open."
            },
        )
        when {
            usingCachedCatalog || (mobileSession.budgets.isEmpty() && cachedCatalog.isNotEmpty()) -> {
                BodyText("The server budget list is unavailable. Saved copies stay on this device.")
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
                } ?: EmptyState("No budgets available", "This paired device has no budget access.")
                SecondaryButton("Try again", onClick = onRefreshBudgets)
            }
            mobileSession.budgets.isEmpty() -> {
                SurfaceCard {
                    Column(Modifier.padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        LabelText("Pair this device first")
                        BodyText("The budget list comes from the server after a device is paired.")
                    }
                }
                SecondaryButton("Pair a device") { navigation.open(AppRoute.SERVER_ADDRESS) }
            }
        }
        mobileSession.budgetError?.takeIf { selectableBudgets.isNotEmpty() }?.let { error ->
            InlineMessage(error, InlineMessageKind.ERROR)
        }
        PrimaryButton(
            if (state.selectedBudgetEncrypted && state.encryptedPassword.isBlank() && !cachedBudgetSelected) {
                "Unlock and open"
            } else {
                "Open budget"
            },
            enabled = selectedBudgetIsAvailable && (!mobileSession.isLoadingBudgets || cachedBudgetSelected),
            onClick = onOpenSelectedBudget,
        )
    }
}

@Composable
internal fun UnlockBudgetScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    PageColumn {
        TextFieldCard(
            "Budget password",
            state.encryptedPassword,
            { state.encryptedPassword = it },
            TextFieldOptions(placeholder = "Enter password", isSecret = true),
        )
        InfoBubble("Tandem uses this password only to unlock the encrypted budget. It’s cleared after the first sync and never saved.")
        PrimaryButton("Unlock budget", enabled = state.encryptedPassword.isNotBlank()) { navigation.replaceTop(AppRoute.FIRST_SYNC) }
    }
}

@Composable
internal fun FirstSyncScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRetrySync: () -> Unit,
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
                mobileSession.isSyncing && mobileSession.syncError != null ->
                    "Retrying · ${mobileSession.syncError}"
                mobileSession.isSyncing -> "Syncing the budget."
                mobileSession.syncComplete ->
                    "${mobileSession.syncedTransactionCount} transactions saved on this device."
                mobileSession.syncError != null -> mobileSession.syncError.orEmpty()
                else -> "Preparing budget."
            }
        val syncMessageColor by
            animateColorAsState(
                targetValue = if (mobileSession.syncError != null) palette.warning else palette.muted,
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
        PrimaryButton("Done", enabled = mobileSession.syncComplete) {
            selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
            navigation.selectTab(AppRoute.BUDGET)
        }
    }
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
