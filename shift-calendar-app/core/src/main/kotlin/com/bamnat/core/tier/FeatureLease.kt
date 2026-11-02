package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 1.36 839 묶음 7일권 (사용자 2026-10-31 수익 2판 B안 "사용자가 불편함을 느끼지 않고 확실히 보상받는다는 느낌", 조사
 * `수익설계-2판-B안-재검토.md` 2-1). The pro features people use now and then come in four bundles. Each bundle's first use
 * opens it for [FIRST_DAYS], quietly; after that one rewarded ad opens it for [AD_DAYS], at most [ADS_PER_DAY] ads a day in
 * all, and an ad that does not come opens it all the same ([Why.NO_AD]): nobody is kept from a feature by an ad. 프로 has
 * every bundle always. What was made while a bundle was open stays (only making new is locked). The theme wardrobe keeps
 * its own lease ([com.bamnat.core.recipe.ThemeLease], 30 days).
 *
 * The bundle of a feature is named by the feature's name, so core needs no app types.
 */
object FeatureLease {
    enum class Bundle(val key: String, val label: String, val features: Set<String>, val gives: String) {
        PAY("pay", "급여", setOf("PAYSLIP_MATCH", "PAY_RULE_IMPORT", "PAY_BULK_EDIT", "TAX_FORECAST", "LEAVE_PAYOUT", "PAY_REPORT", "WORK_COMPARE"),
            "명세서 대조·규칙 자동 채우기·세후 예상·연차수당·급여 리포트·지난달 비교"),
        MONEY("money", "돈 통계", setOf("MONEY_STATS"), "지난달 비교·분류별 지출·올해 흐름"),
        PRINT("print", "인쇄", setOf("ROSTER_PDF"), "근무표 PDF(월·년)"),
        ALARM("alarm", "알람 미션", setOf("SURE_ALARM"), "끄기 미션·일어났나요 확인"),
        ;

        companion object {
            fun of(feature: String): Bundle? = entries.firstOrNull { feature in it.features }
            fun byKey(key: String?): Bundle? = entries.firstOrNull { it.key == key }
        }
    }

    const val FIRST_DAYS = 7L
    const val AD_DAYS = 7L
    const val ADS_PER_DAY = 2
    /** "3일 남음" is said quietly from this many days before the end. */
    const val HEADS_UP_DAYS = 3L
    /**
     * Turning the ad down this many times in a row hides that bundle's ad: for [FIRST_QUIET_DAYS] the first time, [QUIET_DAYS] after
     * (1.36 PC P23: "하루에 세 번 닫았다고 30일은 길어요").
     */
    const val DECLINES = 3
    const val FIRST_QUIET_DAYS = 7L
    const val QUIET_DAYS = 30L

    enum class Why(val key: String, val words: String) {
        FIRST("first", "처음 7일 무료"),
        AD("ad", "광고 보고"),
        NO_AD("no_ad", "광고가 안 와서 그냥"),
        /** 840: back after a month away, the bundle used most opens again (no discount, no ad). */
        BACK("back", "다시 와서 7일 무료"),
        ;

        companion object { fun byKey(key: String?): Why? = entries.firstOrNull { it.key == key } }
    }

    /** One time a bundle was opened, for the reward ledger (보상 장부, 840). */
    data class Grant(val bundle: Bundle, val at: Instant, val until: Instant, val why: Why, val reason: String? = null)

    data class State(
        val until: Map<Bundle, Instant> = emptyMap(),
        /** The bundles whose first week was given (it is given once). */
        val firstGiven: Set<Bundle> = emptySet(),
        /** Ads watched (or tried) on [adsDay]. */
        val adsDay: LocalDate? = null,
        val adsCount: Int = 0,
        val declines: Map<Bundle, Int> = emptyMap(),
        val quietUntil: Map<Bundle, Instant> = emptyMap(),
        val grants: List<Grant> = emptyList(),
        /** How many times each bundle's ad was hidden (the first rest is short). */
        val quiets: Map<Bundle, Int> = emptyMap(),
    ) {
        fun open(bundle: Bundle, now: Instant): Boolean = until[bundle]?.let { now < it } == true
        fun openFeature(feature: String, now: Instant): Boolean = Bundle.of(feature)?.let { open(it, now) } == true

        /** Whole days left (the last day counts), or null when closed. */
        fun daysLeft(bundle: Bundle, now: Instant): Long? = until[bundle]?.takeIf { now < it }?.let { end ->
            (Duration.between(now, end).toHours() + 23) / 24
        }

        fun headsUp(bundle: Bundle, now: Instant): Boolean = daysLeft(bundle, now)?.let { it in 1..HEADS_UP_DAYS } == true

        fun adsToday(today: LocalDate): Int = if (adsDay == today) adsCount else 0
    }

