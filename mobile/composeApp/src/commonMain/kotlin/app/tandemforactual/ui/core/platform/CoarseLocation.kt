package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable

/** A place, rounded by the caller before it's kept. */
internal data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** While-in-use location to about a kilometre: one fix when asked, nothing in the background. */
internal interface CoarseLocator {
    val permitted: Boolean

    suspend fun current(): GeoPoint?
}

@Composable
internal expect fun rememberCoarseLocator(): CoarseLocator

/** Asks for while-in-use location and reports whether it was granted. */
@Composable
internal expect fun rememberLocationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
