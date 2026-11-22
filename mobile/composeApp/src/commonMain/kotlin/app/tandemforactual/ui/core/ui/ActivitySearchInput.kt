@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay

@Composable
internal fun ActivitySearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    autoFocus: Boolean = false,
    focusRequestKey: Int = 0,
    placeholder: String = "Payee, account, category or amount",
) {
    val palette = tandemPalette
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus, focusRequestKey) {
        if (autoFocus) {
            delay(TandemMotion.ROUTE_MILLIS.toLong() + 40L)
            focusRequester.requestFocus()
            delay(40)
            keyboardController?.show()
        }
    }
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(21.dp), tint = palette.muted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                textStyle =
                    TextStyle(
                        color = palette.text,
                        fontSize = TandemTypography.body,
                        lineHeight = TandemTypography.bodyLineHeight,
                        textAlign = TextAlign.Start,
                    ),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = palette.muted,
                                fontSize = TandemTypography.body,
                                lineHeight = TandemTypography.bodyLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Clear search", onClick = { onValueChange("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.muted)
                }
            }
        }
    }
}
