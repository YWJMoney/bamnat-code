package com.bamnat.app.diag

import android.content.Context
import com.bamnat.core.stats.SetupTaps
import com.bamnat.core.work.WorkStyle
import org.json.JSONObject

/**
 * 1.39 [SETUP-20] (사용자 결정 2026-11-07, 발굴 G6 4-1 '처음 1분을 숫자로'): counts the taps on a newly installed phone from its
 * first open until the first setup is saved, and keeps only [SetupTaps.bucket] of it with the kind of work chosen. It goes once
 * with 사용 통계 (`setup: {style, taps}`) when [allowed] (the user's yes and the `stats.setup` row, 처리방침 3.12); until then it
 * stays on the phone, and turning 사용 통계 off forgets it. Never what was tapped, a time or a phone id.
 *
 * A phone that already had its setup when 1.39 came, a setup put off with '나중에', and 처음 설정 다시 받기 are not counted.
 */
class SetupTapStats(
    context: Context,
    private val allowed: () -> Boolean = { false },
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    @Volatile private var counting = false
    @Volatile private var taps = 0

    /** At every start: the first start of a phone without a setup begins counting; any other phone never counts. */
    fun start(setupDone: Boolean, setupLater: Boolean) {
        when (prefs.getString(K_STATE, null)) {
            null -> if (setupDone || setupLater) end() else {
                prefs.edit().putString(K_STATE, COUNTING).putInt(K_TAPS, 0).apply()
                taps = 0; counting = true
            }
            COUNTING -> if (setupDone || setupLater) end() else { taps = prefs.getInt(K_TAPS, 0); counting = true }
        }
    }

    /** One finger down anywhere in the app (MainActivity), while counting. */
    fun tap() {
        if (!counting) return
        taps++
        prefs.edit().putInt(K_TAPS, taps).apply()
    }

    /** The first setup is saved with [style]: the count waits to go. */
    fun finish(style: WorkStyle?) {
        if (!counting) return
        counting = false
        val e = prefs.edit().putString(K_STATE, ENDED).remove(K_TAPS)
        if (style != null) e.putString(K_PENDING, SetupTaps.encode(style, taps))
        e.apply()
    }

    /** '나중에' or a phone that never counts: nothing goes. */
    fun end() {
        counting = false
        prefs.edit().putString(K_STATE, ENDED).remove(K_TAPS).apply()
    }

    /** What waits to go, for 보낼 내용 보기 too. */
    fun pending(): Pair<String, Int>? = SetupTaps.decode(prefs.getString(K_PENDING, null))

    /** The `setup` part of the usage bundle, or null when not allowed or nothing waits. */
    fun payload(): JSONObject? {
        if (!allowed()) return null
        val (style, bucket) = pending() ?: return null
        return JSONObject().put("style", style).put("taps", bucket)
    }

    /** After the bundle went: never again. */
    fun sent() = prefs.edit().remove(K_PENDING).apply()

    /** Turning 사용 통계 off forgets what was waiting. */
    fun clear() = prefs.edit().remove(K_PENDING).apply()

    companion object {
        /** Set by [com.bamnat.app.AppContainer] to `flags.on(FlagCatalog.STATS_SETUP)`; off until then. */
        @Volatile var policyAllows: () -> Boolean = { false }

        private const val FILE = "setup_taps"
        private const val K_STATE = "state"
        private const val K_TAPS = "taps"
        private const val K_PENDING = "pending"
        private const val COUNTING = "counting"
        private const val ENDED = "ended"
    }
}
