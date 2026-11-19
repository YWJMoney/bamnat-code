package com.bamnat.app.company

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.core.plaza.OpenTimes
import com.bamnat.core.time.Today
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 2.0.0 [RVN-22] 검토 I10bc-02 열기 직전에 미리 받기 ('매일 1초'): a little before the phone is likely opened (a usual time, a shift's end,
 * half an hour before an alarm; [OpenTimes]), on Wi‑Fi with the battery not low, the company list and this phone's company page with its
 * room are read as the app's start reads them ([warmCompaniesNow], the list asked with its tags: usually nothing comes back), so 회사·광장
 * show at once. Only for a phone that uses a company (교대·일근, an 알바 with one), never while it saves data. Nothing new is sent and
 * the opens are kept on this phone only ([PREFS]).
 */
class ReadAheadJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        val target = params.extras.getLong(K_FOR, 0L)
        running = c.appScope.launch {
            try {
                runCatching { run(c, target) }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    companion object {
        /** Away from the app's other jobs (JobIdsTest). */
        private const val ID = 4423
        private const val PREFS = "open_times"
        private const val K_OPENS = "opens"
        private const val K_FOR = "for"
        /** A reading ahead that comes this late after its time is let go (the phone was likely opened already). */
        private val LATE: Duration = Duration.ofMinutes(10)

        /** The app came to the front: one mark for this half hour, and the next reading ahead set. */
        fun sawOpen(context: Context) {
            val c = context.appContainer
            c.appScope.launch {
                runCatching {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val kept = OpenTimes.record(OpenTimes.decode(prefs.getString(K_OPENS, null)), Today.dateTime())
                    prefs.edit().putString(K_OPENS, OpenTimes.encode(kept)).apply()
                    schedule(c)
                }
            }
        }

        /** Whether this phone has company pages worth reading ahead. */
        internal fun wanted(c: AppContainer): Boolean =
            c.companyApi.enabled && runCatching { c.companyCalendars() }.getOrDefault(false) && !CompanySearch.saveData(c.appContext)

        /** When the next reading ahead is for, as [OpenTimes.next] sees this phone now; null when there is none. */
        internal suspend fun nextFor(c: AppContainer, now: LocalDateTime = Today.dateTime()): LocalDateTime? {
            val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val kept = OpenTimes.decode(prefs.getString(K_OPENS, null))
            val resolver = c.scheduleRepository.snapshot.first().resolver
            val today = now.toLocalDate()
            val ends = (0L..1L).mapNotNull { d -> resolver.resolve(today.plusDays(d)).let { OpenTimes.shiftEnd(it.date, it.start, it.end) } }
            val alarm = c.alarmScheduler.upcoming.value?.triggerAtMillis
                ?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), Today.zone()) }
            return OpenTimes.next(now, kept, ends, listOfNotNull(alarm))
        }

        /** Sets the one reading ahead (or none), a little before [nextFor]. */
        suspend fun schedule(c: AppContainer) {
            val jobs = c.appContext.getSystemService(JobScheduler::class.java) ?: return
            if (!wanted(c)) return jobs.cancel(ID)
            val now = Today.dateTime()
            val target = nextFor(c, now) ?: return jobs.cancel(ID)
            val zone: ZoneId = Today.zone()
            val targetMillis = target.atZone(zone).toInstant().toEpochMilli()
            val wait = Duration.between(now, target.minus(OpenTimes.AHEAD)).toMillis().coerceAtLeast(0)
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(c.appContext, ReadAheadJob::class.java))
                    .setMinimumLatency(wait)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .setExtras(PersistableBundle().apply { putLong(K_FOR, targetMillis) })
                    .build(),
            )
        }

        /** The reading ahead itself, then the next one set. */
        internal suspend fun run(c: AppContainer, target: Long) {
            val late = target > 0 && Today.millis() > target + LATE.toMillis()
            if (!late && wanted(c)) runCatching { c.warmCompaniesNow() }
            schedule(c)
        }
    }
}
