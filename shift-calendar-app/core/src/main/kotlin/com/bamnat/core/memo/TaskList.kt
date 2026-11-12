package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.39 [TASK-07] 할 일 위젯 (할 일 앱 조사, 사용자 2026-11-07 "할 일 위젯 먼저"): what the home-screen to-do list shows, worked out
 * here so the widget, the studio's sample and the tests read the same list.
 *
 *  - **늦음**: a memo that does not repeat, due in the last [LATE_DAYS] days (its 일정's day, else its date), not finished, that was
 *    meant to be done — it rings or notifies, or it has lines left to tick. A plain dated note ("회식") is a diary line, not a to-do.
 *  - **오늘**: every memo due today (its 일정 or date, or a repeat that comes back today).
 *  - **고정**: a memo with no date that is pinned and not finished ("장보기"); then what was finished today, at the very end.
 *
 * Each row carries the day it is finished on ([Row.day]): today for a repeating one, its own date otherwise.
 */
object TaskList {
    enum class Kind { LATE, TODAY, PINNED }

    data class Row(val memo: Memo, val kind: Kind, val day: LocalDate?, val done: Boolean, val time: LocalTime?) {
        /** The words on the row: the title, else the first line (a checklist's lines joined). */
        val text: String get() = memo.title.ifBlank { memo.preview }.lineSequence().firstOrNull()?.trim().orEmpty().take(TEXT_CHARS)

        /** "2/5" for a checklist's lines or the ☐ lines of a note, else null. */
        val count: String? get() = boxes(memo).takeIf { it.second > 0 }?.let { (done, all) -> "$done/$all" }
    }

    const val LATE_DAYS = 7L
    const val TEXT_CHARS = 40

    fun of(memos: List<Memo>, today: LocalDate, shifts: (Memo) -> MemoRepeat.ShiftOf? = { null }): List<Row> {
        val live = memos.filter { !it.inTrash && !it.isBlank }
        // 1.39 PC 2afb3a76 (날짜 11/6·일정 11/7 20:00 메모가 11/7 19시에 '늦음 · 11/6'): a memo that does not repeat is due on its 일정's
        // day when it has one, else on its date.
        val late = live.filter { m ->
            val d = dueDay(m) ?: return@filter false
            m.repeat.isEmpty() && d.isBefore(today) && !d.isBefore(today.minusDays(LATE_DAYS)) && !m.isDone && meant(m)
        }.sortedWith(compareBy<Memo> { dueDay(it) }.thenBy { it.reminder?.at })
            .map { Row(it, Kind.LATE, dueDay(it), false, it.reminder?.at?.toLocalTime()) }
        val todays = live.filter { m -> if (m.repeat.isNotEmpty()) MemoRepeat.occursOn(m, today, shifts(m)) else dueDay(m) == today }.map { m ->
            val time = m.reminder?.at?.takeIf { it.toLocalDate() == today || m.repeat.isNotEmpty() }?.toLocalTime()
            Row(m, Kind.TODAY, if (m.repeat.isNotEmpty()) today else dueDay(m), m.isDoneOn(if (m.repeat.isNotEmpty()) today else m.date), time)
        }.sortedWith(compareBy<Row> { it.time == null }.thenBy { it.time }.thenByDescending { it.memo.pinned }.thenBy { it.memo.createdAtMillis })
        val shown = (late.map { it.memo.id } + todays.map { it.memo.id }).toSet()
        val pinned = live.filter { it.date == null && it.pinned && !it.isDone && it.id !in shown }
            .sortedByDescending { it.updatedAtMillis }
            .map { Row(it, Kind.PINNED, null, false, null) }
        // PC 2afb3a76 (끝낸 줄이 고정 '장보기'보다 위): what is finished goes to the very end, under the pinned ones.
        val (done, open) = todays.partition { it.done }
        return late + open + pinned + done
    }

    /**
     * 2.0.0 [RVI-53] (검토 MEMO-19) What the 할 일 위젯 shows of [memos] on [today], and what the memos that follow the 근무표 hold
     * (read with the schedule only when drawn): the same value means the widget looks the same, so a save of a memo's words
     * does not draw it again.
     */
    fun looks(memos: List<Memo>, today: LocalDate): List<Any?> =
        of(memos, today).map { listOf(it.memo.id, it.kind, it.day, it.done, it.time, it.text, it.count) } +
            memos.filter { MemoRepeat.followsSchedule(it.repeat) && !it.inTrash }
                .map { listOf(it.id, it.title.ifBlank { it.preview.take(TEXT_CHARS) }, it.repeat, it.skips, it.doneDays, it.reminder?.at) }

    /** The day a memo that does not repeat is due: its 일정's day when it has one, else its date. */
    private fun dueDay(m: Memo): LocalDate? = m.reminder?.at?.toLocalDate() ?: m.date

    /**
     * A to-do rather than a note: it was set to ring or notify, or it has lines left to tick — a checklist's, or ☐ lines written in
     * a note with the editor's '☐ 체크' (PC 2afb3a76: '이삿짐' with '☐ 상자 사기' was left off).
     */
    private fun meant(m: Memo): Boolean = m.reminder != null || boxes(m).let { (done, all) -> all > done }

    /** Ticked and all lines to tick: a checklist's items, else the ☐/☑ (and "- [ ]"/"- [x]") lines of the note. */
    fun boxes(m: Memo): Pair<Int, Int> =
        if (m.isChecklist) m.checkedCount to m.itemCount
        else m.text.lineSequence().mapNotNull { l ->
            when { OPEN_BOX.containsMatchIn(l) -> false; TICKED_BOX.containsMatchIn(l) -> true; else -> null }
        }.toList().let { marks -> marks.count { it } to marks.size }

    private val OPEN_BOX = Regex("^\\s*(☐ |- \\[ ] )\\S")
    private val TICKED_BOX = Regex("^\\s*(☑ |- \\[[xX]] )\\S")

    /**
     * 1.39 [TASK-39] Late as the widget shows it: from a day before, or today's with its time already gone (오늘 09:21 at 09:35, PC 6차).
     * Today's stays on today's list, in red; tomorrow it moves up to 늦음 with its day.
     */
    fun overdue(row: Row, now: java.time.LocalDateTime): Boolean = !row.done && when (row.kind) {
        Kind.LATE -> true
        Kind.TODAY -> row.time != null && !(row.day ?: now.toLocalDate()).atTime(row.time).isAfter(now)
        Kind.PINNED -> false
    }

    /** "오늘 할 일 3개 남음" and the like, for the widget's head; [now] counts today's gone times as late too. */
    fun headline(rows: List<Row>, now: java.time.LocalDateTime? = null): String {
        val left = rows.count { !it.done }
        val late = rows.count { r -> if (now != null) overdue(r, now) else r.kind == Kind.LATE && !r.done }
        return when {
            rows.isEmpty() -> "할 일"
            left == 0 -> "오늘 할 일을 다 끝냈어요"
            late > 0 -> "할 일 ${left}개 · 늦은 것 ${late}개"
            else -> "할 일 ${left}개 남았어요"
        }
    }
}
