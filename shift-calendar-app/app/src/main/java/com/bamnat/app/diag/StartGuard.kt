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
    private const val K_BY_HAND = "by_hand"
    const val LIMIT = 3
    const val SETTLE_MS = 10_000L

    private val _safe = MutableStateFlow(false)
    val safe: StateFlow<Boolean> = _safe

    /** PC 18차: this safe start was asked for in 진단 (점검용), not caused by stops in a row. */
    var byHand: Boolean = false
        private set

    /**
     * Application.onCreate: one more start that has not settled yet; the third in a row opens safely.
     *
     * 2.0.0 [RVL-88] (검토 PC-194, 통합 5차 PC: 강제 종료하고 7초 만에 다시 열기를 되풀이하면 '멈춰서 안전 모드'): a start the
     * person ended themselves (closed from 최근 앱, 강제 종료, an update) did not stop; it is not counted. Only an end Android
     * did not record as the person's (a stop, '응답 없음', the phone taking the memory back, or no record before Android 11) counts,
     * as before. A safe start asked for in 진단 ([armNext], then closed by hand to try it) stays asked for.
     */
    fun begin(context: Context, lastEnd: Int? = lastEnd(context)) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var n = p.getInt(K_UNSETTLED, 0)
        if (n > 0 && !p.getBoolean(K_BY_HAND, false) && byThePerson(lastEnd)) n -= 1
        _safe.value = n >= LIMIT
        byHand = n >= LIMIT && p.getBoolean(K_BY_HAND, false)
        // 1.36 PC A21: '점검용' is kept while the phone stays safe, so a start of the process again (the phone closed it while
        // the person was in its settings) still says 점검용; it goes with the count ([settled]).
        p.edit().putInt(K_UNSETTLED, n + 1).apply()
    }

    /**
     * [RVL-88] The person's own end of the app, by Android's record: not a stop. Android writes a reason only from Android 11
     * ([lastEnd] reads none before) and 'an update' only from Android 14, so each number is read where it has that meaning.
     */
    internal fun byThePerson(reason: Int?): Boolean = when {
        reason == null -> false
        reason == SWIPED -> true
        android.os.Build.VERSION.SDK_INT >= 34 && reason == android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED -> true
        android.os.Build.VERSION.SDK_INT >= 30 ->
            reason == android.app.ApplicationExitInfo.REASON_USER_REQUESTED || reason == android.app.ApplicationExitInfo.REASON_USER_STOPPED
        else -> false
    }

    /** [RVL-88] Closed from 최근 앱: Android records it as 'other' or a signal with "remove task" (as [Trail] reads it); told apart here by its own number. */
    internal const val SWIPED = -2

    /** [RVL-88] How the last process ended (Android 11+), or null. */
    private fun lastEnd(context: Context): Int? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val e = runCatching {
            context.getSystemService(android.app.ActivityManager::class.java)?.getHistoricalProcessExitReasons(context.packageName, 0, 1)?.firstOrNull()
        }.getOrNull() ?: return null
        val swiped = (e.reason == android.app.ApplicationExitInfo.REASON_OTHER || e.reason == android.app.ApplicationExitInfo.REASON_SIGNALED) &&
            (e.description?.contains("remove task", ignoreCase = true) == true || e.description?.contains("swipe", ignoreCase = true) == true)
        return if (swiped) SWIPED else e.reason
    }

    /** [SETTLE_MS] after the month: this start went well, the count starts over (a safe start stays safe until 평소대로). */
    fun settled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_UNSETTLED, 0).remove(K_BY_HAND).apply()
    }

    /** PC 17차 (adb force-stop으로는 안전 모드에 들어갈 길이 없음): a debug build's 진단 makes the next start a safe one. */
    fun armNext(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_UNSETTLED, LIMIT).putBoolean(K_BY_HAND, true).commit()
    }

    /** "평소대로": back to a normal start now. */
    fun leave(context: Context) {
        settled(context)
        _safe.value = false
        byHand = false
    }
}
