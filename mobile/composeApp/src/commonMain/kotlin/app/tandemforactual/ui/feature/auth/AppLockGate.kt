@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.auth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.EncryptedBudgetUnlockResult
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.unlockEncryptedBudget
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.templates.DisplayHeadline
import app.tandemforactual.ui.design.templates.HeadlineDetail
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.sync.SyncStatusRing
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppLockGate(
    state: AppState,
    mobileSession: TandemMobileSession,
    route: AppRoute,
    authenticator: DeviceAuthenticator?,
    lockState: AppLockState,
    appInForeground: Boolean,
    onChooseBudget: () -> Unit,
    vault: EncryptedBudgetVault? = null,
    onUnlocked: () -> Unit = {},
    backTargetRoute: AppRoute? = null,
    backProgress: Float = 0f,
) {
    LaunchedEffect(lockState.unlocked, state.selectedBudgetEncrypted) {
        if (!lockState.unlocked && state.selectedBudgetEncrypted) state.encryptedPassword = ""
    }
    LaunchedEffect(mobileSession.budgetPasswordRejection) {
        val rejection = mobileSession.budgetPasswordRejection
        if (rejection != null && state.selectedBudgetEncrypted) {
            state.encryptedPassword = ""
            if (rejection == "budget_password_incorrect") {
                val secretId = budgetPasswordSecretId(state.selectedBudgetId)
                authenticator?.deleteSecret(secretId)
                authenticator?.forgetPasswordCheck(secretId)
            }
        }
    }
    val budgetLocked = needsEncryptedBudgetUnlock(state, route)
    val lockedBehindBack =
        !budgetLocked && backProgress > 0f && backTargetRoute != null && needsEncryptedBudgetUnlock(state, backTargetRoute)
    val appLocked =
        !budgetLocked &&
            !state.selectedBudgetEncrypted &&
            state.biometricLockEnabled &&
            authenticator != null &&
            !lockState.unlocked
    var prompting by remember { mutableStateOf(false) }
    val unlockApp: () -> Unit = {
        if (authenticator != null && !prompting) {
            prompting = true
            authenticator.authenticate("Unlock Tandem") { success ->
                prompting = false
                if (success) lockState.unlock()
            }
        }
    }
    LaunchedEffect(appLocked, appInForeground) {
        if (appLocked && appInForeground) unlockApp()
    }
    AnimatedVisibility(appLocked, enter = EnterTransition.None, exit = fadeOut()) {
        LockScaffold(Lucide.LockKeyhole, "Tandem is locked.", "Unlock with your fingerprint, face, or screen lock.") {
            PrimaryButton("Unlock", onClick = unlockApp)
        }
    }
    AnimatedVisibility(budgetLocked || lockedBehindBack, enter = EnterTransition.None, exit = fadeOut()) {
        Box(Modifier.graphicsLayer { alpha = if (budgetLocked) 1f else backProgress }) {
            EncryptedBudgetUnlock(
                state,
                mobileSession,
                authenticator,
                vault,
                lockState,
                appInForeground && budgetLocked,
                onChooseBudget,
                onUnlocked,
            )
        }
    }
}

