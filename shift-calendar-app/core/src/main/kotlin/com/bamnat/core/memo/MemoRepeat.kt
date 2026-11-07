package com.bamnat.core.memo

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [NOTE-05] Memos that come back: rent on the 25th every month, an order every Tuesday, a birthday
 * every year. Kept in [Memo.repeat] as "W1".."W7" (ISO day of week, 1 = Monday), "M1".."M31" (day of
 * the month) or "Y0101" (month and day); empty for a memo that is on its own date only. A repeat
 * starts on the memo's date and never shows before it.
 *
 * [EASE-22] 1.38 "근무마다 반복 메모" (오늘근무 분석 P6, 사용자 관찰): "S<근무 종류 id>" comes back on every day that kind of
 * day is on the calendar (야간마다 "도시락", 주간마다 "출입증"). It is read from the schedule each time, never stored per day,
 * so a day changed by hand, a 하루 밀기 or a new order moves the memo with the shift. Without the schedule (a
 * [ShiftOf] of null) it shows on its own date only.
 *
 * 1.39 [CAL2-06] "D" every day (할 일 앱의 매일, TASK-06), and "L0815" every year on a lunar month and day (제사·음력 생일,
 * 비즈니스 달력 리뷰가 바란 것). A lunar day a year does not have (30일 of a 29-day month) falls on that month's last day; a 윤달 is
 * never the day (a memo made on 윤달 comes back on the plain month). An older version reads both as no repeat (its own date).
 */
object MemoRepeat {
    /** The ways a memo can repeat, as the editor offers them. */
    enum class Kind(val label: String) { NONE("없음"), WEEK("매주"), MONTH("매달"), YEAR("매년"), SHIFT("근무마다"), DAY("매일"), LUNAR("매년 음력") }

    /** [EASE-22] The kind of day (shift type id) on a date, from the calendar a memo goes by; null for nothing scheduled. */
    fun interface ShiftOf { fun on(date: LocalDate): Long? }

    /** The kind of [repeat], or [Kind.NONE] for empty or text this app does not know. */
    fun kindOf(repeat: String): Kind = when {
        WEEKLY.matches(repeat) -> Kind.WEEK
        MONTHLY.matches(repeat) -> Kind.MONTH
        YEARLY.matches(repeat) -> Kind.YEAR
        SHIFTLY.matches(repeat) -> Kind.SHIFT
        repeat == DAILY -> Kind.DAY
        LUNARLY.matches(repeat) -> Kind.LUNAR
        else -> Kind.NONE
    }

    /** [EASE-22] The stored text for "every [shiftId] day". */
    fun shiftCode(shiftId: Long): String = "S$shiftId"

    /** [EASE-22] The kind of day a 근무마다 repeat follows, or null for any other repeat. */
    fun shiftIdOf(repeat: String): Long? = if (kindOf(repeat) == Kind.SHIFT) repeat.drop(1).toLongOrNull() else null

    /** The stored text for repeating [kind] from [date]: its weekday, its day or its month and day. [Kind.SHIFT] needs [shiftCode]. */
    fun code(kind: Kind, date: LocalDate): String = when (kind) {
        Kind.NONE, Kind.SHIFT -> ""
        Kind.WEEK -> "W" + date.dayOfWeek.value
        Kind.MONTH -> "M" + date.dayOfMonth
        Kind.YEAR -> "Y%02d%02d".format(date.monthValue, date.dayOfMonth)
        Kind.DAY -> DAILY
        Kind.LUNAR -> lunarOf(date)?.let { "L%02d%02d".format(it.month, it.day) }.orEmpty()
    }

    /** [CAL2-06] The lunar month and day of [date], in the years the lunar calendar is known for; null outside them. */
    private fun lunarOf(date: LocalDate): com.bamnat.core.holiday.LunarDate? =
        if (date.year in LUNAR_YEARS) com.bamnat.core.holiday.KoreanLunar.fromSolar(date) else null

    /** "매주 화요일", "매달 25일", "매년 1월 1일", [EASE-22] "야간마다" ([shiftName] names the kind), or empty for none. */
    fun label(repeat: String, shiftName: (Long) -> String? = { null }): String {
        val n = repeat.drop(1)
        return when (kindOf(repeat)) {
            Kind.NONE -> ""
            Kind.SHIFT -> (n.toLongOrNull()?.let(shiftName) ?: "그 근무") + "마다"
            Kind.WEEK -> "매주 " + "월화수목금토일"[n.toInt() - 1] + "요일"
            Kind.MONTH -> "매달 ${n.toInt()}일"
            Kind.YEAR -> "매년 ${n.take(2).toInt()}월 ${n.drop(2).toInt()}일"
            Kind.DAY -> "매일"
            Kind.LUNAR -> "매년 음력 ${n.take(2).toInt()}월 ${n.drop(2).toInt()}일"
        }
    }

