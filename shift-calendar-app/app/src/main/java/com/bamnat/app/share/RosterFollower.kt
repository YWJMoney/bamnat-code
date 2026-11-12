package com.bamnat.app.share

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
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.schedule.RosterAdopt
import com.bamnat.core.schedule.RosterFollow
import com.bamnat.core.time.Today
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 1.39 [TEAM-22] D9 (사용자 2026-11-07): a 팀 리더 fixes the team's roster and sends it again on the same link. Before, a member's phone
 * fetched it only when the received calendar was opened, and the days put into their own calendar stayed old, so the 근무 알람 could
 * ring for the old shift. Now every fetch of a followed link (the one the setup started from, or one whose days were put in once)
 * goes through [refresh]: the days the link changed go into the member's own calendar at once ([Mode.AUTO], the default for the link
 * the setup started from) or wait for one press ([Mode.ASK], the default for a link put in once), with a notice and a line over the
 * calendar ([FollowNote]) that can take them back. A day the member changed by hand is kept. The alarms re-plan by themselves from the
 * calendar's days ([AppContainer] follows them).
 */
object RosterFollower {
    enum class Mode(val key: String, val words: String) {
        AUTO("auto", "바로 내 달력에 넣기"),
        ASK("ask", "넣을지 물어보기"),
        OFF("off", "넣지 않기"),
    }

    fun mode(cal: ReceivedCalendar): Mode = Mode.entries.firstOrNull { it.key == cal.follow } ?: when {
        cal.mine -> Mode.AUTO
        cal.adopted -> Mode.ASK
        else -> Mode.OFF
    }

    /** Whether this link's changes come into the user's calendar (or are offered). */
    fun followed(cal: ReceivedCalendar): Boolean = mode(cal) != Mode.OFF

    /**
     * 1.39 PC 5c4ad849 (받은 근무표로 넣은 날에 '직접 바꿈'): the followed link whose [date] this day already is in my kinds of day, so
     * the day says where it came from (민지의 근무표대로) and not that the user changed it by hand; null when none says so.
     */
    fun linkFor(cals: List<ReceivedCalendar>, day: com.bamnat.core.schedule.ResolvedDay, mine: List<com.bamnat.core.model.ShiftType>): ReceivedCalendar? =
        cals.firstOrNull { cal ->
            if (cal.gone || !followed(cal)) return@firstOrNull false
            val s = cal.schedule ?: return@firstOrNull false
            val theirs = s.days[day.date] ?: return@firstOrNull false
            RosterAdopt.changes(mapOf(day.date to theirs.type), RosterAdopt.match(theirTypes(s), mine), mine) { day }.isEmpty()
        }

    /** The sender's shifts as [RosterAdopt] pairs them (also the card that puts a month in). */
    fun theirTypes(s: SharedSchedule): List<RosterAdopt.Theirs> = s.types.map { t ->
        val parts = t.time?.split("-")
        fun at(x: String?) = x?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        RosterAdopt.Theirs(t.id, t.short, t.name, at(parts?.getOrNull(0)), at(parts?.getOrNull(1)), t.off)
    }

    /** What the link says for each of its days; leave is the member's own and is not followed. */
    fun theirDays(s: SharedSchedule): Map<LocalDate, RosterFollow.Their> = s.days.values.mapNotNull { d ->
        when {
            d.leave != null -> null
            d.type != null && s.type(d.type) != null -> d.date to RosterFollow.Their.Shift(d.type)
            d.type == null -> d.date to RosterFollow.Their.Rest
            else -> null
        }
    }.toMap()

    private val lock = Mutex()

    /**
     * Fetches [id] if it changed and keeps the new copy; when the link is followed, what changed goes in or is asked. Returns false
     * when it could not be reached. Every fetch of a received link goes through here, so the old copy is never lost before it is
     * compared.
     */
    suspend fun refresh(c: AppContainer, id: String, quiet: Boolean = false): Boolean = lock.withLock {
        val before = c.received.get(id) ?: return@withLock true
        if (before.gone) return@withLock true
        val got = try {
            if (quiet) c.shareApi.readQuietly(id, before.etag) else c.shareApi.readIfChanged(id, before.etag)
        } catch (e: ShareLinkException) {
            if (e.message == "not_found") {
                c.received.markGone(id, e.removedReason)
                if (followed(before)) said(c, id, FollowNote(FollowNote.GONE, Today.instant(), emptyList()))
                return@withLock true
            }
            return@withLock false
        } catch (_: IOException) {
            return@withLock false
        }
        if (got == null) c.received.touch(id) else c.received.update(id, got)
        // Also when nothing came now: another way may have brought the new copy in first (PC a483ca15, 12/3 not put in).
        runCatching { follow(c, id) }
        true
    }

