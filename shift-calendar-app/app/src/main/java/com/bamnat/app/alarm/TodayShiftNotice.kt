package com.bamnat.app.alarm

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.words
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import com.bamnat.core.time.Today

/**
 * [FEAT-18] 잠금 화면 '오늘 근무': a quiet notice that stays, so today's and tomorrow's shift can be
 * read on the lock screen without unlocking. Android has no lock-screen widgets on phones, so this
 * stands in for one. Off by default; turned on in 설정 › 알림과 잠. It moves on at midnight with the widgets.
 */
object TodayShiftNotice {
    const val ID = 7314

    /** Posts or removes the notice; returns true while it is shown, so a midnight wake-up is kept. */
    suspend fun update(context: Context, today: LocalDate = Today.date()): Boolean {
        val container = context.appContainer
        if (!container.settingsRepository.current.todayShiftNotice || !Notifications.canPost(context)) {
            cancel(context)
            return false
        }
        val snapshot = container.scheduleRepository.snapshot.first()
        if (snapshot.isEmpty) {
            cancel(context)
            return false
        }
        val style = container.settingsRepository.current.workStyle
        val (dayLine, nextLine) = lines(snapshot.resolver, today, style)
        // 2.0.0 [RVI-97] (검토 I03c-03) During work on Android 16: how far through it, how long to its end (a bar, and a chip in the
        // status bar), drawn again every 15 minutes while the screen is on. Older phones keep the two lines as they were.
        val now = Today.dateTime()
        val work = listOf(today.minusDays(1), today).flatMap { com.bamnat.core.sleep.SleepSchedule.work(snapshot.resolver.resolve(it)) }
        val going = if (android.os.Build.VERSION.SDK_INT >= 36) com.bamnat.core.schedule.ShiftProgress.of(work, now) else null
        scheduleTick(context, going?.let { com.bamnat.core.schedule.ShiftProgress.nextDraw(it, now) }
            ?: work.map { it.from }.filter { it.isAfter(now) }.minOrNull())
        val untilEnd = com.bamnat.core.sleep.TodayFlow.WorkTalk.of(style).untilEnd
        val title = going?.let { com.bamnat.core.schedule.ShiftProgress.left(it, untilEnd) } ?: dayLine
        val text = if (going != null) "$dayLine · $nextLine" else nextLine
        // 1.39 [NTC-02] 겹친 규칙 알림의 나머지 한 줄 (이달 쓴 돈 …): this notice says it, so there is one notice, not two.
        val extra = folded(context, today)
        fun base() = NotificationCompat.Builder(context, Notifications.TODAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, 3314))
            // [TOWN-08] Its own group, so Android does not bundle it with 소식 or other notices (a tap on the bundle opened this).
            .setGroup(Notifications.GROUP_TODAY).setGroupSummary(true)
            .apply { going?.let { progress(this, it) } }
        val notification = if (extra == null) base().build() else base()
            .setContentText("$text · ${extra.second}")
            .apply { if (going == null) setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${extra.second}")) }
            // 1.36 [SEC-12] M5: a rule's words may hold the 가계부's numbers, so the lock screen keeps only the shifts.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base().build())
            .build()
        Notifications.post(context, ID, notification)
        return true
    }

