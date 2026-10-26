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
    /** One day of the calendar ahead. [start] is null on a day off or a work day without hours. */
    data class Day(val date: LocalDate, val name: String, val work: Boolean, val start: LocalTime?)

    data class Suggestion(val at: LocalDateTime, val label: String, val name: String, val date: LocalDate)

    const val BEFORE_MINUTES = 60L
    val OFF_TIME: LocalTime = LocalTime.of(9, 0)
    private val WORK_NO_HOURS: LocalTime = LocalTime.of(8, 0)

    /** When to tell about [day]: an hour before its start, or the morning. */
    fun timeFor(day: Day): LocalDateTime = when {
        day.work && day.start != null -> day.date.atTime(day.start).minusMinutes(BEFORE_MINUTES)
        day.work -> day.date.atTime(WORK_NO_HOURS)
        else -> day.date.atTime(OFF_TIME)
    }

    private fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

    fun label(day: Day): String =
        if (day.work) "다음 ${day.name}(${md(day.date)}) 출근 1시간 전" else "다음 ${day.name}(${md(day.date)}) 아침"

    /** For the dialog: the next of each kind of shift ahead, and the next day off, soonest first, at most [max]. */
    fun suggestions(days: List<Day>, now: LocalDateTime, max: Int = 4): List<Suggestion> {
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        for (d in days.sortedBy { it.date }) {
            if (d.name.isBlank()) continue
            val at = timeFor(d)
            if (!at.isAfter(now)) continue
            val key = if (d.work) "w:${d.name}" else "off"
            if (!seen.add(key)) continue
            out += Suggestion(at, label(d), d.name, d.date)
            if (out.size >= max) break
        }
        return out.sortedBy { it.at }
    }

    private val OFF_WORDS = listOf("쉬는 날", "쉬는날", "휴무 날", "휴무날", "휴무일", "비번 날", "비번날", "휴일에", "쉴 때")
    private val SUFFIX = "(?:\\s*근무)?\\s*(?:때|날|출근|들어가기\\s*전|전에|전|하는\\s*날|에)"

    /**
     * The time [text] means by a shift ("다음 야간 때", "야간 출근 전", "주간 날", "쉬는 날"), on [days] ahead; null when
     * it names none, or the shift is not on the calendar ahead. "다다음 야간" is the one after.
     */
    fun find(text: String, now: LocalDateTime, days: List<Day>): MemoTime.Found? {
        val ahead = days.sortedBy { it.date }.filter { timeFor(it).isAfter(now) }
        if (ahead.isEmpty()) return null
        var best: Pair<Int, MemoTime.Found>? = null
        val names = ahead.filter { it.name.isNotBlank() }.map { it.name }.distinct().sortedByDescending { it.length }
        for (name in names) {
            val re = Regex("(다다음|다음|담|이번)?\\s*${Regex.escape(name)}$SUFFIX")
            val m = re.find(text) ?: continue
            // A bare name with no "다음" and no suffix is not asked ("야간 수당" is just a word); the regex needs the suffix.
            val second = m.groupValues[1] == "다다음"
            val list = ahead.filter { it.name == name }
            val day = (if (second) list.getOrNull(1) else list.firstOrNull()) ?: continue
            val found = MemoTime.Found(timeFor(day), label(day).replaceFirst("다음 ", if (second) "다다음 " else "다음 "))
            if (best == null || m.range.first < best.first) best = m.range.first to found
        }
        if (best == null) {
            val i = OFF_WORDS.mapNotNull { w -> text.indexOf(w).takeIf { it >= 0 } }.minOrNull()
            if (i != null) ahead.firstOrNull { !it.work }?.let { d ->
                best = i to MemoTime.Found(timeFor(d), "다음 쉬는 날(${md(d.date)}) 아침")
            }
        }
        return best?.second
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
