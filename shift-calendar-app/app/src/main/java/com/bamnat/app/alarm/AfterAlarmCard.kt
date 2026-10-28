package com.bamnat.app.alarm

import android.content.Context
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.appContainer
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [ALRM-16] 1.31 N3 (상세리뷰 N3 '출근 전 오늘 할 일 카드'): right after a shift alarm is turned off, one quiet notice with the day
 * at a glance: "오늘 야간 19:00~07:00" over "이번 주 38시간 · 52시간까지 14시간 · 내일 쉬는 날". Worked out on the phone from the
 * calendar (nothing is sent); it goes by itself after [KEEP_MS]. On by default; 설정 › 알림과 잠 › 알람 뒤 오늘 카드 turns it off,
 * and the notice says so.
 */
object AfterAlarmCard {
    const val ID = 7316
    /** Long enough to read on the way to work. */
    const val KEEP_MS = 3 * 60 * 60 * 1000L
    const val OFF_HINT = "끄려면 설정 › 알림과 잠 › 알람 뒤 오늘 카드"

    /** [post] in the app's background, for the alarm that was just turned off. */
    fun postSoon(context: Context) {
        val app = context.applicationContext
        app.appContainer.appScope.launch { runCatching { post(app) } }
    }

    suspend fun post(context: Context, today: LocalDate = Today.date()) {
        val c = context.appContainer
        val s = c.settingsRepository.current
        if (!s.afterAlarmCard || !Notifications.canPost(context)) return
        val snapshot = c.scheduleRepository.snapshot.first()
        if (snapshot.isEmpty) return
        val (title, text) = lines(snapshot.resolver, today, s.weekStart, s.limits)
        val n = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(KEEP_MS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$OFF_HINT"))
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.TODAY, 3316))
            .build()
        Notifications.post(context, ID, n)
    }

    /** "오늘 야간 19:00~07:00" over "이번 주 38시간 · 52시간까지 14시간 · 내일 쉬는 날". */
    fun lines(resolver: ScheduleResolver, today: LocalDate, weekStart: DayOfWeek, limits: com.bamnat.core.hours.HourLimits): Pair<String, String> {
        val week = WorkHoursCalculator(resolver, weekStart, limits).glance(today)
        val tomorrow = TodayShiftNotice.describe(resolver.resolve(today.plusDays(1)))
        val parts = listOfNotNull(if (week.minutes > 0) "이번 주 ${week.text}" else null, "내일 $tomorrow")
        return "오늘 ${TodayShiftNotice.describe(resolver.resolve(today))}" to parts.joinToString(" · ")
    }
}
