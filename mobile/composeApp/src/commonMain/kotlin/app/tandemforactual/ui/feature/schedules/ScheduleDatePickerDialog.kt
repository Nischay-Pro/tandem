@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatWeekday
import app.tandemforactual.ui.core.format.platformFirstWeekday
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.platformWeekday
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.buttons.TandemNavigationChevronButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.daysInMonth
import app.tandemforactual.ui.feature.transactions.daysInMonth
import app.tandemforactual.ui.feature.transactions.displayTransactionDate
import app.tandemforactual.ui.feature.transactions.parseIsoDate
import app.tandemforactual.ui.feature.transactions.transactionIsoDate
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.flow.first

@Composable
internal fun ScheduleDatePickerDialog(
    visible: Boolean,
    initialDate: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selectedDate = parseIsoDate(initialDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 1, 1)
    var visibleYear by rememberSaveable(initialDate) { mutableStateOf(selectedDate.first) }
    var visibleMonth by rememberSaveable(initialDate) { mutableStateOf(selectedDate.second) }
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - platformFirstWeekday() + 7) % 7
    val cells = List<Int?>(firstDayOffset) { null } + (1..daysInMonth(visibleYear, visibleMonth)).map { it }
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                TandemModalHeader(
                    title = "Choose date",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismiss,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TandemNavigationChevronButton(Lucide.ChevronLeft, "Previous month", onClick = {
                        if (visibleMonth == 1) {
                            visibleMonth = 12
                            visibleYear -= 1
                        } else {
                            visibleMonth -= 1
                        }
                    })
                    Text(
                        formatMonth(visibleYear, visibleMonth),
                        Modifier.weight(1f),
                        color = tandemPalette.text,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TandemNavigationChevronButton(Lucide.ChevronRight, "Next month", onClick = {
                        if (visibleMonth == 12) {
                            visibleMonth = 1
                            visibleYear += 1
                        } else {
                            visibleMonth += 1
                        }
                    })
                }
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { column ->
                        val weekday = formatWeekday(visibleYear, visibleMonth, 1 + ((column - firstDayOffset + 7) % 7))
                        Text(
                            weekday.take(1),
                            Modifier.weight(1f),
                            color = tandemPalette.muted,
                            textAlign = TextAlign.Center,
                            fontSize = TandemTypography.caption,
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val value = transactionIsoDate(visibleYear, visibleMonth, day)
                                val isSelected = value == initialDate
                                SelectableTile(
                                    selected = isSelected,
                                    contentDescription = displayTransactionDate(value),
                                    onSelect = { onSelect(value) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                    shape = CircleShape,
                                    selectedBackground = tandemPalette.accentSurface,
                                    unselectedBackground = Color.Transparent,
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (isSelected) tandemPalette.accent else tandemPalette.text,
                                        fontSize = TandemTypography.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
                    }
                }
                TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}
