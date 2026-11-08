package com.bamnat.core.stats

/**
 * 1.39 [STAT2-02] 진입·머묾·회복: on a day the app opens for the first time that day, which [UsageEvent]s of the LIFE_ kind it is.
 * Only these counts leave the phone with 사용 통계 (깃발 `stats.life`, 처리방침 3.12); the install day and how long it was away do not.
 *
 * - [UsageEvent.LIFE_NEW]: opened on the day it was installed.
 * - [UsageEvent.LIFE_D1], [UsageEvent.LIFE_D7], [UsageEvent.LIFE_D30]: opened exactly 1, 7 or 30 days after the install day
 *   (머묾: the server divides them by the LIFE_NEW of that many days before).
 * - [UsageEvent.LIFE_BACK7], [UsageEvent.LIFE_BACK30]: the last open before today was 7~29, or 30 or more, days ago (회복).
 *
 * Days are epoch days in the phone's time zone; null when not known (no install day, never opened before).
 */
object LifeMarks {
    val KEEP_DAYS = listOf(1L, 7L, 30L)

    fun of(installDay: Long?, lastOpenDay: Long?, today: Long): List<UsageEvent> {
        if (lastOpenDay != null && lastOpenDay >= today) return emptyList()
        val out = ArrayList<UsageEvent>(2)
        if (installDay != null && installDay <= today) {
            when (today - installDay) {
                0L -> out += UsageEvent.LIFE_NEW
                1L -> out += UsageEvent.LIFE_D1
                7L -> out += UsageEvent.LIFE_D7
                30L -> out += UsageEvent.LIFE_D30
            }
        }
        if (lastOpenDay != null) {
            val away = today - lastOpenDay
            if (away >= 30) out += UsageEvent.LIFE_BACK30 else if (away >= 7) out += UsageEvent.LIFE_BACK7
        }
        return out
    }
}
