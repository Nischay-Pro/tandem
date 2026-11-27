package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.settings_status_all_off
import app.tandemforactual.ui.generated.resources.settings_status_off
import app.tandemforactual.ui.generated.resources.settings_status_on
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A settings row subtitle such as "Bank logos on · Exchange rates off", or "Off" when every switch is off. */
@Composable
internal fun settingsSwitchStatus(switches: List<Pair<StringResource, Boolean>>): String {
    if (switches.none { it.second }) return stringResource(Res.string.settings_status_all_off)
    return switches
        .map { (name, on) ->
            stringResource(if (on) Res.string.settings_status_on else Res.string.settings_status_off, stringResource(name))
        }.joinToString(" · ")
}
