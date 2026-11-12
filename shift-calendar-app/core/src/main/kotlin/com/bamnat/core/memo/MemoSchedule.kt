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
data class MemoReminder(
    val at: LocalDateTime,
    val ring: Boolean,
    val notify: Boolean,
    /** 2.0.0 [RVIB-04] (검토 I07ac-01) Told by the work, not the clock ([at] is then the time it was saved for, a fallback). */
    val work: WorkAnchor? = null,
) {
    init {
        require(ring || notify) { "A schedule that neither rings nor notifies" }
    }
}

/**
 * 2.0.0 [RVIB-04] (검토 I07ac-01) 근무에 붙는 알림: "출근 1시간 전", "퇴근 30분 뒤", "야간 끝나고 일어나면". The time is read from each
 * day's work, so a changed shift needs nothing changed and a work past midnight is its own night's (MEMO-10). Other to-do apps do
 * not know the work, so they cannot. [minutes] from the work's start ([From.START]) or end ([From.END]); [From.WAKE] is a night's
 * end and the sleep after it ([minutes]), on nights only. Stored as its [code] ("S-60", "E30", "W420").
 */
data class WorkAnchor(val from: From, val minutes: Int) {
    enum class From(val letter: Char) { START('S'), END('E'), WAKE('W') }

    val code: String get() = "${from.letter}$minutes"

    /** "출근 1시간 전", "퇴근 30분 뒤", "출근할 때", "야간 끝나고 일어나면". */
    val label: String get() = when (from) {
        From.START -> if (minutes == 0) "출근할 때" else if (minutes < 0) "출근 ${span(-minutes)} 전" else "출근 ${span(minutes)} 뒤"
        From.END -> if (minutes == 0) "퇴근할 때" else if (minutes > 0) "퇴근 ${span(minutes)} 뒤" else "퇴근 ${span(-minutes)} 전"
        From.WAKE -> "야간 끝나고 일어나면"
    }

    /** Whether a day with [hours] (start to end; an end at or before the start is the next morning's) has a time for it. */
    fun fits(hours: Pair<LocalTime, LocalTime>?): Boolean = hours != null && (from != From.WAKE || !hours.second.isAfter(hours.first))

    /** When it is told for [day]'s work of [hours], to the minute; null when the day has none for it. */
    fun on(day: LocalDate, hours: Pair<LocalTime, LocalTime>?): LocalDateTime? {
        if (hours == null || !fits(hours)) return null
        val (start, end) = hours
        val night = !end.isAfter(start)
        val base = if (from == From.START) day.atTime(start) else (if (night) day.plusDays(1) else day).atTime(end)
        return base.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
    }

    companion object {
        /** The choices offered, in order: 출근 2시간·1시간·30분 전, 출근할 때, 퇴근할 때, 퇴근 30분·1시간 뒤. */
        val CHOICES = listOf(WorkAnchor(From.START, -120), WorkAnchor(From.START, -60), WorkAnchor(From.START, -30), WorkAnchor(From.START, 0),
            WorkAnchor(From.END, 0), WorkAnchor(From.END, 30), WorkAnchor(From.END, 60))

        fun parse(code: String?): WorkAnchor? {
            if (code.isNullOrEmpty()) return null
            val from = From.entries.firstOrNull { it.letter == code[0] } ?: return null
            val minutes = code.drop(1).toIntOrNull()?.takeIf { it in -24 * 60..24 * 60 } ?: return null
            return WorkAnchor(from, minutes)
        }

        private fun span(m: Int): String = when {
            m % 60 == 0 -> "${m / 60}시간"
            m < 60 -> "${m}분"
            else -> "${m / 60}시간 ${m % 60}분"
        }
    }
}

/**
 * [SCHED-01] Finds a time written in a memo ("내일 오후 3시 치과", "10월 20일 14:30 회의", "금요일 7시 반"),
 * so the editor can offer to make the memo a schedule. Only a clock time makes a schedule; a date
 * alone does not. A time already past is not offered.
 */
object MemoTime {
    data class Found(val at: LocalDateTime, val phrase: String) {
        /**
         * Stored when the user answers, so the same time is not asked about again. 2.0.0 [RVI-25] (검토 MEMO-07) The words read, not
         * the time they make today: '메모로 둘게요' on '3시' was asked again every day.
         */
        val key: String get() = "w:$phrase"

