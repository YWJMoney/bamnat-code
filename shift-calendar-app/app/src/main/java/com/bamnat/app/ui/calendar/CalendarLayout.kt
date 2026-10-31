package com.bamnat.app.ui.calendar

import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.components.EditGroup
import com.bamnat.app.ui.components.EditRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.layout.PageLayout

/**
 * [CUST-01] The calendar page's parts the user can order and turn off, around the month itself. Warnings
 * (알람이 꺼져 있어요, 확인할 기록) are not here: they show whenever they are needed.
 */
enum class CalendarPart(val id: String, val label: String, val what: String, val above: Boolean, val movable: Boolean = true) {
    /** [EASY-05] The question card: one fact a feature still waits for, at most two a day. */
    ASK("ask", "질문 카드", "기능에 필요한 정보를 하루 한두 개만 물어요", above = true, movable = false),
    /** [OBS-02] "오늘 밤낮이 한 일 3개": on a day the app did more than the usual. */
    JOURNAL("journal", "밤낮이 한 일", "백업·자료 갱신처럼 오늘 알아서 한 일을 한 줄로", above = true, movable = false),
    TODAY_FLOW("todayFlow", "오늘의 흐름", "잘 시간·출근·쉬는 시간을 오늘 하루 줄로", above = true),
    TODAY_WORK("todayWork", "오늘 일감·매출·투자", "현장·가게·투자 달력의 오늘 카드", above = true),
    SUMMARY("summary", "이번 달 요약", "근무·급여(또는 수입·매출) 세 숫자", above = true, movable = false),
    COUNTS("counts", "근무 횟수 줄", "달력 바로 위 근무 종류별 횟수", above = true, movable = false),
    /** [CVW-06] 1.35 The month itself: where it stands among the movable parts says which of them are above it. Always shown. */
    MONTH("month", "이번 달 달력", "근무표 칸. 다른 카드를 이 위나 아래로 옮겨요", above = false),
    DAY_CARD("dayCard", "하루 카드", "누른 날의 근무 시간·특근·알람 끄기", above = false),
    HOURS("hours", "주 근무시간 줄", "이 주(월~일)와 3개월 주평균", above = false),
    REST("rest", "휴식 점검", "근무 사이 11시간 못 쉰 날", above = false),
    MEMOS("memos", "이번 달 메모", "날짜가 있는 메모 목록", above = false),
    ;

    /** [J135-06] F-64: the name this kind of work calls it (오늘 일감 on 현장, 오늘 매출 on 가게, 오늘 투자 on 투자). */
    fun labelFor(style: com.bamnat.core.work.WorkStyle?): String = if (this != TODAY_WORK) label else when (style) {
        com.bamnat.core.work.WorkStyle.FIELD -> "오늘 일감"
        com.bamnat.core.work.WorkStyle.BUSINESS -> "오늘 매출"
        com.bamnat.core.work.WorkStyle.INVEST -> "오늘 투자"
        else -> label
    }

