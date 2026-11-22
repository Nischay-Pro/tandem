@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing

/** Whether the save-search sheet is open and the name typed so far, kept across recreation. */
internal class SaveActivitySearchState(
    showSaveSearchDialog: Boolean = false,
    savedSearchNameDraft: String = "",
) {
    var showSaveSearchDialog by mutableStateOf(showSaveSearchDialog)
    var savedSearchNameDraft by mutableStateOf(savedSearchNameDraft)

    companion object {
        val Saver: Saver<SaveActivitySearchState, Any> =
            listSaver(
                save = { listOf(it.showSaveSearchDialog, it.savedSearchNameDraft) },
                restore = { SaveActivitySearchState(it[0] as Boolean, it[1] as String) },
            )
    }
}

@Composable
internal fun rememberSaveActivitySearchState(): SaveActivitySearchState =
    rememberSaveable(saver = SaveActivitySearchState.Saver) { SaveActivitySearchState() }

/** Names the current search so it can be reapplied from the saved searches row. */
@Composable
internal fun SaveActivitySearchSheet(
    saveState: SaveActivitySearchState,
    onSave: (String) -> Unit,
) {
    TandemTaskSheet(
        visible = saveState.showSaveSearchDialog,
        onDismissRequest = { saveState.showSaveSearchDialog = false },
        onDismissed = {},
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Save search",
                    onClose = { saveState.showSaveSearchDialog = false },
                )
                TextFieldCard(
                    "Name",
                    saveState.savedSearchNameDraft,
                    { saveState.savedSearchNameDraft = it.take(80) },
                    TextFieldOptions(placeholder = "Name this search"),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        onClick = { saveState.showSaveSearchDialog = false },
                    )
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = saveState.savedSearchNameDraft.isNotBlank(),
                        onClick = {
                            onSave(saveState.savedSearchNameDraft.trim())
                            saveState.showSaveSearchDialog = false
                        },
                    )
                }
            }
        }
    }
}
