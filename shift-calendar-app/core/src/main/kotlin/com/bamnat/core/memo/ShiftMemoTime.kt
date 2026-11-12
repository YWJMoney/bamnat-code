package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [MEMO-05] 1.27 AK 순번 451 혁신: 근무 기준 알림. A shift worker thinks "다음 야간 때 열쇠 챙기기", not "10월 27일 19시":
 * this finds such words in a memo and the time they mean on the calendar's own schedule, and lists the same times for
 * the schedule dialog's '근무에 맞춰' chips. A work shift is told [BEFORE_MINUTES] before it starts (출근 1시간 전);
 * a day off at [OFF_TIME]. The time is stored as an ordinary schedule ([MemoReminder]), so nothing new is saved
 * (결정 AK-D6).
 */
object ShiftMemoTime {
    /** One day of the calendar ahead. [start] is null on a day off or a work day without hours; 1.39 [CAL2-20] [end] with it. */
    data class Day(val date: LocalDate, val name: String, val work: Boolean, val start: LocalTime?, val end: LocalTime? = null)

    data class Suggestion(val at: LocalDateTime, val label: String, val name: String, val date: LocalDate)

    const val BEFORE_MINUTES = 60L
    val OFF_TIME: LocalTime = LocalTime.of(9, 0)
    private val WORK_NO_HOURS: LocalTime = LocalTime.of(8, 0)

    /**
     * 1.39 [TASK-39] PC 6차: a day off right after a 야간 that ended that morning (07:00 퇴근) is slept through, so its morning
     * would wake the user. Such a day is told in the afternoon: [SLEEP_HOURS] after the shift ends, not before [AFTER_NIGHT].
     */
    val AFTER_NIGHT: LocalTime = LocalTime.of(14, 0)
    const val SLEEP_HOURS = 7L

    /** When the sleep after the 야간 that ends on [date]'s morning ends, as [before] (the day before) says, or null ([ShiftClash.sleptUntil]). */
    private fun sleptUntil(date: LocalDate, before: Day?): LocalTime? {
        if (before == null || !before.work || before.date != date.minusDays(1)) return null
        val start = before.start ?: return null
        val end = before.end ?: return null
        return ShiftClash.sleptUntil(start, end)
    }

    /**
     * When to tell on the day off [date]: its morning, or the afternoon after a 야간 ([AFTER_NIGHT]). 2.0.0 [RVI-42] (검토 MEMO-21)
     * By the same sleep as the 겹침 경고: a night whose sleep runs past the morning (03~05시 퇴근 too) moves it to the afternoon; one
     * that leaves the morning (00:30 마감) keeps it.
     */
    fun offTime(date: LocalDate, before: Day?): LocalDateTime {
        val woke = sleptUntil(date, before)?.takeIf { it.isAfter(OFF_TIME) } ?: return date.atTime(OFF_TIME)
        return date.atTime(maxOf(woke, AFTER_NIGHT))
    }

    /** When to tell about [day]: an hour before its start, or the morning (the afternoon after a 야간, [before] the day before). */
    fun timeFor(day: Day, before: Day? = null): LocalDateTime = when {
        day.work && day.start != null -> day.date.atTime(day.start).minusMinutes(BEFORE_MINUTES)
        day.work -> day.date.atTime(WORK_NO_HOURS)
        else -> offTime(day.date, before)
    }

    /** "아침", or "오후" for a day off told after a 야간. */
    fun partOf(at: LocalDateTime): String = if (at.toLocalTime() < LocalTime.NOON) "아침" else "오후"

    private fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

    fun label(day: Day, at: LocalDateTime = timeFor(day)): String =
        if (day.work) "다음 ${day.name}(${md(day.date)}) 출근 1시간 전" else "다음 ${day.name}(${md(day.date)}) ${partOf(at)}"

    /** The name a day off with no kind of day goes by (a 일근's weekend, a 공휴일 off): "다음 쉬는 날(11/14) 아침". */
    const val REST_NAME = "쉬는 날"

    /**
     * 1.39 [TASK-39] PC 6차: a day off the user planned, a kind of day that is off (비번·휴무) or a day the repeating schedule
     * leaves empty (평일 일근's 토·일). A calendar with nothing scheduled (투자) has none, so nothing is offered there.
     */
    fun isRest(r: com.bamnat.core.schedule.ResolvedDay): Boolean =
        r.isRestDay && (r.shiftType != null || r.source != com.bamnat.core.schedule.DaySource.NONE)

    /**
     * [EASE-22] 1.39 [TASK-44] The schedule a repeating memo goes by: each day's kind (근무마다) and whether it is a day off (쉬는 날마다).
     */
    fun shiftOf(resolver: com.bamnat.core.schedule.ScheduleResolver): MemoRepeat.ShiftOf = object : MemoRepeat.ShiftOf {
        override fun on(date: LocalDate): Long? = resolver.shiftIdOn(date)
        override fun rest(date: LocalDate): Boolean = isRest(resolver.resolve(date))
        override fun hours(date: LocalDate): Pair<java.time.LocalTime, java.time.LocalTime>? =
            resolver.resolve(date).takeIf { it.isWorkDay }?.let { r -> r.start?.let { s -> r.end?.let { e -> s to e } } }
    }

