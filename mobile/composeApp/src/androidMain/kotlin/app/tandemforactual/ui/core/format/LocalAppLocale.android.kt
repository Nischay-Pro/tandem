package app.tandemforactual.ui.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    private var deviceLocale: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val device = deviceLocale ?: Locale.getDefault().also { deviceLocale = it }
        val locale = value?.let { appLocale(it, device) } ?: device
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}

/** The chosen app language in the device's region, so currency, dates and numbers still follow where the phone is. */
internal fun appLocale(
    languageTag: String,
    device: Locale,
): Locale {
    val chosen = Locale.forLanguageTag(languageTag)
    if (chosen.country.isNotEmpty() || device.country.isEmpty()) return chosen
    return runCatching {
        Locale
            .Builder()
            .setLocale(chosen)
            .setRegion(device.country)
            .apply { device.unicodeLocaleKeys.forEach { key -> setUnicodeLocaleKeyword(key, device.getUnicodeLocaleType(key)) } }
            .build()
    }.getOrDefault(chosen)
}
