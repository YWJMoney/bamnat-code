package com.bamnat.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.bamnat.core.time.Today

/**
 * [READ-11] 방금 넣은 것 되돌리기 (1.25 순번 372, 사용자 "근무 전체넣기 … 되돌리기도 안됨"): a whole month put in at once
 * (한 달 근무 넣기, 이번 달 전부 넣기) can be taken back from the calendar's 도구 for [WINDOW_MINUTES] minutes, also after
 * its screen and its snackbar are gone. In memory only: closing the app forgets it, and a newer fill replaces it.
 */
object RecentFill {
    class Entry(val text: String, val atMillis: Long, val calendarId: String, internal val scope: CoroutineScope, internal val undo: suspend () -> Unit)

    private val _entry = MutableStateFlow<Entry?>(null)
    val entry: StateFlow<Entry?> = _entry.asStateFlow()

    const val WINDOW_MINUTES = 10L

    fun record(text: String, calendarId: String, scope: CoroutineScope, undo: suspend () -> Unit) {
        _entry.value = Entry(text, Today.millis(), calendarId, scope, undo)
    }

    /** The fill still to take back on [calendarId], or null once [WINDOW_MINUTES] have passed. */
    fun current(calendarId: String, nowMillis: Long = Today.millis()): Entry? =
        _entry.value?.takeIf { it.calendarId == calendarId && nowMillis - it.atMillis in 0..WINDOW_MINUTES * 60_000 }

    /** Takes [e] back, once: a snackbar's 되돌리기 and the calendar's both lead here. */
    fun undo(e: Entry) {
        if (_entry.value !== e) return
        _entry.value = null
        e.scope.launch { e.undo() }
    }

    /** The fill was taken back some other way (its snackbar): nothing left to undo here. */
    fun clear(e: Entry?) { if (e != null && _entry.value === e) _entry.value = null }
}
