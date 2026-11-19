package com.bamnat.core.plaza

import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 2.0.0 [RVN-20] 검토 U10a-02·I10ab-03 (빈 광장 시작 꾸러미): the week's topic of a quiet plaza when the 운영팀 has not put one
 * (village.js `plaza_topics`, which always wins). Words that fit every kind of work (교대·일근·알바·현장·가게·투자), no 근무 낱말, so a
 * plaza is never asked about a 야간 it does not have (조건 13). The week starts on Monday, as the server's [topicWeek].
 */
object StarterTopics {
    val ALL = listOf(
        "요즘 나만의 작은 즐거움",
        "쉬는 날 꼭 하는 것 하나",
        "힘이 되는 간식이나 음료",
        "처음 시작할 때 들은 좋은 조언",
        "이번 주 가장 고마웠던 순간",
        "요즘 고민 한 가지",
        "출근길·퇴근길에 듣는 것",
        "몸을 지키는 나만의 방법",
    )

    private val MONDAY_ZERO: LocalDate = LocalDate.of(2024, 1, 1) // a Monday

    /**
     * The topic of the week [date] is in, said for [style]'s plaza. 2.0.0 [RVN-24] 검토 PC-211·조건 13: '출근길·퇴근길에 듣는 것' was every
     * plaza's, though a 현장 has no 출근, a shop's 사장님 no 출근·쉬는 날 and an investor no 출근 ([com.bamnat.core.work.TypeWords] forbids them; StarterTopicsTest checks every kind); each kind's
     * own plaza gets the same topic in its own words. 교대·일근·알바 keep ALL as it is.
     */
    fun of(date: LocalDate, style: WorkStyle? = null): String {
        val weeks = Math.floorDiv(ChronoUnit.DAYS.between(MONDAY_ZERO, date), 7L)
        return forKind(ALL[Math.floorMod(weeks, ALL.size.toLong()).toInt()], style)
    }

    /** [topic] of [ALL] in the words of [style]; the same one when its words are already right. */
    fun forKind(topic: String, style: WorkStyle?): String = when (style) {
        WorkStyle.FIELD -> topic.replace("출근길·퇴근길에", "현장 오가는 길에")
        WorkStyle.BUSINESS -> topic.replace("출근길·퇴근길에", "가게 오가는 길에").replace("쉬는 날", "휴무일에")
        WorkStyle.INVEST -> topic.replace("출근길·퇴근길에", "오가는 길에")
        else -> topic
    }
}
