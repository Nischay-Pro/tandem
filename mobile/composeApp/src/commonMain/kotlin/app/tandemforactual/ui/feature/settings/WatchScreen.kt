@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.watch.WatchSettings
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.watch_after_unlock
import app.tandemforactual.ui.generated.resources.watch_after_unlock_detail
import app.tandemforactual.ui.generated.resources.watch_categories
import app.tandemforactual.ui.generated.resources.watch_categories_detail
import app.tandemforactual.ui.generated.resources.watch_categories_empty
import app.tandemforactual.ui.generated.resources.watch_intro
import app.tandemforactual.ui.generated.resources.watch_quick_add
import app.tandemforactual.ui.generated.resources.watch_quick_add_detail
import app.tandemforactual.ui.generated.resources.watch_status_glance
import app.tandemforactual.ui.generated.resources.watch_status_quick_add
import org.jetbrains.compose.resources.stringResource

/** What a paired watch shows and may do: its categories, quick add, and whether it waits for an unlock. */
@Composable
internal fun WatchScreen(
    state: AppState,
    month: LiveBudgetMonthSummary?,
) {
    fun change(settings: WatchSettings) {
        state.watchSettings = settings
    }

    val settings = state.watchSettings
    PageColumn {
        BodyText(stringResource(Res.string.watch_intro))
        SurfaceCard {
            ToggleRow(
                stringResource(Res.string.watch_quick_add),
                subtitle = stringResource(Res.string.watch_quick_add_detail),
                checked = settings.quickAdd,
                onChange = { change(settings.copy(quickAdd = it)) },
            )
            DividerLine()
            ToggleRow(
                stringResource(Res.string.watch_after_unlock),
                subtitle = stringResource(Res.string.watch_after_unlock_detail),
                checked = settings.summaryAfterUnlock,
                onChange = { change(settings.copy(summaryAfterUnlock = it)) },
            )
        }
        WatchCategories(settings, month) { change(settings.togglePin(it)) }
    }
}

@Composable
private fun WatchCategories(
    settings: WatchSettings,
    month: LiveBudgetMonthSummary?,
    onToggle: (String) -> Unit,
) {
    val categories =
        month
            ?.groups
            .orEmpty()
            .filterNot { it.isIncome }
            .flatMap { it.categories }
    SectionCard(stringResource(Res.string.watch_categories), detail = stringResource(Res.string.watch_categories_detail)) {
        if (categories.isEmpty()) BodyText(stringResource(Res.string.watch_categories_empty))
        categories.forEachIndexed { index, category ->
            if (index > 0) DividerLine()
            val pinned = category.id in settings.pinnedCategoryIds
            ToggleRow(
                category.name,
                checked = pinned,
                enabled = pinned || settings.pinnedCategoryIds.size < WatchSettings.MAX_PINNED,
                onChange = { onToggle(category.id) },
            )
        }
    }
}

@Composable
internal fun watchStatus(state: AppState): String =
    stringResource(if (state.watchSettings.quickAdd) Res.string.watch_status_quick_add else Res.string.watch_status_glance)
