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
 *
 * 1.39 [TASK-44] PC 6차 ('반복: 안 함·매일·평일·쉬는 날' in the 일정 창): "B" every 평일 (월~금) and "R" every day off of the calendar the memo
 * goes by (비번·휴무, a 평일 일근's 토·일), read from the schedule like "S" ([ShiftOf.rest]). An older version reads both as no repeat.
 *
 * 2.0.0 [RVI-73] (검토 U07a-07·I07aa-05·T07a-09·T07a-10: 가게의 격주 휴무·매달 둘째 주·납품 요일, 현장의 점검 주기) Four more, all in one
 * window: "K135" on several weekdays (월·수·금), "E2W3" every 2nd (to 4th) week on its weekdays, counted from the memo's own week
 * (격주 수요일), "N10" every 10 days from its date, "Q24" the 2nd 목요일 of every month ("Q5…" its last). Any repeat can end:
 * "W3~20261231" stops after 2026-12-31. Kept in the same text, so the database keeps its version; an older version reads these as no
 * repeat (its own date only).
 */
object MemoRepeat {
    /** The ways a memo can repeat, as the editor offers them. */
    enum class Kind(val label: String) {
        NONE("없음"), WEEK("매주"), MONTH("매달"), YEAR("매년"), SHIFT("근무마다"), DAY("매일"), LUNAR("매년 음력"), WEEKDAYS("평일"), REST("쉬는 날마다"),
        /** [RVI-73] */
        DAYS_OF_WEEK("요일 골라"), EVERY_WEEKS("격주"), EVERY_DAYS("며칠마다"), NTH_WEEKDAY("매달 몇째 요일"),
    }

    /**
     * [EASE-22] The kind of day (shift type id) on a date, from the calendar a memo goes by; null for nothing scheduled. 1.39
     * [TASK-44] [rest]: whether the date is a day off there (false where the schedule is not known).
     */
    fun interface ShiftOf {
        fun on(date: LocalDate): Long?
        fun rest(date: LocalDate): Boolean = false
        /**
         * 2.0.0 [RVI-20] (검토 MEMO-10) The work's start and end on [date], for a 근무마다 time after midnight: '야간마다 01:00' is the
         * night's 01:00, the next calendar day. Null where not known.
         */
        fun hours(date: LocalDate): Pair<java.time.LocalTime, java.time.LocalTime>? = null
    }

    /** 월~금. */
    fun isWeekday(date: LocalDate): Boolean = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    /** 1.39 [TASK-44] A repeat read from the calendar's schedule (근무마다, 쉬는 날마다): without it, its own date only. */
    fun followsSchedule(repeat: String): Boolean = kindOf(repeat).let { it == Kind.SHIFT || it == Kind.REST }

    /** The kind of [repeat], or [Kind.NONE] for empty or text this app does not know. [RVI-73] Its end ("~20261231") aside. */
    fun kindOf(repeat: String): Kind = kindOfBody(bodyOf(repeat))

    private fun kindOfBody(repeat: String): Kind = when {
        repeat.isEmpty() -> Kind.NONE
        WEEKLY.matches(repeat) -> Kind.WEEK
        MONTHLY.matches(repeat) -> Kind.MONTH
        YEARLY.matches(repeat) -> Kind.YEAR
        SHIFTLY.matches(repeat) -> Kind.SHIFT
        repeat == DAILY -> Kind.DAY
        LUNARLY.matches(repeat) -> Kind.LUNAR
        repeat == WEEKDAYS -> Kind.WEEKDAYS
        repeat == RESTS -> Kind.REST
        DAYS_OF.matches(repeat) && daysIn(repeat.drop(1)) != null -> Kind.DAYS_OF_WEEK
        EVERY_WEEK.matches(repeat) && daysIn(repeat.substringAfter('W')) != null -> Kind.EVERY_WEEKS
        EVERY_DAY.matches(repeat) -> Kind.EVERY_DAYS
        NTH.matches(repeat) -> Kind.NTH_WEEKDAY
        else -> Kind.NONE
    }

    /** [RVI-73] The repeat without its end: "W3~20261231" → "W3". */
    fun bodyOf(repeat: String): String = repeat.substringBefore('~')

    /** [RVI-73] The last day [repeat] comes back on, or null when it does not end (or the end cannot be read). */
    fun untilOf(repeat: String): LocalDate? = repeat.substringAfter('~', "").takeIf { it.length == 8 }?.let {
        runCatching { LocalDate.of(it.take(4).toInt(), it.substring(4, 6).toInt(), it.takeLast(2).toInt()) }.getOrNull()
    }

    /** [RVI-73] [repeat] ending after [until] (null: never ends); a memo that does not repeat stays as it is. */
    fun withUntil(repeat: String, until: LocalDate?): String {
        val body = bodyOf(repeat)
        if (body.isEmpty() || until == null) return body
        return body + "~%04d%02d%02d".format(until.year, until.monthValue, until.dayOfMonth)
    }

    /** [RVI-73] "K135": 월·수·금. Null for none, or for weekday digits out of 1..7 or given twice. */
    fun daysOfWeek(days: Set<DayOfWeek>): String? = when (days.size) {
        0 -> null
        1 -> "W" + days.first().value
        7 -> DAILY
        else -> "K" + days.map { it.value }.sorted().joinToString("")
    }

    /** [RVI-73] "E2W3": every [weeks]th week (2..4) on [days], counted from the memo's own week. */
    fun everyWeeks(weeks: Int, days: Set<DayOfWeek>): String? =
        if (days.isEmpty() || weeks !in 2..4) null else "E${weeks}W" + days.map { it.value }.sorted().joinToString("")

    /** [RVI-73] "N10": every [days] days (2..365) from the memo's date. */
    fun everyDays(days: Int): String? = if (days in 2..365) "N$days" else null

    /** [RVI-73] "Q24": the [nth] (1..4, or [LAST]) [day] of every month. */
    fun nthWeekday(nth: Int, day: DayOfWeek): String? = if (nth in 1..4 || nth == LAST) "Q$nth${day.value}" else null

    /** [RVI-73] The 몇째 of [date]'s weekday in its month: 1..4, or [LAST] when it is the month's last one and its 5th. */
    fun nthOf(date: LocalDate): Int = ((date.dayOfMonth - 1) / 7 + 1).let { if (it >= 5) LAST else it }

    /** [RVI-73] The weekdays [repeat] falls on (매주·요일 골라·격주), empty for any other kind. */
    fun weekdaysOf(repeat: String): Set<DayOfWeek> {
        val body = bodyOf(repeat)
        return when (kindOfBody(body)) {
            Kind.WEEK -> setOf(DayOfWeek.of(body.drop(1).toInt()))
            Kind.DAYS_OF_WEEK -> daysIn(body.drop(1)).orEmpty()
            Kind.EVERY_WEEKS -> daysIn(body.substringAfter('W')).orEmpty()
            Kind.WEEKDAYS -> (1..5).map(DayOfWeek::of).toSet()
            else -> emptySet()
        }
    }

    /** [RVI-73] The weeks between of 격주 (2..4), the days of 며칠마다, else null. */
    fun stepOf(repeat: String): Int? {
        val body = bodyOf(repeat)
        return when (kindOfBody(body)) {
            Kind.EVERY_WEEKS -> body.drop(1).substringBefore('W').toInt()
            Kind.EVERY_DAYS -> body.drop(1).toInt()
            else -> null
        }
    }

    /**
     * [RVI-73] [repeat] after its memo moved to [to]: one read from the date (매주, 매달, 매년, 음력, 몇째 요일) is read from the new one, one
     * of its own days (요일 골라, 격주, 며칠마다, 매일, 평일, 쉬는 날, 근무마다) stays as it is; the end stays.
     */
    fun moved(repeat: String, to: LocalDate): String {
        val until = untilOf(repeat)
        val body = bodyOf(repeat)
        val next = when (val kind = kindOfBody(body)) {
            Kind.NONE -> return ""
            Kind.WEEK, Kind.MONTH, Kind.YEAR, Kind.LUNAR -> code(kind, to)
            Kind.NTH_WEEKDAY -> nthWeekday(nthOf(to), to.dayOfWeek).orEmpty()
            else -> body
        }
        return withUntil(next, until?.takeIf { !it.isBefore(to) })
    }

    /**
     * [RVI-73] The first day from [from] on that [repeat] falls on, to start a memo there (a 틀, a repeat picked for a memo with no
     * date): its weekday, its 몇째 요일, its day of the month or year; [from] itself for one counted from its own date (매일, 며칠마다)
     * or read from the schedule. Null for none, or nothing within a year and a bit.
     */
    fun firstFrom(repeat: String, from: LocalDate): LocalDate? {
        val body = bodyOf(repeat)
        return when (kindOfBody(body)) {
            Kind.NONE -> null
            Kind.DAY, Kind.EVERY_DAYS, Kind.SHIFT, Kind.REST -> from
            Kind.WEEK, Kind.DAYS_OF_WEEK, Kind.EVERY_WEEKS, Kind.WEEKDAYS ->
                weekdaysOf(body).let { days -> generateSequence(from) { it.plusDays(1) }.take(7).firstOrNull { it.dayOfWeek in days } }
            // Read as a memo dated well before, so its own date does not count: the pattern alone.
            Kind.MONTH, Kind.YEAR, Kind.LUNAR, Kind.NTH_WEEKDAY -> Memo(date = from.minusYears(2), repeat = body).let { probe ->
                generateSequence(from) { it.plusDays(1) }.take(400).firstOrNull { occursOn(probe, it) }
            }
        }
    }

    /** [RVI-73] The weekday digits "135" as days; null when one is not 1..7 or comes twice. */
    private fun daysIn(digits: String): Set<DayOfWeek>? {
        if (digits.isEmpty() || digits.any { it !in '1'..'7' }) return null
        val days = digits.map { DayOfWeek.of(it - '0') }
        return days.toSet().takeIf { it.size == days.size }
    }

    /** [EASE-22] The stored text for "every [shiftId] day". */
    fun shiftCode(shiftId: Long): String = "S$shiftId"

    /** [EASE-22] The kind of day a 근무마다 repeat follows, or null for any other repeat. */
    fun shiftIdOf(repeat: String): Long? = if (kindOf(repeat) == Kind.SHIFT) bodyOf(repeat).drop(1).toLongOrNull() else null

    /** The stored text for repeating [kind] from [date]: its weekday, its day or its month and day. [Kind.SHIFT] needs [shiftCode]. */
    fun code(kind: Kind, date: LocalDate): String = when (kind) {
        Kind.NONE, Kind.SHIFT -> ""
        Kind.WEEK -> "W" + date.dayOfWeek.value
        Kind.MONTH -> "M" + date.dayOfMonth
        Kind.YEAR -> "Y%02d%02d".format(date.monthValue, date.dayOfMonth)
        Kind.DAY -> DAILY
        Kind.WEEKDAYS -> WEEKDAYS
        Kind.REST -> RESTS
        Kind.LUNAR -> lunarOf(date)?.let { "L%02d%02d".format(it.month, it.day) }.orEmpty()
        // [RVI-73] Each new way starts from [date]'s own weekday (or its 몇째), changed in the repeat window.
        Kind.DAYS_OF_WEEK -> "W" + date.dayOfWeek.value
        Kind.EVERY_WEEKS -> "E2W" + date.dayOfWeek.value
        Kind.EVERY_DAYS -> "N2"
        Kind.NTH_WEEKDAY -> nthWeekday(nthOf(date), date.dayOfWeek).orEmpty()
    }

    /** [CAL2-06] The lunar month and day of [date], in the years the lunar calendar is known for; null outside them. */
    private fun lunarOf(date: LocalDate): com.bamnat.core.holiday.LunarDate? =
        if (date.year in LUNAR_YEARS) com.bamnat.core.holiday.KoreanLunar.fromSolar(date) else null

    /** "매주 화요일", "매달 25일", "매년 1월 1일", [EASE-22] "야간마다" ([shiftName] names the kind), or empty for none. */
    fun label(repeat: String, shiftName: (Long) -> String? = { null }): String {
        val body = bodyOf(repeat)
        val main = labelOfBody(body, shiftName)
        // [RVI-73] "매주 수요일 · 12월 31일까지".
        val until = untilOf(repeat) ?: return main
        return if (main.isEmpty()) main else "$main · ${until.monthValue}월 ${until.dayOfMonth}일까지"
    }

    private fun labelOfBody(repeat: String, shiftName: (Long) -> String?): String {
        val n = repeat.drop(1)
        return when (kindOfBody(repeat)) {
            Kind.NONE -> ""
            Kind.SHIFT -> (n.toLongOrNull()?.let(shiftName) ?: "그 근무") + "마다"
            Kind.WEEK -> "매주 " + "월화수목금토일"[n.toInt() - 1] + "요일"
            Kind.MONTH -> "매달 ${n.toInt()}일"
            Kind.YEAR -> "매년 ${n.take(2).toInt()}월 ${n.drop(2).toInt()}일"
            Kind.DAY -> "매일"
            Kind.WEEKDAYS -> "평일(월~금)마다"
            Kind.REST -> "쉬는 날마다"
            Kind.LUNAR -> "매년 음력 ${n.take(2).toInt()}월 ${n.drop(2).toInt()}일"
            Kind.DAYS_OF_WEEK -> "매주 " + days(weekdaysOf(repeat))
            Kind.EVERY_WEEKS -> (if (stepOf(repeat) == 2) "격주 " else "${stepOf(repeat)}주마다 ") + days(weekdaysOf(repeat)) + "요일"
            Kind.EVERY_DAYS -> "${n.toInt()}일마다"
            Kind.NTH_WEEKDAY -> "매달 " + (if (n.first() - '0' == LAST) "마지막" else NTH_WORDS[n.first() - '1']) + " " +
                "월화수목금토일"[n.last() - '1'] + "요일"
        }
    }

    /** "월·수·금요일" for several, "수요일" for one. */
    private fun days(set: Set<DayOfWeek>): String = set.sortedBy { it.value }.joinToString("·") { "월화수목금토일"[it.value - 1].toString() } +
        if (set.size == 1) "요일" else ""

    /**
     * Whether [memo] is on [date]: on its own date, or on a later date its repeat falls on. A monthly
     * repeat on a day the month does not have (31, 30, 29) falls on the month's last day, and 2월 29일
     * every year on 2월 28일 in other years.
     */
    fun occursOn(memo: Memo, date: LocalDate, shifts: ShiftOf? = null): Boolean {
        val start = memo.date ?: return false
        // 1.38 이 날만 빼기: a day taken off a repeating memo is not one of its days.
        if (date in memo.skips && kindOf(memo.repeat) != Kind.NONE) return false
        // [RVI-73] A repeat that ended: its own date still is.
        untilOf(memo.repeat)?.let { end -> if (date.isAfter(end) && date != start) return false }
        val body = bodyOf(memo.repeat)
        val n = body.drop(1)
        // [EASE-22] A 근무마다 memo is where its kind of day is, its own date too: a day changed to another shift lets it go.
        if (kindOf(memo.repeat) == Kind.SHIFT && shifts != null) return !date.isBefore(start) && shifts.on(date) == n.toLongOrNull()
        // 1.39 [TASK-44] 쉬는 날마다 likewise: on each day off from its date on, its own date too only when that is one.
        if (kindOf(memo.repeat) == Kind.REST && shifts != null) return !date.isBefore(start) && shifts.rest(date)
        // 2.0.0 [RVI-21] (검토 MEMO-15) 평일 begun on a weekend is not on that weekend: its own date counts only on a 평일.
        if (date == start) return kindOf(memo.repeat) != Kind.WEEKDAYS || isWeekday(date)
        if (date.isBefore(start)) return false
        return when (kindOf(memo.repeat)) {
            // [RVI-73]
            Kind.DAYS_OF_WEEK -> date.dayOfWeek in weekdaysOf(body)
            Kind.EVERY_WEEKS -> date.dayOfWeek in weekdaysOf(body) &&
                java.time.temporal.ChronoUnit.WEEKS.between(mondayOf(start), mondayOf(date)) % (stepOf(body) ?: 2) == 0L
            Kind.EVERY_DAYS -> java.time.temporal.ChronoUnit.DAYS.between(start, date) % (stepOf(body) ?: 1) == 0L
            Kind.NTH_WEEKDAY -> date.dayOfWeek.value == n.last() - '0' &&
                (if (n.first() - '0' == LAST) date.plusDays(7).monthValue != date.monthValue else nthOf(date) == n.first() - '0' && date.dayOfMonth <= 28)
            Kind.NONE, Kind.SHIFT, Kind.REST -> false
            Kind.WEEKDAYS -> isWeekday(date)
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

    private fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    /** The days in [from]..[to] that [memo] is on, in order. */
    fun datesIn(memo: Memo, from: LocalDate, to: LocalDate, shifts: ShiftOf? = null): List<LocalDate> {
        val start = memo.date ?: return emptyList()
        val kind = kindOf(memo.repeat)
        if (kind == Kind.NONE || followsSchedule(memo.repeat) && shifts == null) {
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
            followsSchedule(m.repeat) -> {
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

    /** [RVI-73] 몇째 for the month's last one of a weekday. */
    const val LAST = 5
    private val NTH_WORDS = listOf("첫째", "둘째", "셋째", "넷째")
    private val DAYS_OF = Regex("K[1-7]{2,7}")
    private val EVERY_WEEK = Regex("E[2-4]W[1-7]{1,7}")
    private val EVERY_DAY = Regex("N([2-9]|[1-9]\\d|[12]\\d\\d|3[0-5]\\d|36[0-5])")
    private val NTH = Regex("Q[1-5][1-7]")
    private val WEEKLY = Regex("W[1-7]")
    private val MONTHLY = Regex("M([1-9]|[12]\\d|3[01])")
    private val SHIFTLY = Regex("S\\d{1,18}")
    private const val DAILY = "D"
    private const val WEEKDAYS = "B"
    private const val RESTS = "R"
    private val LUNARLY = Regex("L(0[1-9]|1[0-2])(0[1-9]|[12]\\d|30)")
    private val LUNAR_YEARS = 1951..2099
    private val YEARLY = Regex("Y(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])")
}