    /**
     * 1.39 [TEAM-22] For a copy brought in some other way (하루 한 번 소식, opening the link again): what changed since my days were
     * last matched goes in or is asked, as [refresh] does.
     */
    suspend fun settle(c: AppContainer, id: String) = lock.withLock { runCatching { follow(c, id) } }

    /** Compares the copy my days were last matched to ([ReceivedCalendar.basis]) with the link now, and does what its [Mode] says. */
    internal suspend fun follow(c: AppContainer, id: String) {
        val cal = c.received.get(id) ?: return
        val raw = cal.raw ?: return
        val basis = cal.basis
        if (basis != null && basis.toString() == raw.toString()) return
        val mode = mode(cal)
        val old = basis?.let { runCatching { SharedSchedule.parse(it) }.getOrNull() }
        val new = cal.schedule
        if (mode == Mode.OFF || old == null || new == null) { c.received.setBasis(id, raw); return }
        decide(c, cal, mode, old, new)
        c.received.setBasis(id, raw)
    }

    private suspend fun decide(c: AppContainer, after: ReceivedCalendar, mode: Mode, old: SharedSchedule, new: SharedSchedule) {
        val mine = c.scheduleRepository.snapshot.first()
        val pairs = RosterAdopt.match(theirTypes(new), mine.shiftTypes)
        val from = Today.date().withDayOfMonth(1)
        val plan = RosterFollow.plan(theirDays(old), theirDays(new), pairs, mine.shiftTypes, mine.resolver::resolve, from)
        if (plan.isEmpty) return
        runCatching { listKept(c, after, plan, mine.shiftTypes) }
        val prefixes = plan.lines.map { it.substringBefore(' ') }.toSet()
        if (plan.apply.isEmpty()) {
            // 2.0.0 [RVH-01] 검토 CAL-05: a change with only kept days (the member had changed them by hand) does not wipe the note
            // before it: an unanswered '넣을까요?' keeps its days and its button, and a change put in by itself keeps its 되돌리기.
            val prev = after.note?.takeIf { (it.kind == FollowNote.ASK && it.apply.isNotEmpty()) || (it.kind == FollowNote.DONE && it.undo.isNotEmpty()) }
            val kept = (prev?.kept.orEmpty() + plan.kept).distinct().sorted()
            said(c, after.id, prev?.copy(kept = kept, at = Today.instant()) ?: FollowNote(FollowNote.ASK, Today.instant(), emptyList(), kept = kept))
            return
        }
        if (mode == Mode.AUTO) {
            val was = c.scheduleRepository.updateDays(plan.apply.keys.toList()) { e -> e.copy(override = plan.apply.getValue(e.date)) }
            val undo = was.mapValues { (_, e) -> code(e?.override ?: ShiftOverride.None) }
            // [RVH-01] Two changes in a row: 되돌리기 takes both back to how the days were before the first (the older value wins).
            val prev = after.note?.takeIf { it.kind == FollowNote.DONE && it.undo.isNotEmpty() }
            val lines = prev?.lines.orEmpty().filter { it.substringBefore(' ') !in prefixes } + plan.lines
            val kept = (prev?.kept.orEmpty().filter { it !in plan.apply.keys } + plan.kept).distinct().sorted()
            said(c, after.id, FollowNote(FollowNote.DONE, Today.instant(), lines, undo = undo + prev?.undo.orEmpty(), kept = kept))
        } else {
            // A change asked earlier and not answered yet stays in the question with this one.
            val prev = after.note?.takeIf { it.kind == FollowNote.ASK }
            val lines = prev?.lines.orEmpty().filter { it.substringBefore(' ') !in prefixes } + plan.lines
            val apply = prev?.apply.orEmpty() + plan.apply.mapValues { code(it.value) }
            val kept = (prev?.kept.orEmpty().filter { it !in plan.apply.keys } + plan.kept).distinct()
            said(c, after.id, FollowNote(FollowNote.ASK, Today.instant(), lines, apply = apply, kept = kept))
        }
    }