    /** 2.0.0 [RVI-97] The bar from the start of work to its end in the shift's colour, the time left counting down, and the chip. */
    private fun progress(b: NotificationCompat.Builder, n: com.bamnat.core.schedule.ShiftProgress.Now) {
        val color = (n.colorArgb ?: 0xFF5E6AD2).toInt()
        b.setStyle(NotificationCompat.ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(n.totalMinutes.toInt().coerceAtLeast(1)).setColor(color)))
            .setProgress(n.elapsedMinutes.toInt().coerceIn(0, n.totalMinutes.toInt().coerceAtLeast(1)))
            .setStyledByProgress(true))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(com.bamnat.core.schedule.ShiftProgress.chip(n))
            .setWhen(n.to.atZone(Today.zone()).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
    }

    /** 2.0.0 [RVI-97] Drawn again at [at] (the next quarter hour on shift, else the next work's start); quiet, never waking the phone. */
    private fun scheduleTick(context: Context, at: java.time.LocalDateTime?) {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val pi = android.app.PendingIntent.getBroadcast(context, REQ_TICK, android.content.Intent(context, TodayShiftTickReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        if (at == null) { am.cancel(pi); return }
        val millis = at.atZone(Today.zone()).toInstant().toEpochMilli()
        runCatching { am.setWindow(android.app.AlarmManager.RTC, millis, 60_000L, pi) }
    }

    private const val REQ_TICK = 3329

    private fun prefs(context: Context) = context.getSharedPreferences("today_notice_fold", Context.MODE_PRIVATE)

    /** 1.39 [NTC-02] The words a rule folded in today ([fold]), with the rule's name; gone at midnight. */
    fun folded(context: Context, today: LocalDate = Today.date()): Pair<String, String>? {
        val p = prefs(context)
        if (p.getString("day", null) != today.toString()) return null
        val text = p.getString("text", null)?.takeIf { it.isNotBlank() } ?: return null
        return p.getString("who", "").orEmpty() to text
    }

    /**
     * 1.39 [NTC-02] 겹치는 알림 하나로 (사용자 2026-11-08 사진 '오늘 휴무 / 내일 주간' 위에 '오늘 요약 / 오늘 휴무 ~ · 이달 쓴 돈'): rule
     * [who]'s 알림 said today's shift, which this notice already shows; the rest of its words ([rest], maybe none) go into this notice.
     * False when this notice is not showing (off, no 근무표, 알림 꺼짐), so the rule's own notice goes out as before.
     */
    suspend fun fold(context: Context, who: String, rest: String): Boolean {
        val today = Today.date()
        val p = prefs(context)
        val had = p.getString("text", null)?.takeIf { p.getString("day", null) == today.toString() }
        if (rest.isNotBlank()) p.edit().putString("day", today.toString()).putString("who", who)
            .putString("text", listOfNotNull(had?.takeIf { it != rest && rest !in it }, rest).joinToString(" · ").take(160)).apply()
        return update(context, today)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
        scheduleTick(context, null)
    }

    /** "오늘 주간 07:00~19:00" over "내일 야간 19:00~07:00". */
    fun lines(resolver: ScheduleResolver, today: LocalDate, style: com.bamnat.core.work.WorkStyle? = null): Pair<String, String> =
        "오늘 ${describe(resolver.resolve(today), style)}" to "내일 ${describe(resolver.resolve(today.plusDays(1)), style)}"

    /** 2.0.0 [RVH-41] 검토 T30-100: a day off is a shop's 휴무, a field worker's 빈 날 ([style]'s own word). */
    internal fun describe(day: ResolvedDay, style: com.bamnat.core.work.WorkStyle? = null): String {
        // The lock screen is seen by anyone nearby: the kind of leave shows only as a link shows it.
        val leave = day.leave?.let { ShareLinkPayload.leaveLabel(it, kinds = false) }
        val name = day.shiftType?.name?.takeIf { it.isNotBlank() }
        val start = day.start
        val end = day.end
        val work = when {
            day.isWorkDay && start != null && end != null -> "${name ?: "근무"} ${hm(start)}~${hm(end)}"
            day.isWorkDay -> name ?: "근무"
            else -> null
        }
        // 2.0.0 [RVI-66] (PC 통합 1차 N8·PC-39) A 대근 put on a day off said '오늘 휴무' here while the calendar, the night screen and
        // the alarm said 주간 대근: the day's extra work is read with it, on a day off as the day's work, beside the shift otherwise.
        val extra = (day.extras.firstOrNull { it.covered != null } ?: day.extras.firstOrNull())?.let { e ->
            val what = com.bamnat.app.ui.calendar.ExtraText.name(e, style)
            val from = e.from
            val to = e.to
            if (from != null && to != null && !com.bamnat.app.ui.calendar.ExtraText.namesHours(e)) "$what ${hm(from.toLocalTime())}~${hm(to.toLocalTime())}"
            else what
        }
        return when {
            // A whole day off shows the leave alone; 반차 and the like keep the shift beside it.
            leave != null && (day.leave?.isFullDay == true || work == null) -> if (extra != null) "$leave · $extra" else leave
            leave != null -> "$work · $leave"
            work != null -> if (extra != null) "$work · $extra" else work
            extra != null -> extra
            name != null -> name
            else -> style.words().offDay
        }
    }

    private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}

/** 2.0.0 [RVI-97] Draws 잠금 화면 '오늘 근무' again: the bar moves on during work, and starts when work starts. */
class TodayShiftTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try { runCatching { TodayShiftNotice.update(context) } } finally { pending.finish() }
        }
    }
}
