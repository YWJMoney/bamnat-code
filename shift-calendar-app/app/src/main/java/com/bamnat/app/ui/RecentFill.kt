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
 *
 * 2.0.0 [RVL-18] (검토 SCAN-13·U07b-11·I07ba-04) 근태·캘린더 가져오기 too, the same [WINDOW_MINUTES]; said over the calendar as a band
 * ([com.bamnat.app.ui.calendar.RecentFillNotice]) besides 도구, and an undo leaves alone the days changed again since, saying how many.
 */
object RecentFill {
    /** [undo] returns how many days it left alone because they were changed again since. */
    class Entry(val text: String, val atMillis: Long, val calendarId: String, internal val scope: CoroutineScope, internal val undo: suspend () -> Int)

    private val _entry = MutableStateFlow<Entry?>(null)
    val entry: StateFlow<Entry?> = _entry.asStateFlow()

    private val _said = MutableStateFlow<String?>(null)
    /** What the last undo from the calendar did, until heard ([heard]). */
    val said: StateFlow<String?> = _said.asStateFlow()

    private val _closed = MutableStateFlow<Entry?>(null)
    /** The band's X was pressed for this one: 도구 still holds it. */
    val closed: StateFlow<Entry?> = _closed.asStateFlow()

    const val WINDOW_MINUTES = 10L

    fun record(text: String, calendarId: String, scope: CoroutineScope, undo: suspend () -> Unit) =
        recordKeeping(text, calendarId, scope) { undo(); 0 }

    /** 2.0.0 [RVL-18] A fill whose undo says how many days it left alone. */
    fun recordKeeping(text: String, calendarId: String, scope: CoroutineScope, undo: suspend () -> Int) {
        _said.value = null
        _entry.value = Entry(text, Today.millis(), calendarId, scope, undo)
    }

    /** The fill still to take back on [calendarId], or null once [WINDOW_MINUTES] have passed. */
    fun current(calendarId: String, nowMillis: Long = Today.millis()): Entry? =
        _entry.value?.takeIf { it.calendarId == calendarId && nowMillis - it.atMillis in 0..WINDOW_MINUTES * 60_000 }

    /** Milliseconds [e] can still be taken back. */
    fun left(e: Entry, nowMillis: Long = Today.millis()): Long = (e.atMillis + WINDOW_MINUTES * 60_000 - nowMillis).coerceAtLeast(0)

    /** Takes [e] back, once: a snackbar's 되돌리기, the calendar's band and 도구 all lead here. */
    fun undo(e: Entry) {
        if (_entry.value !== e) return
        _entry.value = null
        e.scope.launch { _said.value = undone(e.undo()) }
    }

    /** The fill was taken back some other way (its snackbar): nothing left to undo here. */
    fun clear(e: Entry?) { if (e != null && _entry.value === e) _entry.value = null }

    /** The band is put away; the fill stays in 도구 until its time is up. */
    fun close(e: Entry) { _closed.value = e }

    fun heard() { _said.value = null }

    /** Nothing put in, closed or said (tests: it is kept in memory, so one shot's fill was the next class's band). */
    internal fun reset() {
        _entry.value = null
        _closed.value = null
        _said.value = null
    }

    /** 2.0.0 [RVL-18] What an undo says: all of it, or what it left alone and why. */
    fun undone(kept: Int): String = if (kept > 0) "되돌렸어요. 그 뒤에 고친 ${kept}일은 그대로 두었어요" else "방금 넣은 것을 되돌렸어요"
}
