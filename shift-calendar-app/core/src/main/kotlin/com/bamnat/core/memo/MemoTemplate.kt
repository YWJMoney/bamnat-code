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
    /** [CVW-10] 1.35 입력 칸: what starting from this template asks first. */
    val asks: List<TemplateAsk> = emptyList(),
) {
    /** A new memo from this template on [date] (or none): a repeat moves the date to its next day from [date] or [today]. */
    fun start(date: LocalDate?, today: LocalDate, base: Memo = Memo(), answers: Map<String, String> = emptyMap()): Memo {
        val title = listOfNotNull(emoji, title).joinToString(" ")
        val day = when {
            repeat.startsWith("W") -> (date ?: today).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(repeat.drop(1).toInt())))
            repeat.startsWith("M") -> nextDayOfMonth(date ?: today, repeat.drop(1).toInt())
            else -> date
        }
        return base.copy(
            title = title,
            // [CVW-10] The answers as "칸: 값" lines before the template's own text; a list keeps them at its top, ticked.
            body = if (check.isEmpty()) listOfNotNull(TemplateAsk.lines(asks, answers).ifEmpty { null }, body.ifEmpty { null }).joinToString("\n") else "",
            isChecklist = check.isNotEmpty(),
            checklist = TemplateAsk.lines(asks, answers).lines().filter { it.isNotEmpty() }.map { ChecklistItem(it, checked = true) } +
                check.map { ChecklistItem(it) },
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

/**
 * [CVW-10] 1.35 메모 틀의 입력 칸 (사용자 2026-10-30 "입력하는 부분등 모든 부분에서 자유도"): one thing a template asks when a memo starts
 * from it — free text, a number, 별점 1~5 or one of [choices] — written into the memo as "label: 값".
 */
data class TemplateAsk(val label: String, val type: String = "text", val choices: List<String> = emptyList()) {
    companion object {
        val TYPES = listOf("text", "num", "star", "pick")

        /** The answered ones, in the template's order, one "label: 값" a line; 별점 as ★★★☆☆. */
        fun lines(asks: List<TemplateAsk>, answers: Map<String, String>): String = asks.mapNotNull { a ->
            val v = answers[a.label]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val shown = if (a.type == "star") v.toIntOrNull()?.coerceIn(1, 5)?.let { n -> "★".repeat(n) + "☆".repeat(5 - n) } ?: return@mapNotNull null else v
            "${a.label}: $shown"
        }.joinToString("\n")
    }
}
