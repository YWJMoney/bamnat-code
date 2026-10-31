package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 1.36 840 (수익 2판 2-3, 사용자 2026-10-31 "이탈 0 · 확실한 보상"): the only times a 프로 offer card shows, outside the 프로 menu.
 * 1. The same bundle opened by an ad for the second time in 30 days (once per bundle a month).
 * 2. Two or more bundles opened by ads this month (once a month): "이번 달 광고 3편 = 21일치".
 * 3. Used on 5 of the last 7 days (once a month).
 * 4. Back after 30 days away: no offer, the most used bundle opens 7 days free ([FeatureLease.Why.BACK]), no discount.
 * (체험 종료 7일 전 is TrialConsent's own ask and notice, kept as it is.)
 * Every card has 닫기 and '7일간 보지 않기' ([SNOOZE_DAYS]); none within [GAP_DAYS] of the last; never with 프로, before the launch,
 * right after the app opens or while the user is writing (the app checks those).
 */
object ProOfferTimes {
    enum class Time(val key: String) { SECOND_AD("second"), TWO_BUNDLES("two"), HEAVY_USE("heavy"), BACK("back") }

    data class Due(val time: Time, val key: String, val bundle: FeatureLease.Bundle? = null, val ads: Int = 0, val days: Long = 0)

    const val SNOOZE_DAYS = 7L
    const val GAP_DAYS = 7L
    const val AWAY_DAYS = 30L
    const val HEAVY_DAYS = 5

    /**
     * What to offer today, or null. [usedDays] the days the app was opened (today included), [lastBefore] the last day it was
     * opened before today, [shown] the keys already shown, [snoozedUntil] the end of a '7일간 보지 않기', [lastShown] the last card's day.
     */
    fun due(leases: FeatureLease.State, now: Instant, today: LocalDate, zone: ZoneId, usedDays: Set<LocalDate>, lastBefore: LocalDate?,
            shown: Set<String>, snoozedUntil: LocalDate?, lastShown: LocalDate?): Due? {
        val month = "${today.year}-${today.monthValue.toString().padStart(2, '0')}"
        // 4. Back after a month away: a gift, not an offer, so neither the snooze nor the gap holds it back.
        if (lastBefore != null && ChronoUnit.DAYS.between(lastBefore, today) >= AWAY_DAYS) {
            val key = "back:$today"
            val b = mostUsed(leases)
            if (key !in shown && !leases.open(b, now)) return Due(Time.BACK, key, b)
        }
        if (snoozedUntil != null && today < snoozedUntil) return null
        if (lastShown != null && ChronoUnit.DAYS.between(lastShown, today) < GAP_DAYS) return null
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val ads = leases.grants.filter { it.why != FeatureLease.Why.FIRST && it.why != FeatureLease.Why.BACK }
        // 1. The second ad for one bundle within 30 days, just now.
        val last = ads.lastOrNull()
        if (last != null && Duration.between(last.at, now) < Duration.ofDays(1)) {
            val n = FeatureLease.adsIn30Days(leases, last.bundle, now)
            val key = "second:${last.bundle.key}:$month"
            if (n >= 2 && key !in shown) return Due(Time.SECOND_AD, key, last.bundle, n)
        }
        // 2. Two bundles or more by ads this month.
        val thisMonth = ads.filter { it.at >= monthStart }
        if (thisMonth.map { it.bundle }.toSet().size >= 2 && "two:$month" !in shown) {
            return Due(Time.TWO_BUNDLES, "two:$month", ads = thisMonth.size, days = thisMonth.sumOf { Duration.between(it.at, it.until).toDays() })
        }
        // 3. Five of the last seven days.
        val week = (0L until 7L).map { today.minusDays(it) }
        if (week.count { it in usedDays } >= HEAVY_DAYS && "heavy:$month" !in shown) return Due(Time.HEAVY_USE, "heavy:$month")
        return null
    }

    /** The bundle opened most (first weeks and ads alike); 급여 when none was. */
    fun mostUsed(s: FeatureLease.State): FeatureLease.Bundle =
        s.grants.groupingBy { it.bundle }.eachCount().maxByOrNull { it.value }?.key ?: FeatureLease.Bundle.PAY
}