    /** One press on an asked change: the days go in, and the note can take them back. */
    suspend fun putIn(c: AppContainer, id: String) {
        val note = c.received.get(id)?.note?.takeIf { it.kind == FollowNote.ASK && it.apply.isNotEmpty() } ?: return
        val apply = note.apply.mapValues { override(it.value) }
        val was = c.scheduleRepository.updateDays(apply.keys.toList()) { e -> e.copy(override = apply.getValue(e.date)) }
        c.received.setNote(id, note.copy(kind = FollowNote.DONE, apply = emptyMap(), undo = was.mapValues { (_, e) -> code(e?.override ?: ShiftOverride.None) }))
        cancelNotice(c.appContext, id)
    }

    /** 되돌리기: each day's override as it was before the change went in. */
    suspend fun takeBack(c: AppContainer, id: String) {
        val note = c.received.get(id)?.note?.takeIf { it.kind == FollowNote.DONE } ?: return
        val back = note.undo.mapValues { override(it.value) }
        c.scheduleRepository.updateDays(back.keys.toList()) { e -> e.copy(override = back.getValue(e.date)) }
        c.received.setNote(id, null)
        cancelNotice(c.appContext, id)
    }

    fun close(c: AppContainer, id: String) {
        c.received.setNote(id, null)
        cancelNotice(c.appContext, id)
    }

    /** Every followed link, e.g. when the app opens and from [RosterFollowJob]; the job is kept only while any link is followed. */
    suspend fun checkAll(c: AppContainer) {
        if (!c.shareApi.enabled) return
        c.received.expire()
        val ids = c.received.calendars.value.filter { !it.gone && followed(it) }.map { it.id }
        for (id in ids) refresh(c, id, quiet = true)
        sync(c.appContext, c)
    }

    /** Starts or stops [RosterFollowJob] by whether any link is followed. */
    fun sync(context: Context, c: AppContainer) {
        if (c.received.calendars.value.any { !it.gone && followed(it) }) RosterFollowJob.schedule(context) else RosterFollowJob.cancel(context)
    }

    /** 2.0.0 [RVH-57] The way back of a kept day in [com.bamnat.app.data.AutoTidy]: the link's day put into my calendar. */
    const val KIND_KEPT = "calendar.roster_kept"

    /** [RVH-57] Where '자동으로 정리한 것' lists them: the calendar's received links. */
    const val TIDY_AREA = "calendar"

    /**
     * 2.0.0 [RVH-57] 뿌리 7 '조용히 버려지는 것 없애기' (갈래 K '자동으로 정리한 것', 검토 CAL-05): a day the link changed that I had changed
     * by hand stays as I made it. It was only named in the note over the calendar, gone once closed; now each such day is kept 30 days
     * under '자동으로 정리한 것' with why, and one tap puts the link's day in. A day kept again on a later change is listed once.
     */
    internal suspend fun listKept(c: AppContainer, cal: ReceivedCalendar, plan: RosterFollow.Plan, types: List<com.bamnat.core.model.ShiftType>) {
        if (plan.keptTo.isEmpty()) return
        val tidy = c.autoTidy
        val byId = types.associateBy { it.id }
        for ((d, to) in plan.keptTo) {
            tidy.items.value.filter { it.kind == KIND_KEPT && keptKey(it.payload) == cal.id to d.toString() }.forEach { tidy.forget(it.id) }
            val what = when (to) {
                is ShiftOverride.Shift -> byId[to.shiftTypeId]?.let { t -> t.name.ifBlank { t.label } } ?: "근무"
                else -> "쉬는 날"
            }
            tidy.add(
                area = TIDY_AREA,
                calendarId = com.bamnat.app.data.PersonalCalendar.MAIN,
                // What the link says, not what my day is: '12월 4일 · 근무표는 데이'.
                title = "${d.monthValue}월 ${d.dayOfMonth}일 · 근무표는 $what",
                why = "${cal.name} 근무표에서 바뀐 날이지만, 직접 바꾼 날이라 내 달력은 그대로 뒀어요",
                kind = KIND_KEPT,
                payload = org.json.JSONObject().put("link", cal.id).put("date", d.toString()).put("to", code(to)).toString(),
            )
        }
    }

    /** [RVH-57] A kept day of link [link], for its own row. */
    fun keptOf(item: com.bamnat.app.data.AutoTidy.Item, link: String): Boolean = item.kind == KIND_KEPT && keptKey(item.payload)?.first == link

