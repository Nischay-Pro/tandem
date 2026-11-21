package app.tandemforactual.ui

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
        val locale = value?.let(Locale::forLanguageTag) ?: device
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}