    /**
     * Whether [memo] is on [date]: on its own date, or on a later date its repeat falls on. A monthly
     * repeat on a day the month does not have (31, 30, 29) falls on the month's last day, and 2월 29일
     * every year on 2월 28일 in other years.
     */
    fun occursOn(memo: Memo, date: LocalDate, shifts: ShiftOf? = null): Boolean {
        val start = memo.date ?: return false
        // 1.38 이 날만 빼기: a day taken off a repeating memo is not one of its days.
        if (date in memo.skips && kindOf(memo.repeat) != Kind.NONE) return false
        val n = memo.repeat.drop(1)
        // [EASE-22] A 근무마다 memo is where its kind of day is, its own date too: a day changed to another shift lets it go.
        if (kindOf(memo.repeat) == Kind.SHIFT && shifts != null) return !date.isBefore(start) && shifts.on(date) == n.toLongOrNull()
        if (date == start) return true
        if (date.isBefore(start)) return false
        return when (kindOf(memo.repeat)) {
            Kind.NONE, Kind.SHIFT -> false
            Kind.WEEK -> date.dayOfWeek == DayOfWeek.of(n.toInt())
            Kind.MONTH -> date.dayOfMonth == minOf(n.toInt(), date.lengthOfMonth())
            Kind.YEAR -> {
                val month = n.take(2).toInt()
                val day = n.drop(2).toInt()
                date.monthValue == month && date.dayOfMonth == minOf(day, date.month.length(date.isLeapYear))
            }
            Kind.DAY -> true
            Kind.LUNAR -> {
                val lunar = lunarOf(date) ?: return false
                val month = n.take(2).toInt()
                val day = n.drop(2).toInt()
                if (lunar.leap || lunar.month != month) return false
                // A month of 29 days has no 30일: the memo falls on the 29th.
                lunar.day == day || day == 30 && lunar.day == 29 &&
                    com.bamnat.core.holiday.KoreanLunar.toSolar(lunar.year, month, 30) == null
            }
        }
    }

    /** The days in [from]..[to] that [memo] is on, in order. */
    fun datesIn(memo: Memo, from: LocalDate, to: LocalDate, shifts: ShiftOf? = null): List<LocalDate> {
        val start = memo.date ?: return emptyList()
        val kind = kindOf(memo.repeat)
        if (kind == Kind.NONE || kind == Kind.SHIFT && shifts == null) {
            return if (start in from..to && (kind == Kind.NONE || start !in memo.skips)) listOf(start) else emptyList()
        }
        return generateSequence(maxOf(from, start)) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .filter { occursOn(memo, it, shifts) }.toList()
    }

    /**
     * [memos] with each repeating one put on every day of [from]..[to] it comes back on (a copy with
     * that date and the same id, so opening it opens the memo itself); the rest, and a repeat with no
     * day in the range, as they are. [EASE-22] [shiftsOf] gives the schedule a 근무마다 memo goes by (null: its own date only);
     * one with no such day in the range is left out, so a memo whose shift moved away is not shown on a day without it.
     */
    fun expand(memos: List<Memo>, from: LocalDate, to: LocalDate, shiftsOf: (Memo) -> ShiftOf? = { null }): List<Memo> = memos.flatMap { m ->
        val kind = kindOf(m.repeat)
        when {
            m.date == null || kind == Kind.NONE -> listOf(m)
            kind == Kind.SHIFT -> {
                val shifts = shiftsOf(m)
                if (shifts == null) listOf(m) else datesIn(m, from, to, shifts).map { m.copy(date = it) }
            }
            // Nothing in range: as it is, unless its own date is in range and was taken off (이 날만 빼기).
            else -> datesIn(m, from, to).map { m.copy(date = it) }.ifEmpty { if (m.date in m.skips && m.date in from..to) emptyList() else listOf(m) }
        }
    }

    /** 1.38 [Memo.skips] as stored: epoch days in order, "20760,20764"; '' for none. */
    fun encodeSkips(days: Set<LocalDate>): String = days.map { it.toEpochDay() }.sorted().joinToString(",")

    /** [encodeSkips] read back; anything that is not a day is left out. */
    fun decodeSkips(text: String): Set<LocalDate> =
        if (text.isBlank()) emptySet() else text.split(',').mapNotNullTo(LinkedHashSet()) { it.trim().toLongOrNull()?.let(LocalDate::ofEpochDay) }

    private val WEEKLY = Regex("W[1-7]")
    private val MONTHLY = Regex("M([1-9]|[12]\\d|3[01])")
    private val SHIFTLY = Regex("S\\d{1,18}")
    private const val DAILY = "D"
    private val LUNARLY = Regex("L(0[1-9]|1[0-2])(0[1-9]|[12]\\d|30)")
    private val LUNAR_YEARS = 1951..2099
    private val YEARLY = Regex("Y(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])")
}
