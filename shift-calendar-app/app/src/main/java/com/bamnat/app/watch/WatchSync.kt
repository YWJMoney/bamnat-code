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
        val label = next?.plan?.let { p -> if (p.shiftName.isNotBlank()) com.bamnat.core.alarm.AlarmWords.work(p.shiftName) else "알람" }.orEmpty()
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
        // 1.38 [LEDH-14] 워치로 돈 적기: the main 가계부's most used spending categories of the last 60 days.
        val moneyBook = runCatching { c.calendars.money(PersonalCalendar.MAIN).book.first() }.getOrNull()
        val moneyCategories = runCatching { com.bamnat.app.watch.WatchMoneyIn.categories(moneyBook!!, today) }.getOrDefault(emptyList())
        // 2.0.0 [RVI-10] (검토 U03-10) And the amounts written most often, for one-tap chips.
        val recentAmounts = runCatching { WatchMoneyIn.amounts(moneyBook!!, today) }.getOrDefault(emptyList())
        // 1.38 [SLEEP-14] 워치 타일의 오늘 잘 시간: the same line as the 오늘의 흐름 widget.
        val sleepLine = runCatching {
            if (!settings.sleepTips) null
            // 2.0.0 [RVI-18] (검토 T03-08) 가게·현장·투자: the line by the next alarm and the target, as on the today widget.
            else if (settings.workStyle?.usesPayEngine == false) com.bamnat.core.sleep.RestLine.of(nowTime,
                com.bamnat.core.sleep.SleepMerge.merge(c.sleepRepository.recent(days = 2).first(), c.healthSleep.sleeps.value),
                alarmAt, settings.alarmsEnabled, settings.sleepTargetMinutes)?.text
            else if (snap.isEmpty) null
            else com.bamnat.core.sleep.TodayFlow.of(nowTime, snap.resolver, settings.alarmsEnabled,
                com.bamnat.core.sleep.SleepMerge.merge(c.sleepRepository.recent(days = 2).first(), c.healthSleep.sleeps.value),
                settings.sleepTargetMinutes, settings.commuteMinutes, settings.restCheck, settings.strictRest).line
        }.getOrNull().orEmpty()
        // 2.0.0 [RVI-09] (검토 ALM-15) 이번 주 n시간 / 52시간 is a worker's line: a 가게, 현장 or 투자 calendar sends none, as the phone's
        // 알람 뒤 오늘 카드 already hides it, and the watch says 영업·일정 where it said 근무.
        val payEngine = settings.workStyle?.usesPayEngine != false
        val noun = com.bamnat.app.widget.countdownWord(settings.workStyle)
        // 2.0.0 [RVI-94] (G5-M3) 잠 타일: whether the wrist may write the sleep log, and the sleep now open.
        val asleepSince = runCatching { c.sleepRepository.recent(days = 2).first().firstOrNull { it.end == null && !it.imported }?.start }.getOrNull()
        // 2.0.0 [RVI-95] (TASK-35) 오늘 할 일 타일: 내 달력's to-dos as the 할 일 위젯 lists them.
        val taskRows = runCatching {
            val memos = c.memoRepository.memosFor(PersonalCalendar.MAIN).first()
            com.bamnat.core.memo.TaskList.of(memos, today, c.memoRepository.shiftLookup(memos, PersonalCalendar.MAIN).first())
        }.getOrDefault(emptyList())
        fun payload(at: LocalDateTime, sent: Long) = WatchPayload(days, at, alarmAt, label, name, sentAtMillis = sent, moneyCategories = moneyCategories, sleepLine = sleepLine,
            weekStart = settings.weekStart.value, weekMinutes = if (payEngine) week.totalMinutes else -1, weekLimitMinutes = settings.limits.legalWeeklyHours * 60,
            clock = clock, alarmToday = alarmToday, ringOnWatch = settings.watchAlarm != com.bamnat.app.data.AppSettings.WATCH_ALARM_OFF,
            hourly = settings.workStyle == com.bamnat.core.work.WorkStyle.HOURLY, noun = noun, recentAmounts = recentAmounts,
            sleepOn = settings.sleepTips, asleepSince = asleepSince.takeIf { settings.sleepTips },
            tasks = com.bamnat.core.work.WatchTask.of(taskRows, nowTime), taskHead = com.bamnat.core.memo.TaskList.headline(taskRows, nowTime))
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
                if (date == Today.date()) {
                    c.scheduleRepository.updateDay(date) { it.copy(alarmSkipped = true) }
                    // 2.0.0 [RVI-11] (검토 ALM-10) The phone says so, quietly, with 되돌리기: a brush on the wrist is never a silent alarm off.
                    WatchAlarmNotice.post(applicationContext, date)
                }
                WatchSync.push(applicationContext, force = true)
            }
            // 2.0.0 [RVI-11] 되돌리기 from the watch.
            WatchDays.ALARM_ON_PATH -> c.appScope.launch {
                val date = runCatching { LocalDate.parse(String(event.data)) }.getOrNull() ?: Today.date()
                WatchAlarmNotice.undo(applicationContext, date)
            }
            // [WEAR-06] 1.31 N2: 끄기 and 다시 울림 pressed on the watch while the phone's alarm rings.
            WatchDays.RING_OFF_PATH -> com.bamnat.app.alarm.AlarmService.fromWatch(applicationContext, snooze = false)
            WatchDays.RING_SNOOZE_PATH -> com.bamnat.app.alarm.AlarmService.fromWatch(applicationContext, snooze = true)
            // 1.38 [LEDH-14] 워치로 돈 적기.
            WatchDays.MONEY_PATH -> c.appScope.launch { WatchMoneyIn.write(applicationContext, String(event.data)) }
            // 2.0.0 [RVI-94] 잠들었어요·깼어요 from the wrist, then the days back so the tile says it.
            WatchDays.SLEEP_PATH -> c.appScope.launch {
                runCatching { WatchSleepIn.write(applicationContext, String(event.data)) }
                WatchSync.push(applicationContext, force = true)
            }
            // 2.0.0 [RVI-95] A to-do finished on the wrist, as the 할 일 위젯's circle finishes it.
            WatchDays.TASK_PATH -> c.appScope.launch {
                com.bamnat.core.work.WatchTask.readDone(String(event.data))?.let { (id, day, done) ->
                    runCatching {
                        val memo = c.memoRepository.get(id)?.takeUnless { it.inTrash }
                        if (memo != null && memo.isDoneOn(day) != done) c.memoRepository.save(memo.withDone(done, day, Today.millis()))
                    }
                }
                runCatching { com.bamnat.app.widget.TaskWidget.redraw(applicationContext) }
                WatchSync.push(applicationContext, force = true)
            }
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

