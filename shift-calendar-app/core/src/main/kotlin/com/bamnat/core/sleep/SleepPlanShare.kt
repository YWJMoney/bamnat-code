package com.bamnat.core.sleep

import java.time.LocalDateTime

/**
 * 1.39 G5-N4 (사용자 결정 1차 13번 "3.12 문장 넣고, 기본은 꺼짐"): the sleep the app planned around the work, as a family's link
 * carries it when the sender ticks it. Only from and to, never what was really slept or heard; the receiver's app says one line
 * so a call can wait ("오늘 09:00~16:00 잘 계획이에요"). Off by default, per link, from 처리방침 3.12's date (`sleep.plan_share`).
 */
object SleepPlanShare {
    data class Window(val from: LocalDateTime, val to: LocalDateTime)

    /** Two weeks of plans in a link, at most [MAX] of them. */
    const val DAYS = 14L
    const val MAX = 24
    /** A planned sleep longer than this is not sent (the server refuses it too). */
    const val MAX_HOURS = 16L
    /** How far ahead the receiver's line looks for the next one. */
    const val AHEAD_HOURS = 24L

    /** The planned sleeps not over yet at [now], in order, as many as a link takes. */
    fun windows(sleeps: List<SleepSchedule.Sleep>, now: LocalDateTime): List<Window> =
        sleeps.asSequence()
            .filter { it.to.isAfter(now) && it.to.isAfter(it.from) && !it.to.isAfter(it.from.plusHours(MAX_HOURS)) }
            .map { Window(it.from, it.to) }
            .distinct().sortedBy { it.from }.take(MAX).toList()

    /**
     * The receiver's one line: inside a plan "지금은 잘 계획 시간이에요 · 16:00까지", a plan starting within [AHEAD_HOURS]
     * "오늘 09:00~16:00 잘 계획이에요"; null otherwise.
     */
    fun line(windows: List<Window>, now: LocalDateTime): String? {
        windows.firstOrNull { !now.isBefore(it.from) && now.isBefore(it.to) }?.let { return "지금은 잘 계획 시간이에요 · ${hm(it.to)}까지" }
        val next = windows.filter { it.from.isAfter(now) && !it.from.isAfter(now.plusHours(AHEAD_HOURS)) }.minByOrNull { it.from } ?: return null
        return "${NightLine.day(now, next.from)} ${hm(next.from)}~${hm(next.to)} 잘 계획이에요"
    }

    private fun hm(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
}
