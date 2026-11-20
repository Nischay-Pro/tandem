@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.PrimaryButton
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide

interface DeviceAuthenticator {
    fun isAvailable(): Boolean

    fun authenticate(
        title: String,
        onResult: (Boolean) -> Unit,
    )
}

internal val LocalDeviceAuthenticator = staticCompositionLocalOf<DeviceAuthenticator?> { null }

internal const val APP_LOCK_GRACE_MILLIS = 60_000L

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

@Composable
internal fun AppLockGate(
    lockEnabled: Boolean,
    authenticator: DeviceAuthenticator?,
    lockState: AppLockState,
    appInForeground: Boolean,
) {
    val locked = lockEnabled && authenticator != null && !lockState.unlocked
    var prompting by remember { mutableStateOf(false) }
    val unlock: () -> Unit = {
        if (authenticator != null && !prompting) {
            prompting = true
            authenticator.authenticate("Unlock Tandem") { success ->
                prompting = false
                if (success) lockState.unlock()
            }
        }
    }
    LaunchedEffect(locked, appInForeground) {
        if (locked && appInForeground) unlock()
    }
    AnimatedVisibility(locked, enter = fadeIn(), exit = fadeOut()) {
        AppLockScreen(onUnlock = unlock)
    }
}

@Composable
internal fun AppLockScreen(onUnlock: () -> Unit) {
    val palette = tandemPalette
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(TandemSpacing.screen),
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
                Icon(Lucide.LockKeyhole, contentDescription = null, modifier = Modifier.size(40.dp), tint = palette.accent)
            }
            Text(
                "Tandem is locked.",
                color = palette.text,
                fontSize = TandemTypography.setupHeadline,
                fontFamily = tandemAlloFamily(),
                textAlign = TextAlign.Center,
            )
            Text(
                "Unlock with your fingerprint, face, or screen lock.",
                color = palette.muted,
                fontSize = TandemTypography.body,
                textAlign = TextAlign.Center,
            )
            PrimaryButton("Unlock", onClick = onUnlock)
        }
    }
}