        /** Whether [asked] (the stored answer) answers this one: by its words, or by its time as answers were kept before 2.0.0. */
        fun answeredBy(asked: String?): Boolean = asked?.lines()?.any { it == key || it == at.toString() } == true
    }

    private const val MARKS = "오전|오후|아침|낮|저녁|밤|새벽"
    private val HOUR = Regex("($MARKS)?\\s*(\\d{1,2})\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?")
    private val CLOCK = Regex("(?:($MARKS)\\s*)?(?<![\\d:])(\\d{1,2})\\s*:\\s*([0-5]\\d)(?![\\d:])")
    /**
     * 2.0.0 [RVI-24] (검토 MEMO-20) Not inside a word: '내일배움카드', '오늘의집', '사내일정' are no dates. After it only a space, a sign,
     * a 오전·오후 word or a particle that ends there ('내일은', '오늘까지', '오늘의 할 일').
     */
    private val RELATIVE = Regex("(?<![가-힣])(오늘|내일|모레|글피)(?=$|[^가-힣]|(?:은|는|에|에는|까지|부터|도|의|중|이나)(?:$|[^가-힣])|$MARKS)")
    private val RELATIVE_WORDS = setOf("오늘", "내일", "모레", "글피")
    /** 2.0.0 [RVI-24] "3일 뒤", "10일 후": days from today (it was this month's 3일). */
    private val DAYS_LATER = Regex("(?<![\\d월/])(\\d{1,3})\\s*일\\s*(뒤|후)")
    private val MONTH_DAY = Regex("(?<!\\d)(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    /** 2.0.0 [RVI-24] "1/2씩" is a share, not 1월 2일. */
    private val SLASH = Regex("(?<![\\d/])(\\d{1,2})\\s*/\\s*(\\d{1,2})(?![\\d/])(?!\\s*씩)")
    private val WEEKDAY = Regex("(다다음\\s*주|다음\\s*주|담주|이번\\s*주)?\\s*([월화수목금토일])요일")
    private val DAY_ONLY = Regex("(?<![\\d월/])(\\d{1,2})\\s*일(?!\\s*(?:간|동안|째|뒤|후|전|만에))")

    /**
     * 2.0.0 [RVI-25] (검토 MEMO-07) '내일·모레·N일 뒤' are read from the memo's own day, or the day it was written, not from today:
     * "내일 3시 치과" written on 11/11 is 11/12 when opened on 11/12 too.
     */
    fun find(memo: Memo, now: LocalDateTime, zone: java.time.ZoneId = com.bamnat.core.time.Today.zone()): Found? = find(
        (sequenceOf(memo.title, memo.text) + memo.checklist.asSequence().map { it.text }).joinToString("\n"),
        now,
        memo.date,
        from = memo.date ?: memo.createdAtMillis.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
    )

    /**
     * The first clock time in [text] on its date: a date written next to it, else [memoDate], else the next such time.
     * [from]: the day relative words count from (today when null).
     */
    fun find(text: String, now: LocalDateTime, memoDate: LocalDate? = null, from: LocalDate? = null): Found? {
        val time = findTime(text) ?: return null
        val written = findDate(text, from ?: now.toLocalDate())
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
        /** PC 7차: the time had no 오전·오후 ("8시"), so [at] is a guess the window lets the user flip ([Half]). */
        val unmarked: Boolean = false,
        /**
         * 2.0.0 [RVI-76] (검토 U07a-03) The words named the day ("내일", "11/20", "금요일"): a line written in a calendar day's window
         * without one goes on that day, not on the next such time from now.
         */
        val written: Boolean = false,
    )

    /** PC 7차: 오전 or 오후, for a time written without one. */
    enum class Half { AM, PM }

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
    fun line(text: String, now: LocalDateTime, half: Half? = null): Line {
        val lines = text.trim().lines()
        val first = lines.firstOrNull().orEmpty().trim()
        val rest = lines.drop(1).joinToString("\n").trim()
        val today = now.toLocalDate()
        val written = findDate(first, today)
        val time = findTime(first)
        val again = REPEAT_WORDS.find(first)?.value
        // 2.0.0 [RVI-23] (검토 MEMO-14) '6시 기상' was 18:00 with no 오전·오후 chip: every 1~11시 without a mark is a guess to flip.
        val unmarked = time?.bareHour != null && time.nextDay == 0L
        val morning = MORNING.containsMatchIn(first)
        val at = time?.let { t ->
            val am = t.bareHour?.let { LocalTime.of(it % 12, t.time.minute) }
            // PC 7차: [half] picked in the window says which 8시 it is; '기상·출근' says the morning.
            val clock = when {
                am == null -> t.time
                half == Half.PM -> am.plusHours(12)
                half == Half.AM || morning -> am
                else -> t.time
            }
            val base = (written?.first ?: today).atTime(clock).plusDays(t.nextDay)
            when {
                written != null || base.isAfter(now) -> base
                // "8시 약 먹기" typed at 19시 with no 오전·저녁: tonight's 8시, not tomorrow morning's. Not for a repeat (PC 7차: '평일마다
                // 8시 출근 도장' at 13:10 read 20:00): a 8시 every day is the morning one unless 오후 is picked.
                unmarked && half == null && !morning && again == null && clock.hour < 12 && base.plusHours(12).isAfter(now) -> base.plusHours(12)
                else -> base.plusDays(1)
            }
        }
        var title = first
        listOfNotNull(again, written?.second, time?.phrase).forEach { phrase ->
            // 2.0.0 [RVI-24] '사내일정 내일 3시': the 내일 read, not the one inside 사내일정.
            title = if (phrase in RELATIVE_WORDS) RELATIVE.replaceFirst(title, " ") else title.replaceFirst(phrase, " ")
        }
        title = title.replace(Regex("\\s+"), " ").trim().trim(',', '.', '·', '-').trim()
        if (title.isEmpty()) title = first
        val read = listOfNotNull(again, written?.second, time?.phrase).joinToString(" ")
        val kind = again?.let(::repeatOf) ?: MemoRepeat.Kind.NONE
        // 2.0.0 [RVI-21] (검토 MEMO-15) '평일마다 8시' typed on a Saturday starts on Monday, not Sunday 08:00.
        val skip = if (kind == MemoRepeat.Kind.WEEKDAYS) { d: LocalDate -> generateSequence(d) { it.plusDays(1) }.first(MemoRepeat::isWeekday) } else { d: LocalDate -> d }
        val day = (written?.first ?: at?.toLocalDate())?.let(skip)
        val moved = at?.let { a -> day?.let { a.plusDays(java.time.temporal.ChronoUnit.DAYS.between(a.toLocalDate(), it)) } ?: a }
        return Line(title, rest, day, moved, read, kind, unmarked, written = written != null)
    }

    /** [bareHour]: the hour as written when it had no 오전·오후 and is 1~11 (2.0.0 [RVI-23]), else null. */
    private class Time(val time: LocalTime, val nextDay: Long, val phrase: String, val bareHour: Int? = null)

    /** 2.0.0 [RVI-23] (검토 MEMO-14) Words that put a bare 1~11시 in the morning. */
    private val MORNING = Regex("기상|일어나|출근|아침|조식|등교|새벽")

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
        return Time(LocalTime.of(hour24, minute), nextDay, first.value.trim(), bareHour = h.takeIf { mark == null && it in 1..11 })
    }

