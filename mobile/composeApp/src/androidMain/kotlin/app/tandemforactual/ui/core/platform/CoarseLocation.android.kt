package app.tandemforactual.ui.core.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal actual fun rememberCoarseLocator(): CoarseLocator {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidCoarseLocator(context) }
}

@Composable
internal actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { callback.value(it) }
    return remember(launcher) { { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } }
}

/** The platform location service rather than Play services, so it works on devices without Google. */
private class AndroidCoarseLocator(
    private val context: Context,
) : CoarseLocator {
    override val permitted: Boolean
        get() =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!permitted) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val fresh =
            providers
                .firstOrNull { it == LocationManager.NETWORK_PROVIDER || it == LocationManager.FUSED_PROVIDER }
                ?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R }
                ?.let { provider -> withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { currentLocation(manager, provider) } }
        val location = fresh ?: providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun currentLocation(
        manager: LocationManager,
        provider: String,
    ): Location? =
        suspendCancellableCoroutine { continuation ->
            manager.getCurrentLocation(provider, null, context.mainExecutor) { continuation.resume(it) }
        }
}

private const val FIX_TIMEOUT_MILLIS = 5_000L
