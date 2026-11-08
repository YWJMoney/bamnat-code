package com.bamnat.core.memo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * [SCHED-01] A memo that is a plan: when it is, and how it is brought up. At least one of [ring]
 * (an alarm that rings like the shift alarm) and [notify] (a message in the notification shade).
 */
data class MemoReminder(val at: LocalDateTime, val ring: Boolean, val notify: Boolean) {
    init {
        require(ring || notify) { "A schedule that neither rings nor notifies" }
    }
}

/**
 * [SCHED-01] Finds a time written in a memo ("내일 오후 3시 치과", "10월 20일 14:30 회의", "금요일 7시 반"),
 * so the editor can offer to make the memo a schedule. Only a clock time makes a schedule; a date
 * alone does not. A time already past is not offered.
 */
object MemoTime {
    data class Found(val at: LocalDateTime, val phrase: String) {
        /** Stored when the user answers, so the same time is not asked about again. */
        val key: String get() = at.toString()
    }

    private const val MARKS = "오전|오후|아침|낮|저녁|밤|새벽"
    private val HOUR = Regex("($MARKS)?\\s*(\\d{1,2})\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?")
    private val CLOCK = Regex("(?:($MARKS)\\s*)?(?<![\\d:])(\\d{1,2})\\s*:\\s*([0-5]\\d)(?![\\d:])")
    private val RELATIVE = Regex("(오늘|내일|모레|글피)")
    private val MONTH_DAY = Regex("(?<!\\d)(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    private val SLASH = Regex("(?<![\\d/])(\\d{1,2})\\s*/\\s*(\\d{1,2})(?![\\d/])")
    private val WEEKDAY = Regex("(다다음\\s*주|다음\\s*주|담주|이번\\s*주)?\\s*([월화수목금토일])요일")
    private val DAY_ONLY = Regex("(?<![\\d월/])(\\d{1,2})\\s*일(?!\\s*(?:간|동안|째))")

    fun find(memo: Memo, now: LocalDateTime): Found? = find(
        (sequenceOf(memo.title, memo.text) + memo.checklist.asSequence().map { it.text }).joinToString("\n"),
        now,
        memo.date,
    )

    /** The first clock time in [text] on its date: a date written next to it, else [memoDate], else the next such time. */
    fun find(text: String, now: LocalDateTime, memoDate: LocalDate? = null): Found? {
        val time = findTime(text) ?: return null
        val written = findDate(text, now.toLocalDate())
        val at = when {
            written != null -> written.first.atTime(time.time).plusDays(time.nextDay)
            memoDate != null -> memoDate.atTime(time.time).plusDays(time.nextDay)
            else -> now.toLocalDate().atTime(time.time).plusDays(time.nextDay).let { if (it.isAfter(now)) it else it.plusDays(1) }
        }
        if (!at.isAfter(now)) return null
        val phrase = listOfNotNull(written?.second, time.phrase).joinToString(" ")
        return Found(at, phrase)
    }

    /**
     * 1.38 [FLOW-08] The clock time written in [memo] as it falls on [day], past or not (the day's story shows what was, too):
     * a repeating memo on each of its days, a memo with a date written next to the time on that date. Null for no time.
     */
    fun timeOn(memo: Memo, day: LocalDate): LocalDateTime? {
        val text = (sequenceOf(memo.title, memo.text) + memo.checklist.asSequence().map { it.text }).joinToString("\n")
        val time = findTime(text) ?: return null
        val date = if (memo.repeat.isNotEmpty()) day else findDate(text, memo.date ?: day)?.first ?: day
        return date.atTime(time.time).plusDays(time.nextDay)
    }

    /** 1.39 [TASK-32][TASK-38] One line typed or shared, read: the title left once the day and time words are taken out, and the rest. */
    data class Line(
        val title: String, val rest: String, val date: LocalDate?, val at: LocalDateTime?, val read: String,
        /** 1.39 [TASK-44] "매일", "평일마다", "쉬는 날마다" read as the memo's repeat, out of the title. */
        val repeat: MemoRepeat.Kind = MemoRepeat.Kind.NONE,
    )

    /** 1.39 [TASK-44] PC 6차: '매일 오전 9시 24분 …' left '매일' in the title. The repeat words the line reads. */
    private val REPEAT_WORDS = Regex("(매일|날마다|평일마다|평일에는|쉬는\\s*날마다|휴무\\s*날마다|비번\\s*날마다)")

    private fun repeatOf(word: String): MemoRepeat.Kind = when {
        word.startsWith("평일") -> MemoRepeat.Kind.WEEKDAYS
        word == "매일" || word == "날마다" -> MemoRepeat.Kind.DAY
        else -> MemoRepeat.Kind.REST
    }

    /**
     * 1.39 [TASK-32][TASK-38] The 한 줄 입력 window's reading of [text] ("내일 저녁 6시 치과"): the day and time it names, taken out of the
     * title ("치과") so the title says only the thing (할 일 앱 조사 15-2: that app keeps the words in the title). A time with no day is
     * the next such time; a day with no time is that day. The first line is the title, the other lines the body ([Line.rest]).
     */
    fun line(text: String, now: LocalDateTime): Line {
        val lines = text.trim().lines()
        val first = lines.firstOrNull().orEmpty().trim()
        val rest = lines.drop(1).joinToString("\n").trim()
        val today = now.toLocalDate()
        val written = findDate(first, today)
        val time = findTime(first)
        val at = time?.let { t ->
            val base = (written?.first ?: today).atTime(t.time).plusDays(t.nextDay)
            // "8시 약 먹기" typed at 19시 with no 오전·저녁: tonight's 8시, not tomorrow morning's.
            val unmarked = Regex(MARKS).find(t.phrase) == null && t.time.hour in 1..11 && t.nextDay == 0L
            when {
                written != null || base.isAfter(now) -> base
                unmarked && base.plusHours(12).isAfter(now) -> base.plusHours(12)
                else -> base.plusDays(1)
            }
        }
        val again = REPEAT_WORDS.find(first)?.value
        var title = first
        listOfNotNull(again, written?.second, time?.phrase).forEach { title = title.replaceFirst(it, " ") }
        title = title.replace(Regex("\\s+"), " ").trim().trim(',', '.', '·', '-').trim()
        if (title.isEmpty()) title = first
        val read = listOfNotNull(again, written?.second, time?.phrase).joinToString(" ")
        return Line(title, rest, written?.first ?: at?.toLocalDate(), at, read, again?.let(::repeatOf) ?: MemoRepeat.Kind.NONE)
    }

    private class Time(val time: LocalTime, val nextDay: Long, val phrase: String)

    private fun findTime(text: String): Time? {
        val clock = CLOCK.find(text)
        val hour = HOUR.find(text)
        val first = listOfNotNull(clock, hour).minByOrNull { it.range.first } ?: return null
        val mark = first.groupValues[1].ifEmpty { null }
        val h = first.groupValues[2].toInt()
        val minute = when {
            first === clock -> first.groupValues[3].toInt()
            first.groupValues[4].isNotEmpty() -> 30
            first.groupValues[3].isNotEmpty() -> first.groupValues[3].toInt()
            else -> 0
        }
        if (minute > 59) return null
        // A written 24-hour clock ("14:30") is taken as written; "3시" with no mark is most often the afternoon.
        val (hour24, nextDay) = hourOf(mark, h, guessAfternoon = first === hour) ?: return null
        return Time(LocalTime.of(hour24, minute), nextDay, first.value.trim())
    }

    /** The hour on a 24-hour clock, and 1 when it falls after midnight ("밤 12시"). */
    private fun hourOf(mark: String?, h: Int, guessAfternoon: Boolean): Pair<Int, Long>? {
        if (h > 24) return null
        if (h == 24) return 0 to 1L
        return when (mark) {
            "오후", "저녁" -> (if (h in 1..11) h + 12 else h) to 0L
            "밤" -> when (h) {
                12 -> 0 to 1L
                in 6..11 -> h + 12 to 0L
                else -> h to 0L
            }
            "낮" -> (if (h in 1..6) h + 12 else h) to 0L
            "오전", "아침", "새벽" -> if (h > 12) null else (if (h == 12) 0 else h) to 0L
            else -> (if (guessAfternoon && h in 1..6) h + 12 else h) to 0L
        }
    }

    private fun findDate(text: String, today: LocalDate): Pair<LocalDate, String>? {
        RELATIVE.find(text)?.let { m ->
            val days = when (m.value) { "오늘" -> 0L; "내일" -> 1L; "모레" -> 2L; else -> 3L }
            return today.plusDays(days) to m.value
        }
        (MONTH_DAY.find(text) ?: SLASH.find(text))?.let { m ->
            val date = dateOf(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) ?: return@let
            return (if (date.isBefore(today)) date.plusYears(1) else date) to m.value.trim()
        }
        WEEKDAY.find(text)?.let { m ->
            val day = DayOfWeek.entries["월화수목금토일".indexOf(m.groupValues[2])]
            val which = m.groupValues[1].replace(" ", "")
            // A week from Sunday, as a Korean wall calendar shows it: on a Sunday, "다음주 월요일" is eight days on.
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).with(TemporalAdjusters.nextOrSame(day))
            val date = when (which) {
                "다음주", "담주" -> thisWeek.plusWeeks(1)
                "다다음주" -> thisWeek.plusWeeks(2)
                "이번주" -> thisWeek
                else -> today.with(TemporalAdjusters.nextOrSame(day))
            }
            return date to m.value.trim()
        }
        DAY_ONLY.find(text)?.let { m ->
            val d = m.groupValues[1].toInt()
            val date = dateOf(today.year, today.monthValue, d)
                ?.let { if (it.isBefore(today)) dateOf(it.plusMonths(1).year, it.plusMonths(1).monthValue, d) else it }
                ?: return@let
            return date to m.value.trim()
        }
        return null
    }

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

/**
 * [SCHED-01] Which memo schedules come next, for the one system alarm kept for them.
 *
 * 1.39 [TASK-44] PC 6차 (매일 반복 알림): a repeating memo's schedule comes back at its time of day on every day the memo does, from
 * the schedule's own day on, except a day it was finished (끝내기). [shiftsOf] reads the calendar for 근무마다·쉬는 날마다. [extra] holds
 * a repeating memo's one-off 미루기 (memo id → time), which leaves its time of day as it is.
 */
object MemoSchedules {
    /** How far ahead a repeating schedule is looked for: a year and a lunar month. */
    const val LOOK_DAYS = 400

