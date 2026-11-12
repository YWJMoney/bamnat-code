package com.bamnat.app.company

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.BuildConfig
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.app.share.ReceivedChanges
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.SharedSchedule
import com.bamnat.core.diag.DiagEvents
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.plaza.BamnatNews
import com.bamnat.core.plaza.PlazaNewsItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import com.bamnat.core.time.Today

/**
 * [COMM-10] 광장 소식, widened in 1.23 to 밤낮 소식 [SRV-10]: once a day, one quiet notice with what
 * happened to the user's own things. One ask to `GET /api/digest` brings the plaza news (rules, reports,
 * proposals, 임금표, and what became of 신고 [COMM-11]), new answers to the user's questions [INQ-05] and the
 * notices that began. Received schedules are looked at for changes in the next seven days [SHR-13], a
 * phone calendar that lost its permission is said once [ALRM-01], and the job leaves a line that the
 * phone was alive. No new job: the one 광장 소식 had. Off on 내 활동 (밤낮 소식 알림), or by the developer's switch
 * for the plaza part.
 */
class PlazaNewsJob : JobService() {
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
        private const val ID = 4402
        private val EVERY = TimeUnit.HOURS.toMillis(24)
        private const val PREFS = "plaza_news"
        private const val K_SINCE = "since"
        private const val K_CALENDAR_SAID = "calendar_permission_said"
        /** [SRV-10] 2: runs without a network too (the alive line and the calendar check need none). */
        private const val VERSION = 2

        /** Whether this phone has anything in the plaza to hear about; [CALI-02] from any of its calendars. */
        fun takesPart(c: AppContainer): Boolean = c.settingsRepository.current.plazaRulesAgreed > 0 ||
            c.companyScopes().any { it.store.sent.value.isNotEmpty() || it.store.applied.value != null }

