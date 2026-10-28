package com.bamnat.app.flags

import android.content.Context
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagEval
import com.bamnat.core.flags.FlagSource
import com.bamnat.core.flags.FlagState
import com.bamnat.core.flags.KST
import com.bamnat.core.flags.PhoneFacts
import com.bamnat.core.flags.TypeExposure
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import com.bamnat.app.tier.LaunchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

/**
 * [FLAG-07] 1.28 묶음 AL: every row of the dark-merge book as evaluated on this phone now. Worked out again when a new
 * `/api/config` comes ([LaunchConfig]), on any change in [triggers] (a consent, the 처리방침 yes, the kind of work in
 * use, the admin link, an admin preview), when the app comes back to the front ([refresh]) and at the next 0시 KST
 * (also by the server's clock for LEGAL rows), so a row whose day has come turns on by itself, offline included.
 * Nothing here starts a named job (StartupBudgetTest): the state is one `stateIn` on [scope].
 */
class Flags(
    config: StateFlow<LaunchConfig>,
    triggers: List<Flow<Any?>>,
    scope: CoroutineScope,
    /** This phone now; read afresh at each evaluation. */
    private val facts: () -> PhoneFacts,
    private val now: () -> Instant = Today::instant,
    private val serverNow: () -> Instant? = { null },
) {
    private val tick = MutableStateFlow(0L)

    /** Emits now and again just after each 0시 KST (by the phone's and, when known, the server's clock). */
    private val midnight: Flow<Long> = flow {
        while (true) {
            emit(now().toEpochMilli())
            delay(untilMidnight().toMillis().coerceAtLeast(1_000L))
        }
    }

    private fun untilMidnight(): Duration {
        fun left(at: Instant): Duration = Duration.between(at, at.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant()).plusSeconds(1)
        val phone = left(now())
        val server = serverNow()?.let(::left)
        return if (server != null && server < phone) server else phone
    }

    private fun evaluate(c: LaunchConfig): List<FlagState> = FlagEval.all(facts(), c.overrides(), c.calendar)

    val states: StateFlow<List<FlagState>> = combine(listOf(config, tick, midnight) + triggers) { values -> values[0] as LaunchConfig }
        .map { evaluate(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, runCatching { evaluate(config.value) }.getOrDefault(emptyList()))

    /** Rows by key, for screens that read one. */
    val byKey: StateFlow<Map<String, FlagState>> = states.map { l -> l.associateBy { it.key } }
        .stateIn(scope, SharingStarted.Eagerly, states.value.associateBy { it.key })

    /** Works every row out again now (the app came back, a store changed that has no flow). */
    fun refresh() { tick.value++ }

    /** The answer for [key] now; a key not in the book is its default (off for an unknown key). */
    fun on(key: String): Boolean = byKey.value[key]?.on ?: FlagCatalog.of(key)?.default ?: false

    fun state(key: String): FlagState? = byKey.value[key]

    /** [FLAG-10] A feature the developer rested (the old 기능 스위치) is its row being off. */
    fun paused(s: ServerSwitch): Boolean = !on(FlagCatalog.switchKey(s))

    /**
     * [FLAG-12][FLAG-13] The kinds of work a place where a kind is newly picked shows: hidden ones only on the owner's
     * phone (marked), the kind in use [keep] always.
     */
    fun types(keep: WorkStyle? = null): List<TypeExposure.Choice> = types(byKey.value, facts().let { if (it.admin) it.previews else emptySet() }, keep)

    /**
     * [FLAG-16] What 사용 통계 adds when `stats.more` is on: the keys of the rows on, sorted, without the ones an admin
     * phone turned on for itself (a preview would tell that phone apart). Null when it is off: nothing is added.
     */
    fun statsOn(): List<String>? {
        if (!on(FlagCatalog.STATS_MORE)) return null
        return states.value.filter { it.on && it.source != FlagSource.ADMIN_PREVIEW }.map { it.key }.sorted()
    }

    companion object {
        /**
         * S 1.32 [SECX-01] A kind on only by 미리 보기 is listed as hidden (marked), and a hidden kind shows only on an
         * approved admin phone the developer app gave its `type.*` 미리 보기 ([previews]); being the owner adds nothing.
         */
        fun types(states: Map<String, FlagState>, previews: Set<String>, keep: WorkStyle? = null): List<TypeExposure.Choice> =
            TypeExposure.choices({ s -> states[FlagCatalog.typeKey(s)]?.let { it.on && it.source != FlagSource.ADMIN_PREVIEW } ?: true },
                { s -> FlagCatalog.typeKey(s) in previews }, keep)
    }
}

/**
 * S 1.32 [SECX-01] 1.28~1.31 kept 관리자 미리 보기 on the phone (알람 점검 › 기능 상태 switches). The developer app sets
 * it now, by role, and it comes with `/api/passes`; this clears what an old version left on the phone.
 */
fun clearOldPreviews(context: Context) {
    context.applicationContext.getSharedPreferences("flag_preview", Context.MODE_PRIVATE).edit().clear().apply()
}

/** [FLAG-07] A hash of the install id for rollout places: the id itself never leaves [PhoneFacts]' maker. */
fun installHash(device: String?): String? = device?.takeIf { it.isNotBlank() }?.let { d ->
    java.security.MessageDigest.getInstance("SHA-256").digest(d.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
