package com.bamnat.app.memo

import android.content.Context
import com.bamnat.core.time.Today
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * PC 8차 de44acdf: the memo schedules whose notice is waiting (not yet finished or put off from it), for the one shared memo notice
 * ([MemoScheduleAlarm.SLOT]). The newest is shown; the rest wait behind it. A day's schedules are let go after the day (at least
 * 6 hours on), as the notice itself is. Lost with the app's data, as a notice is.
 */
internal object MemoNoticeQueue {
    private const val PREFS = "memo_notices"

    /** 2.0.0 [RVI-19] (검토 MEMO-03) [day]: the day the schedule is for, which 끝내기 finishes (a 미루기 past midnight is still its first day's). */
    data class Entry(val memo: Long, val at: LocalDateTime, val rest: LocalDateTime?, val title: String, val text: String, val added: Long = 0L,
        val day: java.time.LocalDate? = null)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun minutes(t: LocalDateTime) = t.toEpochSecond(ZoneOffset.UTC) / 60
    private fun time(m: Long) = LocalDateTime.ofEpochSecond(m * 60, 0, ZoneOffset.UTC)

    /** Newest schedule first; of the same minute, the one told last first. */
    fun all(context: Context): List<Entry> = runCatching {
        prefs(context).all.mapNotNull { (k, v) ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            runCatching {
                val o = JSONObject(v as String)
                Entry(id, time(o.getLong("at")), o.optLong("rest", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(::time),
                    o.optString("title"), o.optString("text"), o.optLong("added"),
                    o.optLong("day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay))
            }.getOrNull()
        }
    }.getOrDefault(emptyList()).sortedWith(compareByDescending<Entry> { it.at }.thenByDescending { it.added })

    fun put(context: Context, e: Entry) {
        val o = JSONObject().put("at", minutes(e.at)).put("title", e.title).put("text", e.text).put("added", com.bamnat.core.time.Today.millis())
        e.rest?.let { o.put("rest", minutes(it)) }
        e.day?.let { o.put("day", it.toEpochDay()) }
        prefs(context).edit().putString(e.memo.toString(), o.toString()).commit()
    }

    fun remove(context: Context, memo: Long) {
        prefs(context).edit().remove(memo.toString()).commit()
    }

    /** 2.0.0 [RVI-26] (검토 MEMO-13) The notice was swiped away: all it showed is seen, and does not come back with the next. */
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    /** Schedules of a day before today, more than 6 hours gone, are let go. */
    fun prune(context: Context, now: LocalDateTime = Today.dateTime()) {
        val gone = all(context).filter {
            it.at.toLocalDate() < now.toLocalDate() && java.time.Duration.between(it.at, now).toMillis() > MemoScheduleAlarm.KEEP_AT_LEAST
        }
        if (gone.isEmpty()) return
        prefs(context).edit().apply { gone.forEach { remove(it.memo.toString()) } }.commit()
    }
}