    /** [J135-07] F-67: what it holds on this kind of work (no 특근·알람 on a 현장·가게·투자 calendar). */
    fun whatFor(style: com.bamnat.core.work.WorkStyle?): String = if (this != DAY_CARD) what else when (style) {
        com.bamnat.core.work.WorkStyle.FIELD -> "누른 날의 일감·받을 돈"
        com.bamnat.core.work.WorkStyle.BUSINESS -> "누른 날의 매출·영업 여부"
        com.bamnat.core.work.WorkStyle.INVEST -> "누른 날의 매매·손익"
        else -> what
    }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * [CVW-06] 1.35 Which side of the month each part stands: a movable part before [CalendarPart.MONTH] in [order] is above it, one after
 * it below; the fixed ones (질문 카드, 요약, 횟수 줄) keep their own side.
 */
internal fun calendarAbove(order: List<CalendarPart>): (CalendarPart) -> Boolean {
    val month = order.indexOf(CalendarPart.MONTH)
    return { p -> if (!p.movable || month < 0 || p == CalendarPart.MONTH) p.above else order.indexOf(p) < month }
}

/** [CUST-01] The parts in the user's order, each with whether it shows; three follow their own settings switch. */
internal fun AppSettings.calendarParts(): List<PageLayout.Placed<CalendarPart>> {
    val layout = PageLayout.decode(calendarLayout)
    // [CVW-06] An order from before 1.35 has no month: it goes where the parts above end, never shown as new.
    val placed = layout.arrange(CalendarPart.entries.toList()) { it.id }.let { list ->
        if (layout.order.isEmpty() || CalendarPart.MONTH.id in layout.order) list
        else list.filter { it.part != CalendarPart.MONTH }.let { rest ->
            val at = rest.indexOfLast { it.part.above } + 1
            rest.take(at) + PageLayout.Placed(CalendarPart.MONTH, true, false) + rest.drop(at)
        }
    }
    return placed.map { p ->
        when (p.part) {
            CalendarPart.MONTH -> p.copy(shown = true, isNew = false)
            CalendarPart.DAY_CARD -> p.copy(shown = showDayCard)
            CalendarPart.REST -> p.copy(shown = restCheck)
            CalendarPart.MEMOS -> p.copy(shown = calendarMemos)
            else -> p
        }
    }
}

/** [FIT-01] Whether [part] belongs on this calendar: it suits its kind of work, or the user added it ([added]). */
internal fun AppSettings.partFits(part: CalendarPart, added: Set<String>): Boolean =
    FitTable.visible(FitSurface.CALENDAR_PARTS, part.id, workStyle, added)

/** [FIT-01] [calendarParts] on screen: a part that does not suit this calendar is not shown, unless added. */
internal fun AppSettings.calendarParts(added: Set<String>): List<PageLayout.Placed<CalendarPart>> =
    // B 1.32 [CALX-04] 달력만 보기 puts every part away; 화면 꾸미기's own choices come back when it is off.
    calendarParts().map { if (it.shown && (calendarOnly || !partFits(it.part, added))) it.copy(shown = false) else it }

/** [CUST-01] [shown] for [part]: the three with their own switch change that switch, the rest the layout line. */
internal fun AppSettings.withPartShown(part: CalendarPart, shown: Boolean): AppSettings = when (part) {
    CalendarPart.DAY_CARD -> copy(showDayCard = shown)
    CalendarPart.REST -> copy(restCheck = shown)
    CalendarPart.MEMOS -> copy(calendarMemos = shown, memoInCells = memoInCells && shown)
    else -> {
        val placed = calendarParts().map { if (it.part == part) it.copy(shown = shown) else it }
        copy(calendarLayout = PageLayout.of(placed) { it.id }.encode())
    }
}

/**
 * [CUST-01] [part] moved [by] places among the movable parts; [CVW-06] 1.35 the month is one of them, so a card moved past it changes
 * side (이번 달 메모 above the month for a memo-first page). The fixed parts stay where they are.
 */
internal fun AppSettings.withPartMoved(part: CalendarPart, by: Int, seen: (CalendarPart) -> Boolean = { true }): AppSettings {
    val placed = calendarParts()
    val movable = placed.filter { it.part.movable }.toMutableList()
    val from = movable.indexOfFirst { it.part == part }
    // The next part the user can see that way (one that does not suit this calendar is stepped over).
    var to = from + by
    while (to in movable.indices && !seen(movable[to].part) && movable[to].part != CalendarPart.MONTH) to += by
    if (from < 0 || to !in movable.indices) return this
    movable[from] = movable[to].also { movable[to] = movable[from] }
    val fixed = placed.filterNot { it.part.movable }
    return copy(calendarLayout = PageLayout.of(fixed + movable) { it.id }.encode())
}

internal fun calendarEditGroups(
    placed: List<PageLayout.Placed<CalendarPart>>,
    style: com.bamnat.core.work.WorkStyle? = null,
    fits: (CalendarPart) -> Boolean = { true },
): List<EditGroup> {
    val above = calendarAbove(placed.map { it.part })
    fun rows(up: Boolean) = placed.filter { above(it.part) == up && fits(it.part) && it.part != CalendarPart.MONTH }.sortedBy { if (it.part.movable) 0 else 1 }.map {
        EditRow(it.part.id, it.part.labelFor(style), it.part.whatFor(style), it.shown, it.part.movable, it.isNew)
    }
    return listOf(EditGroup("위쪽 (달력 위)", rows(true)), EditGroup("아래쪽 (달력 아래)", rows(false)))
}

/**
 * [CUST-01] 화면 꾸미기 for the calendar: the parts around the month, and the month's own view options
 * (첫 요일, 칸의 시간, 음력, 날짜를 누르면) that used to be only in 설정 › 달력 화면.
 */
@androidx.compose.runtime.Composable
internal fun CalendarEditSheet(
    settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, onDismiss: () -> Unit,
    /** [FIT-02] The calendar on screen, whose added parts '더 보기' keeps. */
    calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) {
    val placed = settings.calendarParts()
    val context = androidx.compose.ui.platform.LocalContext.current
    val added = com.bamnat.app.ui.guide.rememberFitAdded(calendar)
    com.bamnat.app.ui.components.PageEditSheet(
        share = com.bamnat.core.recipe.PieceKind.LAYOUT,
        crossGroups = true,
        title = "달력 카드 배치",
        groups = calendarEditGroups(placed, settings.workStyle) { settings.partFits(it, added) },
        others = com.bamnat.app.ui.guide.fitOthers(FitSurface.CALENDAR_PARTS, settings.workStyle, added) { id ->
            CalendarPart.byId(id)?.let { it.label to it.what }
        },
        onAdd = { id, on -> com.bamnat.app.ui.guide.setFitAdded(context, calendar, FitSurface.CALENDAR_PARTS, id, on) },
        middle = "이번 달 달력",
        onShow = { id, shown -> CalendarPart.byId(id)?.let { p -> update { it.withPartShown(p, shown) } } },
        onMove = { id, by -> CalendarPart.byId(id)?.let { p -> update { it.withPartMoved(p, by) { q -> it.partFits(q, added) } } } },
        onReset = { update { it.copy(calendarLayout = "", showDayCard = false, restCheck = AppSettings().restCheck, calendarMemos = AppSettings().calendarMemos) } },
        onAsk = {
            onDismiss()
            com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "카드 배치"))
        },
        onDismiss = onDismiss,
        options = {
        androidx.compose.material3.Text("달력 보기", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        OptionChips("한 주의 시작", listOf(java.time.DayOfWeek.SUNDAY to "일요일", java.time.DayOfWeek.MONDAY to "월요일"), settings.weekStart) { d ->
            update { it.copy(weekStart = d) }
        }
        OptionChips("날짜를 누르면", listOf(com.bamnat.app.data.PhoneDayView.SHEET to "하루 화면", com.bamnat.app.data.PhoneDayView.WEEK to "그 주만"),
            settings.phoneDayView) { v -> update { it.copy(phoneDayView = v) } }
        OptionSwitch("날짜 칸에 근무 시간", settings.showHoursInCells) { on -> update { it.copy(showHoursInCells = on) } }
        OptionSwitch("음력 날짜", settings.showLunar) { on -> update { it.copy(showLunar = on) } }
        // E 1.32 [LEDX-07]
        OptionSwitch("날짜 밑에 그날 쓴 돈", settings.spendInCells) { on -> update { it.copy(spendInCells = on) } }
        // 1.32 [LEDX-23]
        OptionSwitch("지출 히트맵", settings.spendHeat) { on -> update { it.copy(spendHeat = on) } }
        OptionSwitch("날짜 밑에 메모 첫 글자", settings.memoInCells) { on -> update { it.copy(memoInCells = on, calendarMemos = it.calendarMemos || on) } }
        // [CVW-09] 1.35
        OptionChips("날짜를 길게 누르면", listOf("menu" to "빠른 메뉴", "memo" to "바로 메모", "open" to "하루 열기"), settings.longPress) { v ->
            update { it.copy(longPress = v) }
        }
        androidx.compose.material3.HorizontalDivider(androidx.compose.ui.Modifier.padding(vertical = 8.dp))
        // [CVW-02] 1.35 달력 모양; 메모 줄 turns the cells' memo on with it.
        CalendarViewEditor(com.bamnat.core.recipe.CalendarView.decode(settings.calView), { v -> update { it.withCalendarView(v) } })
        com.bamnat.app.ui.recipe.ShareThisSetting(com.bamnat.core.recipe.PieceKind.CAL_VIEW, androidx.compose.ui.Modifier.heightIn(min = 48.dp))
        },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
private fun <T> OptionChips(label: String, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, name) ->
                com.bamnat.app.ui.components.FilterChip(selected = v == value, onClick = { onPick(v) }, label = { androidx.compose.material3.Text(name) })
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = androidx.compose.ui.Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}

/** [CVW-02] [v] as the settings keep it; memo lines turn the cells' memo on, 안 보임 off. */
internal fun AppSettings.withCalendarView(v: com.bamnat.core.recipe.CalendarView): AppSettings = with(CalendarViewSettings) { withView(v) }

object CalendarViewSettings {
    fun AppSettings.withView(v: com.bamnat.core.recipe.CalendarView): AppSettings = copy(
        calView = v.encode(),
        memoInCells = when (v.memo) { null -> memoInCells; 0 -> false; else -> true },
    )
}
