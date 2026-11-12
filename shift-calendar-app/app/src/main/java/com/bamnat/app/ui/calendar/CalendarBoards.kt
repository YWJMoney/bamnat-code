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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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

// [LINK-01] 1.39 The boards read the day as the month's cell does (com.bamnat.core.schedule.DayWork): a 대근 or 특근 on a day off shows as
// that work (its colour, name and times), not as 휴무.
private fun workOf(day: ResolvedDay?): com.bamnat.core.schedule.DayWork? = day?.let { com.bamnat.core.schedule.DayWork.of(it) }

private fun shiftColor(day: ResolvedDay?): Color? {
    val w = workOf(day) ?: return null
    val argb = if (w.worked) w.colorArgb else day?.shiftType?.colorArgb
    return argb?.let { Color((it or 0xFF000000L).toInt()) }
}

/** The short letters the band shows: the kind's label, the covered kind's for a 대근, the extra kind's mark for a 특근 on a day off. */
private fun labelOf(day: ResolvedDay): String? {
    if (day.isWorkDay) return day.shiftType?.label
    day.coveredShift?.takeIf { !it.isOff }?.let { return it.label }
    day.extras.firstOrNull()?.let { return it.kind.mark }
    return day.shiftType?.label
}

private fun timesOf(day: ResolvedDay?): String? {
    val w = workOf(day) ?: return null
    if (!w.worked) return null
    val s = w.start ?: return null
    val e = w.end ?: return null
    // 2.0.0 [RVH-10] 검토 CAL-15: times as every other screen writes them ("07:00", or 오전/오후 when the phone says so).
    return "${Fmt.time(s)}–${Fmt.time(e)}"
}

/**
 * [RVH-10] 검토 CAL-15: a date's number in the month view's colours (일요일·공휴일 red, 토요일 blue, the theme's own when it has
 * them), so the timeline and the week board read the same as the month.
 */