        /** [SRV-10] Whether the daily job has anything to do on this phone. */
        fun wanted(c: AppContainer): Boolean {
            val s = c.settingsRepository.current
            return (s.plazaNews && (takesPart(c) || c.inquiryStore.asked || c.received.calendars.value.isNotEmpty() || c.recipes.on.value)) ||
                s.phoneCalendars.isNotEmpty() || s.alarmsEnabled
        }

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID)?.extras?.getInt("v") == VERSION) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, PlazaNewsJob::class.java))
                    .setPeriodic(EVERY, EVERY / 4)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .setExtras(PersistableBundle().apply { putInt("v", VERSION) })
                    .build(),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(ID)
        }

        /** Everything the daily job does; one notice at most for the news, one for the calendar permission. */
        suspend fun check(c: AppContainer) {
            c.telemetry.event(DiagEvents.ALIVE)
            calendarPermission(c)
            if (!c.settingsRepository.current.plazaNews) return
            // 1.36 [VIL-05] 마을 V5 소식 하나로: 밤낮 소식 and 마을 장터 소식 in one notice, held through sleep and work.
            val recipeLines = runCatching { c.recipes.newsLines() }.getOrDefault(emptyList())
            val changes = runCatching { receivedChanges(c) }.getOrDefault(emptyList())
            val digest = runCatching { digest(c) }.getOrNull()
            // 1.38 PC 9차 LOW (알림은 '새 답 2개', 내 글 화면은 '1개'): the server counts a day's answers, the screen what this phone has
            // not seen; the notice now says the screen's number, and no line when they were all read already.
            val plaza = sameAnswerCount(c, digest?.plaza.orEmpty())
            val text = BamnatNews.text(plaza, digest?.answers ?: 0, digest?.notices.orEmpty(), changes)
            if (text == null && recipeLines.isEmpty()) return
            val target = when {
                // [TOWN-01] 1.37: only 장터 news opens 장터; only answers to my posts open those posts.
                text == null -> NoticeLinks.Target.MARKET
                text.onlyAnswers && recipeLines.isEmpty() -> NoticeLinks.Target.INQUIRY
                plaza.isNotEmpty() && plaza.all { it.kind == "replies" } && changes.isEmpty() && (digest?.answers ?: 0) == 0 &&
                    digest?.notices.isNullOrEmpty() && recipeLines.isEmpty() -> NoticeLinks.Target.VILLAGE_POSTS
                // 1.39 발굴 2-1·2-3: a newer version of the way in use, or an answer to my 보완 요청: the pay screen's way card has both,
                // wherever the plaza keeps its company rules.
                plaza.isNotEmpty() && plaza.all { it.kind == "newer" || it.kind == "fix_answer" } && changes.isEmpty() &&
                    (digest?.answers ?: 0) == 0 && digest?.notices.isNullOrEmpty() && recipeLines.isEmpty() -> NoticeLinks.Target.PAY
                // 1.39 발굴 (마을 돌봄 스레드 요청): '보완 요청이 n개 왔어요' about one version opens that version's talk.
                fixTalk(plaza) != null && changes.isEmpty() && (digest?.answers ?: 0) == 0 && digest?.notices.isNullOrEmpty() &&
                    recipeLines.isEmpty() -> NoticeLinks.Target.FIX_TALK
                plaza.isEmpty() && changes.isNotEmpty() -> NoticeLinks.Target.TODAY
                plaza.isEmpty() && (digest?.answers ?: 0) > 0 && recipeLines.isEmpty() -> NoticeLinks.Target.INQUIRY
                else -> NoticeLinks.Target.PLAZA
            }
            val title = when {
                text == null -> "마을 장터 소식"
                recipeLines.isEmpty() -> text.title
                else -> "밤낮·마을 소식"
            }
            val lines = (text?.lines.orEmpty() + recipeLines).take(MAX_LINES)
            tell(c, title, lines, target, if (target == NoticeLinks.Target.FIX_TALK) fixTalk(plaza) else null)
        }

        /**
         * The one 소식 notice: [lines] added to any still waiting, held through sleep and work. 1.39 [PLZC-41] the 광장 push
         * ([PlazaPush]) tells through here too, so 답 바로 알림 and the daily 소식 stay one notice in one channel. A waiting line that
         * starts with [replacing] gives way to the new one (a count that grew).
         */
        internal suspend fun tell(c: AppContainer, title: String, lines: List<String>, target: NoticeLinks.Target, payload: String? = null,
            replacing: String? = null) {
            if (lines.isEmpty()) return
            val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // What waits is the notice as written now (the server's news is read once), added to any still waiting.
            val held = heldOf(prefs)
            val apart = held != null && (held.target != target || held.payload != payload)
            val all = Held(title.takeIf { held == null } ?: "밤낮·마을 소식", ((held?.lines?.filterNot { replacing != null && it.startsWith(replacing) } ?: emptyList()) + lines).distinct().take(MAX_LINES),
                if (apart) NoticeLinks.Target.PLAZA else target, payload.takeIf { !apart })
            keep(prefs, all)
            if (com.bamnat.app.sleep.QuietHold.holdIfAsleep(c.appContext, com.bamnat.app.sleep.QuietHold.Source.VILLAGE, Today.dateTime())) return
            postHeld(c.appContext)
        }

        /** 1.38: the 새 답 lines as one, counted as 마을 › 내 글 counts them (unseen on this phone); the server's lines if that cannot be read. */
        private suspend fun sameAnswerCount(c: AppContainer, items: List<com.bamnat.core.plaza.PlazaNewsItem>): List<com.bamnat.core.plaza.PlazaNewsItem> {
            val replies = items.filter { it.kind == "replies" }
            if (replies.isEmpty()) return items
            val fresh = runCatching { VillageReplies.fresh(c.appContext, c.companyApi.myPosts(c.companyStore.device)).values.sum() }.getOrNull()
                ?: return items
            val rest = items.filter { it.kind != "replies" }
            return if (fresh <= 0) rest else rest + replies.first().copy(count = fresh, company = replies.singleOrNull()?.company)
        }

        /**
         * 1.39 발굴: the talk a news of only 보완 요청 opens, as [NoticeLinks.FixTalk.payload]; null when the news is about anything else
         * or about more than one version (then 광장 opens as before).
         */
        internal fun fixTalk(plaza: List<PlazaNewsItem>): String? {
            if (plaza.isEmpty() || plaza.any { it.kind != "fix" }) return null
            val one = plaza.map { it.companyId to it.setup }.distinct().singleOrNull() ?: return null
            val (company, setup) = one
            if (company.isNullOrBlank() || setup.isNullOrBlank()) return null
            val i = plaza.first()
            return NoticeLinks.FixTalk(company, setup, "${i.company ?: "회사"} · ${i.dept?.ifEmpty { null } ?: "회사 전체"}").payload
        }

        /** 1.36 [VIL-05] One 소식 notice at most: what [check] wrote, posted now or when the user is off work and awake. */
        private data class Held(val title: String, val lines: List<String>, val target: NoticeLinks.Target, val payload: String? = null)
        private const val K_HELD = "held"
        private const val MAX_LINES = 7

        private fun heldOf(prefs: android.content.SharedPreferences): Held? = prefs.getString(K_HELD, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                Held(o.getString("title"), o.getJSONArray("lines").let { a -> List(a.length()) { a.getString(it) } },
                    NoticeLinks.Target.valueOf(o.getString("target")), o.optStringOrNull("payload"))
            }.getOrNull()
        }

        private fun keep(prefs: android.content.SharedPreferences, h: Held) {
            prefs.edit().putString(K_HELD, JSONObject().put("title", h.title).put("lines", org.json.JSONArray(h.lines)).put("target", h.target.name)
                .apply { h.payload?.let { put("payload", it) } }.toString()).apply()
        }

        /** Posts the 소식 waiting, if any (from [check] at once, or from [com.bamnat.app.sleep.QuietHold] after work or sleep). */
        fun postHeld(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val h = heldOf(prefs) ?: return
            prefs.edit().remove(K_HELD).apply()
            if (h.lines.isEmpty()) return
            val n = NotificationCompat.Builder(context, Notifications.PLAZA_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(h.title)
                .setContentText(h.lines.first())
                .setStyle(NotificationCompat.InboxStyle().also { s -> h.lines.forEach(s::addLine) })
                .setContentIntent(Notifications.open(context, h.target, 2203, payload = h.payload))
                .setAutoCancel(true)
                // [TOWN-08] PC 확인 73f69cbd: Android folded this into one bundle with '오늘 휴무' and a tap opened the calendar.
                // A group of its own (as its own summary) keeps it apart.
                .setGroup(Notifications.GROUP_NEWS).setGroupSummary(true)
                .build()
            Notifications.post(context, Notifications.PLAZA_ID, n)
            // [OBS-01] 기록 지점 10.
            context.appContainer.journal.note(com.bamnat.core.journal.JournalKind.NEWS)
        }


        /** What came from the server: the plaza part (empty when it is not asked), answers and notice titles. */
        data class Digest(val plaza: List<PlazaNewsItem>, val answers: Int, val notices: List<String>,
            /** 2.0.0 [RVN-14] HQ-51 What others did to what this phone put out, for the 알림함 (empty from a server before 2.0.0). */
            val reactions: List<com.bamnat.core.plaza.Reaction> = emptyList())

        /**
         * [SRV-10] One ask for the day. Only a phone that took part in the plaza or sent a question asks;
         * [R12] the first ask carries no `since` and the server answers for the last day.
         */
        suspend fun digest(c: AppContainer): Digest? {
            if (BuildConfig.COMPANY_BASE_URL.isBlank()) return null
            val plazaOn = takesPart(c) && !c.flags.paused(ServerSwitch.PLAZA_NEWS)
            if (!plazaOn && !c.inquiryStore.asked) return null
            val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val since = prefs.getString(K_SINCE, null)
            val o = serverCall {
                serverRequest(BuildConfig.COMPANY_BASE_URL, "GET", "/api/digest" + (since?.let { "?since=" + URLEncoder.encode(it, "UTF-8") } ?: ""),
                    device = c.companyStore.device)
            }
            o.optStringOrNull("now")?.let { prefs.edit().putString(K_SINCE, it).apply() }
            val answers = o.optJSONArray("answers").objects().sumOf { it.optInt("count") }
            // The 설정 tab's badge is right the next time the app opens.
            if (answers > 0) runCatching { c.inquiryStore.update(c.inquiryApi.mine(c.companyStore.device)) }
            return Digest(
                plaza = if (plazaOn) o.optJSONArray("plaza").rows(::item) else emptyList(),
                // [J133-24] Answers the reply job already said at once are not said again.
                answers = if (c.inquiryStore.asked && c.inquiryStore.unread.value > InquiryReplyJob.said(c.appContext)) answers else 0,
                notices = o.optJSONArray("notices").objects().map { it.optString("title") }.filter { it.isNotBlank() },
                reactions = o.optJSONArray("reactions").rows(::reaction),
            )
        }

        /** 2.0.0 [RVN-14] HQ-51 One line of `reactions`; a line it cannot read is left out (COMP-49). */
        internal fun reaction(o: JSONObject) = com.bamnat.core.plaza.Reaction(
            kind = o.getString("kind"), target = o.getString("target"), title = o.optString("title"), count = o.optInt("count"),
            atMillis = Instant.parse(o.getString("at")).toEpochMilli(), room = o.optStringOrNull("room"),
            postKind = o.optStringOrNull("postKind"), up = o.optInt("up"), down = o.optInt("down"), theme = o.optStringOrNull("theme"),
        )

        /** 2.0.0 [RVN-14] HQ-51 The 알림함's rows of [d]'s reactions, the kinds turned off in 설정 › 알림 left out. */
        fun reactionRows(c: AppContainer, d: Digest): List<com.bamnat.core.plaza.Reactions.Row> =
            com.bamnat.core.plaza.Reactions.rows(d.reactions, com.bamnat.core.plaza.Reactions.decodeOff(c.settingsRepository.current.reactionsOff))

        internal fun item(i: JSONObject) = PlazaNewsItem(
            kind = i.optString("kind"), company = i.optJSONObject("company")?.optString("name"), dept = i.optStringOrNull("dept"),
            version = if (i.has("version") && !i.isNull("version")) i.optInt("version") else null, trade = i.optStringOrNull("trade"),
            atMillis = runCatching { Instant.parse(i.optString("at")).toEpochMilli() }.getOrDefault(0L),
            result = i.optStringOrNull("result"), post = i.optStringOrNull("post"), count = i.optInt("count"),
            companyId = i.optJSONObject("company")?.optStringOrNull("id"), setup = i.optStringOrNull("setup"),
        )

        /**
         * [SHR-13] Each received schedule asked with its tag, so only one that changed downloads; a change
         * in its next seven days becomes a line ("민지 10/25 주간 → 야간").
         */
        suspend fun receivedChanges(c: AppContainer, today: LocalDate = Today.date()): List<String> {
            if (!c.shareApi.enabled) return emptyList()
            c.received.expire()
            val lines = mutableListOf<String>()
            for (cal in c.received.calendars.value.filter { !it.gone }) {
                val got = try {
                    c.shareApi.readQuietly(cal.id, cal.etag)
                } catch (e: ShareLinkException) {
                    if (e.message == "not_found") c.received.markGone(cal.id, e.removedReason)
                    null
                } catch (e: java.io.IOException) {
                    null
                } ?: continue
                val old = cal.schedule
                val after = c.received.update(cal.id, got)
                // 1.39 [TEAM-22] A followed link's change goes into the user's own days (or is asked) before the old copy is gone.
                if (after != null) com.bamnat.app.share.RosterFollower.settle(c, cal.id)
                val new = runCatching { SharedSchedule.parse(got.schedule) }.getOrNull() ?: continue
                val changed = ReceivedChanges.nextDays(old, new, today)
                if (changed.isNotEmpty()) lines += "${cal.name} " + changed.take(2).joinToString(", ") +
                    (if (changed.size > 2) " 외 ${changed.size - 2}일" else "")
            }
            return lines
        }

        /** [ALRM-01] R9: 휴대폰 캘린더에 쓰기 is on but the permission was taken back: said once, until it is given again. */
        fun calendarPermission(c: AppContainer) {
            val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val on = c.settingsRepository.current.phoneCalendars.isNotEmpty()
            val allowed = com.bamnat.app.calendar.PhoneCalendar(c.appContext).allowed()
            if (!on || allowed) {
                if (prefs.getBoolean(K_CALENDAR_SAID, false)) prefs.edit().putBoolean(K_CALENDAR_SAID, false).apply()
                return
            }
            if (prefs.getBoolean(K_CALENDAR_SAID, false)) return
            prefs.edit().putBoolean(K_CALENDAR_SAID, true).apply()
            val n = NotificationCompat.Builder(c.appContext, Notifications.DAY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("휴대폰 캘린더에 근무를 쓰지 못하고 있어요")
                .setContentText("캘린더 권한이 꺼졌어요. 휴대폰 캘린더의 근무가 옛것일 수 있어요")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "캘린더 권한이 꺼져서 휴대폰 캘린더(삼성 캘린더 등)의 밤낮 근무를 고치지 못해요. 그곳의 근무는 옛것일 수 있어요. " +
                        "눌러서 설정 › 데이터에서 다시 켜 주세요."))
                .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.PHONE_CALENDAR, 2204))
                .setAutoCancel(true)
                .build()
            Notifications.post(c.appContext, Notifications.CALENDAR_PERMISSION_ID, n)
        }
    }
}
