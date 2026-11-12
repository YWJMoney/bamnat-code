package com.bamnat.app.ui.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.core.model.ShiftType
import java.time.DayOfWeek

/**
 * 1.37 PC MED-2 사용성 제안 (알바 출근 요일: 칩 4개가 한 줄에 빽빽하고 '저녁 알바'가 안 보임): for each kind of work a row of the
 * seven days, so every kind is in sight without scrolling sideways. A day takes one kind; another tapped on it takes its place, and
 * a day taken by another kind is ringed in that kind's color. [weekly] is indexed by [DayOfWeek.ordinal] (월 = 0).
 */
@Composable
fun WeekByType(types: List<ShiftType>, weekly: List<Long?>, onSet: (Int, Long?) -> Unit, modifier: Modifier = Modifier) {
    val byId = types.associateBy { it.id }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        types.forEach { t ->
            val color = t.colorArgb.asColor()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShiftBadge(t, size = 20.dp)
                Text(t.name + (t.start?.let { s -> t.end?.let { e -> " ${Fmt.time(s)}–${Fmt.time(e)}" } } ?: ""),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${weekly.count { it == t.id }}일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [RVH-31] 총괄 #451 사진 검토: in a card on a folded phone the row is about 305dp, a seventh 43dp, under the 44dp at the least.
            // The row reaches 8dp into the card's padding each side, so each day is 45dp or more wide (and 48dp tall).
            Row(Modifier.fillMaxWidth().layout { m, c ->
                val extra = 8.dp.roundToPx()
                val w = if (c.hasBoundedWidth) c.maxWidth + 2 * extra else c.maxWidth
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(if (c.hasBoundedWidth) c.maxWidth else p.width, p.height) { p.place(-extra, 0) }
            }, horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.entries.forEach { day ->
                    val i = day.ordinal
                    val on = weekly.getOrNull(i) == t.id
                    val other = weekly.getOrNull(i)?.takeIf { it != t.id }?.let { byId[it] }?.takeIf { !it.isOff }
                    DayToggle(day, on, color, other, Modifier.weight(1f), size = 44.dp,
                        said = "${Fmt.dayOfWeekShort(day)}요일 ${t.name} " + when {
                            on -> "일해요"
                            other != null -> "아니고 ${other.name}"
                            else -> "쉼"
                        },
                    ) { onSet(i, if (it) t.id else null) }
                }
            }
        }
        Text("한 요일에는 한 가지만 넣어요. 다른 줄에서 누르면 그 근무로 바뀌어요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [RVH-31] 검토 WORK-10 One day of the week to tap on or off: the circle drawn [size] (44dp, 40dp where the rows are many) and the
 * place to tap at least 48dp tall and a seventh of the row wide. Before 2.0.0 the tap was the circle only, 40~44dp. [other] is
 * the kind of work that has the day now, ringed in its color.
 */
@Composable
fun DayToggle(
    day: DayOfWeek, on: Boolean, color: androidx.compose.ui.graphics.Color, other: ShiftType?, modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp, said: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
    onChange: (Boolean) -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (on) color else MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (other != null) Modifier.border(2.dp, other.colorArgb.asColor().copy(alpha = 0.6f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(Fmt.dayOfWeekShort(day), style = style, color = if (on) contentColorOn(color) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
