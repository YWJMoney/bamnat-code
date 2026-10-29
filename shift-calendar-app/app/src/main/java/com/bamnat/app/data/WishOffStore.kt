package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.time.Today
import com.bamnat.core.work.WishOff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 1.32 [WTYP-11] 희망 오프 of each calendar ([WishOff]), by calendar id: its own preferences file, carried in the backup
 * ([SettingsFilesBackup.PARTS]) so the days picked and the deadline come back on a new phone.
 */
class WishOffStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(read())
    val all: StateFlow<Map<String, WishOff>> = _all.asStateFlow()

    fun of(calendarId: String): WishOff = _all.value[calendarId] ?: WishOff()

    fun toggle(calendarId: String, day: LocalDate) = write(calendarId) { it.toggle(day).prune(Today.date()) }

    fun setDeadline(calendarId: String, day: Int?) = write(calendarId) { it.copy(deadlineDay = day?.coerceIn(1, 31)) }

    /** [BAK-01] After a restore wrote the file anew. */
    fun reload() { _all.value = read() }

    private fun write(calendarId: String, change: (WishOff) -> WishOff) {
        val next = change(of(calendarId))
        if (next.isEmpty) prefs.edit().remove(calendarId).apply() else prefs.edit().putString(calendarId, encode(next).toString()).apply()
        _all.value = if (next.isEmpty) _all.value - calendarId else _all.value + (calendarId to next)
    }

    private fun read(): Map<String, WishOff> = prefs.all.keys.associateWith { decode(prefs.getString(it, null)) }

    companion object {
        const val FILE = "wish_off"

        fun encode(w: WishOff): JSONObject = JSONObject().put("deadline", w.deadlineDay ?: 0)
            .put("days", JSONArray(w.days.sorted().map { it.toString() }))

        fun decode(text: String?): WishOff = runCatching {
            val o = JSONObject(text!!)
            val days = o.optJSONArray("days")
            WishOff(
                deadlineDay = o.optInt("deadline").takeIf { it in 1..31 },
                days = (0 until (days?.length() ?: 0)).mapNotNullTo(sortedSetOf()) { runCatching { LocalDate.parse(days!!.getString(it)) }.getOrNull() },
            )
        }.getOrDefault(WishOff())
    }
}
