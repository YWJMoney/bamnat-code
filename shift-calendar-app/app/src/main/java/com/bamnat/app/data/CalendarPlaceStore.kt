package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.time.Today
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVH-15] 검토 CAL-20: the day picked and the month on screen, kept on this phone. When the phone closes 밤낮 while the user
 * is away for a moment (the camera, a bank app) the calendar opens again where it was; after [KEEP] it opens on today as before.
 * Only on this phone (SettingsFilesBackup.DEVICE_ONLY "calendar_place").
 */
class CalendarPlaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The month left on screen, if it was left less than [KEEP] ago. */
    fun month(): YearMonth? = fresh()?.let { runCatching { YearMonth.parse(prefs.getString(K_MONTH, null)) }.getOrNull() }

    /** The day left picked, if it was left less than [KEEP] ago. */
    fun day(): LocalDate? = fresh()?.let { runCatching { LocalDate.parse(prefs.getString(K_DAY, null)) }.getOrNull() }

    /** Called when the place changes and when 밤낮 goes behind another app, so [KEEP] counts from when the user left. */
    fun keep(month: YearMonth, day: LocalDate) {
        prefs.edit().putString(K_MONTH, month.toString()).putString(K_DAY, day.toString())
            .putLong(K_AT, Today.instant().toEpochMilli()).apply()
    }

    private fun fresh(): Unit? {
        val at = prefs.getLong(K_AT, 0L).takeIf { it > 0 } ?: return null
        val age = Today.instant().toEpochMilli() - at
        return Unit.takeIf { age in 0 until KEEP.toMillis() }
    }

    companion object {
        const val FILE = "calendar_place"
        val KEEP: Duration = Duration.ofHours(6)
        private const val K_MONTH = "month"
        private const val K_DAY = "day"
        private const val K_AT = "at"
    }
}
