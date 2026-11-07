package com.bamnat.app.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bamnat.app.alarm.AlarmService
import com.bamnat.app.alarm.AlarmSoon
import com.bamnat.app.alarm.AlarmsOff
import com.bamnat.app.alarm.BootAlarms
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.appContainer
import com.bamnat.core.sleep.SmartWakeRule
import com.bamnat.core.time.Today

/**
 * 1.38 [SLEEP-08] 깨우는 구간, kept to 갈래1's six promises:
 * 1. Only earlier: the on-time alarm stays as it is (setAlarmClock in AlarmScheduler); this is a separate alarm at the
 *    window's start that only listens.
 * 2. Armed and cleared from [com.bamnat.app.alarm.AlarmScheduler.reschedule], so a skipped (AlarmSoon) or turned-off
 *    (AlarmsOff) alarm never has a window.
 * 3. Rung in the window and turned off by the user: the on-time ring of the same alarm is turned off quietly
 *    ([AlarmSoon.skipQuietly], from [AlarmService]'s dismiss). Not before: PC 11차 found the early ring itself silenced when
 *    the on-time one was marked off first, and an early ring nobody turned off leaves the on-time one to ring.
 * 4. Locked after a reboot: no window.
 * 5. The same ringing screen and [RingRequest]; only a line under it says why it came early.
 * 6. Off by default (0 minutes): nothing is armed and the alarm tests see no change.
 *
 * The window listens only while 자는 동안 소리 is measuring ([SleepRecordService]): without it the phone cannot tell a
 * light sleep, and the alarm rings on time.
 */
object SmartWake {
    private const val REQ = 3411
    private const val PREFS = "smart_wake"
    private const val K_EARLY_AT = "early_at"
    private const val K_EARLY_MIN = "early_min"
    const val ACTION_WINDOW = "com.bamnat.app.sleep.SMART_WINDOW"

    @Volatile private var watching: RingRequest? = null
    @Volatile private var watchMinutes = 0
    /** Times something moved while the window listens, the last [SmartWakeRule.LIGHT_WITHIN_MILLIS] only. */
    private val moves = ArrayDeque<Long>()

    private fun intent(context: Context, ring: RingRequest?): PendingIntent {
        val i = Intent(context, SmartWakeReceiver::class.java).setAction(ACTION_WINDOW)
        ring?.putInto(i)
        return PendingIntent.getBroadcast(context, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** From AlarmScheduler.reschedule: the window for the next alarm, or none. Never throws into the alarm's own planning. */
    fun arm(context: Context, ring: RingRequest?) {
        runCatching {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            // Never builds or waits for the app's container from inside the alarm's planning (CI: a scheduler made on its own
            // pulled the container in and raced its first planning); before it is ready the window is left as it was.
            val minutes = if (BootAlarms.isLocked(context)) 0
                else (context.applicationContext as? com.bamnat.app.BamnatApp)?.readyContainer()?.settingsRepository?.current?.smartWakeMinutes
                    ?: return
            val from = ring?.takeIf { it.label == null && !it.isSnooze }?.let { SmartWakeRule.windowStart(it.triggerAtMillis, minutes) }
            if (ring == null || from == null || from <= Today.millis()) {
                am.cancel(intent(context, null))
                return
            }
            val op = intent(context, ring)
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, from, op)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, from, op)
        }
    }

    /** The window began: listen through the measuring, if the night is being measured and the alarm still stands. */
    internal fun windowOpened(context: Context, ring: RingRequest) {
        if (BootAlarms.isLocked(context) || AlarmsOff.isOff(context) || AlarmSoon.isSkipped(context, ring.triggerAtMillis)) return
        if (SleepRecordService.recording.value == null) return
        synchronized(moves) { moves.clear() }
        watching = ring
        watchMinutes = context.appContainer.settingsRepository.current.smartWakeMinutes
    }

    /** From the measuring, after each minute or movement: rings now when the sleep is light. */
    internal fun check(context: Context, movedAt: Long?) {
        val ring = watching ?: return
        val now = Today.millis()
        if (now >= ring.triggerAtMillis) { watching = null; return }
        val recent = synchronized(moves) {
            if (movedAt != null) moves.addLast(movedAt)
            while (moves.isNotEmpty() && now - moves.first() > SmartWakeRule.LIGHT_WITHIN_MILLIS) moves.removeFirst()
            moves.toList()
        }
        if (!SmartWakeRule.wakeNow(now, ring.triggerAtMillis, watchMinutes, recent)) return
        watching = null
        ringEarly(context, ring, now)
    }

    /** Rings [ring] now, before its time; its on-time ring stands until this one is turned off. */
    internal fun ringEarly(context: Context, ring: RingRequest, now: Long = Today.millis()) {
        val early = (ring.triggerAtMillis - now) / 60_000
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(K_EARLY_AT, ring.triggerAtMillis).putLong(K_EARLY_MIN, early).commit()
        if (!BootAlarms.isLocked(context)) runCatching { context.appContainer.telemetry.event("alarm_smart_ring", "$early") }
        // The on-time ring stays until this one is turned off ([offEarly]).
        AlarmService.start(context, ring)
    }

    /** [ring] is ringing before its time because of the window. */
    fun rangEarly(context: Context, ring: RingRequest, now: Long = Today.millis()): Boolean =
        ring.label == null && !ring.isSnooze && now < ring.triggerAtMillis &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_EARLY_AT, 0L) == ring.triggerAtMillis

    /** From AlarmService's dismiss: the user is up, so the on-time ring of the same alarm is off, quietly. */
    fun offEarly(context: Context, ring: RingRequest) {
        if (rangEarly(context, ring)) AlarmSoon.skipQuietly(context, ring.triggerAtMillis)
    }

    /**
     * The line for the ringing screen when this alarm came early, else null. PC 12차 (w-8): judged by when the ring began
     * ([startedAt]), so an early ring keeps its line however long it is shown, and the on-time ring of the same alarm,
     * begun at its time, has none.
     */
    fun earlyLine(context: Context, ring: RingRequest, startedAt: Long = Today.millis()): String? {
        if (ring.label != null || ring.isSnooze || ring.recheck || startedAt >= ring.triggerAtMillis) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong(K_EARLY_AT, 0L) != ring.triggerAtMillis) return null
        return SmartWakeRule.line(p.getLong(K_EARLY_MIN, 0L))
    }
}

class SmartWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = RingRequest.fromIntent(intent) ?: return
        SmartWake.windowOpened(context, ring)
    }
}
