package com.bamnat.core.recipe

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * [RCP-07] How the plaza orders recipes (설계서 4-4), the same formulas as the server's `recipeScore()` (cases in
 * `docs/recipe-cases.json`):
 * - 좋은 순: the Wilson lower bound (95%) of 좋아요 among 좋아요+안좋아요 (표가 적으면 보수적으로, Evan Miller "How Not To Sort By
 *   Average Rating"), mixed 7:3 with the 7일 유지율 once 20 or more applied it. Fewer than 5 votes: only on 새로.
 * - 요즘 뜨는: log(1 + 좋아요 + 적용×2) ÷ (hours since it went up + 2)^1.5 (HN·Lemmy).
 * Votes from a phone in trust level 0 or installed less than 3 days count half here; the counts shown stay as they are.
 */
object Rank {
    const val Z = 1.96
    const val MIN_VOTES = 5
    const val MIN_APPLIED = 20

    /** Wilson score lower bound of [pos] out of [n]. */
    fun wilson(pos: Double, n: Double): Double {
        if (n <= 0.0) return 0.0
        val p = pos / n
        val z2 = Z * Z
        return (p + z2 / (2 * n) - Z * sqrt((p * (1 - p) + z2 / (4 * n)) / n)) / (1 + z2 / n)
    }

    /** 좋은 순 score from weighted likes and dislikes, applied and kept-7-days counts; null when too few votes to rank. */
    fun good(likes: Double, dislikes: Double, applied: Long, kept7: Long): Double? {
        val n = likes + dislikes
        if (n < MIN_VOTES) return null
        val w = wilson(likes, n)
        return if (applied >= MIN_APPLIED) 0.7 * w + 0.3 * (kept7.toDouble() / applied).coerceIn(0.0, 1.0) else w
    }

    /** 요즘 뜨는 score. */
    fun hot(likes: Double, applied: Long, hours: Double): Double = ln(1.0 + likes + applied * 2.0) / (hours.coerceAtLeast(0.0) + 2.0).pow(1.5)

    /** 7일 유지율 in percent, shown only from [MIN_APPLIED] applications. */
    fun keptPercent(applied: Long, kept7: Long): Int? = if (applied < MIN_APPLIED) null else (100.0 * kept7 / applied).toInt().coerceIn(0, 100)

    /** How much one vote counts in ranking: half for a new or level-0 phone. */
    fun weight(trustLevel: Int, installedDays: Int): Double = if (trustLevel <= 0 || installedDays < 3) 0.5 else 1.0
}
