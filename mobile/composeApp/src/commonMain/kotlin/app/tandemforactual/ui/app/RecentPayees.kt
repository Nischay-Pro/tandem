@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.recentPayeeIds
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem

/** The payees used most recently, in that order, shown above the full list. */
internal fun recentPayeeGroup(
    values: List<SelectorItem>,
    recentPayeeIds: List<String>,
): List<SelectorGroup> {
    val byId = values.associateBy { it.id }
    val recent = recentPayeeIds.mapNotNull { byId[it] }
    return if (recent.isEmpty() || values.size <= 8) emptyList() else listOf(SelectorGroup("Recent", recent))
}
