@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.about
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class OpenSourceLibrary(
    val name: String,
    val purpose: String,
    val license: String,
)

internal val OPEN_SOURCE_LIBRARIES =
    listOf(
        OpenSourceLibrary("Kotlin", "Language and standard library", "Apache-2.0"),
        OpenSourceLibrary("kotlinx.coroutines", "Background work", "Apache-2.0"),
        OpenSourceLibrary("kotlinx.serialization", "Reading and writing JSON", "Apache-2.0"),
        OpenSourceLibrary("Compose Multiplatform", "Interface on Android and iOS", "Apache-2.0"),
        OpenSourceLibrary("Ktor", "Talking to your Actual server", "Apache-2.0"),
        OpenSourceLibrary("cryptography-kotlin", "Encrypted budgets and planning data", "Apache-2.0"),
        OpenSourceLibrary("SQLCipher", "Encrypted storage on the device", "BSD-3-Clause"),
        OpenSourceLibrary("AndroidX Room and SQLite", "On-device database", "Apache-2.0"),
        OpenSourceLibrary("AndroidX Activity, Fragment and Biometric", "App lock and screens on Android", "Apache-2.0"),
        OpenSourceLibrary("AndroidX WorkManager", "Background sync on Android", "Apache-2.0"),
        OpenSourceLibrary("AndroidX WindowManager", "Foldable and tablet layouts", "Apache-2.0"),
        OpenSourceLibrary("AndroidX SplashScreen and ProfileInstaller", "Startup on Android", "Apache-2.0"),
        OpenSourceLibrary("LiteRT", "Runs receipt scanning on the device", "Apache-2.0"),
        OpenSourceLibrary("PaddleOCR PP-OCRv6", "Receipt text detection and recognition models", "Apache-2.0"),
        OpenSourceLibrary("Compose Icons", "Icon components", "MIT"),
        OpenSourceLibrary("Lucide", "Icon artwork", "ISC"),
        OpenSourceLibrary("TheSVG", "Merchant icon catalog, at build time", "MIT"),
    )