@Composable
private fun EncryptedBudgetUnlock(
    state: AppState,
    mobileSession: TandemMobileSession,
    authenticator: DeviceAuthenticator?,
    vault: EncryptedBudgetVault?,
    lockState: AppLockState,
    appInForeground: Boolean,
    onChooseBudget: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val budgetId = state.selectedBudgetId
    val secretId = budgetPasswordSecretId(budgetId)
    val canUseBiometrics = remember(authenticator) { authenticator?.canStoreSecrets() == true }
    var hasStoredPassword by remember(budgetId) {
        mutableStateOf(canUseBiometrics && authenticator?.hasStoredSecret(secretId) == true)
    }
    var rememberWithBiometrics by remember(budgetId) { mutableStateOf(hasStoredPassword || state.biometricLockEnabled) }
    var usePassword by remember(budgetId) { mutableStateOf(!hasStoredPassword) }
    var password by remember(budgetId) { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember(budgetId) {
        mutableStateOf(
            when (mobileSession.budgetPasswordRejection) {
                "budget_password_incorrect" -> "That password didn’t unlock this budget. It may have changed in Actual."
                else -> null
            },
        )
    }
    var prompting by remember { mutableStateOf(false) }
    val retryAtMillis = state.unlockRetryAtEpochMillis
    var nowMillis by remember { mutableStateOf(mobileSession.nowEpochMillis()) }
    LaunchedEffect(retryAtMillis) {
        while (mobileSession.nowEpochMillis() < retryAtMillis) {
            nowMillis = mobileSession.nowEpochMillis()
            delay(1_000)
        }
        nowMillis = mobileSession.nowEpochMillis()
    }
    val waitSeconds = ((retryAtMillis - nowMillis + 999) / 1_000).coerceAtLeast(0)

    fun submit(
        candidate: String,
        fromBiometrics: Boolean,
    ) {
        if (candidate.isBlank() || checking || mobileSession.nowEpochMillis() < retryAtMillis) return
        checking = true
        error = null
        scope.launch {
            val unlockWith = {
                state.unlockFailureCount = 0
                state.unlockRetryAtEpochMillis = 0L
                state.encryptedPassword = candidate
                lockState.unlock()
                onUnlocked()
            }
            val rememberChoice = {
                when {
                    fromBiometrics || authenticator == null -> unlockWith()
                    rememberWithBiometrics && canUseBiometrics ->
                        authenticator.storeSecret(secretId, candidate, "Use biometrics for ${state.selectedBudget}") { unlockWith() }
                    else -> {
                        authenticator.deleteSecret(secretId)
                        unlockWith()
                    }
                }
            }
            val rejectStoredPassword = {
                state.unlockFailureCount += 1
                unlockWaitMillis(state.unlockFailureCount).takeIf { it > 0 }?.let {
                    state.unlockRetryAtEpochMillis = mobileSession.nowEpochMillis() + it
                }
                if (fromBiometrics) {
                    authenticator?.deleteSecret(secretId)
                    hasStoredPassword = false
                    usePassword = true
                }
            }
            if (vault != null) {
                val result = mobileSession.unlockEncryptedBudget(vault, budgetId, candidate)
                checking = false
                when (result) {
                    EncryptedBudgetUnlockResult.UNLOCKED -> rememberChoice()
                    EncryptedBudgetUnlockResult.WRONG_PASSWORD -> {
                        rejectStoredPassword()
                        error =
                            if (fromBiometrics) {
                                "Your saved password no longer works. It may have changed in Actual. Enter the current password."
                            } else {
                                "That password didn’t unlock this budget. It may have changed in Actual."
                            }
                    }
                    EncryptedBudgetUnlockResult.TOO_MANY_ATTEMPTS ->
                        error = "Too many incorrect passwords. Try again in a few minutes."
                    EncryptedBudgetUnlockResult.NEEDS_CONNECTION ->
                        error = "Connect to the internet to unlock this budget for the first time."
                    EncryptedBudgetUnlockResult.FAILED -> {
                        rejectStoredPassword()
                        error = "Actual didn’t accept that password. Check it and try again."
                    }
                }
                return@launch
            }
            mobileSession.synchronize(budgetId = budgetId, encryptionPassword = candidate)
            val synced = mobileSession.syncComplete
            val offlineCheck =
                if (!synced && !mobileSession.isServerReachable) {
                    withContext(Dispatchers.Default) { authenticator?.matchesPasswordCheck(secretId, candidate) }
                } else {
                    null
                }
            if (synced) withContext(Dispatchers.Default) { authenticator?.rememberPasswordCheck(secretId, candidate) }
            if (mobileSession.budgetPasswordRejection == "budget_password_incorrect") authenticator?.forgetPasswordCheck(secretId)
            checking = false
            if (!encryptedBudgetUnlockAccepted(synced, mobileSession.isServerReachable, offlineCheck)) {
                if (!mobileSession.isServerReachable && offlineCheck == null) {
                    error = "Connect to the internet to unlock this budget for the first time."
                    return@launch
                }
                rejectStoredPassword()
                error = "That password didn’t unlock this budget. It may have changed in Actual."
                return@launch
            }
            rememberChoice()
        }
    }

    val unlockWithBiometrics: () -> Unit = {
        if (authenticator != null && !prompting && !checking) {
            prompting = true
            authenticator.readSecret(secretId, "Unlock ${state.selectedBudget}") { secret ->
                prompting = false
                if (secret == null) {
                    if (authenticator.hasStoredSecret(secretId).not()) {
                        hasStoredPassword = false
                        usePassword = true
                    }
                } else {
                    submit(secret, fromBiometrics = true)
                }
            }
        }
    }
    LaunchedEffect(budgetId, appInForeground) {
        if (hasStoredPassword && !usePassword && appInForeground) unlockWithBiometrics()
    }

    LockScaffold(
        if (usePassword) Lucide.LockKeyhole else Lucide.FingerprintPattern,
        "Unlock your budget.",
        "${state.selectedBudget} is encrypted. " +
            if (usePassword) "Enter the password you use to open it in Actual." else "Use your fingerprint or face to open it.",
    ) {
        if (usePassword) {
            TextFieldCard(
                "Budget encryption password",
                password,
                {
                    password = it
                    error = null
                },
                TextFieldOptions(placeholder = "Password", isSecret = true),
            )
            FieldErrorMessage(error)
            if (canUseBiometrics) {
                BiometricPasswordCheckbox(rememberWithBiometrics) { rememberWithBiometrics = it }
            }
            PrimaryButton(
                when {
                    checking -> "Unlocking…"
                    waitSeconds > 0 -> "Try again in ${waitSeconds}s"
                    else -> "Unlock"
                },
                enabled = password.isNotBlank() && !checking && waitSeconds == 0L,
                loading = checking,
            ) { submit(password, fromBiometrics = false) }
            if (hasStoredPassword) LockTextAction("Use biometrics instead") { usePassword = false }
        } else {
            FieldErrorMessage(error)
            PrimaryButton(
                if (checking) "Unlocking…" else "Unlock with biometrics",
                enabled = !checking,
                loading = checking,
                onClick = unlockWithBiometrics,
            )
            LockTextAction("Use password instead") { usePassword = true }
        }
        LockTextAction("Choose another budget", onClick = onChooseBudget)
    }
}

@Composable
private fun LockTextAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(TandemShapes.capsule))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        color = tandemPalette.accent,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LockScaffold(
    icon: ImageVector,
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    val palette = tandemPalette
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(TandemSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.large),
        ) {
            SyncStatusRing(icon, palette.accent, active = false, caption = null)
            DisplayHeadline(title, textAlign = TextAlign.Center)
            HeadlineDetail(detail, textAlign = TextAlign.Center)
            content()
        }
    }
}
