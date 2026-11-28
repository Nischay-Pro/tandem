package app.tandemforactual.ui.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import kotlin.math.abs

private const val EDGE_DP = 0.5f

private const val WEEK_LENGTH = 7

private val anyNode = SemanticsMatcher("any node") { true }

private val tappableRoles = setOf(Role.Button, Role.Tab, Role.Checkbox, Role.Switch, Role.RadioButton, Role.DropdownList)

/**
 * Everything on screen that doesn't fit a narrow window: windows and dialogs wider than the app's window, controls and
 * text pushed past its edge or cut off by their container, text that doesn't fit its line, and tappable controls
 * smaller than a touch target. Content inside a horizontally scrolling row may run past the edge, and calendar days
 * may be narrower than a touch target.
 */
internal fun SemanticsNodeInteractionsProvider.windowFitProblems(where: String): List<String> {
    val problems = mutableListOf<String>()
    val roots = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
    val windowWidth = roots.maxOfOrNull { it.size.width.toFloat() / it.density() } ?: return problems
    roots.forEach { root ->
        val width = root.size.width / root.density()
        if (width > windowWidth + EDGE_DP) problems += "$where: a dialog ${width.inDp()} wide is wider than the window"
    }
    onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
        if (node.isRoot || node.size.width == 0 || node.hidden()) return@forEach
        problems += node.edgeProblems(where)
        problems += node.textProblems(where)
    }
    val tappable = onAllNodes(anyNode).fetchSemanticsNodes().filter { it.size.width > 0 && !it.hidden() && it.isTappable() }
    val days =
        tappable
            .groupBy { it.rowKey() }
            .values
            .filter { it.size >= WEEK_LENGTH }
            .flatten()
    tappable.forEach { node ->
        if (days.any { it.sameCellAs(node) }) return@forEach
        val density = node.density()
        val width = node.size.width / density
        val height = node.size.height / density
        if (minOf(width, height) < MIN_TOUCH_TARGET.value - EDGE_DP) {
            problems += "$where: '${node.label()}' is ${width.inDp()} x ${height.inDp()}, smaller than a touch target"
        }
    }
    return problems.distinct()
}

private fun SemanticsNode.edgeProblems(where: String): List<String> {
    if (!isContent() || insideHorizontalScroll()) return emptyList()
    val density = density()
    val rootWidth = rootNode().size.width / density
    val left = positionInRoot.x / density
    val right = left + size.width / density
    val shown = boundsInRoot
    return when {
        left < -EDGE_DP || right > rootWidth + EDGE_DP -> {
            listOf("$where: '${label()}' spans ${left.inDp()} to ${right.inDp()}, past the ${rootWidth.inDp()} window")
        }

        shown.height > 0f && shown.width / density < size.width / density - EDGE_DP -> {
            listOf("$where: '${label()}' is cut off, showing ${(shown.width / density).inDp()} of ${(size.width / density).inDp()}")
        }

        else -> {
            emptyList()
        }
    }
}

private fun SemanticsNode.textProblems(where: String): List<String> {
    val getLayout = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return emptyList()
    if (SemanticsProperties.EditableText in config || insideHorizontalScroll()) return emptyList()
    val layouts = mutableListOf<TextLayoutResult>()
    getLayout(layouts)
    val widest = layouts.maxOfOrNull { layout -> (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) } }
    val fits = widest == null || widest <= size.width + EDGE_DP * density()
    val cut = layouts.any { it.multiParagraph.didExceedMaxLines && it.layoutInput.overflow == TextOverflow.Clip }
    return when {
        !fits -> listOf("$where: text '${label()}' doesn't fit its line and is clipped")
        cut -> listOf("$where: text '${label()}' runs out of lines and is cut off without an ellipsis")
        else -> emptyList()
    }
}

private fun SemanticsNode.isContent(): Boolean =
    SemanticsProperties.Text in config ||
        SemanticsProperties.EditableText in config ||
        SemanticsProperties.ContentDescription in config ||
        SemanticsActions.OnClick in config

private fun SemanticsNode.isTappable(): Boolean =
    SemanticsActions.OnClick in config &&
        SemanticsProperties.Disabled !in config &&
        config.getOrNull(SemanticsProperties.Role) in tappableRoles

private fun SemanticsNode.hidden(): Boolean =
    generateSequence(this) { it.parent }.any { SemanticsProperties.HideFromAccessibility in it.config }

private fun SemanticsNode.insideHorizontalScroll(): Boolean =
    generateSequence(parent) { it.parent }.any { SemanticsProperties.HorizontalScrollAxisRange in it.config }

/** Calendar days share a row seven to a week, so in a narrow window they're narrower than a touch target by design. */
private fun SemanticsNode.rowKey(): Pair<Int, Int> = rootNode().id to positionInRoot.y.toInt()

private fun SemanticsNode.sameCellAs(other: SemanticsNode): Boolean =
    rootNode().id == other.rootNode().id && abs(size.height - other.size.height) <= 2 && abs(size.width - other.size.width) <= 2

private fun SemanticsNode.rootNode(): SemanticsNode = generateSequence(this) { it.parent }.last()

private fun SemanticsNode.density(): Float = layoutInfo.density.density

private fun SemanticsNode.label(): String =
    (
        config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: config.getOrNull(SemanticsProperties.TestTag)
            ?: "node $id"
    ).take(48)

private fun Float.inDp(): String = "${"%.1f".format(this)}dp"