/**
 * 2.0.0 [RVI-94] (검토 I03b-04, G5-M3) 잠들었어요·깼어요 from the wrist, the phone's side: the same sleep log as the phone's button, at
 * the time pressed, once even when the message came twice. Only while 오늘의 흐름 keeps the log (the tile sends nothing otherwise).
 */
object WatchSleepIn {
    private const val PREFS = "bamnat_watch_sleep"
    private const val K_SEEN = "seen"

    suspend fun write(context: Context, text: String) {
        val press = com.bamnat.core.work.WatchSleep.decode(text) ?: return
        val c = context.appContainer
        if (!c.settingsRepository.current.sleepTips) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(K_SEEN, "").orEmpty().split(',').filter { it.isNotBlank() }
        if (press.id in seen) return
        prefs.edit().putString(K_SEEN, (seen + press.id).takeLast(20).joinToString(",")).apply()
        if (press.asleep) c.sleepRepository.fellAsleep(press.atMillis) else c.sleepRepository.wokeUp(press.atMillis)
        c.telemetry.event("watch_sleep", if (press.asleep) "asleep" else "awake")
        runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(context) }
    }
}

/**
 * 1.38 [LEDH-14] 워치로 돈 적기, the phone's side: a line from the watch is written to the main 가계부 as spending today (or the day
 * the watch says), once even when it came twice. It is saved as typed by hand ([MoneySource.MANUAL]) with the note "워치에서 적음".
 */
object WatchMoneyIn {
    private const val PREFS = "bamnat_watch_money"
    private const val K_SEEN = "seen"

    /** 2.0.0 [RVI-10] The spending amounts written most often in the last 60 days (at least twice), for the watch's chips. */
    fun amounts(book: com.bamnat.core.money.MoneyBook, today: java.time.LocalDate): List<Long> {
        val lately = book.entries.filter { it.kind == com.bamnat.core.money.MoneyKind.EXPENSE && !it.date.isBefore(today.minusDays(60)) }.map { it.amount }
        val twice = lately.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        return com.bamnat.core.work.WatchMoney.recentAmounts(lately.filter { it in twice })
    }

    fun categories(book: com.bamnat.core.money.MoneyBook, today: java.time.LocalDate): List<String> {
        val shown = book.categoriesOf(com.bamnat.core.money.MoneyKind.EXPENSE).toSet()
        val used = book.entries.asSequence().filter { it.kind == com.bamnat.core.money.MoneyKind.EXPENSE && !it.date.isBefore(today.minusDays(60)) && it.category in shown }
            .groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(com.bamnat.core.work.WatchMoney.MAX_CATEGORIES)
        return (used + com.bamnat.core.work.WatchMoney.FALLBACK.filter { it in shown }).distinct().take(com.bamnat.core.work.WatchMoney.MAX_CATEGORIES)
    }

    suspend fun write(context: Context, text: String) {
        val line = com.bamnat.core.work.WatchMoney.decode(text) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(K_SEEN, "").orEmpty().split(',').filter { it.isNotBlank() }
        if (line.id in seen) return
        prefs.edit().putString(K_SEEN, (seen + line.id).takeLast(50).joinToString(",")).apply()
        val c = context.appContainer
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(line.atMillis), Today.zone()).takeIf { !it.toLocalDate().isAfter(Today.date()) } ?: Today.dateTime()
        val money = c.calendars.money(PersonalCalendar.MAIN)
        val book = money.book.first()
        val category = line.category.takeIf { it in book.categoriesOf(com.bamnat.core.money.MoneyKind.EXPENSE) } ?: com.bamnat.core.money.MoneyCategories.OTHER
        money.save(com.bamnat.core.money.MoneyEntry(date = at.toLocalDate(), kind = com.bamnat.core.money.MoneyKind.EXPENSE, amount = line.amount,
            category = category, note = "워치에서 적음", time = at.toLocalTime().withSecond(0).withNano(0),
            accountId = com.bamnat.core.money.MoneyAccounts.lastUsed(book.entries, com.bamnat.core.money.MoneyKind.EXPENSE, book.accounts)))
        runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(context) }
    }
}
