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
    // [CNV-06] 1.37 (사용자 2026-11-04 "위쪽 유닛을 아래쪽에 배치하고 싶을수도"): 요약·횟수 줄도 달력 아래로 옮겨요.
    SUMMARY("summary", "이번 달 요약", "근무·급여(또는 수입·매출) 세 숫자", above = true),
    COUNTS("counts", "근무 횟수 줄", "근무 종류별 횟수", above = true),
    /** [CVW-06] 1.35 The month itself: where it stands among the movable parts says which of them are above it. Always shown. */
    MONTH("month", "이번 달 달력", "근무표 칸. 다른 카드를 이 위나 아래로 옮겨요", above = false),
    DAY_CARD("dayCard", "하루 카드", "누른 날의 근무 시간·특근·알람 끄기", above = false),
    HOURS("hours", "주 근무시간 줄", "이 주(월~일)와 3개월 주평균", above = false),
    REST("rest", "휴식 점검", "근무 사이 11시간 못 쉰 날", above = false),
    MEMOS("memos", "이번 달 메모", "날짜가 있는 메모 목록", above = false),
    /** 1.39 [FLOW-22] 하루 이야기 (FLOW-08) of the picked day as a card; off until added here (see [lineParts]). */
    DAY_STORY("dayStory", "하루 이야기", "누른 날의 근무·알람·잠·돈·메모를 시간 순서 한 줄로", above = false),
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
internal fun AppSettings.calendarParts(): List<PageLayout.Placed<CalendarPart>> =
    lineParts().map { p ->
        when (p.part) {
            CalendarPart.DAY_CARD -> p.copy(shown = showDayCard)
            CalendarPart.REST -> p.copy(shown = restCheck)
            CalendarPart.MEMOS -> p.copy(shown = calendarMemos)
            else -> p
        }
    }

/**
 * [CNV-06] The parts as the layout line has them, the three with their own switch as the line says. A move writes this back, so
 * moving a card never turns 하루 카드 off in the line (PC 확인 1.37 A1: 요약을 내렸다 올리면 하루 카드가 숨겨졌어요).
 */
private fun AppSettings.lineParts(): List<PageLayout.Placed<CalendarPart>> {
    val layout = PageLayout.decode(calendarLayout)
    // [CVW-06] An order from before 1.35 has no month: it goes where the parts above end, never shown as new.
    // [EASE-01] 1.38 (오늘근무 분석: 열자마자 5주가 다 보이게): with no order of the user's own the month comes first and the cards go
    // under it; an order the user made (카드 배치) stays as it is.
    val placed = layout.arrange(if (layout.order.isEmpty()) CALENDAR_FIRST else CalendarPart.entries.toList()) { it.id }.let { list ->
        if (layout.order.isEmpty() || CalendarPart.MONTH.id in layout.order) list
        else list.filter { it.part != CalendarPart.MONTH }.let { rest ->
            val at = rest.indexOfLast { it.part.above } + 1
            rest.take(at) + PageLayout.Placed(CalendarPart.MONTH, true, false) + rest.drop(at)
        }
    }
    return placed.map { p ->
        when {
            p.part == CalendarPart.MONTH -> p.copy(shown = true, isNew = false)
            // 1.39 [FLOW-22] A card that came with 1.39 starts off (not every day on every calendar wants it); turned on in 카드 배치,
            // it is in the line and follows the line from then on.
            p.part == CalendarPart.DAY_STORY && p.part.id !in layout.order -> p.copy(shown = false)
            else -> p
        }
    }
}

/**
 * [EASE-01] 1.38: the parts when the user has not placed them: the fixed lines (질문 카드, 밤낮이 한 일) still over the month, then
 * the month, then the day and today's cards and the month's numbers under it.
 * 1.38 통합 12차 사용성 제안 (새로 깐 폰에서 오늘의 흐름이 달력 아래라 '카드가 사라졌다'로 읽혀요): 오늘의 흐름 is looked at every day, so
 * it stays over the month as one line ([TodayFlowSection] starts it folded here) and opens on a tap; the month still fits.
 */
internal val CALENDAR_FIRST: List<CalendarPart> = listOf(
    CalendarPart.ASK, CalendarPart.JOURNAL, CalendarPart.TODAY_FLOW, CalendarPart.MONTH, CalendarPart.DAY_CARD, CalendarPart.TODAY_WORK,
    CalendarPart.SUMMARY, CalendarPart.COUNTS, CalendarPart.HOURS, CalendarPart.REST, CalendarPart.MEMOS, CalendarPart.DAY_STORY,
)

