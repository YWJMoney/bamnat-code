package com.bamnat.app.diag

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * C 1.32 [STAB-04] 571: when the app stopped [LIMIT] times in a row before it had run [SETTLE_MS] after its month showed,
 * the next start is a safe one: the calendar, alarms and backups work, but the jobs that wait for the first month
 * (widgets, the watch, links, the phone calendar, the plaza) stay asleep, so a fault in one of them cannot keep the app
 * from opening. The calendar says so in one line with "평소대로", which wakes them now and starts counting again.
 * Not on a locked phone (its settings cannot be read yet; [BamnatApp] does not call [begin] then).
 */
object StartGuard {
    private const val PREFS = "start_guard"
    private const val K_UNSETTLED = "unsettled"
    const val LIMIT = 3
    const val SETTLE_MS = 10_000L

    private val _safe = MutableStateFlow(false)
    val safe: StateFlow<Boolean> = _safe

    /** Application.onCreate: one more start that has not settled yet; the third in a row opens safely. */
    fun begin(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = p.getInt(K_UNSETTLED, 0)
        _safe.value = n >= LIMIT
        p.edit().putInt(K_UNSETTLED, n + 1).apply()
    }

    /** [SETTLE_MS] after the month: this start went well, the count starts over (a safe start stays safe until 평소대로). */
    fun settled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_UNSETTLED, 0).apply()
    }

    /** "평소대로": back to a normal start now. */
    fun leave(context: Context) {
        settled(context)
        _safe.value = false
    }
}
