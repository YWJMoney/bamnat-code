package com.bamnat.app.company

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * [J133-24] 1.33 (실사용 테스트 "문의에 답이 와도 하루를 기다려야 알아요"): while a question of this phone waits for the developer,
 * a look every 3 hours; a new answer is one notice at once ("문의에 답이 왔어요"), the daily 밤낮 소식 leaving it out. Off with
 * 설정 › 알림 › 밤낮 소식, and gone by itself once nothing waits.
 */
class InquiryReplyJob : JobService() {
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
        private const val ID = 4403
        private val EVERY = TimeUnit.HOURS.toMillis(3)
        private const val PREFS = "inquiry_reply"
        private const val K_SAID = "said"

        /** After a question went, or the app opened with one waiting. */
        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, InquiryReplyJob::class.java))
                    .setPeriodic(EVERY, EVERY / 3)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(ID)
        }

        /** The answers already said in a notice, so the daily news does not say them again. */
        fun said(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_SAID, 0)

        suspend fun check(c: AppContainer) {
            if (!c.settingsRepository.current.plazaNews || !c.inquiryApi.enabled || !c.inquiryStore.asked) {
                cancel(c.appContext)
                return
            }
            val list = c.inquiryApi.mine(c.companyStore.device)
            c.inquiryStore.update(list)
            val unread = c.inquiryStore.unread.value
            val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val said = prefs.getInt(K_SAID, 0)
            if (unread > said) post(c, unread)
            if (unread != said) prefs.edit().putInt(K_SAID, unread).apply()
            if (list.none { it.waiting }) cancel(c.appContext)
        }

        private fun post(c: AppContainer, unread: Int) {
            val n = NotificationCompat.Builder(c.appContext, Notifications.PLAZA_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("문의에 답이 왔어요")
                .setContentText(if (unread > 1) "읽지 않은 답 ${unread}개 · 눌러서 보기" else "눌러서 답을 볼 수 있어요")
                .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.INQUIRY, 2205))
                .setAutoCancel(true)
                .build()
            Notifications.post(c.appContext, Notifications.INQUIRY_ID, n)
        }
    }
}
