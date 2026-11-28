@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.checkup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.RuleFinding
import app.tandemforactual.data.actual.RuleFindingKind
import app.tandemforactual.data.actual.RuleSeverity
import app.tandemforactual.ui.core.format.RuleText
import app.tandemforactual.ui.core.format.ruleFieldLabel
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.TriangleAlert

private fun RuleSeverity.icon(): ImageVector =
    when (this) {
        RuleSeverity.PROBLEM -> Lucide.CircleAlert
        RuleSeverity.WARNING -> Lucide.TriangleAlert
        RuleSeverity.SUGGESTION -> Lucide.Info
    }

private fun RuleSeverity.label(): String =
    when (this) {
        RuleSeverity.PROBLEM -> "Needs fixing"
        RuleSeverity.WARNING -> "Worth a look"
        RuleSeverity.SUGGESTION -> "Suggestion"
    }

@Composable
private fun RuleSeverity.color(): Color =
    when (this) {
        RuleSeverity.PROBLEM -> tandemPalette.negative
        RuleSeverity.WARNING -> tandemPalette.warning
        RuleSeverity.SUGGESTION -> tandemPalette.accent
    }

private fun fieldList(fields: List<String>): String {
    val names = fields.map { ruleFieldLabel(it).lowercase() }
    return if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

internal fun RuleFinding.title(): String =
    when (kind) {
        RuleFindingKind.MISSING_REFERENCE -> "Points to something deleted"
        RuleFindingKind.CONTRADICTORY, RuleFindingKind.NO_CONDITIONS -> "Never runs"
        RuleFindingKind.NO_ACTIONS -> "Does nothing"
        RuleFindingKind.NO_OP_ACTIONS -> if (severity == RuleSeverity.WARNING) "Changes nothing" else "Has a step that changes nothing"
        RuleFindingKind.SHADOWED -> "Always overridden"
        RuleFindingKind.DUPLICATE -> if (related.size == 1) "Duplicate rule" else "${related.size + 1} identical rules"
        RuleFindingKind.NEAR_DUPLICATE -> "Could be one rule"
        RuleFindingKind.BROAD -> "Matches most transactions"
    }

internal fun RuleFinding.explanation(): String =
    when (kind) {
        RuleFindingKind.MISSING_REFERENCE -> {
            if (severity == RuleSeverity.PROBLEM) {
                "It uses a ${fieldList(fields)} that no longer exists, so it can’t make the change it promises."
            } else {
                "It looks for a ${fieldList(fields)} that no longer exists, so that part never matches."
            }
        }

        RuleFindingKind.CONTRADICTORY -> {
            "Its conditions can’t all be true at once, so it never matches a transaction."
        }

        RuleFindingKind.NO_CONDITIONS -> {
            "It has no conditions, and Actual only runs rules that have at least one."
        }

        RuleFindingKind.NO_ACTIONS -> {
            "It has no actions, so transactions it matches stay as they are."
        }

        RuleFindingKind.NO_OP_ACTIONS -> {
            "Setting the ${fieldList(fields)} has no effect: it’s empty, matches what the rule looks for, " +
                "or a later step in the same rule replaces it."
        }

        RuleFindingKind.SHADOWED -> {
            "Whenever it runs, the rule below also runs afterwards in the same stage and sets the ${fieldList(fields)}, " +
                "so its changes never stick."
        }

        RuleFindingKind.DUPLICATE -> {
            "These rules match the same transactions and make the same changes. One is enough."
        }

        RuleFindingKind.NEAR_DUPLICATE -> {
            "These rules make the same changes and differ only in the ${fieldList(fields)} they look for. " +
                "One rule that matches any of them does the same job."
        }

        RuleFindingKind.BROAD -> {
            "It matches $matched of your last $checked transactions (${matched * 100 / checked.coerceAtLeast(1)}%). " +
                "Check its conditions are as narrow as you meant."
        }
    }

/** What merging a duplicate group does, or null when the finding has nothing to merge. */
internal fun RuleFinding.mergeLabel(): Pair<String, String>? =
    when (kind) {
        RuleFindingKind.DUPLICATE -> {
            "Keep one rule" to if (related.size == 1) "Deletes the copy" else "Deletes the other ${related.size}"
        }

        RuleFindingKind.NEAR_DUPLICATE -> {
            "Merge into one rule" to "Updates the first and deletes the other ${if (related.size == 1) "one" else "${related.size}"}"
        }

        else -> {
            null
        }
    }

/** The rule checks, worst first, each opening its explanation and the rules involved. */
@Composable
internal fun RuleFindingsSection(
    findings: List<RuleFinding>,
    rules: Map<String, RuleText>,
    onOpen: (RuleFinding) -> Unit,
) {
    val problems = findings.count { it.severity == RuleSeverity.PROBLEM }
    SectionCard(
        if (findings.size == 1) "1 rule to check" else "${findings.size} rules to check",
        if (problems > 0) "$problems won’t work as written" else "They run, but may not do what you meant",
    ) {
        findings.forEachIndexed { index, finding ->
            if (index > 0) DividerLine()
            ListRow(
                finding.title(),
                rules[finding.ruleId]?.let { "${it.conditions} → ${it.actions}" } ?: "Rule no longer on this device",
                finding.severity.label(),
                leading = RowLeading.Icon(finding.severity.icon()),
                options = ListRowOptions(trailingColor = finding.severity.color(), subtitleMaxLines = 2),
                onClick = { onOpen(finding) },
            )
        }
    }
}

/** One finding: why it matters, the rules as the rule editor lists them, and what to do about it. */
@Composable
internal fun RuleFindingDialog(
    finding: RuleFinding?,
    rules: Map<String, RuleText>,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
    onMerge: (RuleFinding) -> Unit,
    onIgnore: (RuleFinding) -> Unit,
) {
    TandemChoiceDialog(visible = finding != null, onDismissRequest = onDismiss) {
        val shown = finding ?: return@TandemChoiceDialog
        SheetCard(
            maxWidth = 520.dp,
            spacing = TandemSpacing.small,
            header = { TandemModalHeader(shown.title(), onClose = onDismiss, leadingIcon = shown.severity.icon()) },
        ) {
            SeverityLabel(shown.severity)
            Text(shown.explanation(), color = tandemPalette.text, fontSize = TandemTypography.compactBody)
            (listOf(shown.ruleId) + shown.related).forEachIndexed { index, id ->
                val label =
                    when {
                        shown.related.isEmpty() -> null
                        index == 0 -> "First rule"
                        else -> "Rule ${index + 1}"
                    }
                RuleCard(rules[id], label = label) {
                    onEdit(id)
                }
            }
            shown.mergeLabel()?.takeIf { canEdit && rules.keys.containsAll(listOf(shown.ruleId) + shown.related) }?.let { (title, detail) ->
                ListRow(
                    title,
                    detail,
                    leading = RowLeading.Icon(Lucide.GitMerge),
                    options = ListRowOptions(showChevron = false),
                    onClick = { onMerge(shown) },
                )
            }
            ListRow(
                "Ignore",
                "Stop listing this here",
                leading = RowLeading.Icon(Lucide.EyeOff),
                options = ListRowOptions(showChevron = false),
                onClick = { onIgnore(shown) },
            )
        }
    }
}

@Composable
private fun SeverityLabel(severity: RuleSeverity) {
    val color = severity.color()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Icon(severity.icon(), contentDescription = null, Modifier.size(TandemSpacing.medium), tint = color)
        Text(severity.label(), color = color, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RuleCard(
    rule: RuleText?,
    label: String?,
    onEdit: () -> Unit,
) {
    val palette = tandemPalette
    SurfaceCard {
        ListRow(
            rule?.conditions ?: "Rule no longer on this device",
            rule?.actions,
            listOfNotNull(label, rule?.stage).joinToString(" · ").ifBlank { null },
            leading = RowLeading.Icon(Lucide.Pencil),
            options = ListRowOptions(trailingColor = palette.muted, dense = true),
            onClick = if (rule != null) onEdit else null,
        )
    }
}
