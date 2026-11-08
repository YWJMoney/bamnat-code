package com.bamnat.app.boss

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
import com.bamnat.app.share.ShareLinkException
import com.bamnat.core.boss.StaffClock
import com.bamnat.core.boss.StaffPunch
import com.bamnat.core.time.Today
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 1.38 [P3-B-11] 직원 출퇴근 → 사장님 알림 (경쟁 앱 조사 제안, 사용자 2026-11-06): while any staff member has a 출퇴근 링크, a look
 * every 15 minutes (the shortest Android allows). A new press is kept as a 기록 and said in one notice per person ("박마바님 출근
 * 9:02"). No location: the server keeps only the press's time. Gone by itself when no link is left.
 */
class StaffClockJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        running = c.appScope.launch {
            try { runCatching { check(c) } } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    companion object {
        private const val ID = 4406
        private val EVERY = TimeUnit.MINUTES.toMillis(15)
        /** Presses older than this are kept but not said (a restore, the first look after a long while). */
        private val SAY_WITHIN: Duration = Duration.ofHours(12)

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, StaffClockJob::class.java))
                    .setPeriodic(EVERY, EVERY / 3)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) { context.getSystemService(JobScheduler::class.java)?.cancel(ID) }

        /** Reads every link once; returns how many new presses came. Also called when the 급여 대장 opens. */
        suspend fun check(c: AppContainer): Int {
            val store = c.phaseThree
            val linked = store.allStaff().filter { it.clockId != null && it.clockToken != null }
            if (linked.isEmpty() || !c.shareApi.enabled) { cancel(c.appContext); return 0 }
            var fresh = 0
            for (s in linked) {
                // 퇴사일이 지나면 그 링크는 저절로 끊어요(사용자 2026-11-06 보안 검토: 그만둔 사람이 계속 누를 수 없게).
                if (s.leftOn?.isBefore(Today.date()) == true) {
                    runCatching { c.shareApi.clockDelete(s.clockId!!, s.clockToken!!) }.onSuccess { store.forgetClock(s.id) }
                    continue
                }
                val got = try {
                    c.shareApi.clockRead(s.clockId!!, s.clockToken!!)
                } catch (e: ShareLinkException) {
                    // Cut elsewhere or run out: this phone forgets it, and the staff dialog offers a new one.
                    if (e.message == "not_found" || e.message == "forbidden") store.forgetClock(s.id)
                    continue
                } catch (_: Exception) { continue }
                val new = store.addPunches(s.id, got.map { StaffPunch(s.id, it.at, it.inside) })
                fresh += new.size
                val say = new.filter { Duration.between(it.at, Today.instant()) < SAY_WITHIN }
                if (say.isNotEmpty()) post(c, s.id, s.name, say)
            }
            return fresh
        }

        private fun post(c: AppContainer, staffId: String, name: String, presses: List<StaffPunch>) {
            val f = java.time.format.DateTimeFormatter.ofPattern("H:mm")
            val text = presses.sortedBy { it.at }.joinToString(" · ") { (if (it.inside) "출근 " else "퇴근 ") + it.at.atZone(StaffClock.ZONE).format(f) }
            val n = NotificationCompat.Builder(c.appContext, Notifications.CLOCK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${name}님 $text")
                .setContentText("출퇴근 링크에서 눌렀어요. 급여 대장에서 확인하고 넣을 수 있어요.")
                .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.MANAGE, 2231))
                .setAutoCancel(true)
                .build()
            Notifications.post(c.appContext, Notifications.CLOCK_ID, n, tag = staffId)
        }
    }
}
