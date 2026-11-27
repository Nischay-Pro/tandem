@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.automation.automationViaAssistants
import app.tandemforactual.ui.core.automation.automationViaShortcuts
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.settingsSwitchStatus
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_detail
import app.tandemforactual.ui.generated.resources.automation_detail_assistants
import app.tandemforactual.ui.generated.resources.automation_detail_shortcuts
import app.tandemforactual.ui.generated.resources.automation_reads
import app.tandemforactual.ui.generated.resources.automation_reads_detail
import app.tandemforactual.ui.generated.resources.automation_reads_short
import app.tandemforactual.ui.generated.resources.automation_reads_title
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts_title
import app.tandemforactual.ui.generated.resources.automation_setup_tasker
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_title
import app.tandemforactual.ui.generated.resources.automation_writes
import app.tandemforactual.ui.generated.resources.automation_writes_detail
import app.tandemforactual.ui.generated.resources.automation_writes_short
import app.tandemforactual.ui.generated.resources.automation_writes_title
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two separate opt-ins for automation apps, adding transactions and reading budget numbers, and where to set them up. */
@Composable
internal fun AutomationScreen(state: AppState) {
    PageColumn {
        BodyText(stringResource(automationDetail()))
        OptInCard(
            stringResource(Res.string.automation_writes),
            state.automationWritesEnabled,
            { state.automationWritesEnabled = it },
            stringResource(Res.string.automation_writes_title),
            stringResource(Res.string.automation_writes_detail),
        )
        OptInCard(
            stringResource(Res.string.automation_reads),
            state.automationReadsEnabled,
            { state.automationReadsEnabled = it },
            stringResource(Res.string.automation_reads_title),
            stringResource(Res.string.automation_reads_detail),
        )
        val (setupTitle, setupDetail) = automationSetup()
        SurfaceCard {
            ListRow(
                stringResource(setupTitle),
                stringResource(setupDetail),
                leading = RowLeading.Icon(Lucide.Info),
                options = ListRowOptions(showChevron = false, subtitleMaxLines = 6),
            )
        }
    }
}

/** A switch with everything it allows spelled out underneath, so nothing is hidden behind an ellipsis. */
@Composable
internal fun OptInCard(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    detailTitle: String,
    detail: String,
    extra: @Composable () -> Unit = {},
) {
    SurfaceCard {
        ToggleRow(title, checked = checked, onChange = onChange)
        extra()
        DividerLine()
        ListRow(
            detailTitle,
            detail,
            leading = RowLeading.None,
            options = ListRowOptions(showChevron = false, subtitleMaxLines = 10),
        )
    }
}

@Composable
internal fun automationStatus(state: AppState): String =
    settingsSwitchStatus(
        listOf(
            Res.string.automation_writes_short to state.automationWritesEnabled,
            Res.string.automation_reads_short to state.automationReadsEnabled,
        ),
    )

/** Names the callers this device has: Shortcuts and Siri on iOS, Tasker on Android, and assistants from Android 16. */
internal fun automationDetail(): StringResource =
    when {
        automationViaShortcuts -> Res.string.automation_detail_shortcuts
        automationViaAssistants -> Res.string.automation_detail_assistants
        else -> Res.string.automation_detail
    }

internal fun automationSetup(): Pair<StringResource, StringResource> =
    if (automationViaShortcuts) {
        Res.string.automation_setup_shortcuts_title to Res.string.automation_setup_shortcuts
    } else {
        Res.string.automation_setup_tasker_title to Res.string.automation_setup_tasker
    }
