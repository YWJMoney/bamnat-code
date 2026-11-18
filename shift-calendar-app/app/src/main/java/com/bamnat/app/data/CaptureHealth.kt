package com.bamnat.app.data

import android.content.Context
import android.content.SharedPreferences
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.YearMonth

/**
 * 2.0.0 [RVK-16] (검토 MONEY-13·U06a-17): whether 결제 알림 읽기 is really reading — when it last read a payment notification,
 * how many lines it wrote this month, and when the phone last connected or cut the reader — so 자동 기록 can say
 * "마지막으로 읽음 오늘 14:02 · 이번 달 23건" and, after three quiet days, offer to connect it again.
 *
 * Times and counts only (never a text or an amount), on this phone only: `capture_health` is not in the backup rules.
 */
class CaptureHealth(context: Context, private val clock: () -> Long = Today::millis) {
    data class State(
        /** When a notification that looked like a payment was last read, or null when none was yet. */
        val lastRead: Long? = null,
        /** "2026-11": the month [written] counts. */
        val month: String = "",
        /** Lines written from notifications in [month], over every calendar and the investor one. */
        val written: Int = 0,
        /** When the phone last connected the reader (at start, after a restart, after 다시 연결). */
        val connectedAt: Long? = null,
        /** When the phone last cut it; later than [connectedAt] means it is cut now. */
        val cutAt: Long? = null,
    ) {
        /** Lines written this month, as of [now]: 0 once the month has turned. */
        fun writtenIn(now: Long): Int = if (month == monthOf(now)) written else 0

        /** True when the reader is on but has read nothing for [QUIET_DAYS] days, or the phone cut it. */
        fun quiet(now: Long): Boolean {
            if (cutAt != null && (connectedAt == null || cutAt > connectedAt)) return true
            val since = lastRead ?: connectedAt ?: return false
            return now - since >= QUIET_DAYS * DAY
        }
    }

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("capture_health", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    /** A notification that looked like a payment was read just now. */
    fun sawPayment() = edit { putLong(K_READ, clock()) }

    /** A line was written from a notification. */
    @Synchronized
    fun wrote() {
        val now = clock()
        val month = monthOf(now)
        val count = if (_state.value.month == month) _state.value.written + 1 else 1
        edit { putString(K_MONTH, month); putInt(K_WRITTEN, count) }
    }

    fun connected() = edit { putLong(K_CONNECTED, clock()) }
    fun cut() = edit { putLong(K_CUT, clock()) }

    @Synchronized
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = load()
    }

    private fun load(): State = State(
        lastRead = prefs.getLong(K_READ, 0L).takeIf { it > 0 },
        month = prefs.getString(K_MONTH, "").orEmpty(),
        written = prefs.getInt(K_WRITTEN, 0),
        connectedAt = prefs.getLong(K_CONNECTED, 0L).takeIf { it > 0 },
        cutAt = prefs.getLong(K_CUT, 0L).takeIf { it > 0 },
    )

    companion object {
        const val QUIET_DAYS = 3L
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val K_READ = "read"
        private const val K_MONTH = "month"
        private const val K_WRITTEN = "written"
        private const val K_CONNECTED = "connected"
        private const val K_CUT = "cut"

        fun monthOf(millis: Long): String = YearMonth.from(Instant.ofEpochMilli(millis).atZone(Today.zone())).toString()
    }
}
