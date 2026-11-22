@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.auth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

internal const val APP_LOCK_GRACE_MILLIS = 60_000L

internal const val UNLOCK_ATTEMPTS_BEFORE_WAIT = 5

private val UNLOCK_WAIT_STEPS_MILLIS = listOf(30_000L, 120_000L, 600_000L, 1_800_000L)

internal fun unlockWaitMillis(failures: Int): Long {
    if (failures <= 0 || failures % UNLOCK_ATTEMPTS_BEFORE_WAIT != 0) return 0L
    val step = failures / UNLOCK_ATTEMPTS_BEFORE_WAIT - 1
    return UNLOCK_WAIT_STEPS_MILLIS[step.coerceAtMost(UNLOCK_WAIT_STEPS_MILLIS.lastIndex)]
}

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

internal fun encryptedBudgetUnlockAccepted(
    syncSucceeded: Boolean,
    serverReachable: Boolean,
    offlineCheck: Boolean?,
): Boolean = syncSucceeded || (!serverReachable && offlineCheck == true)

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
        AppRoute.SWITCH_BUDGET,
        AppRoute.BUDGET_UNLOCK,
        AppRoute.FIRST_SYNC,
    )

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
