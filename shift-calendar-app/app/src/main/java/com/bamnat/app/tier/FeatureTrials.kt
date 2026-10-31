package com.bamnat.app.tier

import android.content.Context
import com.bamnat.core.tier.FeatureLease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * [MON-13] 1.36 (사용자 결정 2026-10-31: '해당 기능을 사용한 기간부터 일주일 제공') and 1.36 839 묶음 7일권: the four bundles of
 * [FeatureLease] as this phone has them: each one's first week from its first use, the days an ad (or a missing ad) gave, today's
 * ads, the 'no's. Kept on the phone (a reinstall starts again); nothing goes to the server. The first weeks of 1.36's earlier
 * builds (금액 비교하기·세후 예상, kept by feature name) count as the 급여 bundle's.
 */
class FeatureTrials(context: Context?) {
    private val prefs = context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    /** The bundles as they stand. */
    val all: StateFlow<FeatureLease.State> = state.asStateFlow()

    /** Starts [feature]'s bundle's first week at [at] unless it began before; true when it starts now. */
    fun start(feature: Feature, at: Long): Boolean {
        val next = FeatureLease.firstUse(state.value, feature.name, Instant.ofEpochMilli(at)) ?: return false
        save(next)
        return true
    }

    /** An ad watched to the end, or one that did not come: [bundle] for 7 days. */
    fun grant(bundle: FeatureLease.Bundle, now: Instant, today: LocalDate, why: FeatureLease.Why, reason: String? = null) =
        save(FeatureLease.granted(state.value, bundle, now, today, why, reason))

    /** The offer closed without an ad. */
    fun decline(bundle: FeatureLease.Bundle, now: Instant) = save(FeatureLease.declined(state.value, bundle, now))

    /** For testing on a debug build: every bundle back to never used. */
    fun clear() {
        state.value = FeatureLease.State()
        prefs?.edit()?.clear()?.apply()
    }

    private fun save(s: FeatureLease.State) {
        state.value = s
        prefs?.edit()?.putString(KEY, encode(s))?.apply()
    }

    private fun load(): FeatureLease.State {
        val p = prefs ?: return FeatureLease.State()
        p.getString(KEY, null)?.let { return runCatching { decode(it) }.getOrDefault(FeatureLease.State()) }
        // 1.36's first builds: a feature name to the millis its week began.
        val old = p.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }
        if (old.isEmpty()) return FeatureLease.State()
        val start = Instant.ofEpochMilli(old.minOf { it.second })
        return FeatureLease.State(
            until = mapOf(FeatureLease.Bundle.PAY to start.plus(java.time.Duration.ofDays(FeatureLease.FIRST_DAYS))),
            firstGiven = setOf(FeatureLease.Bundle.PAY),
        )
    }

    companion object {
        const val FILE = "pro_trials"
        private const val KEY = "lease"
        const val DAYS = 7
        const val MS = DAYS * 86_400_000L

        fun encode(s: FeatureLease.State): String = JSONObject()
            .put("until", JSONObject().apply { s.until.forEach { (b, t) -> put(b.key, t.toEpochMilli()) } })
            .put("first", JSONArray(s.firstGiven.map { it.key }))
            .put("adsDay", s.adsDay?.toString() ?: JSONObject.NULL)
            .put("ads", s.adsCount)
            .put("no", JSONObject().apply { s.declines.forEach { (b, n) -> put(b.key, n) } })
            .put("quiet", JSONObject().apply { s.quietUntil.forEach { (b, t) -> put(b.key, t.toEpochMilli()) } })
            .put("grants", JSONArray(s.grants.takeLast(MAX_GRANTS).map { g ->
                JSONObject().put("b", g.bundle.key).put("at", g.at.toEpochMilli()).put("to", g.until.toEpochMilli()).put("why", g.why.key)
                    .put("r", g.reason ?: JSONObject.NULL)
            }))
            .toString()

        fun decode(text: String): FeatureLease.State {
            val o = JSONObject(text)
            fun times(name: String) = o.optJSONObject(name)?.let { m ->
                m.keys().asSequence().mapNotNull { k -> FeatureLease.Bundle.byKey(k)?.let { it to Instant.ofEpochMilli(m.getLong(k)) } }.toMap()
            }.orEmpty()
            val first = o.optJSONArray("first")?.let { a -> (0 until a.length()).mapNotNull { FeatureLease.Bundle.byKey(a.optString(it)) }.toSet() }.orEmpty()
            val no = o.optJSONObject("no")?.let { m -> m.keys().asSequence().mapNotNull { k -> FeatureLease.Bundle.byKey(k)?.let { it to m.getInt(k) } }.toMap() }.orEmpty()
            val grants = o.optJSONArray("grants")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val g = a.optJSONObject(i) ?: return@mapNotNull null
                    val b = FeatureLease.Bundle.byKey(g.optString("b")) ?: return@mapNotNull null
                    val why = FeatureLease.Why.byKey(g.optString("why")) ?: return@mapNotNull null
                    FeatureLease.Grant(b, Instant.ofEpochMilli(g.getLong("at")), Instant.ofEpochMilli(g.getLong("to")), why,
                        g.optString("r").takeIf { !g.isNull("r") && it.isNotEmpty() })
                }
            }.orEmpty()
            return FeatureLease.State(times("until"), first, o.optString("adsDay").takeIf { !o.isNull("adsDay") && it.isNotEmpty() }?.let(LocalDate::parse),
                o.optInt("ads"), no, times("quiet"), grants)
        }

        /** The ledger keeps the last year or so of grants. */
        private const val MAX_GRANTS = 200
    }
}
