package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 1.36 840 보상 장부 (수익 2판 2-2): what this phone was given for free in a month, said at the top of the 프로 screen for someone
 * without 프로 and as the body of an offer card.
 * - 받은 날: by source (a bundle's first week and ads, the theme wardrobe, invites), and their sum.
 * - 돈으로 치면: the days at the yearly plan's real price a day (연 요금 ÷ 365), only when Play gave that price (거짓 할인 없음).
 * - 밤낮이 찾아 준 돈: the payslip checks where the app's month came to more than the payslip, this year, only when there are any.
 */
object RewardLedger {
    /** [how] 1.36 PC P22: the ways those days came, "처음 7일 + 광고 7일", when more than one. */
    data class Part(val label: String, val days: Long, val how: String? = null)

    data class Month(val parts: List<Part>, val ads: Int, val wonValue: Long?, val found: Long) {
        val days: Long get() = parts.sumOf { it.days }
        val empty: Boolean get() = days == 0L && found == 0L

        /** "급여 7일 · 테마 30일 · 친구 초대 7일". */
        val partsLine: String get() = parts.filter { it.days > 0 }.joinToString(" · ") { "${it.label} ${it.days}일" + (it.how?.let { h -> "($h)" } ?: "") }
    }

    /**
     * [extra] other free days given in the month (테마, 친구 초대), [yearlyMicros] the yearly plan's price from Play (null when not
     * known), [found] the year's 찾아 준 돈 so far.
     */
    fun month(leases: FeatureLease.State, from: Instant, to: Instant, extra: List<Part>, yearlyMicros: Long?, found: Long): Month {
        val given = leases.grants.filter { it.at >= from && it.at < to }
        val byBundle = FeatureLease.Bundle.entries.map { b ->
            val mine = given.filter { it.bundle == b }
            val ways = mine.groupBy { it.why }.map { (why, gs) -> WAYS.getValue(why) to gs.sumOf { Duration.between(it.at, it.until).toDays() } }
                .groupBy({ it.first }, { it.second }).map { (w, ds) -> w to ds.sum() }
            Part(b.label, mine.sumOf { Duration.between(it.at, it.until).toDays() },
                ways.takeIf { it.size > 1 }?.joinToString(" + ") { (w, d) -> "$w ${d}일" })
        }
        val parts = (byBundle + extra).filter { it.days > 0 }
        val days = parts.sumOf { it.days }
        return Month(parts, given.count { it.why == FeatureLease.Why.AD || it.why == FeatureLease.Why.NO_AD }, wonValue(days, yearlyMicros), found)
    }

    private val WAYS = mapOf(FeatureLease.Why.FIRST to "처음", FeatureLease.Why.AD to "광고", FeatureLease.Why.NO_AD to "광고",
        FeatureLease.Why.BACK to "다시 와서")

    /** [days] at the yearly price a day, to the 100원; null without a price. */
    fun wonValue(days: Long, yearlyMicros: Long?): Long? {
        if (yearlyMicros == null || yearlyMicros <= 0 || days <= 0) return null
        return (days * (yearlyMicros / 1_000_000.0) / 365 / 100).roundToLong() * 100
    }

    /** The yearly plan's price a day in 원 ("하루 82원"), or null. */
    fun perDay(yearlyMicros: Long?): Long? = yearlyMicros?.takeIf { it > 0 }?.let { (it / 1_000_000.0 / 365).roundToLong() }

    /** The yearly plan's price a month in 원 ("월 2,499원 꼴"), or null. */
    fun perMonth(yearlyMicros: Long?): Long? = yearlyMicros?.takeIf { it > 0 }?.let { (it / 1_000_000.0 / 12).toLong() }

    /**
     * 3판 1절 할인 표기: how much less the yearly plan is than the monthly one paid for 12 months, in whole percent, only when Play gave
     * both prices in the same currency and the yearly one is less; null otherwise (nothing is said).
     */
    fun savingsPercent(monthlyMicros: Long?, yearlyMicros: Long?): Int? {
        if (monthlyMicros == null || yearlyMicros == null || monthlyMicros <= 0 || yearlyMicros <= 0) return null
        val year = monthlyMicros * 12
        if (yearlyMicros >= year) return null
        return ((1 - yearlyMicros.toDouble() / year) * 100).toInt().takeIf { it > 0 }
    }
}
