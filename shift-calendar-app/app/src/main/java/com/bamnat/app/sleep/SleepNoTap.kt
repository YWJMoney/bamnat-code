package com.bamnat.app.sleep

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.bamnat.app.appContainer
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.sleep.SleepAuto
import com.bamnat.core.time.Today
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVI-96] (검토 I03c-01) 누르지 않는 수면 기록. 패턴 카드 needed two taps a day (잠들었어요·깼어요). The wake side is the alarm
 * turned off (2.0.0 [RVG-05]); this is the other side, with no permission and nothing leaving the phone: near the time to sleep
 * for the next alarm, the phone put on its charger ([JOB_CHARGE]) and then left still with the screen off until the phone calls
 * itself idle ([JOB_IDLE]) is a night begun at the charger. When the alarm is turned off that night goes to 잠 자동으로 잡기's
 * own pieces ([SleepAutoWatch]), and the morning asks once, "23:40–06:00 · 6시간 20분, 맞나요?" (after two yeses it goes in by
 * itself, as there). A sleep already tapped or measured is never asked about. Off with 설정 › 알림과 잠 › [ROW]; only once
 * 잠 자동으로 잡기 is open (`sleep.auto`, 처리방침 3.11).
 */
object SleepNoTap {
    const val ROW = "충전하면 잠 잡기"
    private const val JOB_CHARGE = 4411
    private const val JOB_IDLE = 4412
    private const val JOB_REVIEW = 4413
    private const val PREFS = "sleep_no_tap"
    private const val K_CHARGED = "charged"
    private const val K_STILL = "still"
    /** How long the charger may stay in before the night it is for; older ones are let go. */
    private const val KEEP_MILLIS = 18 * 60 * 60 * 1000L
    /** A charge this recent is the same night's: kept, not started over. */
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L

    fun on(context: Context): Boolean {
        val c = context.appContainer
        val s = c.settingsRepository.current
        return s.sleepNoTap && s.sleepTips && c.flags.on(FlagCatalog.SLEEP_AUTO)
    }

    /** Planned with the alarms: one job that waits for the charger inside the window before [alarm]. */
    fun plan(context: Context, alarm: LocalDateTime?) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        val w = SleepRecordOffer.window(alarm, context.appContainer.settingsRepository.current.sleepTargetMinutes)
        val now = Today.dateTime()
        if (!on(context) || w == null || w.endInclusive.isBefore(now)) {
            jobs.cancel(JOB_CHARGE)
            return
        }
        // Already on the charger for this night: the alarms planned again do not start it over.
        val charged = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_CHARGED, 0L)
        if (charged > 0 && !local(charged).isBefore(w.start)) return
        val delay = Duration.between(now, w.start).toMillis().coerceAtLeast(0L)
        runCatching {
            jobs.schedule(JobInfo.Builder(JOB_CHARGE, ComponentName(context, SleepNoTapJob::class.java))
                .setMinimumLatency(delay)
                .setOverrideDeadline(Duration.between(now, w.endInclusive).toMillis().coerceAtLeast(delay))
                .setRequiresCharging(true)
                .build())
        }
    }

    /** A job ran: on the charger in the window (then waits for stillness), or still on it at last. */
    internal suspend fun ran(context: Context, id: Int) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = Today.millis()
        val charging = context.getSystemService(android.os.BatteryManager::class.java)?.isCharging == true
        when (id) {
            JOB_CHARGE -> if (charging && on(context) && prefs.getLong(K_CHARGED, 0L) < now - FRESH_MILLIS) {
                prefs.edit().putLong(K_CHARGED, now).remove(K_STILL).apply()
                runCatching {
                    jobs.schedule(JobInfo.Builder(JOB_IDLE, ComponentName(context, SleepNoTapJob::class.java))
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .build())
                }
            }
            JOB_IDLE -> if (prefs.getLong(K_CHARGED, 0L) > now - KEEP_MILLIS) prefs.edit().putLong(K_STILL, now).apply()
            JOB_REVIEW -> SleepAutoWatch.review(context, phone = true)
        }
    }

    /** The alarm turned off: the night that began at the charger, if the phone was still since, goes to the pieces to ask about. */
    fun alarmOff(context: Context, now: Long = Today.millis()) {
        if (!on(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val charged = prefs.getLong(K_CHARGED, 0L).takeIf { it > now - KEEP_MILLIS }?.let(::local)
        val still = prefs.getLong(K_STILL, 0L).takeIf { it > 0 }?.let(::local)
        prefs.edit().remove(K_CHARGED).remove(K_STILL).apply()
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_IDLE)
        val night = SleepAuto.phoneNight(charged, still, local(now)) ?: return
        SleepAutoWatch.addSegments(context, listOf(night), now)
        context.appContainer.telemetry.event("sleep_no_tap", "min=${night.minutes}")
        // Asked once it has settled (SleepAuto.SETTLE_MINUTES), as 잠 자동으로 잡기's pieces are.
        runCatching {
            context.getSystemService(JobScheduler::class.java)?.schedule(JobInfo.Builder(JOB_REVIEW, ComponentName(context, SleepNoTapJob::class.java))
                .setMinimumLatency((SleepAuto.SETTLE_MINUTES + 1) * 60_000L)
                .setOverrideDeadline((SleepAuto.SETTLE_MINUTES + 15) * 60_000L)
                .build())
        }
    }

    private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Today.zone())
}

/** 2.0.0 [RVI-96] The charger, the stillness and the morning's question. */
class SleepNoTapJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = applicationContext.appContainer.appScope.launch {
            try {
                runCatching { SleepNoTap.ran(applicationContext, params.jobId) }
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