    /** [r] as a [Day] for the times above: its kind of day, or [REST_NAME] for a planned day off with none; null for nothing planned. */
    fun of(r: com.bamnat.core.schedule.ResolvedDay): Day? {
        val type = r.shiftType
        return when {
            type != null -> Day(r.date, type.name, r.isWorkDay, r.start, r.end)
            isRest(r) -> Day(r.date, REST_NAME, false, null, null)
            else -> null
        }
    }

    /** [days] by date, for the day before each. */
    private fun beforeOf(days: List<Day>): (Day) -> Day? {
        val byDate = days.associateBy { it.date }
        return { d -> byDate[d.date.minusDays(1)] }
    }

    /** For the dialog: the next of each kind of shift ahead, and the next day off, soonest first, at most [max]. */
    fun suggestions(days: List<Day>, now: LocalDateTime, max: Int = 4): List<Suggestion> {
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        val before = beforeOf(days)
        for (d in days.sortedBy { it.date }) {
            if (d.name.isBlank()) continue
            val at = timeFor(d, before(d))
            if (!at.isAfter(now)) continue
            val key = if (d.work) "w:${d.name}" else "off"
            if (!seen.add(key)) continue
            out += Suggestion(at, label(d, at), d.name, d.date)
            if (out.size >= max) break
        }
        return out.sortedBy { it.at }
    }

    /** 2.0.0 [RVI-53] (검토 MEMO-19) Each kind of day's pattern, made once: [find] runs as one types. */
    private val byName = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    private val OFF_WORDS = listOf("쉬는 날", "쉬는날", "휴무 날", "휴무날", "휴무일", "비번 날", "비번날", "휴일에", "쉴 때")
    private val SUFFIX = "(?:\\s*근무)?\\s*(?:때|날|출근|들어가기\\s*전|전에|전|하는\\s*날|에)"

    /**
     * The time [text] means by a shift ("다음 야간 때", "야간 출근 전", "주간 날", "쉬는 날"), on [days] ahead; null when
     * it names none, or the shift is not on the calendar ahead. "다다음 야간" is the one after.
     */
    fun find(text: String, now: LocalDateTime, days: List<Day>): MemoTime.Found? = match(text, now, days)?.found

    /** 2.0.0 [RVI-76] (검토 U07a-11) What [find] found and where in [text] its words are, so a 한 줄 can take them out of its title. */
    data class Match(val found: MemoTime.Found, val range: IntRange)

    /** [find] with the words it read. */
    fun match(text: String, now: LocalDateTime, days: List<Day>): Match? {
        val before = beforeOf(days)
        val ahead = days.sortedBy { it.date }.filter { timeFor(it, before(it)).isAfter(now) }
        if (ahead.isEmpty()) return null
        var best: Pair<IntRange, MemoTime.Found>? = null
        val names = ahead.filter { it.name.isNotBlank() }.map { it.name }.distinct().sortedByDescending { it.length }
        for (name in names) {
            val re = byName.getOrPut(name) { Regex("(다다음|다음|담|이번)?\\s*${Regex.escape(name)}$SUFFIX") }
            val m = re.find(text) ?: continue
            // A bare name with no "다음" and no suffix is not asked ("야간 수당" is just a word); the regex needs the suffix.
            val second = m.groupValues[1] == "다다음"
            val list = ahead.filter { it.name == name }
            val day = (if (second) list.getOrNull(1) else list.firstOrNull()) ?: continue
            val at = timeFor(day, before(day))
            val found = MemoTime.Found(at, label(day, at).replaceFirst("다음 ", if (second) "다다음 " else "다음 "))
            if (best == null || m.range.first < best.first.first) best = m.range to found
        }
        if (best == null) {
            val word = OFF_WORDS.mapNotNull { w -> text.indexOf(w).takeIf { it >= 0 }?.let { it until it + w.length } }.minByOrNull { it.first }
            if (word != null) ahead.firstOrNull { !it.work }?.let { d ->
                val at = timeFor(d, before(d))
                best = word to MemoTime.Found(at, "다음 쉬는 날(${md(d.date)}) ${partOf(at)}")
            }
        }
        return best?.let { (range, found) -> Match(found, range) }
    }

    /**
     * [MEMO-06] The latest day before [date] that had the same shift as [date] (by name), among [past] days with a memo:
     * where the last 인수인계 of this kind of shift is.
     */
    fun lastSame(date: LocalDate, name: String?, past: List<Pair<LocalDate, String?>>): LocalDate? {
        if (name.isNullOrBlank()) return null
        return past.filter { it.first.isBefore(date) && it.second == name }.maxOfOrNull { it.first }
    }
}
