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
        // 1.38 PC 12.1 (식당에 '이번 주 시간(52시간까지)'): the week's hours against the legal limit are a wage worker's; a shop, a
        // freelancer or an investor gets no card (the setting is on by default and its row shows only to work with a 근무표).
        if (s.workStyle?.usesPayEngine == false) return
        val snapshot = c.scheduleRepository.snapshot.first()
        if (snapshot.isEmpty) return
        // PC 267560dd: after an alarm three notices said "오늘 야간 19:00~07:00". When 잠금 화면 '오늘 근무' or the day's planning
        // notice (with its nap advice) already says the shift, this card adds only the week.
        val said = s.todayShiftNotice || showing(context, Notifications.BRIEFING_ID)
        val (title, text) = lines(snapshot.resolver, today, s.weekStart, s.limits, shiftSaid = said) ?: return
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
            // [EASE-19] Turning the alarm off opened nothing; the day is one tap away here.
            .addAction(0, "오늘 보기", Notifications.open(context, NoticeLinks.Target.TODAY, 3317))
            // 1.39 [FLOW-16] 아침 한 장 in the same card: last night's sleep not yet answered opens the morning card on the sleep screen.
            .apply { if (morningAsk(c)) addAction(0, "잘 잤어요?", Notifications.open(context, NoticeLinks.Target.SLEEP, 3318)) }
            .build()
        Notifications.post(context, ID, n)
    }

    /**
     * 1.39 [FLOW-16] A sleep that ended in the last hours has no morning answer yet (SleepMorning.pending), or the sleep is still
     * open (깼어요 not pressed: the sleep screen has it, and the morning card follows).
     */
    private suspend fun morningAsk(c: com.bamnat.app.AppContainer): Boolean = runCatching {
        val log = c.sleepRepository.recent(days = 2).first()
        val checks = c.sleepRepository.checks(days = 2).first()
        log.any { it.end == null } || com.bamnat.core.sleep.SleepMorning.pending(log, checks, Today.dateTime()) != null
    }.getOrDefault(false)

    private fun showing(context: Context, id: Int): Boolean = runCatching {
        context.getSystemService(android.app.NotificationManager::class.java)?.activeNotifications?.any { it.id == id } == true
    }.getOrDefault(false)

    /** "오늘 야간 19:00~07:00" over "이번 주 38시간 · 52시간까지 14시간 · 내일 쉬는 날"; null when there is nothing to add. */
    fun lines(resolver: ScheduleResolver, today: LocalDate, weekStart: DayOfWeek, limits: com.bamnat.core.hours.HourLimits,
              shiftSaid: Boolean = false): Pair<String, String>? {
        val week = WorkHoursCalculator(resolver, weekStart, limits).glance(today)
        // Another notice says the shift already: the week alone, or nothing.
        if (shiftSaid) return if (week.minutes > 0) "이번 주 ${week.text}" to "내일 ${TodayShiftNotice.describe(resolver.resolve(today.plusDays(1)))}" else null
        val tomorrow = TodayShiftNotice.describe(resolver.resolve(today.plusDays(1)))
        val parts = listOfNotNull(if (week.minutes > 0) "이번 주 ${week.text}" else null, "내일 $tomorrow")
        return "오늘 ${TodayShiftNotice.describe(resolver.resolve(today))}" to parts.joinToString(" · ")
    }
}
