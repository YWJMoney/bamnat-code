package com.bamnat.core.formula

import java.time.Duration
import java.time.Instant

/**
 * 1.39 [FX-02] 하루 써 보기 (사용자 2026-11-07 17:14·17:16 "그 기능을 누를 때 … 하루 동안 써 보게", 17:35 "무료 사용자도 입혀보기를 통해 얼마나
 * 좋은지 명확히 체감"): a free phone may run each work's 기능 장치 for [HOURS] hours, once per work. While one runs every 기능 장치 on the
 * phone runs (the works' 규칙·단추 are kept as one, [Logic.plus]); when it ends only [FxPro.free] runs again and nothing made is undone.
 * 1.39 [FX-23]: the app marks where each received 규칙·단추 came from, and a try opens only that work's ones ([FxPro.free] with `open`).
 *
 * The key of a work is its 장터 code, or [OWN] for what the user made in the 스튜디오 (one try for all their own).
 */
object FxTaste {
    const val HOURS = 24L
    const val OWN = "own"
    /** The most works whose try is remembered (oldest forgotten first; a forgotten one is not offered again by [status] for long). */
    const val KEEP = 200

    sealed interface Status {
        /** Never tried here: [start] opens it. */
        data object Ready : Status
        /** Running now, until [until]. */
        data class Using(val until: Instant) : Status
        /** Tried before; 프로 keeps it on. */
        data class Used(val endedAt: Instant) : Status
    }

    /** Each work tried, with when its try began. */
    data class State(val tried: Map<String, Instant> = emptyMap()) {
        /** The try running now, its work and end, or null. */
        fun running(now: Instant): Pair<String, Instant>? = tried.entries
            .map { (k, at) -> k to at.plus(Duration.ofHours(HOURS)) }
            .filter { now < it.second }
            .maxByOrNull { it.second }

        fun open(now: Instant): Boolean = running(now) != null

        /** 1.39 [FX-23] The works whose try runs now (작품마다 그 작품 것만 열어요). */
        fun openKeys(now: Instant): Set<String> = tried.filter { now < it.value.plus(Duration.ofHours(HOURS)) }.keys

        /** 1.39 [FX-24] The work whose try ended last (끝 알림이 그 작품 장터 자세히로), or null. */
        fun lastEnded(now: Instant): String? = tried.entries.filter { now >= it.value.plus(Duration.ofHours(HOURS)) }.maxByOrNull { it.value }?.key
    }

    fun status(s: State, key: String, now: Instant): Status {
        val at = s.tried[key] ?: return Status.Ready
        val end = at.plus(Duration.ofHours(HOURS))
        return if (now < end) Status.Using(end) else Status.Used(end)
    }

    /** [key]'s try begun at [now], or null when it was tried before. */
    fun start(s: State, key: String, now: Instant): State? {
        if (key.isBlank() || key in s.tried) return null
        val all = (s.tried + (key to now)).entries.sortedBy { it.value }.takeLast(KEEP).associate { it.key to it.value }
        return State(all)
    }

    /** As the phone keeps it: `{"AB12CD":1731000000000}`. */
    fun encode(s: State): String = com.bamnat.core.recipe.RecipeCodec.canonical(s.tried.mapValues { it.value.toEpochMilli() })

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String?): State {
        if (text.isNullOrBlank()) return State()
        val m = runCatching { com.bamnat.core.remote.Json.parse(text) as? Map<String, Any?> }.getOrNull() ?: return State()
        return State(m.mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k to Instant.ofEpochMilli(it) } }.toMap())
    }

    /** "오늘 밤 9:40까지 써 볼 수 있어요" style is the app's; this is the hours and minutes left, for "23시간 10분 남음". */
    fun left(until: Instant, now: Instant): Duration = Duration.between(now, until).coerceAtLeast(Duration.ZERO)
}