    private fun keptKey(payload: String): Pair<String, String>? =
        runCatching { org.json.JSONObject(payload).let { it.getString("link") to it.getString("date") } }.getOrNull()

    /** [RVH-57] '되살리기' on a kept day: the link's day goes into my calendar, as '내 달력에 넣기' would have put it. */
    suspend fun putBackKept(c: AppContainer, payload: String): Boolean {
        val o = runCatching { org.json.JSONObject(payload) }.getOrNull() ?: return false
        val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: return false
        val to = override(o.optString("to"))
        if (to == ShiftOverride.None) return false
        c.scheduleRepository.updateDays(listOf(date)) { e -> e.copy(override = to) }
        return true
    }

    internal fun code(o: ShiftOverride): String = when (o) {
        ShiftOverride.None -> "n"
        ShiftOverride.Off -> "o"
        is ShiftOverride.Shift -> "s${o.shiftTypeId}"
    }

    internal fun override(code: String): ShiftOverride = when {
        code == "o" -> ShiftOverride.Off
        code.startsWith("s") -> code.drop(1).toLongOrNull()?.let { ShiftOverride.Shift(it) } ?: ShiftOverride.None
        else -> ShiftOverride.None
    }

    /** Keeps the note with the link and says it once in a notice (held while asleep like other quiet news is not needed: it has a sound for the alarms' sake). */
    private fun said(c: AppContainer, id: String, note: FollowNote) {
        c.received.setNote(id, note)
        val cal = c.received.get(id) ?: return
        val (title, text) = words(cal, note)
        val n = NotificationCompat.Builder(c.appContext, Notifications.ROSTER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.TODAY, 2232))
            .setAutoCancel(true)
            // 1.39 PC c0ca3d9f: on its own, so Android never bundles it with 자는 중 (whose ‘깼어요’ then hid under a fold).
            .setGroup(Notifications.GROUP_ROSTER + id).setGroupSummary(true)
            .build()
        Notifications.post(c.appContext, Notifications.ROSTER_ID, n, tag = id)
    }

    private fun cancelNotice(context: Context, id: String) {
        runCatching { androidx.core.app.NotificationManagerCompat.from(context).cancel(id, Notifications.ROSTER_ID) }
    }

    /** The notice's and the calendar line's words. */
    fun words(cal: ReceivedCalendar, note: FollowNote): Pair<String, String> {
        val days = note.lines.take(3).joinToString(", ") + if (note.lines.size > 3) " 외 ${note.lines.size - 3}일" else ""
        // PC a483ca15: the same words twice and '12/2은'; the days are named after a colon, with no 은/는 to get wrong.
        val keptDays = note.kept.joinToString(", ") { "${it.monthValue}/${it.dayOfMonth}" }
        val kept = if (note.kept.isEmpty()) "" else " · 직접 바꿔서 그대로 둔 날: $keptDays"
        return when (note.kind) {
            FollowNote.DONE -> "${cal.roster}가 바뀌어 내 달력에 넣었어요" to "$days$kept. 근무 알람도 바뀐 근무대로 울려요."
            FollowNote.ASK -> if (note.lines.isEmpty()) "${cal.roster}가 바뀌었어요" to "직접 바꾼 날이라 그대로 뒀어요: $keptDays"
            else "${cal.roster}가 바뀌었어요 · 내 달력에 넣을까요?" to "$days$kept. 넣으면 근무 알람도 따라가요."
            else -> "${cal.roster} 링크가 끊겼어요" to "내 달력은 그대로예요. 새 링크를 받으면 다시 이어서 받아요."
        }
    }
}

/**
 * 1.39 [TEAM-22] While a link is followed, a look every 6 hours (with a network) so a 리더's change reaches the member's alarms without
 * the app being opened. Gone by itself when no followed link is left.
 */
class RosterFollowJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext.appContainer
        running = c.appScope.launch {
            try { runCatching { RosterFollower.checkAll(c) } } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    companion object {
        private const val ID = 4407
        private val EVERY = TimeUnit.HOURS.toMillis(6)

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(ID) != null) return
            jobs.schedule(
                JobInfo.Builder(ID, ComponentName(context, RosterFollowJob::class.java))
                    .setPeriodic(EVERY, EVERY / 4)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) { context.getSystemService(JobScheduler::class.java)?.cancel(ID) }
    }
}
