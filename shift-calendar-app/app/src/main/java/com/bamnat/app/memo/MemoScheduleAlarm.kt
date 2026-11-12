package com.bamnat.app.memo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.alarm.AlarmService
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.appContainer
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoSchedules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.bamnat.core.time.Today

/**
 * [SCHED-01] Memos that are schedules: one system alarm for the next of them. At that minute each
 * memo shows a notice, rings like an alarm, or both, as the user chose. A ringing schedule is set
 * as an alarm clock, so it is on time in Doze like the shift alarm; a notice-only one is exact
 * when the phone allows it.
 */
object MemoScheduleAlarm {
    private const val REQUEST = 3201
    private const val NOTIFICATION_BASE = 7400
    private const val EXTRA_AT = "at"
    /** A schedule delivered later than this (the phone was off) is not announced any more. */
    private const val STALE_AFTER_MILLIS = 2 * 60 * 60 * 1000L

    /**
     * 1.39 [TASK-44] [shiftsOf]: the schedule a 근무마다·쉬는 날마다 memo goes by, so a repeating schedule rings on each of its days; a
     * repeating memo's 미루기 is read from [MemoSnoozeStore].
     */
    fun reschedule(
        context: Context, memos: List<Memo>, after: LocalDateTime = Today.dateTime(),
        shiftsOf: (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = { null },
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // 2.0.0 [RVI-22] From the first schedule on, what is missed (the phone off, an alarm late) can be told.
        if (lastTold(context) == null) markTold(context, Today.dateTime())
        val extra = MemoSnoozeStore.all(context)
        val days = MemoSnoozeStore.days(context)
        val next = MemoSchedules.next(memos, after, shiftsOf, extra, days)
        if (next == null) {
            am.cancel(pending(context, 0L))
            return
        }
        val at = next.atZone(Today.zone()).toInstant().toEpochMilli()
        val op = pending(context, at)
        val rings = MemoSchedules.dueAt(memos, next, shiftsOf, extra, days).any { it.reminder?.ring == true }
        when {
            rings -> AlarmScheduler.setAlarm(context, am, at, op)
            Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms() -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    /** 1.39 [TASK-44] [reschedule] from the saved memos, with the schedule each repeating one goes by. */
    suspend fun rescheduleNow(context: Context, after: LocalDateTime = Today.dateTime()) {
        val container = context.appContainer
        val memos = container.memoRepository.memos.first().filter { it.reminder != null }
        reschedule(context, memos, after, shiftsFor(context, memos))
        // 2.0.0 [RVIB-05] The next day off's 지난 할 일 card goes with the memos and the calendar.
        MemoRestCard.plan(context)
    }

    /** The schedule each 근무마다·쉬는 날마다 memo of [memos] goes by (one reading per calendar); null for any other. */
    suspend fun shiftsFor(context: Context, memos: List<Memo>): (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = runCatching {
        val container = context.appContainer
        container.memoRepository.shiftLookup(memos, container.calendars.active.value).first()
    }.getOrDefault { _: Memo -> null }

    private fun pending(context: Context, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, MemoScheduleReceiver::class.java).putExtra(EXTRA_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What the memo is called in a notice or on the ringing screen. */
    fun label(memo: Memo): String = memo.title.ifBlank { memo.preview }.ifBlank { "일정" }.take(40)

    private val time = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    /**
     * PC 8차 de44acdf: Android 16 bundles two of an app's notices that are not in a real group (a group summary with no children
     * counts as none) into '밤낮 · 2', so 끝내기 took two taps. Memo schedules share one notice ([SLOT]): the newest is shown with
     * its own buttons and '남은 일정 N개' says what else is waiting; when it is finished or put off, the next one comes back, quietly.
     * The notice is the one child of its own group ([GROUP], a summary that Android hides with a single child), so it is not
     * bundled with the app's other notices either.
     */
    internal const val SLOT = NOTIFICATION_BASE
    private const val SUMMARY = NOTIFICATION_BASE - 1
    internal const val GROUP = "bamnat_memo"

    /**
     * 1.39 [TASK-04][TASK-20][TASK-39] [rest] is when to tell on the memo's calendar's next day off (its morning, the afternoon after a
     * 야간; null: none ahead), offered first in 다른 때….
     */
    fun post(context: Context, memo: Memo, at: LocalDateTime, rest: LocalDateTime? = null) = postAll(context, listOf(Told(memo, at, rest)))

    /**
     * One memo to tell: due at [at], for [day] (2.0.0 [RVI-19], the day 끝내기 finishes), [rest] for its 미루기. [missed]: its time went by
     * while the phone was off or the alarm came late (2.0.0 [RVI-22], 검토 MEMO-02), so it is told, not rung.
     */
    data class Told(val memo: Memo, val at: LocalDateTime, val rest: LocalDateTime?, val day: java.time.LocalDate = at.toLocalDate(), val missed: Boolean = false)

    /** Memos due together are told with one sound: the queue takes them all, then the newest is shown. */
    internal fun postAll(context: Context, told: List<Told>) {
        if (told.isEmpty()) return
        // 2.0.0 [RVI-99] (검토 I07aa-02) Two or more missed ones are one quiet card of their own, with 모두 끝내기 and 쉬는 날로.
        val missed = told.filter { it.missed }
        val due = if (missed.size >= MemoMissed.CARD_FROM) { MemoMissed.add(context, missed); told - missed.toSet() } else told
        if (due.isEmpty()) return
        MemoNoticeQueue.prune(context)
        due.forEach { t ->
            val memo = t.memo
            val body = memo.preview.takeIf { it.isNotBlank() && memo.title.isNotBlank() }
            val head = if (t.missed) "놓친 일정 · ${missedWhen(t.at)}" else "${t.at.format(time)} 일정"
            MemoNoticeQueue.put(context, MemoNoticeQueue.Entry(memo.id, t.at, t.rest, label(memo), listOfNotNull(head, body).joinToString(" · "), day = t.day))
        }
        clearOldNotices(context)
        // Cancelled first, so the new schedule sounds even when an older one is up in the same notice.
        Notifications.cancel(context, SLOT)
        show(context, quiet = false)
    }

    /** Memo [id]'s notice is done with (끝내기, 미루기): the next waiting one is shown quietly, or the notice goes. */
    fun dismiss(context: Context, id: Long) {
        MemoNoticeQueue.remove(context, id)
        MemoNoticeQueue.prune(context)
        show(context, quiet = true)
    }

    /**
     * 2.0.0 [RVI-19] (검토 MEMO-04) The saved memos changed: one finished anywhere (the app, the 할 일 위젯, the calendar) on the day
     * its notice or 미루기 is for, put in the trash, deleted or no longer a schedule, is taken off the notice and its 미루기 let go,
     * as the notice's own 끝내기 does. [memos]: every memo with a schedule.
     */
    fun settle(context: Context, memos: List<Memo>) {
        val byId = memos.associateBy { it.id }
        fun over(id: Long, day: java.time.LocalDate?): Boolean {
            val m = byId[id] ?: return true
            return m.inTrash || m.reminder == null || m.isDoneOn(day ?: m.date)
        }
        // 2.0.0 [RVI-99] And off the 놓친 일정 card.
        MemoMissed.settle(context) { id, day -> over(id, day) }
        val gone = MemoNoticeQueue.all(context).filter { over(it.memo, it.day ?: it.at.toLocalDate()) }
        gone.forEach { MemoNoticeQueue.remove(context, it.memo) }
        if (gone.isNotEmpty()) show(context, quiet = true)
        val days = MemoSnoozeStore.days(context)
        MemoSnoozeStore.all(context).keys.filter { over(it, days[it]) }.forEach { MemoSnoozeStore.remove(context, it) }
    }

    /** "오후 2:05", or "어제 오후 11:50" and "11/9 오전 9:00" for an older one. */
    private fun missedWhen(at: LocalDateTime): String = when (at.toLocalDate()) {
        Today.date() -> at.format(time)
        Today.date().minusDays(1) -> "어제 ${at.format(time)}"
        else -> "${at.monthValue}/${at.dayOfMonth} ${at.format(time)}"
    }

    private const val PREFS = "memo_schedule"
    private const val K_TOLD = "told_until"

    /** 2.0.0 [RVI-22] Up to when schedules have been told (minutes, local); null before the first. */
    internal fun lastTold(context: Context): LocalDateTime? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong(K_TOLD, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { LocalDateTime.ofEpochSecond(it * 60, 0, java.time.ZoneOffset.UTC) }

    internal fun markTold(context: Context, at: LocalDateTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_TOLD, at.toEpochSecond(java.time.ZoneOffset.UTC) / 60).apply()
    }

    /** 2.0.0 [RVI-22] How far back missed schedules are told after the phone was off, and how many at most. */
    private const val MISSED_DAYS = 3L
    private const val MISSED_MAX = 20

    private fun show(context: Context, quiet: Boolean) {
        val waiting = MemoNoticeQueue.all(context)
        val top = waiting.firstOrNull()
        if (top == null) {
            Notifications.cancel(context, SLOT)
            Notifications.cancel(context, SUMMARY)
            return
        }
        val others = waiting.drop(1)
        val otherLine = others.joinToString(" · ") { "${it.title}(${it.at.format(time)})" }
        val keep = keepFor(top.at)
        val notification = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(top.title)
            .setContentText(top.text)
            .apply {
                if (others.isNotEmpty()) {
                    setSubText("남은 일정 ${others.size}개")
                    setStyle(NotificationCompat.BigTextStyle().bigText("${top.text}\n남은 일정: $otherLine"))
                    setNumber(waiting.size)
                }
            }
            // 2.0.0 [RVI-26] (검토 MEMO-13) Opens that memo, not the memo tab; swiped away, what it showed does not come back.
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MEMO_OPEN, REQUEST + 1,
                payload = "${top.memo}|${top.day ?: top.at.toLocalDate()}"))
            .setDeleteIntent(MemoNoticeClearedReceiver.intent(context))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(quiet)
            // PC 7차 c92cec3e: a notice clears itself after its day (at least 6 hours on).
            .setTimeoutAfter(keep)
            .setWhen(top.at.atZone(Today.zone()).toInstant().toEpochMilli())
            // 1.39 [TASK-39] 끝내기 · 10분 뒤 · 다른 때… (두 앱 실사용 2차): 끝내기 finishes the memo on that day for real (the 할 일 앱's
            // Complete left the task open in its list), and 다른 때… opens a small window with the day off's morning first.
            // 2.0.0 [RVI-19] (검토 MEMO-03) The day it is for: a 미루기 past midnight finishes its first day, not the next.
            .addAction(0, "끝내기", MemoDoneReceiver.intent(context, top.memo, top.day ?: top.at.toLocalDate()))
            .addAction(0, com.bamnat.core.memo.MemoSnooze.label(com.bamnat.core.memo.MemoSnooze.Way.MIN10, top.rest),
                MemoSnoozeReceiver.intent(context, top.memo, com.bamnat.core.memo.MemoSnooze.Way.MIN10, top.rest, 0, top.day ?: top.at.toLocalDate()))
            .addAction(0, "다른 때…", MemoLaterActivity.intent(context, top.memo, top.rest, top.day ?: top.at.toLocalDate()))
            .build()
        val summary = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("일정")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(keep)
            .build()
        Notifications.post(context, SLOT, notification)
        Notifications.post(context, SUMMARY, summary)
    }

    /** A notice for [at] stays until the end of its day, and at least 6 hours (a late-evening one is not gone at midnight). */
    internal fun keepFor(at: LocalDateTime, now: Long = Today.millis()): Long {
        val dayEnd = at.toLocalDate().plusDays(1).atStartOfDay(Today.zone()).toInstant().toEpochMilli()
        return maxOf(dayEnd - now, KEEP_AT_LEAST)
    }

    internal const val KEEP_AT_LEAST = 6 * 60 * 60 * 1000L

    /** The one-notice-per-memo notices of the builds before (7400 + memo id) are taken down; the shared one replaces them. */
    private fun clearOldNotices(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        runCatching { nm.activeNotifications }.getOrNull().orEmpty()
            // Only memo notices: the memo channel, no tag (the date notices on that channel carry one), an old memo id.
            .filter { it.tag == null && it.notification.channelId == Notifications.MEMO_CHANNEL_ID && it.id in SLOT + 1..NOTIFICATION_BASE + 0x3ff }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    /**
     * The memo alarm for [atMillis] came (0: none, after a reboot or a new clock). Due ones are told and rung; 2.0.0 [RVI-22] (검토
     * MEMO-02) so is, quietly in the same notice, every schedule since the last told one that went by unsaid (the phone off, the alarm
     * late, more than one in the minutes it was late), within [MISSED_DAYS]. One more than 2 hours late is told as missed, not rung.
     */
    internal fun handle(
        context: Context, memos: List<Memo>, atMillis: Long, now: Long = Today.millis(), restOf: (Memo) -> LocalDateTime? = { null },
        shiftsOf: (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = { null },
        /** 2.0.0 [RVIB-05] The end of the work a time falls in (null: not at work, or 근무 중 알림 모으기 off). */
        workEnd: (LocalDateTime) -> LocalDateTime? = { null },
    ) {
        val nowLocal = AlarmScheduler.millisToLocal(now)
        val extra = MemoSnoozeStore.all(context)
        val days = MemoSnoozeStore.days(context)
        val at = if (atMillis == 0L) null else AlarmScheduler.millisToLocal(atMillis).withSecond(0).withNano(0)
        val stale = atMillis != 0L && now - atMillis > STALE_AFTER_MILLIS
        val due = if (at == null || stale) emptyList() else MemoSchedules.dueOn(memos, at, shiftsOf, extra, days)
        val since = lastTold(context)?.let { maxOf(it, nowLocal.minusDays(MISSED_DAYS)) }
        val missed = if (since == null) emptyList() else MemoSchedules.between(memos, since, nowLocal, shiftsOf, extra, days, MISSED_MAX)
            .filter { (t, d) -> t != at && due.none { it.memo.id == d.memo.id } }
        markTold(context, maxOf(nowLocal, at ?: nowLocal))
        // 2.0.0 [RVIB-05] (검토 I07ac-02) A notice-only one due at work waits, and comes with the others of that work when it ends.
        val told = due.filter { it.memo.reminder?.notify == true }
        val end = at?.let(workEnd)
        val (held, now2) = if (end == null) emptyList<com.bamnat.core.memo.MemoSchedules.Due>() to told
            else told.partition { com.bamnat.core.memo.MemoBacklog.waits(it.memo) }
        if (end != null) MemoMissed.hold(context, held.map { Told(it.memo, at!!, restOf(it.memo), it.day) }, end)
        postAll(context, missed.map { (t, d) -> Told(d.memo, t, restOf(d.memo), d.day, missed = true) } +
            now2.map { Told(it.memo, at!!, restOf(it.memo), it.day) })
        val ringing = due.filter { it.memo.reminder?.ring == true }.map { it.memo }
        if (ringing.isEmpty()) return
        // [ALRM-09] 오늘 남은 알람 모두 끄기: the schedule still shows, quietly, instead of ringing.
        if (com.bamnat.app.alarm.AlarmsOff.isOff(context, now)) {
            postAll(context, due.filter { it.memo.reminder?.ring == true && it.memo.reminder?.notify != true }.map { Told(it.memo, at!!, restOf(it.memo), it.day) })
            return
        }
        val ring = RingRequest(
            shiftName = "",
            shiftDate = at!!.toLocalDate(),
            shiftStart = at.toLocalTime(),
            offsetMinutes = 0,
            soundUri = null,
            triggerAtMillis = atMillis,
            custom = true,
            label = ringing.joinToString(" · ") { label(it) }.take(60),
        )
        // Not allowed to start the ringing screen from here: the loud notice rings instead.
        AlarmService.startOrFallback(context, ring)
    }
}

class MemoScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val atMillis = intent.getLongExtra("at", 0L)
        val pending = goAsync()
        container.appScope.launch {
            try {
                run(context, atMillis)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * The memo alarm for [atMillis] (0 after a reboot or a new clock, 2.0.0 [RVI-22]: what went by meanwhile is told as missed),
         * then the next one set.
         */
        suspend fun run(context: Context, atMillis: Long) {
            val container = context.appContainer
            // Read again now, so a memo changed or deleted since the alarm was set is not announced.
            val memos = container.memoRepository.memos.first()
            // 1.39 [TASK-44] A 근무마다·쉬는 날마다 schedule is due on its days only, as the calendar says now.
            val shifts = MemoScheduleAlarm.shiftsFor(context, memos.filter { it.reminder != null })
            val extra = MemoSnoozeStore.all(context)
            val days = MemoSnoozeStore.days(context)
            // 1.39 [TASK-20] Each due memo's next day off, on the calendar it goes by, for its notice's 미루기.
            val due = if (atMillis == 0L) emptyList() else MemoSchedules.dueAt(memos, AlarmScheduler.millisToLocal(atMillis), shifts, extra, days)
            val rests = HashMap<Long, LocalDateTime?>()
            due.forEach { rests[it.id] = MemoSnoozeReceiver.nextRest(container, it) }
            // 2.0.0 [RVIB-05] 근무 중 알림 모으기: the work on the calendar on screen; what waited for a work that has ended (a restart
            // drops the alarm) comes now.
            MemoMissed.release(context)
            val workEnd = MemoRestCard.workEnd(context)
            MemoScheduleAlarm.handle(context, memos, atMillis, restOf = { m -> if (m.id in rests) rests[m.id] else null }, shiftsOf = shifts, workEnd = workEnd)
            // A repeating memo's 미루기 that has now rung (or went by) is let go.
            MemoSnoozeStore.dropUpTo(context, if (atMillis != 0L) maxOf(AlarmScheduler.millisToLocal(atMillis), Today.dateTime()) else Today.dateTime())
            // 1.39 [TASK-39] The schedule's time has come: the 할 일 위젯 shows it late (red) from now, not at the next redraw (PC 6차).
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
            val after = if (atMillis != 0L) AlarmScheduler.millisToLocal(atMillis) else Today.dateTime()
            MemoScheduleAlarm.reschedule(context, memos, maxOf(after, Today.dateTime().minusSeconds(1)), shifts)
            MemoRestCard.plan(context)
        }
    }
}

/** 2.0.0 [RVI-26] (검토 MEMO-13) The shared memo notice swiped away: what it showed is seen and does not come back with the next one. */
class MemoNoticeClearedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MemoNoticeQueue.clear(context)
    }

    companion object {
        private const val REQUEST = 3203

        fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, MemoNoticeClearedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * 2.0.0 [RVI-27] (검토 ALM-21) A notice button's own address (memo id and what it does), so two memos whose ids are 1024 apart no
 * longer share one PendingIntent (the request code kept the low 10 bits only) and one button finish the other memo.
 */
internal fun memoButtonData(kind: String, memo: Long, slot: Int = 0): android.net.Uri = android.net.Uri.parse("bamnat-memo://$kind/$memo/$slot")

/**
 * 1.39 [TASK-04][TASK-20] A 미루기 pressed on a memo's schedule notice: the schedule moves to 10분 뒤, 1시간 뒤 or the next day
 * off's morning (내일 when there is none), the notice goes, and the one system alarm is set again from the saved memos.
 */
class MemoSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val id = intent.getLongExtra(EXTRA_MEMO, -1L)
        val way = com.bamnat.core.memo.MemoSnooze.Way.of(intent.getStringExtra(EXTRA_WAY)) ?: return
        val rest = restAt(intent)
        val day = dayOf(intent)
        val pending = goAsync()
        container.appScope.launch {
            try {
                put(context, id, way, rest, day)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MEMO = "memo"
        private const val EXTRA_WAY = "way"
        private const val EXTRA_REST = "restAt"
        private const val EXTRA_DAY = "day"
        private const val REQUEST_BASE = 0x40000

        /** 2.0.0 [RVI-19] The day the schedule put off was for. */
        internal fun putDay(intent: Intent, day: java.time.LocalDate?): Intent = intent.apply { day?.let { putExtra(EXTRA_DAY, it.toEpochDay()) } }

        internal fun dayOf(intent: Intent): java.time.LocalDate? =
            intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)

        /** 1.39 [TASK-39] The day off's time, as minutes (a notice posted before this kept the day only, under "rest", and is let be). */
        internal fun putRest(intent: Intent, rest: LocalDateTime?): Intent =
            intent.apply { rest?.let { putExtra(EXTRA_REST, it.toEpochSecond(java.time.ZoneOffset.UTC) / 60) } }

        internal fun restAt(intent: Intent): LocalDateTime? = intent.getLongExtra(EXTRA_REST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            ?.let { LocalDateTime.ofEpochSecond(it * 60, 0, java.time.ZoneOffset.UTC) }

        fun intent(context: Context, memo: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: LocalDateTime?, slot: Int, day: java.time.LocalDate? = null): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_BASE + (memo.toInt() and 0x3ff) * 4 + slot,
                Intent(context, MemoSnoozeReceiver::class.java).setData(memoButtonData("snooze", memo, slot))
                    .putExtra(EXTRA_MEMO, memo).putExtra(EXTRA_WAY, way.key)
                    .let { putRest(it, rest) }.let { putDay(it, day) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /**
         * Memo [id]'s schedule moved [way] from now, its notice taken down; the new time, or null when it is gone. 2.0.0 [RVI-19]
         * [day]: the day it was for, kept with a repeating memo's 미루기 so 끝내기 after midnight finishes that day.
         */
        suspend fun put(context: Context, id: Long, way: com.bamnat.core.memo.MemoSnooze.Way, rest: LocalDateTime?, day: java.time.LocalDate? = null): LocalDateTime? {
            MemoScheduleAlarm.dismiss(context, id)
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id) ?: return null
            val r = memo.reminder ?: return null
            val at = com.bamnat.core.memo.MemoSnooze.at(way, Today.dateTime(), r.at, rest)
            // 1.39 [TASK-44] A repeating memo keeps its time of day for the days after: this 미루기 is once only, kept on the side.
            if (memo.date != null && com.bamnat.core.memo.MemoRepeat.kindOf(memo.repeat) != com.bamnat.core.memo.MemoRepeat.Kind.NONE) {
                MemoSnoozeStore.put(context, id, at, day ?: MemoSnoozeStore.days(context)[id] ?: Today.date())
                MemoScheduleAlarm.rescheduleNow(context)
            } else {
                // 2.0.0 [RVI-56] (검토 U07a-14) Not an edit: the list keeps its order.
                repo.save(memo.copy(reminder = r.copy(at = at)), touch = false)
            }
            return at
        }

        /**
         * When to tell on the next day off of the calendar [memo] goes by, or null. 1.39 [TASK-39] PC 6차: a day the schedule leaves
         * empty counts (평일 일근's 토요일), and the day after a 야간 is told in the afternoon ([com.bamnat.core.memo.ShiftMemoTime.offTime]).
         */
        suspend fun nextRest(container: com.bamnat.app.AppContainer, memo: Memo): LocalDateTime? = runCatching {
            val cal = com.bamnat.app.data.MemoRepository.calendarOf(memo, container.calendars.active.value)
            val resolver = container.calendars.repository(cal).live.first().resolver
            restAfter(Today.date(), resolver::resolve)
        }.getOrNull()

        internal fun restAfter(today: java.time.LocalDate, resolve: (java.time.LocalDate) -> com.bamnat.core.schedule.ResolvedDay): LocalDateTime? {
            val day = com.bamnat.core.memo.MemoSnooze.nextRest(today) { d -> com.bamnat.core.memo.ShiftMemoTime.isRest(resolve(d)) } ?: return null
            return com.bamnat.core.memo.ShiftMemoTime.offTime(day, com.bamnat.core.memo.ShiftMemoTime.of(resolve(day.minusDays(1))))
        }
    }
}

/**
 * 1.39 [TASK-39] 끝내기 on a memo's schedule notice: the memo is finished on the notice's day (a repeating one that day only), in the
 * memo list and on the 할 일 위젯 at once. The 할 일 앱 seen in 실사용 2차 took its notice's Complete away but left the task open.
 */
class MemoDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val id = intent.getLongExtra(EXTRA_MEMO, -1L)
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay)
        val pending = goAsync()
        container.appScope.launch {
            try {
                finish(context, id, day)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MEMO = "memo"
        private const val EXTRA_DAY = "day"
        private const val REQUEST_BASE = 0x48000

        fun intent(context: Context, memo: Long, day: java.time.LocalDate): PendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_BASE + (memo.toInt() and 0x3ff),
            Intent(context, MemoDoneReceiver::class.java).setData(memoButtonData("done", memo)).putExtra(EXTRA_MEMO, memo).putExtra(EXTRA_DAY, day.toEpochDay()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Memo [id] finished on [day] (never brought back: a second press does nothing), its notice gone; null when it is gone. */
        suspend fun finish(context: Context, id: Long, day: java.time.LocalDate?): Memo? {
            MemoScheduleAlarm.dismiss(context, id)
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id)?.takeUnless { it.inTrash } ?: return null
            // A 미루기 still waiting on a repeating memo finished now does not ring.
            MemoSnoozeStore.remove(context, id)
            val saved = if (memo.isDoneOn(day)) memo else repo.save(memo.withDone(true, day, Today.millis()), touch = false)
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(context) }
            return saved
        }
    }
}

/**
 * 1.39 [TASK-44] A repeating memo's 미루기 (10분 뒤, 다른 때…): once only, so it is kept here (memo id → time) and its schedule keeps its
 * time of day for the days after. Let go once it has rung or the memo is finished; lost with the app's data, as a 미루기 may be.
 */
internal object MemoSnoozeStore {
    private const val PREFS = "memo_snoozes"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Map<Long, LocalDateTime> = runCatching {
        prefs(context).all.mapNotNull { (k, v) ->
            val id = k.toLongOrNull() ?: return@mapNotNull null
            val minutes = (v as? Long) ?: return@mapNotNull null
            id to LocalDateTime.ofEpochSecond(minutes * 60, 0, java.time.ZoneOffset.UTC)
        }.toMap()
    }.getOrDefault(emptyMap())

    /** 2.0.0 [RVI-19] (검토 MEMO-03) The day each 미루기 was for (kept beside it as "d<id>"; none for one put off before 2.0.0). */
    fun days(context: Context): Map<Long, java.time.LocalDate> = runCatching {
        prefs(context).all.mapNotNull { (k, v) ->
            val id = k.removePrefix(DAY).takeIf { k.startsWith(DAY) }?.toLongOrNull() ?: return@mapNotNull null
            id to java.time.LocalDate.ofEpochDay((v as? Long) ?: return@mapNotNull null)
        }.toMap()
    }.getOrDefault(emptyMap())

    fun put(context: Context, id: Long, at: LocalDateTime, day: java.time.LocalDate? = null) {
        prefs(context).edit().putLong(id.toString(), at.toEpochSecond(java.time.ZoneOffset.UTC) / 60)
            .apply { if (day != null) putLong(DAY + id, day.toEpochDay()) else remove(DAY + id) }.apply()
    }

    fun remove(context: Context, id: Long) {
        prefs(context).edit().remove(id.toString()).remove(DAY + id).apply()
    }

    fun dropUpTo(context: Context, at: LocalDateTime) {
        val gone = all(context).filterValues { !it.isAfter(at) }.keys
        if (gone.isNotEmpty()) prefs(context).edit().apply { gone.forEach { remove(it.toString()); remove(DAY + it) } }.apply()
    }

    private const val DAY = "d"
}