/** [FIT-01] Whether [part] belongs on this calendar: it suits its kind of work, or the user added it ([added]). */
internal fun AppSettings.partFits(part: CalendarPart, added: Set<String>): Boolean =
    FitTable.visible(FitSurface.CALENDAR_PARTS, part.id, workStyle, added)

/** [FIT-01] [calendarParts] on screen: a part that does not suit this calendar is not shown, unless added. */
internal fun AppSettings.calendarParts(added: Set<String>): List<PageLayout.Placed<CalendarPart>> =
    // B 1.32 [CALX-04] 달력만 보기 puts every part away; 화면 꾸미기's own choices come back when it is off.
    calendarParts().map { if (it.shown && (calendarOnly || !partFits(it.part, added))) it.copy(shown = false) else it }

/**
 * 1.39 [FLOW-22] PC 4차: the layout line as the calendar reads it ([lineParts]: 하루 이야기 off until it is put in), for editors that
 * work on the line itself (스튜디오 카드 배치). Read raw, a line with no 하루 이야기 in it says it shows, so ＋ 카드 넣기 never offered it
 * and any card moved there wrote it in as shown.
 */
internal fun AppSettings.calendarLineSettled(): String = PageLayout.of(lineParts()) { it.id }.encode()

/** [CUST-01] [shown] for [part]: the three with their own switch change that switch, the rest the layout line. */
internal fun AppSettings.withPartShown(part: CalendarPart, shown: Boolean): AppSettings = when (part) {
    CalendarPart.DAY_CARD -> copy(showDayCard = shown)
    CalendarPart.REST -> copy(restCheck = shown)
    CalendarPart.MEMOS -> copy(calendarMemos = shown, memoInCells = memoInCells && shown)
    else -> {
        val placed = lineParts().map { if (it.part == part) it.copy(shown = shown) else it }
        copy(calendarLayout = PageLayout.of(placed) { it.id }.encode())
    }
}

/**
 * [CUST-01] [part] moved [by] places among the movable parts; [CVW-06] 1.35 the month is one of them, so a card moved past it changes
 * side (이번 달 메모 above the month for a memo-first page). The fixed parts stay where they are.
 */
internal fun AppSettings.withPartMoved(part: CalendarPart, by: Int, seen: (CalendarPart) -> Boolean = { true }): AppSettings {
    val placed = lineParts()
    val movable = placed.filter { it.part.movable }.toMutableList()
    val from = movable.indexOfFirst { it.part == part }
    // The next part the user can see that way (one that does not suit this calendar is stepped over).
    var to = from + by
    while (to in movable.indices && !seen(movable[to].part) && movable[to].part != CalendarPart.MONTH) to += by
    if (from < 0 || to !in movable.indices) return this
    // 1.38: taken out and put in at [to], not swapped, so a card stepped over (one that does not suit this calendar) keeps its place
    // among the others (오늘의 흐름 and 오늘 일감 swapped when 요약 went up past both).
    movable.add(to, movable.removeAt(from))
    val fixed = placed.filterNot { it.part.movable }
    return copy(calendarLayout = PageLayout.of(fixed + movable) { it.id }.encode())
}

/**
 * [EASE-01] 1.38: [part] back on the side the month-first line ([CALENDAR_FIRST]) has it, at its place there among the others, so a card
 * moved across and back is the line it was (PC 확인 1.37 A2 'no change' again).
 */
internal fun AppSettings.withPartHome(part: CalendarPart): AppSettings {
    val placed = lineParts()
    val me = placed.firstOrNull { it.part == part } ?: return this
    val rest = placed.filter { it.part != part }
    val before = CALENDAR_FIRST.takeWhile { it != part }.toSet()
    val at = rest.indexOfLast { it.part in before } + 1
    return copy(calendarLayout = PageLayout.of(rest.take(at) + me + rest.drop(at)) { it.id }.encode())
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
        // [CELL-01] 1.39 칸에 일감·메모를 줄줄이: 알아서(일감 달력, 또는 메모를 칸에 보일 때) · 늘 · 끔.
        OptionChips("날짜 칸에 일감·메모를 줄마다", listOf("auto" to "알아서", "on" to "늘", "off" to "한 줄만"), settings.cellList) { v ->
            update { it.copy(cellList = v) }
        }
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
