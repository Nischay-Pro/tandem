@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.automation.automationViaAssistants
import app.tandemforactual.ui.core.automation.automationViaShortcuts
import app.tandemforactual.ui.core.security.LocalBackgroundBudgetKeyStore
import app.tandemforactual.ui.core.session.BackgroundBudgetKey
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.settingsSwitchStatus
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.automation_encrypted
import app.tandemforactual.ui.generated.resources.automation_encrypted_shortcuts
import app.tandemforactual.ui.generated.resources.automation_encrypted_tasker
import app.tandemforactual.ui.generated.resources.automation_exposed
import app.tandemforactual.ui.generated.resources.automation_intro
import app.tandemforactual.ui.generated.resources.automation_intro_assistants
import app.tandemforactual.ui.generated.resources.automation_intro_shortcuts
import app.tandemforactual.ui.generated.resources.automation_needs_background_key
import app.tandemforactual.ui.generated.resources.automation_no_encrypted
import app.tandemforactual.ui.generated.resources.automation_reads
import app.tandemforactual.ui.generated.resources.automation_reads_alerts
import app.tandemforactual.ui.generated.resources.automation_reads_amounts
import app.tandemforactual.ui.generated.resources.automation_reads_balance
import app.tandemforactual.ui.generated.resources.automation_reads_hidden
import app.tandemforactual.ui.generated.resources.automation_reads_limit
import app.tandemforactual.ui.generated.resources.automation_reads_short
import app.tandemforactual.ui.generated.resources.automation_reads_subtitle
import app.tandemforactual.ui.generated.resources.automation_setup_offers
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts_open
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts_search
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts_siri
import app.tandemforactual.ui.generated.resources.automation_setup_shortcuts_title
import app.tandemforactual.ui.generated.resources.automation_setup_steps
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_actions
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_add
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_choose
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_events
import app.tandemforactual.ui.generated.resources.automation_setup_tasker_title
import app.tandemforactual.ui.generated.resources.automation_writes
import app.tandemforactual.ui.generated.resources.automation_writes_fields
import app.tandemforactual.ui.generated.resources.automation_writes_history
import app.tandemforactual.ui.generated.resources.automation_writes_limit
import app.tandemforactual.ui.generated.resources.automation_writes_rules
import app.tandemforactual.ui.generated.resources.automation_writes_short
import app.tandemforactual.ui.generated.resources.automation_writes_subtitle
import app.tandemforactual.ui.generated.resources.automation_writes_undo
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Workflow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two separate opt-ins for automation apps, with what they expose and how to set them up one tap away. */
@Composable
internal fun AutomationScreen(state: AppState) {
    var exposedOpen by rememberSaveable { mutableStateOf(false) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    val blocked = automationBlockedFor(state)?.let { stringResource(it) }
    val setupTitle = stringResource(automationSetupTitle())
    PageColumn {
        BodyText(stringResource(automationIntro()))
        SurfaceCard {
            ToggleRow(
                stringResource(Res.string.automation_writes),
                blocked ?: stringResource(Res.string.automation_writes_subtitle),
                state.automationWritesEnabled,
            ) { state.automationWritesEnabled = it }
            DividerLine()
            ToggleRow(
                stringResource(Res.string.automation_reads),
                blocked ?: stringResource(Res.string.automation_reads_subtitle),
                state.automationReadsEnabled,
            ) { state.automationReadsEnabled = it }
        }
        SurfaceCard {
            ListRow(
                stringResource(Res.string.automation_exposed),
                leading = RowLeading.Icon(Lucide.Eye),
                onClick = { exposedOpen = true },
            )
            DividerLine()
            ListRow(setupTitle, leading = RowLeading.Icon(Lucide.Workflow), onClick = { setupOpen = true })
        }
    }
    SettingsInfoSheet(exposedOpen, stringResource(Res.string.automation_exposed), automationExposure()) { exposedOpen = false }
    SettingsInfoSheet(setupOpen, setupTitle, automationSetup()) { setupOpen = false }
}

/** Why the open budget can't be reached from automation, or null when it can. */
@Composable
private fun automationBlockedFor(state: AppState): StringResource? {
    if (!state.selectedBudgetEncrypted) return null
    if (automationViaShortcuts) return Res.string.automation_no_encrypted
    val keyStore = LocalBackgroundBudgetKeyStore.current
    return Res.string.automation_needs_background_key.takeIf { BackgroundBudgetKey.budgetIn(keyStore) != state.selectedBudgetId }
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
internal fun automationIntro(): StringResource =
    when {
        automationViaShortcuts -> Res.string.automation_intro_shortcuts
        automationViaAssistants -> Res.string.automation_intro_assistants
        else -> Res.string.automation_intro
    }

internal fun automationSetupTitle(): StringResource =
    if (automationViaShortcuts) Res.string.automation_setup_shortcuts_title else Res.string.automation_setup_tasker_title

internal fun automationExposure(): List<InfoGroup> =
    listOf(
        InfoGroup(
            Res.string.automation_writes,
            listOf(
                Res.string.automation_writes_fields,
                Res.string.automation_writes_rules,
                Res.string.automation_writes_undo,
                Res.string.automation_writes_history,
                Res.string.automation_writes_limit,
            ),
        ),
        InfoGroup(
            Res.string.automation_reads,
            listOf(
                Res.string.automation_reads_amounts,
                Res.string.automation_reads_balance,
                Res.string.automation_reads_alerts,
                Res.string.automation_reads_limit,
                Res.string.automation_reads_hidden,
            ),
        ),
        InfoGroup(
            Res.string.automation_encrypted,
            listOf(if (automationViaShortcuts) Res.string.automation_encrypted_shortcuts else Res.string.automation_encrypted_tasker),
        ),
    )

internal fun automationSetup(): List<InfoGroup> =
    if (automationViaShortcuts) {
        listOf(
            InfoGroup(
                Res.string.automation_setup_steps,
                listOf(
                    Res.string.automation_setup_shortcuts_open,
                    Res.string.automation_setup_shortcuts_search,
                    Res.string.automation_setup_shortcuts_siri,
                ),
                numbered = true,
            ),
        )
    } else {
        listOf(
            InfoGroup(
                Res.string.automation_setup_steps,
                listOf(Res.string.automation_setup_tasker_add, Res.string.automation_setup_tasker_choose),
                numbered = true,
            ),
            InfoGroup(
                Res.string.automation_setup_offers,
                listOf(Res.string.automation_setup_tasker_actions, Res.string.automation_setup_tasker_events),
            ),
        )
    }
