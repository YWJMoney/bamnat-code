package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 1.38 [DSG-06] 주 띠 (흐름·디자인 계획 4장): seven days in a row of circles, the picked one filled, a small dot under
 * each day that has something worth seeing ([marks], in its meaning colour). Used over a summary page with the existing
 * chip row under it for filtering, so a week reads at a glance and one tap moves between days.
 */
@Composable
fun WeekStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    marks: Map<LocalDate, Tone> = emptyMap(),
    today: LocalDate? = null,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** 1.39 [FLOW-19] How many things each day has (메모 n개), a plain dot under it where there is no [marks] colour. */
    counts: Map<LocalDate, Int> = emptyMap(),
    /** What [counts] counts, for TalkBack ("메모"). */
    countWord: String = "",
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        days.forEach { d ->
            val picked = d == selected
            val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            val mark = marks[d]
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = picked, role = Role.Tab, onClick = { onPick(d) })
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일 ($weekday)" +
                            (if (d == today) ", 오늘" else "") + when (mark) {
                                Tone.Good -> ", 좋음"; Tone.Caution -> ", 살펴볼 것"; Tone.Danger -> ", 위험"; else -> ""
                            } + (counts[d]?.takeIf { it > 0 }?.let { ", $countWord ${it}개" } ?: "")
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekday, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ring = if (d == today && !picked) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                Box(Modifier.size(36.dp).then(ring)
                    .background(if (picked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall,
                        color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.size(6.dp).background(mark?.takeIf { it != Tone.Plain }?.color()
                    ?: MaterialTheme.colorScheme.onSurfaceVariant.takeIf { (counts[d] ?: 0) > 0 }
                    ?: androidx.compose.ui.graphics.Color.Transparent, CircleShape))
            }
        }
    }
}
