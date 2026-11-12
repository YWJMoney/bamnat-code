package com.bamnat.core.sleep

/**
 * 1.39 [FLOW-09] 수면 화면 머리 (흐름·디자인 계획 3-5): one number, the last 7 days' average, and one line under it against the
 * target ("목표보다 34분 부족 · 7일 중 5일 기록"). Replaces the three same-size tiles. No score (밤낮 원칙 "점수 없음").
 */
object SleepHead {
    /** [tone] is the meaning colour's name ("good", "caution" or null), as in DayStory. */
    data class Head(val value: String, val caption: String, val tone: String?)

    /** Within this of the target reads as 목표만큼. */
    const val EVEN_MINUTES = 10L

    fun of(week: SleepDebt, target: Int): Head {
        if (week.days == 0) return Head("—", "잠을 적으면 최근 7일 평균이 여기 나와요 · 목표 ${SleepInsights.hours(target.toLong())}", null)
        val avg = week.averageMinutes
        val diff = target - avg
        val vs = when {
            diff >= EVEN_MINUTES -> "목표보다 ${SleepInsights.hours(diff)} 부족"
            diff <= -EVEN_MINUTES -> "목표보다 ${SleepInsights.hours(-diff)} 더 잤어요"
            else -> "목표만큼 잤어요"
        }
        val tone = when {
            diff >= SleepInsights.SHORT_MINUTES -> "caution"
            diff < EVEN_MINUTES -> "good"
            else -> null
        }
        // 2.0.0 [RVI-40] (검토 ALM-25) '기록': '적음' read as '부족'.
        return Head(SleepInsights.hours(avg), "$vs · ${SleepDebt.WINDOW_DAYS}일 중 ${week.days}일 기록", tone)
    }
}
