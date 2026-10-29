package com.bamnat.core.memo

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * [RCP-11] 1.34 메모 틀 (새 기능): a memo to start from — 제목, 이모지, 체크 목록 or 본문, and a 반복 ("M25" 매달 25일, "W2" 매주
 * 화요일). The user keeps their own and a 나눔 판 recipe adds more; a template is never a memo until the user starts one from it.
 */
data class MemoTemplate(
    val id: String,
    val title: String,
    val emoji: String? = null,
    val check: List<String> = emptyList(),
    val body: String = "",
    val repeat: String = "",
    /** The recipe code it came with, or null for the user's own. */
    val from: String? = null,
) {
    /** A new memo from this template on [date] (or none): a repeat moves the date to its next day from [date] or [today]. */
    fun start(date: LocalDate?, today: LocalDate, base: Memo = Memo()): Memo {
        val title = listOfNotNull(emoji, title).joinToString(" ")
        val day = when {
            repeat.startsWith("W") -> (date ?: today).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(repeat.drop(1).toInt())))
            repeat.startsWith("M") -> nextDayOfMonth(date ?: today, repeat.drop(1).toInt())
            else -> date
        }
        return base.copy(
            title = title,
            body = if (check.isEmpty()) body else "",
            isChecklist = check.isNotEmpty(),
            checklist = check.map { ChecklistItem(it) },
            date = day,
            repeat = if (day != null && repeat.isNotEmpty()) repeat else "",
        )
    }

    companion object {
        const val MAX = 40

        /** The next day [d] of a month on or after [from]; a short month uses its last day. */
        fun nextDayOfMonth(from: LocalDate, d: Int): LocalDate {
            val here = from.withDayOfMonth(minOf(d, from.lengthOfMonth()))
            if (!here.isBefore(from)) return here
            val next = from.plusMonths(1).withDayOfMonth(1)
            return next.withDayOfMonth(minOf(d, next.lengthOfMonth()))
        }
    }
}