@Composable
internal fun dayNumberColor(date: LocalDate): Color {
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    val look = com.bamnat.app.ui.theme.LocalCalendarLook.current
    return when {
        com.bamnat.core.holiday.KoreanHolidays.nameOf(date) != null -> look.holiday(cal)
        date.dayOfWeek == DayOfWeek.SUNDAY -> look.sunday(cal)
        date.dayOfWeek == DayOfWeek.SATURDAY -> look.saturday(cal)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** [LINK-01] A board day's part states: 오늘·고른 날 and the day's work (근무 종류, 대근·특근·연차·직접 바꾼 날), as the month's cells have them. */
private fun boardStates(day: ResolvedDay, today: LocalDate, picked: Boolean, hasPattern: Boolean): Set<String> =
    com.bamnat.core.schedule.DayWork.of(day, hasPattern).states + setOfNotNull("today".takeIf { day.date == today }, "sel".takeIf { picked })

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
    val works = remember(days) { days.associate { (d, r) -> d to workOf(r)!! } }
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.band"), RoundedCornerShape(0.dp))).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WeekdayHeader(weekStart, Modifier.padding(start = 0.dp))
        for (week in days.chunked(7)) {
            Row(Modifier.fillMaxWidth().height(52.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.6f))) { // [RVH-11]
                week.forEachIndexed { i, (date, day) ->
                    val c = shiftColor(day)
                    // [LINK-01] A 대근·특근 day breaks the band of days off around it.
                    fun key(r: ResolvedDay) = labelOf(r) to (works[r.date]?.worked == true)
                    val sameBefore = i > 0 && key(week[i - 1].second) == key(day) && c != null
                    val sameAfter = i < week.size - 1 && key(week[i + 1].second) == key(day) && c != null
                    val shape = RoundedCornerShape(
                        topStart = if (sameBefore) 0.dp else 10.dp, bottomStart = if (sameBefore) 0.dp else 10.dp,
                        topEnd = if (sameAfter) 0.dp else 10.dp, bottomEnd = if (sameAfter) 0.dp else 10.dp,
                    )
                    val inMonth = YearMonth.from(date) == month
                    val work = works.getValue(date)
                    val off = !work.worked && day.shiftType != null
                    val name = work.name
                    val isPicked = date == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(start = if (sameBefore) 0.dp else 2.dp, end = if (sameAfter) 0.dp else 2.dp)
                            .background((c ?: MaterialTheme.colorScheme.surfaceContainerHighest).copy(alpha = (if (off) 0.28f else 0.85f) * (if (inMonth) 1f else 0.35f)), shape)
                            .then(if (date == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                            // [TAB-07] 부품 cal.band.slot (오늘·고른 날 상태도).
                            .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.band.slot", boardStates(day, today, isPicked, snapshot.hasPattern), com.bamnat.app.logic.scopeOn(date)), shape, ))
                            .clickable { onDay(date) }
                            .clearAndSetSemantics { contentDescription = "${Fmt.monthDay(date)} ${name ?: "근무 없음"}"; this.selected = isPicked },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, fontWeight = if (date == today) FontWeight.Bold else null,
                            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 2.dp))
                        // A run of the same shift says its name once, on its first day.
                        if (!sameBefore) Text(labelOf(day) ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
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
    val barStyle = bp.style("cal.line.bar")
    val barColor = barStyle.fillColor
    // PC 확인 fc2db3b0 사용성 제안: '진하기' alone keeps each shift's own colour, only stronger or fainter.
    val barAlpha = barStyle.fillA?.let { it / 100f }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val rowShape = RoundedCornerShape(8.dp)
    Column(modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.line"), RoundedCornerShape(0.dp))).padding(horizontal = 12.dp)) {
        // PC 확인 c6bfa8b2 사용성 제안: a month with no timed work drew only grey bars; it says how bars come, with one example.
        if (rows.none { (_, _, pair) -> pair.first.isNotEmpty() }) Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), rowShape).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("이달에는 시각이 있는 근무가 없어요", style = MaterialTheme.typography.labelLarge)
                Text("근무를 넣을 때 시작·끝 시각이 있으면 하루 막대가 생겨요. 빠른 입력이나 날짜를 눌러 넣어요",
                    style = MaterialTheme.typography.bodySmall, color = ink)
            }
            val example = MaterialTheme.colorScheme.primary
            Canvas(Modifier.padding(start = 8.dp).width(72.dp).height(14.dp).clearAndSetSemantics { contentDescription = "예시 막대, 9시부터 18시" }) {
                drawRoundRect(grid.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(6f, 6f))
                drawRoundRect(example.copy(alpha = 0.9f), topLeft = Offset(size.width * 9 / 24f, 0f), size = Size(size.width * 9 / 24f, size.height),
                    cornerRadius = CornerRadius(6f, 6f))
            }
        }
        // 2.0.0 [RVH-11] 검토 CAL-16: the date and time columns grow with the font size (to 200%), so "07:00–15:00" and "28 수"
        // are not cut under big text; the hour ruler moves with them.
        val grow = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 2f)
        val dateW = 52.dp * grow
        val timeW = 76.dp * grow
        Row(Modifier.fillMaxWidth().padding(start = dateW, end = timeW, bottom = 4.dp)) {
            for (h in listOf("0", "6", "12", "18")) Text(h, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
        }
        for ((date, day, pair) in rows) {
            val (spans, beforeColor) = pair
            val c = shiftColor(day)
            val times = timesOf(day)
            val isPicked = date == selected
            // [FXR-03] 타임라인 하루 줄과 막대 with the day's values, so 조건부 꾸미기 works on them.
            val dayScope = com.bamnat.app.logic.scopeOn(date)
            val dayBar = dayScope?.let { bp.style("cal.line.bar", scope = it) }
            val rowBarColor = dayBar?.fillColor ?: barColor
            val rowBarAlpha = dayBar?.fillA?.let { it / 100f } ?: barAlpha
            Row(
                Modifier.fillMaxWidth().heightIn(min = 30.dp)
                    .background(if (date == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(8.dp))
                    // [TAB-07] 부품 cal.line.row (오늘, 고른 날).
                    .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.line.row", boardStates(day, today, isPicked, snapshot.hasPattern), dayScope), rowShape))
                    .clickable { onDay(date) }
                    .clearAndSetSemantics {
                        contentDescription = "${Fmt.monthDay(date)} ${workOf(day)?.let { w -> listOfNotNull(w.name, w.extraKinds.takeIf { it.isNotEmpty() }?.joinToString("·")).distinct().joinToString(" ") } ?: "근무 없음"}" + (times?.let { " $it" } ?: "") +
                            (spans.firstOrNull { it.carried }?.to?.let { ", 전날 근무가 ${it / 60}시 ${it % 60}분까지" } ?: "")
                        this.selected = isPicked
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${date.dayOfMonth} ${Fmt.dayOfWeekShort(date.dayOfWeek)}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (date == today) FontWeight.Bold else null,
                    color = if (date == today) MaterialTheme.colorScheme.primary else dayNumberColor(date), // [RVH-10]
                    modifier = Modifier.width(dateW))
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val w = size.width
                    drawRoundRect(grid.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(6f, 6f))
                    for (h in 1..3) drawLine(grid, Offset(w * h / 4f, 0f), Offset(w * h / 4f, size.height), 1f)
                    for (s in spans) {
                        // PC 확인 fc2db3b0: the night carried into the morning takes the 막대 꾸밈 too.
                        val color = rowBarColor ?: s.colorArgb?.let { Color((it or 0xFF000000L).toInt()) } ?: (if (s.carried) beforeColor else c) ?: Color.Gray
                        val at = Offset(w * s.from / Timeline.DAY, 0f)
                        val box = Size(w * s.minutes / Timeline.DAY, size.height)
                        val alpha = rowBarAlpha?.let { if (s.carried) it * 0.6f else it } ?: if (s.carried) 0.5f else 0.9f
                        drawRoundRect((if (rowBarColor != null) color else color.copy(alpha = alpha)), topLeft = at, size = box, cornerRadius = CornerRadius(6f, 6f))
                        // PC 확인 bad7d860: the night carried into the morning was too faint; it gets its colour's outline too.
                        if (s.carried) drawRoundRect(color, topLeft = at, size = box, cornerRadius = CornerRadius(6f, 6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                    }
                }
                val carriedTo = spans.firstOrNull { it.carried }?.to
                Text(times ?: carriedTo?.let { "~${it / 60}:${"%02d".format(it % 60)}" } ?: workOf(day)?.name ?: "", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.width(timeW))
            }
        }
    }
}

/**
 * [TAB-04] 누락 점검 N1 주 판 (제안 28): each week of [month] as a card — the week's hours on top, then its seven days side by side, each
 * with its shift, times and first memo, big enough to plan a week by. A tap picks the day as everywhere.
 */
@Composable
internal fun WeekCardsMonth(
    month: YearMonth,
    snapshot: ScheduleSnapshot,
    weekStart: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    memoTitles: Map<LocalDate, List<String>>,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
    val weeks = remember(month, snapshot, weekStart) {
        generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }
            .map { w -> (0..6).map { w.plusDays(it.toLong()) }.map { it to snapshot.resolver.resolve(it) } }.toList()
    }
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.weekly"), RoundedCornerShape(0.dp))).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (week in weeks) {
            // PC 확인 1ceb13ae: the span (12h) disagreed with the month cell's 11h; the week counts the same 근무 시간 (휴게 빼고).
            // [LINK-01] 1.39 잔업·특근·대근 count too, and a day off with a 대근·특근 is a day worked.
            val minutes = week.sumOf { (_, d) -> d.totalMinutes }
            val works = week.count { (_, d) -> workOf(d)?.worked == true }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text("${Fmt.monthDay(week.first().first)} – ${Fmt.monthDay(week.last().first)}", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    Text(listOfNotNull("근무 ${works}일".takeIf { works > 0 }, (minutes / 60).takeIf { it > 0 }?.let { "${it}시간" + if (minutes % 60 > 0) " ${minutes % 60}분" else "" })
                        .joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for ((date, day) in week) {
                        val inMonth = YearMonth.from(date) == month
                        val isPicked = date == selected
                        val c = shiftColor(day)
                        val times = timesOf(day)
                        val memo = memoTitles[date].orEmpty()
                        val states = setOfNotNull("today".takeIf { date == today }, "sel".takeIf { isPicked }, "dim".takeIf { !inMonth },
                            "sat".takeIf { date.dayOfWeek == DayOfWeek.SATURDAY }, "sun".takeIf { date.dayOfWeek == DayOfWeek.SUNDAY }) +
                            com.bamnat.core.schedule.DayWork.of(day, snapshot.hasPattern).states
                        Column(
                            Modifier.weight(1f).heightIn(min = 104.dp)
                                .background(if (isPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .then(if (date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.weekly.day", states, com.bamnat.app.logic.scopeOn(date)), RoundedCornerShape(8.dp)))
                                .clickable { onDay(date) }
                                .clearAndSetSemantics {
                                    contentDescription = "${Fmt.monthDay(date)} ${Fmt.dayOfWeekShort(date.dayOfWeek)} ${workOf(day)?.name ?: "근무 없음"}" +
                                        (times?.let { " $it" } ?: "") + (if (memo.isNotEmpty()) ", 메모 ${memo.size}개: ${memo.first()}" else "")
                                    this.selected = isPicked
                                }
                                .padding(3.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("${date.dayOfMonth} ${Fmt.dayOfWeekShort(date.dayOfWeek)}", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                fontWeight = if (date == today) FontWeight.Bold else null,
                                color = dayNumberColor(date).let { if (!inMonth) it.copy(alpha = 0.5f) else it }) // [RVH-10]
                            workOf(day)?.name?.let { t ->
                                Text(t + (workOf(day)?.extraKinds?.filter { it != t }?.firstOrNull()?.let { "+" + it.take(1) } ?: ""), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                                    color = c?.let { com.bamnat.app.ui.components.contentColorOn(it) } ?: MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().background(c ?: Color.Transparent, RoundedCornerShape(4.dp)).padding(vertical = 1.dp))
                            }
                            times?.let { Text(it.replace("–", "\n"), style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 2, lineHeight = MaterialTheme.typography.labelSmall.fontSize) }
                            memo.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                            if (memo.size > 1) Text("+${memo.size - 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * [TAB-04] 누락 점검 N1 연간 판 (제안 28): the year of [year] in twelve small months coloured by shift, with the year's counts on top
 * (the same months as 위 메뉴의 '1년 달력'). The arrows go a year back or on; a month opens it on the 월 판.
 */
@Composable
internal fun YearBoard(
    year: Int,
    snapshot: ScheduleSnapshot,
    weekStart: DayOfWeek,
    today: LocalDate,
    onYear: (Int) -> Unit,
    onOpen: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    /** [RVH-19] The calendar's kind, for its words. */
    style: com.bamnat.core.work.WorkStyle? = null,
) {
    val days by androidx.compose.runtime.produceState<Map<LocalDate, ResolvedDay>?>(null, snapshot, year) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            snapshot.resolver.resolveRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).associateBy { it.date }
        }
    }
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    Column(modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("cal.year"), RoundedCornerShape(0.dp))).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // PC 확인 1ceb13ae 사용성 제안: the year arrows sat close to the months; they get room around them.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            androidx.compose.material3.IconButton(onClick = { onYear(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 해") }
            Text("${year}년", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            androidx.compose.material3.IconButton(onClick = { onYear(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 해") }
        }
        val loaded = days ?: emptyMap()
        YearCounts(snapshot, loaded.values, style)
        val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
        val columns = if (big) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            (1..12).chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val month = YearMonth.of(year, m)
                        Box(Modifier.weight(1f).then(com.bamnat.app.ui.parts.PartMods.box(
                            bp.style("cal.year.month", setOfNotNull("today".takeIf { YearMonth.from(today) == month }), com.bamnat.app.logic.scopeOn(com.bamnat.app.logic.dayOfMonth(month, today))), RoundedCornerShape(12.dp)))) {
                            MiniMonth(month, loaded, null, weekStart, today, Modifier.fillMaxWidth(), style) { onOpen(month) }
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
