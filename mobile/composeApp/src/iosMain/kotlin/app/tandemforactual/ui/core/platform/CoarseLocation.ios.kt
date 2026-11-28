package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

@Composable
internal actual fun rememberCoarseLocator(): CoarseLocator = remember { IosCoarseLocator() }

@Composable
internal actual fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val locator = remember { IosCoarseLocator() }
    return remember(locator) { { locator.requestPermission { callback.value(it) } } }
}

@OptIn(ExperimentalForeignApi::class)
private class IosCoarseLocator : CoarseLocator {
    private val manager = CLLocationManager().apply { desiredAccuracy = kCLLocationAccuracyKilometer }
    private val delegate = LocationDelegate()

    init {
        manager.delegate = delegate
    }

    override val permitted: Boolean
        get() = manager.authorizationStatus in setOf(kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways)

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            onResult(permitted)
            return
        }
        delegate.onAuthorization = { onResult(permitted) }
        manager.requestWhenInUseAuthorization()
    }

    override suspend fun current(): GeoPoint? {
        if (!permitted) return null
        val location =
            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<CLLocation?> { continuation ->
                    delegate.pending = continuation
                    manager.requestLocation()
                }
            } ?: manager.location
        return location?.coordinate?.useContents { GeoPoint(latitude, longitude) }
    }
}

private class LocationDelegate :
    NSObject(),
    CLLocationManagerDelegateProtocol {
    var pending: CancellableContinuation<CLLocation?>? = null
    var onAuthorization: (() -> Unit)? = null

    @ObjCSignatureOverride
    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        finish(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    @ObjCSignatureOverride
    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: NSError,
    ) {
        finish(null)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus == kCLAuthorizationStatusNotDetermined) return
        onAuthorization?.invoke()
        onAuthorization = null
    }

    private fun finish(location: CLLocation?) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(location)
    }
}

private const val FIX_TIMEOUT_MILLIS = 5_000L
