package com.bamnat.app.watch

import android.content.Context
import com.bamnat.app.alarm.UpcomingAlarm
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import com.bamnat.core.time.Today

/**
 * [SHFT-08][REL-09] Sends the main calendar (the one the home widgets show) to a paired Wear OS watch through
 * the Wearable Data Layer: the days from last month's 1st to the end of next month ([WatchDays.window]), the next alarm
 * and when it was sent. The Data Layer goes phone to watch directly (Bluetooth or Wi-Fi via Google Play
 * services); nothing goes to the app's servers. On a phone with no watch it only keeps one small item locally.
 */
object WatchSync {
    private val lock = Mutex()
    private var lastBody: String? = null
    private var lastAt = 0L
    /** The same days are sent again at most this often, so the watch's "몇 분 전" stays honest. */
    private const val RESEND_MILLIS = 60 * 60 * 1000L

    /**
     * [snapshot]: the main calendar (its alarms, 출근·퇴근 and today's alarm stay the main one's); [shown]: [WEAR-04] 1.31 the
     * calendar chosen for the watch, read here when not given.
     */
    suspend fun push(context: Context, snapshot: ScheduleSnapshot? = null, upcoming: UpcomingAlarm? = null, force: Boolean = false,
                     shown: ScheduleSnapshot? = null) = lock.withLock {
        val c = context.appContainer
        val snap = snapshot ?: c.scheduleRepository.snapshot.first()
        val next = upcoming ?: c.alarmScheduler.upcoming.value
        val today = Today.date()
        val (from, until) = WatchDays.window(today)
        val settings = c.settingsRepository.settings.value
        val chosenId = settings.watchCalendar.takeIf { it != PersonalCalendar.MAIN && c.calendars.get(it) != null }
        val view = if (chosenId == null) snap else shown ?: c.calendars.repository(chosenId).snapshot.first()
        val days = WatchDays.of(from, until, view.resolver::resolve)
        val alarmAt = next?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerAtMillis), Today.zone()) }
        val label = next?.plan?.let { p -> if (p.shiftName.isNotBlank()) "${p.shiftName} 근무" else "알람" }.orEmpty()
        val name = c.calendars.get(chosenId ?: PersonalCalendar.MAIN)?.name ?: PersonalCalendar.MAIN_NAME
        // [WEAR-03] This week's hours against 주 52시간, what an 알바 can write now, and whether today has an alarm to turn off.
        val week = com.bamnat.core.hours.WorkHoursCalculator(view.resolver, java.time.DayOfWeek.MONDAY, settings.limits).week(today)
        val nowTime = Today.dateTime()
        val clock = when (com.bamnat.app.widget.HourlyClockAction.offered(settings.workStyle, snap.resolver.resolve(today), nowTime) { snap.resolver.resolve(today.minusDays(1)) }) {
            true -> WatchPayload.Clock.OUT
            false -> WatchPayload.Clock.IN
            null -> WatchPayload.Clock.NONE
        }
        val alarmToday = com.bamnat.core.alarm.AlarmPlanner.alarmsOn(snap.resolver, today).any { it.triggerAt.isAfter(nowTime) }
        fun payload(at: LocalDateTime, sent: Long) = WatchPayload(days, at, alarmAt, label, name, sentAtMillis = sent,
            weekStart = settings.weekStart.value, weekMinutes = week.totalMinutes, weekLimitMinutes = settings.limits.legalWeeklyHours * 60,
            clock = clock, alarmToday = alarmToday, ringOnWatch = settings.watchAlarm != com.bamnat.app.data.AppSettings.WATCH_ALARM_OFF)
        val sameDays = WatchDays.encode(payload(LocalDateTime.of(2000, 1, 1, 0, 0), 0))
        val now = Today.millis()
        if (!force && sameDays == lastBody && now - lastAt < RESEND_MILLIS) return@withLock
        val body = WatchDays.encode(payload(Today.dateTime(), now))
        val request = PutDataMapRequest.create(WatchDays.PATH).apply { dataMap.putString(WatchDays.KEY, body) }.asPutDataRequest().setUrgent()
        val ok = runCatching { await(Wearable.getDataClient(context).putDataItem(request)) != null }.getOrDefault(false)
        if (ok) {
            lastBody = sameDays
            lastAt = now
        }
    }

    /** A Play services task's result, or null when it failed (no Play services, no Wear OS app). */
    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
    }
}

/** [SHFT-08] The watch app asks for fresh days when it opens ("/bamnat/sync"); the phone sends them. */
class WatchRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val c = applicationContext.appContainer
        when (event.path) {
            SYNC_PATH -> c.appScope.launch { WatchSync.push(applicationContext, force = true) }
            // [WEAR-03] 오늘 알람 끄기 from the tile: the same as 알람 끄기 on the phone, today only (I 리뷰 N7 c).
            WatchDays.ALARM_OFF_PATH -> c.appScope.launch {
                val date = runCatching { LocalDate.parse(String(event.data)) }.getOrNull() ?: Today.date()
                // The alarms follow the calendar's change as they do for the phone's own 알람 끄기.
                if (date == Today.date()) c.scheduleRepository.updateDay(date) { it.copy(alarmSkipped = true) }
                WatchSync.push(applicationContext, force = true)
            }
            // [WEAR-06] 1.31 N2: 끄기 and 다시 울림 pressed on the watch while the phone's alarm rings.
            WatchDays.RING_OFF_PATH -> com.bamnat.app.alarm.AlarmService.fromWatch(applicationContext, snooze = false)
            WatchDays.RING_SNOOZE_PATH -> com.bamnat.app.alarm.AlarmService.fromWatch(applicationContext, snooze = true)
            // [WEAR-03] An 알바's 출근·퇴근 from the watch, written as the home widget's button writes it (I 리뷰 N7 d).
            WatchDays.CLOCK_PATH -> c.appScope.launch {
                val snap = c.scheduleRepository.snapshot.first()
                val now = Today.dateTime()
                val out = String(event.data) == "out"
                runCatching {
                    com.bamnat.app.widget.HourlyClockAction.write(c.scheduleRepository, out, snap.resolver.resolve(now.toLocalDate()), now) {
                        snap.resolver.resolve(now.toLocalDate().minusDays(1))
                    }
                }
                com.bamnat.app.widget.WidgetRefresher.refresh(applicationContext)
                WatchSync.push(applicationContext, force = true)
            }
        }
    }

    companion object {
        const val SYNC_PATH = "/bamnat/sync"
    }
}