    /** The first use of [feature] while its bundle was never opened: its first week, quietly. Null when nothing changes. */
    fun firstUse(s: State, feature: String, now: Instant): State? {
        val b = Bundle.of(feature) ?: return null
        if (b in s.firstGiven || s.open(b, now)) return null
        val end = now.plus(Duration.ofDays(FIRST_DAYS))
        return s.copy(until = s.until + (b to end), firstGiven = s.firstGiven + b, grants = s.grants + Grant(b, now, end, Why.FIRST))
    }

    /**
     * Whether the ad button shows for [bundle]: closed now, under today's [ADS_PER_DAY], and not hidden after [DECLINES]
     * turn-downs. (A bundle with days left shows no ad: "같은 묶음은 7일에 1번".)
     */
    fun canWatch(s: State, bundle: Bundle, now: Instant, today: LocalDate): Boolean =
        !s.open(bundle, now) && s.adsToday(today) < ADS_PER_DAY && s.quietUntil[bundle]?.let { now < it } != true

    /** Today's ads are used up: the ad button says so and the 프로 line stays. */
    fun capped(s: State, today: LocalDate): Boolean = s.adsToday(today) >= ADS_PER_DAY

    /** An ad watched to the end, or one that did not come ([why] [Why.NO_AD], with its [reason]): [bundle] for [AD_DAYS]. */
    fun granted(s: State, bundle: Bundle, now: Instant, today: LocalDate, why: Why = Why.AD, reason: String? = null): State {
        val from = s.until[bundle]?.takeIf { now < it } ?: now
        val end = from.plus(Duration.ofDays(AD_DAYS))
        val count = s.adsToday(today) + 1
        return s.copy(until = s.until + (bundle to end), adsDay = today, adsCount = count, declines = s.declines - bundle,
            grants = s.grants + Grant(bundle, now, end, why, reason))
    }

    /** The offer closed without watching: after [DECLINES] in a row the bundle's ad hides, [FIRST_QUIET_DAYS] then [QUIET_DAYS]. */
    fun declined(s: State, bundle: Bundle, now: Instant): State {
        val n = (s.declines[bundle] ?: 0) + 1
        if (n < DECLINES) return s.copy(declines = s.declines + (bundle to n))
        val times = (s.quiets[bundle] ?: 0) + 1
        val days = if (times == 1) FIRST_QUIET_DAYS else QUIET_DAYS
        return s.copy(declines = s.declines - bundle, quietUntil = s.quietUntil + (bundle to now.plus(Duration.ofDays(days))),
            quiets = s.quiets + (bundle to times))
    }

    /** 840: back after [ProOfferTimes.AWAY_DAYS] away: [bundle] for 7 days, not counted as an ad. */
    fun back(s: State, bundle: Bundle, now: Instant): State {
        val from = s.until[bundle]?.takeIf { now < it } ?: now
        val end = from.plus(Duration.ofDays(AD_DAYS))
        return s.copy(until = s.until + (bundle to end), grants = s.grants + Grant(bundle, now, end, Why.BACK))
    }

    /** The grants of [month]'s days (보상 장부: "이번 달 받은 날"). */
    fun daysGiven(s: State, from: Instant, to: Instant): Long = s.grants.filter { it.at >= from && it.at < to }
        .sumOf { Duration.between(it.at, it.until).toDays() }

    /** How many times this bundle was opened by an ad in the 30 days before [now] (제안 '때' 1: the second in a month). */
    fun adsIn30Days(s: State, bundle: Bundle, now: Instant): Int =
        s.grants.count { it.bundle == bundle && (it.why == Why.AD || it.why == Why.NO_AD) && Duration.between(it.at, now) < Duration.ofDays(30) }
}
