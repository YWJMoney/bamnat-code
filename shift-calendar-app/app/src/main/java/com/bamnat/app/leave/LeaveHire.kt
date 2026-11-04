package com.bamnat.app.leave

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * [LEAVE-14] 1.36 실사용 S11 (일근: 연차 화면이 0일, '급여 설정을 하면…'만): the hire date typed on the 연차 screen when the calendar
 * has no pay setup yet (or one without a hire date), so the law's leave is counted from it alone. A pay setup's own hire date wins.
 */
object LeaveHire {
    private const val FILE = "leave_hire"
    private val dates = MutableStateFlow<Map<String, LocalDate>>(emptyMap())
    private var prefs: android.content.SharedPreferences? = null

    val all: StateFlow<Map<String, LocalDate>> = dates.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        dates.value = p.all.mapNotNull { (k, v) -> (v as? String)?.let { s -> runCatching { k to LocalDate.parse(s) }.getOrNull() } }.toMap()
    }

    fun of(calendarId: String): LocalDate? = dates.value[calendarId]

    fun set(calendarId: String, date: LocalDate?) {
        dates.value = if (date == null) dates.value - calendarId else dates.value + (calendarId to date)
        prefs?.edit()?.apply { if (date == null) remove(calendarId) else putString(calendarId, date.toString()) }?.apply()
    }
}
