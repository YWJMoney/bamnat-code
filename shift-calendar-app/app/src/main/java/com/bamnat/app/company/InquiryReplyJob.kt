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
        // 1.36 PC 확인 a4899e83: 4403은 OutboxJob과 같은 번호라 문의를 보낼 때 대기 작업이 이 작업을 덮어써, 답 알림이 뜨지 않았어요.
        private const val ID = 4405
        private val EVERY = TimeUnit.HOURS.toMillis(3)
        /** 1.36 (사용자 2026-11-01 "답을 바로"): 보낸 지 하루 안에는 15분마다(안드로이드가 허락하는 가장 짧은 주기), 그 뒤는 3시간마다. */
        private val SOON = TimeUnit.MINUTES.toMillis(15)
        private val SOON_FOR = TimeUnit.DAYS.toMillis(1)
        private const val PREFS = "inquiry_reply"
        private const val K_SAID = "said"

        /** After a question went, or the app opened with one waiting. */
        fun schedule(context: Context, soon: Boolean = true) {
            // 1.36 [PUSH-01] With 바로 알림 in the build, the phone's token goes to the server too (the job stays as the fallback).
            if (PushPhone.configured) context.applicationContext.appContainer.let { c -> c.appScope.launch { runCatching { PushPhone.sync(c) } } }
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            val every = if (soon) SOON else EVERY
            if (jobs.getPendingJob(ID)?.intervalMillis == every) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, InquiryReplyJob::class.java))
                    .setPeriodic(every, every / 3)
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

        /**
         * [RVF-02] Whether [unread] holds an answer not said yet, and from now on [unread] is what was said. The count said only
         * goes down when answers are read ([lowerSaid]), so an answer written after the last one was read is said again.
         */
        internal fun sayNow(context: Context, unread: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val said = prefs.getInt(K_SAID, 0)
            if (unread != said) prefs.edit().putInt(K_SAID, unread).apply()
            return unread > said
        }

        /**
         * [RVF-02] 개발 전 검토 COMP-41: answers the user has read are no longer counted as said, so the next one (the release's
         * '고쳐서 나갔어요' too) brings its notice. Before this, one read answer kept every later one quiet.
         */
        fun lowerSaid(context: Context, unread: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(K_SAID, 0) > unread) prefs.edit().putInt(K_SAID, unread).apply()
        }

        /** [fromPush]: 1.36 [PUSH-01] the server said an answer was just written. */
        suspend fun check(c: AppContainer, fromPush: Boolean = false) {
            if (!c.settingsRepository.current.plazaNews || !c.inquiryApi.enabled || !c.inquiryStore.asked) {
                cancel(c.appContext)
                // 밤낮 소식 off: the server forgets this phone's token as well.
                runCatching { PushPhone.sync(c) }
                return
            }
            val list = c.inquiryApi.mine(c.companyStore.device)
            c.inquiryStore.update(list)
            val unread = c.inquiryStore.unread.value
            if (sayNow(c.appContext, unread)) post(c, unread)
            if (list.none { it.waiting }) cancel(c.appContext)
            // A question sent in the last day is looked at every 15 minutes; an older one every 3 hours.
            else schedule(c.appContext, soon = list.any { it.waiting && java.time.Duration.between(it.updatedAt, com.bamnat.core.time.Today.instant()).toMillis() < SOON_FOR })
        }

        private fun post(c: AppContainer, unread: Int) {
            val n = NotificationCompat.Builder(c.appContext, Notifications.INQUIRY_CHANNEL_ID)
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
