package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-05] (검토 I07ac-02, 대기열 TASK-22) 누르는 횟수 0의 정리. A memo notice (told, not rung) that comes due at work waits and
 * comes with the others of that work in one card when it ends (퇴근 때 한 번), and the to-dos left from before come, without blame,
 * as one card on the next day off's morning ("지난 할 일 3개 · 모두 끝내기 / 오늘로 / 지우기"). A ringing schedule rings on time,
 * always, and so does one set by the work itself (출근 1시간 전).
 */
object MemoBacklog {
    /** How many days ahead the next day off is looked for. */
    const val LOOK_DAYS = 21L

    /** The most memo titles the card's one line names. */
    private const val NAMED = 3

    /** A schedule that may wait for the end of work: a notice only, not one told by the work itself ([WorkAnchor]). */
    fun waits(memo: Memo): Boolean = memo.reminder?.let { it.notify && !it.ring && it.work == null } == true

    /**
     * The end of the work [at] falls in, from each day's hours ([hours]; that day's and the day before's, for a night past midnight),
     * or null when [at] is not at work.
     */
    fun workEnd(at: LocalDateTime, hours: (LocalDate) -> Pair<LocalTime, LocalTime>?): LocalDateTime? {
        for (d in listOf(at.toLocalDate().minusDays(1), at.toLocalDate())) {
            val (start, end) = hours(d) ?: continue
            val from = d.atTime(start)
            val to = if (end.isAfter(start)) d.atTime(end) else d.plusDays(1).atTime(end)
            if (!at.isBefore(from) && at.isBefore(to)) return to
        }
        return null
    }

    /**
     * When the next day off's card comes: the morning ([ShiftMemoTime.offTime], the afternoon after a 야간) of the first day off from
     * [now] on whose morning is still ahead; null with none in [LOOK_DAYS] (a calendar with no days off, 투자).
     */
    fun restMorning(now: LocalDateTime, isRest: (LocalDate) -> Boolean, day: (LocalDate) -> ShiftMemoTime.Day?): LocalDateTime? =
        (0L until LOOK_DAYS).asSequence().map { now.toLocalDate().plusDays(it) }.filter(isRest)
            .map { ShiftMemoTime.offTime(it, day(it.minusDays(1))) }.firstOrNull { it.isAfter(now) }

    /** What is left from before [today]: the 늦음 week's to-dos and the older ones ([TaskList.old]), oldest first. */
    fun left(memos: List<Memo>, today: LocalDate): List<Memo> =
        (TaskList.old(memos, today) + TaskList.of(memos, today).filter { it.kind == TaskList.Kind.LATE }.map { it.memo }).distinctBy { it.id }

    /** "지난 할 일 3개" over "쉬는 날이에요. 천천히 해도 돼요 · 약 받기 · 장보기 · 택배 외 2개". */
    fun restLines(left: List<Memo>): Pair<String, String> =
        "지난 할 일 ${left.size}개" to (listOf("쉬는 날이에요. 천천히 해도 돼요") + named(left.map { title(it) })).joinToString(" · ")

    /** "근무 중에 온 일정 3개". */
    fun workTitle(n: Int): String = "근무 중에 온 일정 ${n}개"

    /** "약 받기 · 장보기 · 택배 외 2개". */
    fun named(titles: List<String>): String =
        titles.take(NAMED).joinToString(" · ") + if (titles.size > NAMED) " 외 ${titles.size - NAMED}개" else ""

    private fun title(m: Memo): String = m.title.ifBlank { m.preview }.lineSequence().firstOrNull()?.trim().orEmpty().take(TaskList.TEXT_CHARS)
}
