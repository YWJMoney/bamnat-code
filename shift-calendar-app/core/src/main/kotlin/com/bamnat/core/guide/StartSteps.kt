package com.bamnat.core.guide

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * [GUIDE-20] 1.31 P2 (상세리뷰 P2, 사용자 원칙 "사용자 편의 최우선"): the first week, one card over the calendar with the three
 * things that make a shift app worth keeping (알람 맞추기 → 근무 넣기 → 회사 고르기), and nothing else asking for attention
 * meanwhile (the question card and tips wait). It goes when all are done, after [DAYS] days, or when the user says 그만 보기.
 */
object StartSteps {
    const val DAYS = 7L

    enum class Step(val label: String, val doneLabel: String, val action: String) {
        ALARM("근무 알람 켜기", "알람이 켜져 있어요", "켜기"),
        SHIFTS("근무 넣기", "근무가 들어 있어요", "넣기"),
        COMPANY("우리 회사 고르기", "회사를 골랐어요", "고르기"),
    }

    /** [J133-71] [label]: the step in the calendar's kind of work's words (현장: 일정 알람, 알바: 일하는 날). */
    data class Row(val step: Step, val done: Boolean, val label: String = step.label)

    /** [J133-71] 1.33: what each step is called for [style]. */
    fun label(step: Step, style: com.bamnat.core.work.WorkStyle?): String = when (step) {
        Step.ALARM -> when (style) {
            com.bamnat.core.work.WorkStyle.FIELD -> "일정 알람 켜기"
            com.bamnat.core.work.WorkStyle.BUSINESS -> "영업 알람 켜기"
            else -> step.label
        }
        Step.SHIFTS -> if (style == com.bamnat.core.work.WorkStyle.HOURLY) "일하는 날 넣기" else step.label
        Step.COMPANY -> step.label
    }

    /**
     * The rows to show today, or null for no card: [installed] more than [DAYS] days ago (or unknown), [dismissed], or every
     * row done. [companyApplies]: the company server is there and the kind of work has companies (not 투자·자영업).
     */
    fun rows(
        installed: LocalDate?, today: LocalDate, dismissed: Boolean,
        alarmsOn: Boolean, hasShifts: Boolean, companyLinked: Boolean, companyApplies: Boolean,
        style: com.bamnat.core.work.WorkStyle? = null,
    ): List<Row>? {
        // [J133-71] 1.33: a 투자 calendar has no work alarm, shifts or company; its own first step is the trade card.
        if (dismissed || installed == null || style == com.bamnat.core.work.WorkStyle.INVEST) return null
        val age = ChronoUnit.DAYS.between(installed, today)
        if (age < 0 || age >= DAYS) return null
        val rows = listOfNotNull(
            Row(Step.ALARM, alarmsOn, label(Step.ALARM, style)),
            Row(Step.SHIFTS, hasShifts, label(Step.SHIFTS, style)),
            Row(Step.COMPANY, companyLinked).takeIf { companyApplies },
        )
        return rows.takeIf { r -> r.any { !it.done } }
    }

    /** "시작 3단계 중 1개 했어요". */
    fun title(rows: List<Row>): String = "시작 ${rows.size}단계 중 ${rows.count { it.done }}개 했어요"
}
