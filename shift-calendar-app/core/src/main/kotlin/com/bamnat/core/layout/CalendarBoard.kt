package com.bamnat.core.layout

import com.bamnat.core.model.WorkTime
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalTime

/**
 * [TAB-04] 1.36 달력 판짜기 (2차 조사 제안 28, Notion 보기): the boards the calendar tab lays a month on. 월 and 목록 were there; 근무 띠
 * draws each week as one band of the shifts' colours so a rota's 주기 reads at a glance, and 타임라인 draws each day as a 24-hour bar with
 * the shift on it (a night shift runs on into the next morning). 주 and 연간 stay where they were (하루 카드의 '주', 위 메뉴의 '한 해').
 */
enum class CalendarBoard(val key: String, val label: String, val what: String) {
    MONTH("month", "월", "한 달을 칸으로"),
    LIST("list", "목록", "날마다 한 줄로"),
    BAND("band", "근무 띠", "주마다 근무를 띠로, 주기가 한눈에"),
    LINE("line", "타임라인", "하루 24시간 막대에 근무 시간"),
    ;

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String?): CalendarBoard = entries.firstOrNull { it.key == key } ?: MONTH

        /**
         * [TAB-05] 유형 전문 달력: the board each kind of work is shown first when the user asks for its 전문 판 — 교대 근무 띠 (주기),
         * 일근·알바 타임라인 (시간), 현장·가게·투자 목록 (일감·매출·손익 한 줄씩).
         */
        fun expertFor(style: WorkStyle?): CalendarBoard = when (style) {
            WorkStyle.SHIFT -> BAND
            WorkStyle.DAY, WorkStyle.HOURLY -> LINE
            WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST -> LIST
            null -> MONTH
        }
    }
}

/** [TAB-04] One stretch of a 타임라인 bar: minutes from this day's midnight, [from] to [to] (to ≤ 1440). */
data class TimeSpan(val from: Int, val to: Int, val carried: Boolean = false) {
    val minutes: Int get() = to - from
}

object Timeline {
    const val DAY = WorkTime.MINUTES_PER_DAY

    /**
     * The stretches of [date]'s 타임라인 bar: its own shift up to midnight, and what [yesterday]'s shift carried past midnight ([TimeSpan.carried]).
     * A day off or a shift with no times draws nothing of its own.
     */
    fun spans(date: LocalDate, today: ResolvedDay?, yesterday: ResolvedDay?): List<TimeSpan> {
        val out = ArrayList<TimeSpan>(2)
        yesterday?.let { y -> carry(y)?.let { if (it > 0) out += TimeSpan(0, minOf(it, DAY), carried = true) } }
        today?.let { t ->
            if (t.date == date) own(t)?.let { (s, e) -> if (e > s) out += TimeSpan(s, e) }
        }
        return out
    }

    /** Where [day]'s shift stands on its own date: start to end, or to midnight when it runs past. */
    private fun own(day: ResolvedDay): Pair<Int, Int>? {
        val s = day.start ?: return null
        val e = day.end ?: return null
        if (day.shiftType?.isOff == true) return null
        val from = minutes(s)
        val span = WorkTime.spanMinutes(s, e)
        return from to minOf(from + span, DAY)
    }

    /** Minutes of [day]'s shift past its own midnight, 0 for one that ends the same day. */
    fun carry(day: ResolvedDay): Int? {
        val s = day.start ?: return null
        val e = day.end ?: return null
        if (day.shiftType?.isOff == true) return null
        val over = minutes(s) + WorkTime.spanMinutes(s, e) - DAY
        return if (over > 0) over else 0
    }

    private fun minutes(t: LocalTime) = t.hour * 60 + t.minute
}

/**
 * [TAB-04] 근무 띠: each week of [days] (Monday..Sunday or the week start the user keeps) as one row, and the rota's 주기 the band shows —
 * the shortest repeat of shift names that fits the month, when there is one ("주주야야비휴" → 6일 주기).
 */
object ShiftBand {
    fun cycle(names: List<String?>, maxLength: Int = 28): Int? {
        val known = names.dropWhile { it == null }.dropLastWhile { it == null }
        if (known.size < 4 || known.any { it == null }) return null
        for (n in 2..minOf(maxLength, known.size / 2)) {
            if (known.indices.all { i -> i < n || known[i] == known[i - n] }) return n
        }
        return null
    }
}
