package com.bamnat.app.sleep

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.core.alarm.AlarmCodec
import com.bamnat.core.model.DayAlarm
import com.bamnat.core.sleep.NapAlarm
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2.0.0 [RVI-17] (검토 ALM-08, PC 재확인) The wake a 낮잠 put in with the day's alarms: so it rings as 낮잠 알람 (not as the night's
 * '야간 근무 알람 · 20:00 근무 시작 · 이 날 알람'), and so an early 깼어요 takes it back out instead of leaving it to ring later.
 */
object NapWake {
    private const val PREFS = "nap_wake"
    private const val K_DATE = "date"
    private const val K_AT = "at"
    private const val K_OWN = "own"
    private const val K_CAL = "calendar"
    private const val K_LIST = "list"

    data class Nap(val date: LocalDate, val at: Long, val hadOwn: Boolean, val calendar: String, val merged: List<DayAlarm>?)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The nap's wake at [at] (millis) went in with shift day [date]'s alarms in [calendar], making them [merged]; [hadOwn]
     * whether the day had alarms of its own before.
     */
    fun save(context: Context, date: LocalDate, at: Long, hadOwn: Boolean, calendar: String, merged: List<DayAlarm>) {
        prefs(context).edit().putLong(K_DATE, date.toEpochDay()).putLong(K_AT, at).putBoolean(K_OWN, hadOwn)
            .putString(K_CAL, calendar).putString(K_LIST, AlarmCodec.encodeDayAlarms(merged)).apply()
    }

    fun read(context: Context): Nap? {
        val p = prefs(context)
        if (!p.contains(K_AT)) return null
        return Nap(LocalDate.ofEpochDay(p.getLong(K_DATE, 0)), p.getLong(K_AT, 0), p.getBoolean(K_OWN, false),
            p.getString(K_CAL, null) ?: com.bamnat.app.data.PersonalCalendar.MAIN, AlarmCodec.decodeDayAlarms(p.getString(K_LIST, null)))
    }

    fun clear(context: Context) { prefs(context).edit().clear().apply() }

    /** Whether the alarm of shift day [date] at [at] (millis), set by hand for the day, is the nap's wake. */
    fun isNap(context: Context, date: LocalDate, at: Long, custom: Boolean): Boolean =
        custom && read(context)?.let { it.date == date && it.at == at } == true

    /**
     * Sleep is over (깼어요 from anywhere, or the nap's own alarm): a wake still ahead goes from the day's alarms, and the day
     * follows its shift again when the nap was all it had of its own. One already past is only forgotten.
     */
    suspend fun afterWake(container: AppContainer, now: Long = Today.millis()) {
        val context = container.appContext
        val nap = read(context) ?: return
        clear(context)
        if (nap.at <= now) return
        val schedule = container.calendars.repository(nap.calendar)
        val wake = LocalDateTime.ofInstant(Instant.ofEpochMilli(nap.at), Today.zone())
        val alarms = schedule.snapshot.first().resolver.resolve(nap.date).entry?.customAlarms ?: return
        if (alarms.none { it.at(nap.date) == wake }) return
        schedule.setDayAlarms(nap.date, NapAlarm.withoutWake(nap.date, alarms, wake, nap.hadOwn, nap.merged))
    }
}
