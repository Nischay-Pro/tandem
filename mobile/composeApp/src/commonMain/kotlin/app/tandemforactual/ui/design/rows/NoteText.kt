@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.rows

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

internal data class NoteLink(
    val range: IntRange,
    val url: String,
)

private val noteUrl = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

/** Web addresses in [text], without trailing punctuation or an unmatched closing bracket; `www.` links open over https. */
internal fun noteLinks(text: String): List<NoteLink> =
    noteUrl
        .findAll(text)
        .mapNotNull { match ->
            var raw = match.value
            while (raw.isNotEmpty() && (raw.last() in ".,;:!?'*_" || unmatchedClose(raw))) raw = raw.dropLast(1)
            val prefix = if (raw.startsWith("www.", ignoreCase = true)) "www." else raw.substringBefore("://") + "://"
            if (raw.length <= prefix.length) return@mapNotNull null
            val url = if (prefix == "www.") "https://$raw" else raw
            NoteLink(match.range.first until match.range.first + raw.length, url)
        }.toList()

private fun unmatchedClose(text: String): Boolean {
    val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    val open = pairs[text.last()] ?: return false
    return text.count { it == text.last() } > text.count { it == open }
}

internal fun linkedNote(
    note: String,
    linkColor: Color,
    uriHandler: UriHandler,
): AnnotatedString =
    buildAnnotatedString {
        append(note)
        val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        noteLinks(note).forEach { link ->
            addLink(
                LinkAnnotation.Url(link.url, styles) { runCatching { uriHandler.openUri(link.url) } },
                link.range.first,
                link.range.last + 1,
            )
        }
    }

/** A note with tappable web addresses; past [collapsedLines] it folds behind "Show more". */
@Composable
internal fun NoteText(
    note: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 6,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = tandemPalette.accent
    val text = remember(note, linkColor, uriHandler) { linkedNote(note, linkColor, uriHandler) }
    var expanded by rememberSaveable(note) { mutableStateOf(false) }
    var overflows by remember(note) { mutableStateOf(false) }
    val motion = if (tandemReduceMotion) Modifier else Modifier.animateContentSize(tween(220))
    Column(modifier.then(motion)) {
        Text(
            text,
            color = tandemPalette.text,
            fontSize = TandemTypography.body,
            lineHeight = TandemTypography.bodyLineHeight,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows) {
            Text(
                if (expanded) "Show less" else "Show more",
                color = tandemPalette.accent,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(top = TandemSpacing.small, bottom = TandemSpacing.extraSmall),
            )
        }
    }
}
