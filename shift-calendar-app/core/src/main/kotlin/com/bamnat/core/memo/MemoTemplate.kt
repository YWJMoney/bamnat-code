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
        val said = TemplateAsk.lines(asks, answers)
        // [CVW-10] A list keeps only its own items (자유도 점검 F-56: answers ticked in 완료 read as done tasks). Its answers are kept
        // whole as a note above the list, not in the title, which a long answer cut and then lost ([J135-07] F-63·F-65).
        val title = listOfNotNull(emoji, title).joinToString(" ")
        val day = when {
            repeat.startsWith("W") -> (date ?: today).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(repeat.drop(1).toInt())))
            repeat.startsWith("M") -> nextDayOfMonth(date ?: today, repeat.drop(1).toInt())
            else -> picked(answers, today) ?: date
        }
        return base.copy(
            title = title,
            // [CVW-10] The answers as "칸: 값" lines before the template's own text; a list keeps them at its top, ticked.
            body = if (check.isEmpty()) listOfNotNull(said.ifEmpty { null }, body.ifEmpty { null }).joinToString("\n") else said,
            isChecklist = check.isNotEmpty(),
            checklist = check.map { ChecklistItem(it) },
            date = day,
            repeat = if (day != null && repeat.isNotEmpty()) repeat else "",
        )
    }

    /**
     * [TAB-03] PC 확인 bf62e966 ("고른 날짜 11/12와 메모 날짜 11/11이 따로 놀아 가계부엔 11/11로"): a 날짜 칸 named for the memo's own day
     * (날짜, 일자, 날) is that day, so the memo, the 달력 and '가계부에 넣기' all go by what the user picked.
     */
    private fun picked(answers: Map<String, String>, today: LocalDate): LocalDate? = asks.withIndex().firstNotNullOfOrNull { (i, a) ->
        if (a.type != "date" || a.label.trim() !in DAY_LABELS) return@firstNotNullOfOrNull null
        val v = (answers["#$i"] ?: answers[a.label])?.trim()?.takeIf { it.isNotEmpty() } ?: return@firstNotNullOfOrNull null
        MemoProps.read(a.label, v, today.year).date
    }

    companion object {
        const val MAX = 40
        private val DAY_LABELS = setOf("날짜", "일자", "날", "작업일", "근무일", "영업일", "매매일")

        /**
         * [TAB-03] 1.36 이 메모를 틀로 저장 (PC 확인 bad7d860 "눌러도 반응이 없어요", 사용성 제안 "틀 이름을 물어 주세요"): [memo] as a
         * template named [name]. Each "칸: 값" line whose name is in [asked] becomes an 입력 칸 of that property's type (금액, 날짜, 별점…)
         * and leaves the body, so starting from it asks for a fresh value instead of copying today's; the other lines stay as written.
         */
        fun fromMemo(memo: Memo, id: String, name: String, asked: Set<String>, choices: Map<String, List<String>> = emptyMap()): MemoTemplate {
            val props = MemoProps.of(memo).associateBy { it.label }
            val asks = ArrayList<TemplateAsk>()
            val body = if (memo.isChecklist) "" else memo.text.lineSequence().filter { line ->
                val label = MemoProps.labelOfLine(line)
                val p = label?.let { props[it] }
                if (p != null && label in asked && asks.none { it.label == label }) {
                    val picks = choices[label].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MemoProps.MAX_CHOICES)
                    asks += when {
                        // [TAB-01] N9: a 칸 the user gave words to pick from asks as 고르기.
                        picks.size >= 2 -> TemplateAsk(label, "pick", picks)
                        p.type == MemoProps.Type.NUM && MemoProps.isMoneyLabel(label) -> TemplateAsk(label, "money")
                        else -> TemplateAsk(label, askType(p.type))
                    }
                    false
                } else true
            }.joinToString("\n").trim().take(300)
            return MemoTemplate(
                id = id,
                title = name.trim().ifBlank { memo.title.ifBlank { (memo.checklist.firstOrNull()?.text ?: memo.text.lineSequence().firstOrNull()).orEmpty() } }.take(30),
                check = if (memo.isChecklist) memo.checklist.map { it.text }.filter { it.isNotBlank() }.take(20) else emptyList(),
                body = body,
                repeat = memo.repeat.takeIf { it.startsWith("W") || it.startsWith("M") }.orEmpty(),
                asks = asks.take(MAX_ASKS),
            )
        }

        const val MAX_ASKS = 8

        private fun askType(t: MemoProps.Type): String = when (t) {
            MemoProps.Type.NUM -> "num"; MemoProps.Type.MONEY -> "money"; MemoProps.Type.STAR -> "star"; MemoProps.Type.DATE -> "date"
            MemoProps.Type.TIME -> "time"; MemoProps.Type.CHECK -> "check"; MemoProps.Type.TEXT -> "text"; MemoProps.Type.PICK -> "pick"
        }

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
        /** [TAB-03] 1.36 금액·날짜·시각·체크 added to 글·숫자·별점·고르기, each read back as that property type ([MemoProps]). */
        val TYPES = listOf("text", "num", "star", "pick", "money", "date", "time", "check")

        /**
         * The answered ones, in the template's order, one "label: 값" a line; 별점 as ★★★☆☆. An answer is found by its place ("#0", "#1"…)
         * first, so two 칸 with the same name keep their own answers (자유도 점검 F-55), and by its name as before.
         */
        fun lines(asks: List<TemplateAsk>, answers: Map<String, String>): String = asks.withIndex().mapNotNull { (i, a) ->
            val v = (answers["#$i"] ?: answers[a.label])?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val shown = when (a.type) {
                "star" -> v.toIntOrNull()?.coerceIn(1, 5)?.let { n -> "★".repeat(n) + "☆".repeat(5 - n) } ?: return@mapNotNull null
                // [J135-06] F-62: a 숫자 칸 writes a number or nothing.
                "num" -> v.replace(",", "").takeIf { it.toDoubleOrNull() != null } ?: return@mapNotNull null
                // [TAB-03] 금액: whole won with commas and 원, so the 표 adds it up and '가계부에 넣기' reads it.
                "money" -> v.replace(",", "").replace("원", "").trim().toDoubleOrNull()?.let { "%,d원".format(it.toLong()) } ?: return@mapNotNull null
                "date" -> MemoProps.read(a.label, v).takeIf { it.type == MemoProps.Type.DATE }?.raw ?: return@mapNotNull null
                "time" -> MemoProps.read(a.label, if (v.length in 3..4 && v.all { it.isDigit() }) v.dropLast(2) + ":" + v.takeLast(2) else v).takeIf { it.type == MemoProps.Type.TIME }?.raw ?: return@mapNotNull null
                "check" -> when (v) { "1", "예", "true" -> "예"; "0", "아니오", "false" -> "아니오"; else -> return@mapNotNull null }
                else -> v
            }
            "${a.label}: $shown"
        }.joinToString("\n")
    }
}
