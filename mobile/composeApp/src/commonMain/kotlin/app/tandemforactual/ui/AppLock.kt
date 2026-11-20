@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.FieldErrorMessage
import app.tandemforactual.ui.components.PrimaryButton
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

interface DeviceAuthenticator {
    fun isAvailable(): Boolean

    fun authenticate(
        title: String,
        onResult: (Boolean) -> Unit,
    )

    fun canStoreSecrets(): Boolean = false

    fun hasStoredSecret(id: String): Boolean = false

    fun storeSecret(
        id: String,
        secret: String,
        title: String,
        onResult: (Boolean) -> Unit,
    ) = onResult(false)

    fun readSecret(
        id: String,
        title: String,
        onResult: (String?) -> Unit,
    ) = onResult(null)

    fun deleteSecret(id: String) = Unit

    fun deleteAllSecrets() = Unit
}

internal val LocalDeviceAuthenticator = staticCompositionLocalOf<DeviceAuthenticator?> { null }

internal const val APP_LOCK_GRACE_MILLIS = 60_000L

internal fun budgetPasswordSecretId(budgetId: String): String = "budget-password:$budgetId"

internal class AppLockState(
    private val now: () -> Long,
) {
    var unlocked by mutableStateOf(false)
        private set
    private var backgroundedAt by mutableLongStateOf(-1L)

    fun onForegroundChanged(foreground: Boolean) {
        if (!foreground) {
            backgroundedAt = now()
        } else if (backgroundedAt >= 0 && now() - backgroundedAt >= APP_LOCK_GRACE_MILLIS) {
            unlocked = false
            backgroundedAt = -1L
        }
    }

    fun unlock() {
        unlocked = true
        backgroundedAt = -1L
    }
}

internal fun needsEncryptedBudgetUnlock(
    state: AppState,
    route: AppRoute,
): Boolean =
    state.selectedBudgetEncrypted &&
        state.selectedBudgetId.isNotBlank() &&
        state.encryptedPassword.isBlank() &&
        route !in ENCRYPTED_UNLOCK_EXEMPT_ROUTES

private val ENCRYPTED_UNLOCK_EXEMPT_ROUTES =
    setOf(
        AppRoute.WELCOME,
        AppRoute.SERVER_ADDRESS,
        AppRoute.SIGN_IN,
        AppRoute.BUDGET_CHOOSER,
        AppRoute.BUDGET_UNLOCK,
        AppRoute.FIRST_SYNC,
    )

@Composable
internal fun AppLockGate(
    state: AppState,
    mobileSession: TandemMobileSession,
    route: AppRoute,
    authenticator: DeviceAuthenticator?,
    lockState: AppLockState,
    appInForeground: Boolean,
    onChooseBudget: () -> Unit,
) {
    LaunchedEffect(lockState.unlocked, state.selectedBudgetEncrypted) {
        if (!lockState.unlocked && state.selectedBudgetEncrypted) state.encryptedPassword = ""
    }
    LaunchedEffect(mobileSession.budgetPasswordRejection) {
        if (mobileSession.budgetPasswordRejection != null && state.selectedBudgetEncrypted) state.encryptedPassword = ""
    }
    val budgetLocked = needsEncryptedBudgetUnlock(state, route)
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
    AnimatedVisibility(appLocked, enter = fadeIn(), exit = fadeOut()) {
        LockScaffold(Lucide.LockKeyhole, "Tandem is locked.", "Unlock with your fingerprint, face, or screen lock.") {
            PrimaryButton("Unlock", onClick = unlockApp)
        }
    }
    AnimatedVisibility(budgetLocked, enter = fadeIn(), exit = fadeOut()) {
        EncryptedBudgetUnlock(state, mobileSession, authenticator, lockState, appInForeground, onChooseBudget)
    }
}

@Composable
private fun EncryptedBudgetUnlock(
    state: AppState,
    mobileSession: TandemMobileSession,
    authenticator: DeviceAuthenticator?,
    lockState: AppLockState,
    appInForeground: Boolean,
    onChooseBudget: () -> Unit,
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

    fun submit(
        candidate: String,
        fromBiometrics: Boolean,
    ) {
        if (candidate.isBlank() || checking) return
        checking = true
        error = null
        scope.launch {
            mobileSession.synchronize(budgetId = budgetId, encryptionPassword = candidate)
            checking = false
            if (mobileSession.budgetPasswordRejection != null) {
                if (fromBiometrics) {
                    authenticator?.deleteSecret(secretId)
                    hasStoredPassword = false
                    usePassword = true
                }
                error = "That password didn’t unlock this budget. It may have changed in Actual."
                return@launch
            }
            val unlockWith = {
                state.encryptedPassword = candidate
                lockState.unlock()
            }
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
                if (checking) "Unlocking…" else "Unlock",
                enabled = password.isNotBlank() && !checking,
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
            Box(
                Modifier
                    .size(104.dp)
                    .border(2.dp, palette.accent.copy(alpha = 0.45f), CircleShape)
                    .background(palette.accent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = palette.accent)
            }
            Text(
                title,
                color = palette.text,
                fontSize = TandemTypography.setupHeadline,
                fontFamily = tandemAlloFamily(),
                textAlign = TextAlign.Center,
            )
            Text(detail, color = palette.muted, fontSize = TandemTypography.body, textAlign = TextAlign.Center)
            content()
        }
    }
}

@Composable
internal fun BiometricPasswordCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(TandemShapes.small))
                .background(if (checked) palette.accent else palette.raised)
                .border(1.5.dp, if (checked) palette.accent else palette.border, RoundedCornerShape(TandemShapes.small)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accentContent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text("Use biometrics to unlock this budget", color = palette.text, fontSize = TandemTypography.compactBody)
            Text(
                "Stores the password on this device, protected by your fingerprint or face",
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
        }
    }
}
