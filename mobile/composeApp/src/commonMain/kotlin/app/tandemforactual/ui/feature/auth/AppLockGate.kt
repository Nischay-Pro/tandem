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
import app.tandemforactual.ui.core.security.DeviceAuthenticator
import app.tandemforactual.ui.core.security.budgetPasswordSecretId
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.TandemMobileSession
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
import kotlinx.coroutines.delay

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
    val form =
        remember(budgetId) {
            val hasStoredPassword = canUseBiometrics && authenticator?.hasStoredSecret(secretId) == true
            EncryptedBudgetUnlockForm(
                hasStoredPassword = hasStoredPassword,
                rememberWithBiometrics = hasStoredPassword || state.biometricLockEnabled,
                error =
                    when (mobileSession.budgetPasswordRejection) {
                        "budget_password_incorrect" -> "That password didn’t unlock this budget. It may have changed in Actual."
                        else -> null
                    },
            )
        }
    val progress = remember { EncryptedBudgetUnlockProgress() }
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
    val unlocker =
        EncryptedBudgetUnlocker(
            state,
            mobileSession,
            authenticator,
            vault,
            lockState,
            onUnlocked,
            form,
            progress,
            scope,
            canUseBiometrics,
            retryAtMillis,
        )
    LaunchedEffect(budgetId, appInForeground) {
        if (form.hasStoredPassword && !form.usePassword && appInForeground) unlocker.unlockWithBiometrics()
    }

    LockScaffold(
        if (form.usePassword) Lucide.LockKeyhole else Lucide.FingerprintPattern,
        "Unlock your budget.",
        "${state.selectedBudget} is encrypted. " +
            if (form.usePassword) "Enter the password you use to open it in Actual." else "Use your fingerprint or face to open it.",
    ) {
        if (form.usePassword) {
            PasswordUnlockForm(form, progress.checking, canUseBiometrics, waitSeconds) {
                unlocker.submit(form.password, fromBiometrics = false)
            }
        } else {
            BiometricUnlockForm(form, progress.checking, onUnlock = unlocker::unlockWithBiometrics)
        }
        LockTextAction("Choose another budget", onClick = onChooseBudget)
    }
}

/** Password field, biometrics opt-in and unlock button. */
@Composable
private fun PasswordUnlockForm(
    form: EncryptedBudgetUnlockForm,
    checking: Boolean,
    canUseBiometrics: Boolean,
    waitSeconds: Long,
    onSubmit: () -> Unit,
) {
    TextFieldCard(
        "Budget encryption password",
        form.password,
        {
            form.password = it
            form.error = null
        },
        TextFieldOptions(placeholder = "Password", isSecret = true),
    )
    FieldErrorMessage(form.error)
    if (canUseBiometrics) {
        BiometricPasswordCheckbox(form.rememberWithBiometrics) { form.rememberWithBiometrics = it }
    }
    PrimaryButton(
        when {
            checking -> "Unlocking…"
            waitSeconds > 0 -> "Try again in ${waitSeconds}s"
            else -> "Unlock"
        },
        enabled = form.password.isNotBlank() && !checking && waitSeconds == 0L,
        loading = checking,
    ) { onSubmit() }
    if (form.hasStoredPassword) LockTextAction("Use biometrics instead") { form.usePassword = false }
}

/** Biometric unlock button with a way back to the password. */
@Composable
private fun BiometricUnlockForm(
    form: EncryptedBudgetUnlockForm,
    checking: Boolean,
    onUnlock: () -> Unit,
) {
    FieldErrorMessage(form.error)
    PrimaryButton(
        if (checking) "Unlocking…" else "Unlock with biometrics",
        enabled = !checking,
        loading = checking,
        onClick = onUnlock,
    )
    LockTextAction("Use password instead") { form.usePassword = true }
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
