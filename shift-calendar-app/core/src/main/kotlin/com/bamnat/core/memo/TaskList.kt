package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.39 [TASK-07] 할 일 위젯 (할 일 앱 조사, 사용자 2026-11-07 "할 일 위젯 먼저"): what the home-screen to-do list shows, worked out
 * here so the widget, the studio's sample and the tests read the same list.
 *
 *  - **늦음**: a memo that does not repeat, dated in the last [LATE_DAYS] days, not finished, that was meant to be done — it rings or
 *    notifies, or it is a checklist with lines left. A plain dated note ("회식") is a diary line, not a to-do, so it never nags.
 *  - **오늘**: every memo that falls on today (its date, or a repeat that comes back today), finished ones at the end.
 *  - **고정**: a memo with no date that is pinned and not finished ("장보기").
 *
 * Each row carries the day it is finished on ([Row.day]): today for a repeating one, its own date otherwise.
 */
object TaskList {
    enum class Kind { LATE, TODAY, PINNED }

    data class Row(val memo: Memo, val kind: Kind, val day: LocalDate?, val done: Boolean, val time: LocalTime?) {
        /** The words on the row: the title, else the first line (a checklist's lines joined). */
        val text: String get() = memo.title.ifBlank { memo.preview }.lineSequence().firstOrNull()?.trim().orEmpty().take(TEXT_CHARS)

        /** "2/5" for a checklist with lines, else null. */
        val count: String? get() = memo.itemCount.takeIf { memo.isChecklist && it > 0 }?.let { "${memo.checkedCount}/$it" }
    }

    const val LATE_DAYS = 7L
    const val TEXT_CHARS = 40

    fun of(memos: List<Memo>, today: LocalDate, shifts: (Memo) -> MemoRepeat.ShiftOf? = { null }): List<Row> {
        val live = memos.filter { !it.inTrash && !it.isBlank }
        val late = live.filter { m ->
            val d = m.date ?: return@filter false
            m.repeat.isEmpty() && d.isBefore(today) && !d.isBefore(today.minusDays(LATE_DAYS)) && !m.isDone && meant(m)
        }.sortedWith(compareBy<Memo> { it.date }.thenBy { it.reminder?.at })
            .map { Row(it, Kind.LATE, it.date, false, it.reminder?.at?.toLocalTime()) }
        val todays = live.filter { m -> MemoRepeat.occursOn(m, today, shifts(m)) }.map { m ->
            val time = m.reminder?.at?.takeIf { it.toLocalDate() == today || m.repeat.isNotEmpty() }?.toLocalTime()
            Row(m, Kind.TODAY, if (m.repeat.isNotEmpty()) today else m.date, m.isDoneOn(if (m.repeat.isNotEmpty()) today else m.date), time)
        }.sortedWith(compareBy<Row> { it.done }.thenBy { it.time == null }.thenBy { it.time }.thenByDescending { it.memo.pinned }.thenBy { it.memo.createdAtMillis })
        val pinned = live.filter { it.date == null && it.pinned && !it.isDone }
            .sortedByDescending { it.updatedAtMillis }
            .map { Row(it, Kind.PINNED, null, false, null) }
        return late + todays + pinned
    }

    /** A to-do rather than a note: it was set to ring or notify, or it is a checklist with lines left. */
    private fun meant(m: Memo): Boolean = m.reminder != null || (m.isChecklist && m.itemCount > m.checkedCount)

    /** "오늘 할 일 3개 남음" and the like, for the widget's head. */
    fun headline(rows: List<Row>): String {
        val left = rows.count { !it.done }
        val late = rows.count { it.kind == Kind.LATE }
        return when {
            rows.isEmpty() -> "할 일"
            left == 0 -> "오늘 할 일을 다 끝냈어요"
            late > 0 -> "할 일 ${left}개 · 늦은 것 ${late}개"
            else -> "할 일 ${left}개 남았어요"
        }
    }
}
