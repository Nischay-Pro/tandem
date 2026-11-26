package app.tandemforactual.ui.core.session

import androidx.compose.runtime.staticCompositionLocalOf

/** Asks the platform to let Tandem reach an address on the user's own network, before Tandem connects to it. */
fun interface LanAccess {
    suspend fun request(address: String)
}

/** The platform's [LanAccess]; without one, every address is assumed reachable. */
val LocalLanAccess = staticCompositionLocalOf<LanAccess?> { null }
