package com.bamnat.app.sleep

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.time.Today
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVI-89] (검토 I03b-02) 측정 시작 권하기. Measuring the night's sounds starts only with a tap before bed, and the nights it
 * was forgotten are the ones missed. Around the time to sleep for the next alarm (the target sleep before it, an hour either
 * side), putting the phone on its charger asks once, quietly, "오늘 밤 소리 잴까요?"; the tap opens the app and starts there (a
 * microphone may only start from the front, as the bedtime notice's 측정 시작 does). Only on a phone that has measured before,
 * with the microphone given, not while measuring or asleep. Off with 설정 › 알림과 잠 › [ROW].
 */
object SleepRecordOffer {
    const val ROW = "충전하면 소리 재기 묻기"
    private const val JOB_ID = 4410
    private const val REQ_OPEN = 3410
    const val NOTICE_ID = 7322
    private const val PREFS = "sleep_record_offer"
    private const val K_ASKED = "asked"
    /** How far before and after the time to sleep the charger asks. */
    const val BEFORE_MINUTES = 60L
    const val AFTER_MINUTES = 90L

    /** The window the charger asks in for an alarm at [alarm] after [targetMinutes] of sleep: null with no alarm. */
    fun window(alarm: LocalDateTime?, targetMinutes: Int): ClosedRange<LocalDateTime>? {
        val bed = alarm?.minusMinutes(targetMinutes.toLong()) ?: return null
        return bed.minusMinutes(BEFORE_MINUTES)..bed.plusMinutes(AFTER_MINUTES)
    }

    /** Planned with the alarms: one waiting job that runs once the phone charges inside the window. */
    fun plan(context: Context, alarm: LocalDateTime?) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        val c = context.appContainer
        val s = c.settingsRepository.current
        val w = window(alarm, s.sleepTargetMinutes)
        val now = Today.dateTime()
        if (!s.recordOffer || w == null || w.endInclusive.isBefore(now)) {
            jobs.cancel(JOB_ID)
            return
        }
        val delay = Duration.between(now, w.start).toMillis().coerceAtLeast(0L)
        runCatching {
            jobs.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, SleepRecordOfferJob::class.java))
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(Duration.between(now, w.endInclusive).toMillis().coerceAtLeast(delay))
                    .setRequiresCharging(true)
                    .build(),
            )
        }
    }

    /** The job came (charging, or the window's end): asks when it is still the time and all is right for it. */
    internal suspend fun maybeAsk(context: Context, alarm: LocalDateTime?) {
        val c = context.appContainer
        val s = c.settingsRepository.current
        val now = Today.dateTime()
        val w = window(alarm, s.sleepTargetMinutes) ?: return
        val charging = (context.getSystemService(android.os.BatteryManager::class.java))?.isCharging == true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val night = w.start.toLocalDate().toString()
        val asleep = c.sleepRepository.recent(days = 2).first().any { e ->
            e.end == null && !e.imported && Duration.between(e.start, now).toMinutes() < SleepDebt.MAX_SLEEP_MINUTES
        }
        if (!s.recordOffer || now !in w || !charging || asleep || prefs.getString(K_ASKED, null) == night) return
        if (SleepRecordService.recording.value != null || SleepRecordService.lastNight(context) == null) return
        if (!runCatching { SleepRecordService.allowed(context) }.getOrDefault(false)) return
        prefs.edit().putString(K_ASKED, night).apply()
        val n = NotificationCompat.Builder(context, Notifications.DAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setContentTitle("오늘 밤 소리 잴까요?")
            .setContentText("충전 중이에요 · 누르면 바로 재기 시작해요")
            .setStyle(NotificationCompat.BigTextStyle().bigText("충전 중이에요 · 누르면 바로 재기 시작해요\n묻지 않으려면 설정 › 알림과 잠 › $ROW"))
            .setContentIntent(PendingIntent.getActivity(context, REQ_OPEN,
                NoticeLinks.intent(context, NoticeLinks.Target.SLEEP, payload = SleepRecordService.FROM_NOTICE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.between(now, w.endInclusive).toMillis().coerceAtLeast(60_000L))
            .build()
        Notifications.post(context, NOTICE_ID, n)
        c.telemetry.event("sleep_record_offer")
    }
}

/** 2.0.0 [RVI-89] Runs once the phone charges inside the window. */
class SleepRecordOfferJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        running = c.appScope.launch {
            try {
                val next = c.alarmScheduler.upcoming.value?.plan?.triggerAt
                runCatching { SleepRecordOffer.maybeAsk(applicationContext, next) }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}
