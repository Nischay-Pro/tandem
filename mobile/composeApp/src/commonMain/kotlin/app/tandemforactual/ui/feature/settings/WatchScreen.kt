@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.MAX_WATCH_CATEGORIES
import app.tandemforactual.data.store.WatchMonthCategory
import app.tandemforactual.data.store.watchCategoryChoices
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.settingsSwitchStatus
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.watch_after_unlock
import app.tandemforactual.ui.generated.resources.watch_after_unlock_detail
import app.tandemforactual.ui.generated.resources.watch_after_unlock_title
import app.tandemforactual.ui.generated.resources.watch_detail
import app.tandemforactual.ui.generated.resources.watch_pinned
import app.tandemforactual.ui.generated.resources.watch_pinned_detail
import app.tandemforactual.ui.generated.resources.watch_pinned_empty
import app.tandemforactual.ui.generated.resources.watch_quick_add
import app.tandemforactual.ui.generated.resources.watch_quick_add_detail
import app.tandemforactual.ui.generated.resources.watch_quick_add_short
import app.tandemforactual.ui.generated.resources.watch_quick_add_title
import app.tandemforactual.ui.generated.resources.watch_status_pinned
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.stringResource

/** What the paired watch shows and may add. The same page serves every kind of watch; the platform decides when it appears. */
@Composable
internal fun WatchScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val settings = state.watchSettings
    PageColumn {
        BodyText(stringResource(Res.string.watch_detail))
        OptInCard(
            stringResource(Res.string.watch_quick_add),
            settings.quickAddEnabled,
            { state.watchSettings = state.watchSettings.copy(quickAddEnabled = it) },
            stringResource(Res.string.watch_quick_add_title),
            stringResource(Res.string.watch_quick_add_detail),
        )
        OptInCard(
            stringResource(Res.string.watch_after_unlock),
            settings.summaryAfterUnlock,
            { state.watchSettings = state.watchSettings.copy(summaryAfterUnlock = it) },
            stringResource(Res.string.watch_after_unlock_title),
            stringResource(Res.string.watch_after_unlock_detail),
        )
        PinnedCategories(state, mobileSession)
    }
}

@Composable
private fun PinnedCategories(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    var choices by remember(state.selectedBudgetId) { mutableStateOf<List<WatchMonthCategory>>(emptyList()) }
    LaunchedEffect(state.selectedBudgetId, mobileSession.lastSyncSummary, mobileSession.databaseGeneration) {
        val repository = mobileSession.repository ?: return@LaunchedEffect
        choices = runCatching { watchCategoryChoices(repository, state.selectedBudgetId, platformTodayIsoDate()) }.getOrDefault(emptyList())
    }
    val pinned = state.watchSettings.pinnedCategoryIds
    SectionCard(stringResource(Res.string.watch_pinned), detail = stringResource(Res.string.watch_pinned_detail)) {
        if (choices.isEmpty()) {
            ListRow(
                stringResource(Res.string.watch_pinned_empty),
                null,
                leading = RowLeading.Icon(Lucide.Info),
                options = ListRowOptions(showChevron = false, subtitleMaxLines = 3),
            )
        }
        choices.forEachIndexed { index, category ->
            if (index > 0) DividerLine()
            val checked = category.id in pinned
            CheckBoxRow(
                title = category.name,
                subtitle = category.group,
                checked = checked,
                enabled = checked || pinned.size < MAX_WATCH_CATEGORIES,
                onChange = { state.watchSettings = state.watchSettings.pinning(category.id, it) },
            )
        }
    }
}

@Composable
internal fun watchStatus(state: AppState): String {
    val settings = state.watchSettings
    val quickAdd = settingsSwitchStatus(listOf(Res.string.watch_quick_add_short to settings.quickAddEnabled))
    if (settings.pinnedCategoryIds.isEmpty()) return quickAdd
    return "$quickAdd · ${stringResource(Res.string.watch_status_pinned, settings.pinnedCategoryIds.size)}"
}
