@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.automation.automationViaAssistants
import app.tandemforactual.ui.core.automation.automationViaShortcuts
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_detail
import app.tandemforactual.ui.generated.resources.automation_detail_assistants
import app.tandemforactual.ui.generated.resources.automation_detail_shortcuts
import app.tandemforactual.ui.generated.resources.automation_reads
import app.tandemforactual.ui.generated.resources.automation_reads_detail
import app.tandemforactual.ui.generated.resources.automation_title
import app.tandemforactual.ui.generated.resources.automation_writes
import app.tandemforactual.ui.generated.resources.automation_writes_detail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two separate opt-ins for automation apps: adding transactions, and reading budget numbers. */
@Composable
internal fun AutomationSection(state: AppState) {
    SectionCard(stringResource(Res.string.automation_title), stringResource(automationDetail())) {
        ToggleRow(
            stringResource(Res.string.automation_writes),
            stringResource(Res.string.automation_writes_detail),
            state.automationWritesEnabled,
        ) { state.automationWritesEnabled = it }
        DividerLine()
        ToggleRow(
            stringResource(Res.string.automation_reads),
            stringResource(Res.string.automation_reads_detail),
            state.automationReadsEnabled,
        ) { state.automationReadsEnabled = it }
    }
}

/** Names the callers this device has: Shortcuts and Siri on iOS, Tasker on Android, and assistants from Android 16. */
internal fun automationDetail(): StringResource =
    when {
        automationViaShortcuts -> Res.string.automation_detail_shortcuts
        automationViaAssistants -> Res.string.automation_detail_assistants
        else -> Res.string.automation_detail
    }