    /** The hour on a 24-hour clock, and 1 when it falls after midnight ("밤 12시"): one reading with the calendar's ([RVH-06]). */
    private fun hourOf(mark: String?, h: Int, guessAfternoon: Boolean): Pair<Int, Long>? =
        com.bamnat.core.time.KoreanHour.of(mark, h, guessAfternoon)

    private fun findDate(text: String, today: LocalDate): Pair<LocalDate, String>? {
        RELATIVE.find(text)?.let { m ->
            val days = when (m.value) { "오늘" -> 0L; "내일" -> 1L; "모레" -> 2L; else -> 3L }
            return today.plusDays(days) to m.value
        }
        DAYS_LATER.find(text)?.let { m ->
            val n = m.groupValues[1].toLong()
            if (n in 1..366) return today.plusDays(n) to m.value.trim()
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

    /**
     * 2.0.0 [RVI-19] (검토 MEMO-03·04) A memo due at a minute and the day it is due for: a repeating memo's 미루기 past midnight is still
     * its first day's, and a '야간마다 01:00' is its night's ([RVI-20]). 끝내기 finishes that day.
     */
    data class Due(val memo: Memo, val day: LocalDate)

    /**
     * The earliest schedule strictly after [after], or null. [extraDays]: the day each 미루기 in [extra] was for, so one put off
     * from a day finished since (in the app, on the widget) rings no more (2.0.0 [RVI-19], 검토 MEMO-04).
     */
    fun next(
        memos: List<Memo>, after: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
        extraDays: Map<Long, LocalDate> = emptyMap(),
    ): LocalDateTime? {
        // 1.39.1 [R391-06] (검토 MEMO-01) A finished one-off memo rings no more, nor does its 미룬 time.
        val live = memos.filter { !it.inTrash && !finished(it) }
        val own = live.mapNotNull { nextOf(it, after, shiftsOf(it)) }
        val byId = live.associateBy { it.id }
        val put = extra.filter { (id, at) -> byId[id]?.let { m -> !putOffDone(m, extraDays[id]) } == true && at.isAfter(after) }.values
        return (own + put).minOrNull()
    }

    /** [memo]'s next schedule strictly after [after]: its own, or for a repeating one the next day it comes back on. */
    fun nextOf(memo: Memo, after: LocalDateTime, shifts: MemoRepeat.ShiftOf? = null): LocalDateTime? {
        val r = memo.reminder ?: return null
        r.work?.let { return workNext(memo, r, it, after, shifts) }
        if (!repeats(memo)) return r.at.takeIf { it.isAfter(after) && !finished(memo) }
        val time = r.at.toLocalTime().withSecond(0).withNano(0)
        // A day early: a night's 01:00 rings on the calendar day after its night (2.0.0 [RVI-20]).
        var day = maxOf(after.toLocalDate().minusDays(1), r.at.toLocalDate())
        repeat(LOOK_DAYS) {
            if (MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day)) {
                val at = ringsAt(memo, day, time, shifts)
                if (at.isAfter(after)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * 2.0.0 [RVIB-04] A memo told by the work: each of its days' work read for the time. Without the schedule read, the time it was
     * saved for.
     */
    private fun workNext(memo: Memo, r: MemoReminder, a: WorkAnchor, after: LocalDateTime, shifts: MemoRepeat.ShiftOf?): LocalDateTime? {
        if (shifts == null || !repeats(memo)) {
            if (finished(memo)) return null
            val day = memo.date ?: r.at.toLocalDate()
            return (shifts?.let { a.on(day, it.hours(day)) } ?: r.at).takeIf { it.isAfter(after) }
        }
        // Two days early: 출근 2시간 전 of a work starting after midnight, or a night's 퇴근 뒤, is told the calendar day after or before.
        var day = maxOf(after.toLocalDate().minusDays(2), memo.date ?: r.at.toLocalDate())
        repeat(LOOK_DAYS) {
            if (MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day)) {
                val at = a.on(day, shifts.hours(day))
                if (at != null && at.isAfter(after)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * 2.0.0 [RVI-20] (검토 MEMO-10) When a repeating [memo] at [time] rings for its [day]: on that day, except a 근무마다 time inside a
     * night's hours after midnight (22:00~07:00, 01:00), which is that night's, the calendar day after.
     */
    fun ringsAt(memo: Memo, day: LocalDate, time: LocalTime, shifts: MemoRepeat.ShiftOf?): LocalDateTime {
        if (MemoRepeat.kindOf(memo.repeat) == MemoRepeat.Kind.SHIFT) {
            val (start, end) = shifts?.hours(day) ?: return day.atTime(time)
            if (!end.isAfter(start) && time.isBefore(start) && !time.isAfter(end)) return day.plusDays(1).atTime(time)
        }
        return day.atTime(time)
    }

    /** The memos whose schedule is at [at], to the minute. */
    fun dueAt(
        memos: List<Memo>, at: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
        extraDays: Map<Long, LocalDate> = emptyMap(),
    ): List<Memo> = dueOn(memos, at, shiftsOf, extra, extraDays).map { it.memo }

    /** 2.0.0 [RVI-19] [dueAt] with the day each is due for. */
    fun dueOn(
        memos: List<Memo>, at: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
        extraDays: Map<Long, LocalDate> = emptyMap(),
    ): List<Due> {
        val minute = at.withSecond(0).withNano(0)
        return memos.filter { !it.inTrash && !finished(it) }.mapNotNull { m ->
            dayAt(m, minute, shiftsOf(m))?.let { Due(m, it) }
                ?: extra[m.id]?.takeIf { it.withSecond(0).withNano(0) == minute && !putOffDone(m, extraDays[m.id]) }
                    ?.let { Due(m, extraDays[m.id] ?: m.reminder?.at?.toLocalDate()?.takeUnless { repeats(m) } ?: minute.toLocalDate()) }
        }
    }

    /**
     * 2.0.0 [RVI-22] (검토 MEMO-02) Every schedule after [from] up to [to] (a late alarm, the phone off, the clock moved on), each
     * with its time, earliest first; at most [max].
     */
    fun between(
        memos: List<Memo>, from: LocalDateTime, to: LocalDateTime,
        shiftsOf: (Memo) -> MemoRepeat.ShiftOf? = { null }, extra: Map<Long, LocalDateTime> = emptyMap(),
        extraDays: Map<Long, LocalDate> = emptyMap(), max: Int = 50,
    ): List<Pair<LocalDateTime, Due>> {
        val out = ArrayList<Pair<LocalDateTime, Due>>()
        var t = from
        while (out.size < max) {
            val at = next(memos, t, shiftsOf, extra, extraDays) ?: break
            if (at.isAfter(to)) break
            dueOn(memos, at, shiftsOf, extra, extraDays).forEach { out += at to it }
            t = at
        }
        return out.take(max)
    }

    private fun repeats(memo: Memo) = memo.date != null && MemoRepeat.kindOf(memo.repeat) != MemoRepeat.Kind.NONE

    /** 1.39.1 [R391-06] A one-off memo marked 끝냄 (a repeating one is finished day by day, see [Memo.isDoneOn]). */
    private fun finished(memo: Memo) = !repeats(memo) && memo.doneAtMillis != null

    /** 2.0.0 [RVI-19] A repeating memo's 미루기 for [day], finished since: it rings no more. */
    private fun putOffDone(memo: Memo, day: LocalDate?): Boolean = repeats(memo) && day != null && memo.isDoneOn(day)

    /** The day [memo] is due for at [minute] by its own schedule, or null. */
    private fun dayAt(memo: Memo, minute: LocalDateTime, shifts: MemoRepeat.ShiftOf?): LocalDate? {
        val r = memo.reminder ?: return null
        // 2.0.0 [RVIB-04] Told by the work: the day whose work puts it at [minute] (from the day before to two days back, or the next).
        r.work?.let { a ->
            if (shifts == null || !repeats(memo)) {
                val day = memo.date ?: r.at.toLocalDate()
                val at = shifts?.let { a.on(day, it.hours(day)) } ?: r.at.withSecond(0).withNano(0)
                return day.takeIf { at == minute }
            }
            val start = memo.date ?: r.at.toLocalDate()
            return (-1..2).map { minute.toLocalDate().minusDays(it.toLong()) }.firstOrNull { day ->
                !day.isBefore(start) && MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day) && a.on(day, shifts.hours(day)) == minute
            }
        }
        val own = r.at.withSecond(0).withNano(0)
        if (!repeats(memo)) return own.toLocalDate().takeIf { own == minute }
        if (own.toLocalTime() != minute.toLocalTime()) return null
        // Its day, or (2.0.0 [RVI-20]) the night before for a 근무마다 time after midnight.
        return listOf(minute.toLocalDate(), minute.toLocalDate().minusDays(1)).firstOrNull { day ->
            !day.isBefore(own.toLocalDate()) && MemoRepeat.occursOn(memo, day, shifts) && !memo.isDoneOn(day) &&
                ringsAt(memo, day, own.toLocalTime(), shifts) == minute
        }
    }
}
