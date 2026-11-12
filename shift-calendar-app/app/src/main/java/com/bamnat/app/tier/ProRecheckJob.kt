package com.bamnat.app.tier

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.core.time.Today
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * [R200-03] (검토 PRO-08 넘긴 일) A subscription's purchases read again without the app being opened: once a day while its end
 * is near ([com.bamnat.core.tier.PlaySub.recheckDue], from 6시간 before it), so a renewal is known before the next alarm asks
 * for 프로 (끄기 미션). Set while there is a subscription to watch, gone by itself after.
 */
class ProRecheckJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        running = c.appScope.launch {
            try {
                runCatching { check(c) }
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

    companion object {
        private const val ID = 4409
        /** Android lets a periodic job run anywhere in its last [FLEX]; a day apart at most, as [recheckDue] counts. */
        private val EVERY = TimeUnit.HOURS.toMillis(12)
        private val FLEX = TimeUnit.HOURS.toMillis(4)

        /** After the purchases were read (the app came to the front): watch the subscription, or stop when there is none to watch. */
        fun schedule(context: Context, c: AppContainer = context.applicationContext.appContainer) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            val sub = c.proStore.current.sub
            if (sub == null || !sub.play().recheckWanted(Today.instant())) { jobs.cancel(ID); return }
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, ProRecheckJob::class.java))
                    .setPeriodic(EVERY, FLEX)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        /** True when Play was asked. */
        suspend fun check(c: AppContainer): Boolean {
            val sub = c.proStore.current.sub
            val now = Today.instant()
            if (sub == null || !sub.play().recheckWanted(now)) { schedule(c.appContext, c); return false }
            if (!sub.play().recheckDue(now, sub.checkedAt)) return false
            c.billing.refresh()
            c.featureGate.refresh()
            // The copy a locked reboot rings from keeps the 끄기 미션 as 프로 now says.
            runCatching { c.alarmScheduler.saveBootCopy() }
            schedule(c.appContext, c)
            return true
        }
    }
}
