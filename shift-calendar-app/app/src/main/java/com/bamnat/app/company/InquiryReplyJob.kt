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
        private const val PREFS = "inquiry_reply"
        private const val K_SAID = "said"

        /**
         * How often a waiting question is looked at. 1.36 (사용자 2026-11-01 "답을 바로"): the first day every 15 minutes (the shortest
         * Android allows), then every 3 hours. 2.0.0 [RVN-22] 검토 COMP-55·I10ba-05: a phone whose token is with the server hears of an
         * answer by push, so the look is only the fallback (every 6 hours), and after a week of waiting once a day for every phone
         * (before, a late answer kept a phone waking 8 to 96 times a day for weeks).
         */
        internal fun every(pushed: Boolean, waited: java.time.Duration): Long = when {
            waited.toDays() >= 7 -> TimeUnit.DAYS.toMillis(1)
            pushed -> TimeUnit.HOURS.toMillis(6)
            waited.toDays() < 1 -> TimeUnit.MINUTES.toMillis(15)
            else -> TimeUnit.HOURS.toMillis(3)
        }

        /** After a question went ([waited] zero), or the app opened with one waiting since [waited]. */
        fun schedule(context: Context, waited: java.time.Duration = java.time.Duration.ZERO) {
            // 1.36 [PUSH-01] With 바로 알림 in the build, the phone's token goes to the server too (the job stays as the fallback).
            if (PushPhone.configured) context.applicationContext.appContainer.let { c -> c.appScope.launch { runCatching { PushPhone.sync(c) } } }
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            val every = every(PushPhone.given(context), waited)
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

        /** The one count of answers said before 2.0.0 (each question keeps its own since: [InquiryStore.told]). */
        internal fun said(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_SAID, 0)

        /**
         * 2.0.0 [RVN-22] 검토 I10ba-03: the questions of [list] with an answer neither opened nor told yet, from now on told. Each
         * question keeps its own count (the one count [RVF-02] lowered as answers were read could still be held up by another
         * question's unread answers).
         */
        internal fun toTell(c: AppContainer, list: List<Inquiry>): List<Inquiry> {
            c.inquiryStore.startTelling(list, said(c.appContext))
            return c.inquiryStore.untold(list).also(c.inquiryStore::tell)
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
            toTell(c, list).takeIf { it.isNotEmpty() }?.let { post(c.appContext, it) }
            val waiting = list.filter { it.waiting }
            if (waiting.isEmpty()) cancel(c.appContext)
            else schedule(c.appContext, java.time.Duration.between(waiting.maxOf { it.updatedAt }, com.bamnat.core.time.Today.instant()))
        }

        /** 2.0.0 [RVN-22] 검토 U10b-01: the answer's first line, as the notice says it (one line, 60 letters). */
        internal fun firstLine(i: Inquiry): String = i.messages.lastOrNull { it.fromDeveloper }?.text.orEmpty()
            .lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            .let { if (it.length > 60) it.take(59) + "…" else it }

        /** What was asked, in a few words, for a notice of several questions. */
        private fun asked(i: Inquiry): String = i.messages.firstOrNull { !it.fromDeveloper }?.text.orEmpty()
            .lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            .let { if (it.length > 16) it.take(15) + "…" else it }.ifEmpty { "보내 주신 문의" }

        /**
         * 2.0.0 [RVN-22] 검토 U10b-01·I10bb-04: one question's answer says its first line and opens that question; after a fix
         * reached this phone ([Inquiry.askFixed]) its two answers are on the notice itself (해결됐어요 goes at once, 아직이에요 opens
         * the question with it said, for the one line of what is still wrong). Several questions: one notice, the list.
         */
        internal fun post(context: Context, told: List<Inquiry>) {
            val one = told.singleOrNull()
            val b = NotificationCompat.Builder(context, Notifications.INQUIRY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
            if (one != null) {
                val line = firstLine(one).ifEmpty { "눌러서 답을 볼 수 있어요" }
                val whole = one.messages.lastOrNull { it.fromDeveloper }?.text.orEmpty().trim().take(400)
                b.setContentTitle("문의에 답이 왔어요")
                    .setContentText(line)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(whole.ifEmpty { line }))
                    .setContentIntent(Notifications.open(context, NoticeLinks.Target.INQUIRY, 2205, payload = one.id))
                if (one.askFixed && one.fixed == 0) {
                    b.addAction(0, "해결됐어요", InquiryFixedReceiver.intent(context, one.id))
                    b.addAction(0, "아직이에요", Notifications.open(context, NoticeLinks.Target.INQUIRY, 2206, payload = one.id + STILL_MARK))
                }
            } else {
                b.setContentTitle("문의 ${told.size}개에 답이 왔어요")
                    .setContentText("‘${asked(told.first())}’ 외 ${told.size - 1}개 · 눌러서 보기")
                    .setContentIntent(Notifications.open(context, NoticeLinks.Target.INQUIRY, 2205))
            }
            Notifications.post(context, Notifications.INQUIRY_ID, b.build())
        }

        /** A notice's 아직이에요: the question's id with this mark opens it with 아직이에요 said. */
        const val STILL_MARK = "~still"
    }
}
