package com.bamnat.app.diag

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.bamnat.app.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 2.0.0 [RVL-71] Wakes 데이터 점검 once a day while the phone is idle and the battery is not low, kept across restarts; the check itself
 * runs only once a week is due ([DataSelfCheck.due]). The app also looks when it opens.
 */
class DataCheckJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        running = c.appScope.launch {
            try {
                c.dataSelfCheck.runIfDue()
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
        private const val ID = 4411
        private val EVERY = TimeUnit.DAYS.toMillis(1)

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, DataCheckJob::class.java))
                    .setPeriodic(EVERY, EVERY / 4)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresDeviceIdle(true)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(ID)
        }
    }
}
