package com.bamnat.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.parts.fillColor
import com.bamnat.core.layout.CalendarBoard
import com.bamnat.core.layout.ShiftBand
import com.bamnat.core.layout.Timeline
import com.bamnat.core.schedule.ResolvedDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private fun shiftColor(day: ResolvedDay?): Color? = day?.shiftType?.let { Color((it.colorArgb or 0xFF000000L).toInt()) }

private fun timesOf(day: ResolvedDay?): String? {
    val s = day?.start ?: return null
    val e = day.end ?: return null
    if (day.shiftType?.isOff == true) return null
    return "${s.hour}:${"%02d".format(s.minute)}–${e.hour}:${"%02d".format(e.minute)}"
}

/**
 * [TAB-04] 1.36 판 고르기: the board menu under the calendar's view button — 월 · 목록 · 근무 띠 · 타임라인, each with one line on what it
 * shows, and 이 유형 전문 판 marked ([TAB-05]).
 */
@Composable
internal fun CalendarBoardMenu(open: Boolean, current: CalendarBoard, expert: CalendarBoard, onPick: (CalendarBoard) -> Unit, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        for (b in CalendarBoard.entries) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(b.label + if (b == expert && b != CalendarBoard.MONTH) " · 이 유형 전문" else "", fontWeight = if (b == current) FontWeight.Bold else null)
                        Text(b.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { if (b == current) Icon(Icons.Filled.Check, null) else Box(Modifier.size(24.dp)) },
                onClick = { onPick(b); onDismiss() },
                modifier = Modifier.semantics { selected = b == current },
            )
        }
    }
}

/**
 * [TAB-04] 근무 띠: each week as one band of the shifts' colours, a run of the same shift drawn as one piece, so a rota's turn reads at a
 * glance; under it the rota's 주기 when the month repeats one ("6일 주기").
 */
@Composable
internal fun ShiftBandMonth(
    month: YearMonth,
    snapshot: ScheduleSnapshot,
    weekStart: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
    val days = remember(month, snapshot, weekStart) {
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { it to snapshot.resolver.resolve(it) }.toList()
    }
    val cycle = remember(days) { ShiftBand.cycle(days.filter { YearMonth.from(it.first) == month }.map { it.second.shiftType?.name }) }
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.band"), RoundedCornerShape(0.dp))).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WeekdayHeader(weekStart, Modifier.padding(start = 0.dp))
        for (week in days.chunked(7)) {
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                week.forEachIndexed { i, (date, day) ->
                    val c = shiftColor(day)
                    val sameBefore = i > 0 && week[i - 1].second.shiftType?.id == day.shiftType?.id && c != null
                    val sameAfter = i < week.size - 1 && week[i + 1].second.shiftType?.id == day.shiftType?.id && c != null
                    val shape = RoundedCornerShape(
                        topStart = if (sameBefore) 0.dp else 10.dp, bottomStart = if (sameBefore) 0.dp else 10.dp,
                        topEnd = if (sameAfter) 0.dp else 10.dp, bottomEnd = if (sameAfter) 0.dp else 10.dp,
                    )
                    val inMonth = YearMonth.from(date) == month
                    val off = day.shiftType?.isOff == true
                    val name = day.shiftType?.name
                    val isPicked = date == selected
                    Box(
                        Modifier.weight(1f).height(52.dp).padding(start = if (sameBefore) 0.dp else 2.dp, end = if (sameAfter) 0.dp else 2.dp)
                            .background((c ?: MaterialTheme.colorScheme.surfaceContainerHighest).copy(alpha = (if (off) 0.28f else 0.85f) * (if (inMonth) 1f else 0.35f)), shape)
                            .then(if (date == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                            // [TAB-07] 부품 cal.band.slot (오늘·고른 날 상태도).
                            .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.band.slot", setOfNotNull("today".takeIf { date == today }, "sel".takeIf { isPicked })), shape, ))
                            .clickable { onDay(date) }
                            .clearAndSetSemantics { contentDescription = "${Fmt.monthDay(date)} ${name ?: "근무 없음"}"; this.selected = isPicked },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, fontWeight = if (date == today) FontWeight.Bold else null,
                            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp))
                        // A run of the same shift says its name once, on its first day.
                        if (!sameBefore) Text(day.shiftType?.label ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = if (c != null && !off) com.bamnat.app.ui.components.contentColorOn(c) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        Text(
            cycle?.let { "이 달은 ${it}일마다 같은 순서로 돌아요" } ?: "같은 근무가 이어지는 날은 한 띠로 이어져요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center,
        )
    }
}

/**
 * [TAB-04] 타임라인: each day of the month a 24-hour bar with its shift on it in the shift's colour, and what the night before carried past
 * midnight in a lighter colour; 6·12·18시 marked. A tap picks the day.
 */
@Composable
internal fun TimelineMonth(
    month: YearMonth,
    snapshot: ScheduleSnapshot,
    today: LocalDate,
    selected: LocalDate,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(month, snapshot) {
        (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val day = snapshot.resolver.resolve(date)
            val before = snapshot.resolver.resolve(date.minusDays(1))
            Triple(date, day, Timeline.spans(date, day, before) to shiftColor(before))
        }
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val barColor = bp.style("cal.line.bar").fillColor
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 64.dp, bottom = 4.dp)) {
            for (h in listOf("0", "6", "12", "18")) Text(h, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
        }
        for ((date, day, pair) in rows) {
            val (spans, beforeColor) = pair
            val c = shiftColor(day)
            val times = timesOf(day)
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val isPicked = date == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 30.dp)
                    .background(if (date == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onDay(date) }
                    .clearAndSetSemantics {
                        contentDescription = "${Fmt.monthDay(date)} ${day.shiftType?.name ?: "근무 없음"}" + (times?.let { " $it" } ?: "")
                        this.selected = isPicked
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${date.dayOfMonth} ${Fmt.dayOfWeekShort(date.dayOfWeek)}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (date == today) FontWeight.Bold else null,
                    color = if (date == today) MaterialTheme.colorScheme.primary else if (weekend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(52.dp))
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val w = size.width
                    drawRoundRect(grid.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(6f, 6f))
                    for (h in 1..3) drawLine(grid, Offset(w * h / 4f, 0f), Offset(w * h / 4f, size.height), 1f)
                    for (s in spans) {
                        val color = (if (s.carried) beforeColor else barColor ?: c) ?: Color.Gray
                        drawRoundRect(
                            color.copy(alpha = if (s.carried) 0.45f else 0.9f),
                            topLeft = Offset(w * s.from / Timeline.DAY, 0f),
                            size = Size(w * s.minutes / Timeline.DAY, size.height),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }
                Text(times ?: day.shiftType?.name ?: "", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
        }
    }
}