    /** The earliest schedule strictly after [after], or null. */
    fun next(
        memos: List<Memo>, after: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
    ): LocalDateTime? {
        val live = memos.filter { !it.inTrash }
        val own = live.mapNotNull { nextOf(it, after, shiftsOf(it)) }
        val ids = live.mapTo(HashSet()) { it.id }
        val put = extra.filter { (id, at) -> id in ids && at.isAfter(after) }.values
        return (own + put).minOrNull()
    }

    /** [memo]'s next schedule strictly after [after]: its own, or for a repeating one the next day it comes back on. */
    fun nextOf(memo: Memo, after: LocalDateTime, shifts: MemoRepeat.ShiftOf? = null): LocalDateTime? {
        val r = memo.reminder ?: return null
        if (!repeats(memo)) return r.at.takeIf { it.isAfter(after) }
        val time = r.at.toLocalTime().withSecond(0).withNano(0)
        var day = maxOf(after.toLocalDate(), r.at.toLocalDate())
        repeat(LOOK_DAYS) {
            if (MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day)) {
                val at = day.atTime(time)
                if (at.isAfter(after)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /** The memos whose schedule is at [at], to the minute. */
    fun dueAt(
        memos: List<Memo>, at: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
    ): List<Memo> {
        val minute = at.withSecond(0).withNano(0)
        return memos.filter { !it.inTrash && (isAt(it, minute, shiftsOf(it)) || extra[it.id]?.withSecond(0)?.withNano(0) == minute) }
    }

    private fun repeats(memo: Memo) = memo.date != null && MemoRepeat.kindOf(memo.repeat) != MemoRepeat.Kind.NONE

    private fun isAt(memo: Memo, minute: LocalDateTime, shifts: MemoRepeat.ShiftOf?): Boolean {
        val r = memo.reminder ?: return false
        val own = r.at.withSecond(0).withNano(0)
        if (!repeats(memo)) return own == minute
        val day = minute.toLocalDate()
        return own.toLocalTime() == minute.toLocalTime() && !day.isBefore(own.toLocalDate()) &&
            MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day)
    }
}
