package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.core.plaza.ChangeAsk
import com.bamnat.core.plaza.SharePart
import com.bamnat.core.plaza.ShareParts
import com.bamnat.core.plaza.ShareWay
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PLAZA-07] 결정 D79: the work order and pay way shared with the company's plaza, through the same file as 보내기
 * (CompanyShare.exportForServer: no amounts, no names, nothing personal).
 *
 * [PLAZA-34] 1.27 AI (사용자 2026-10-25: "주기를 바꾸거나 급여설정을 바꾸면 해당 내용을 나눌까요로 … 내용 수정시 물어보게"):
 * each time a cycle or pay way of a known company is saved, what the plaza would see is held against the one last shared or
 * asked about ([CompanyStore.autoBase], 결정 AI-D9). The same values saved again ask nothing. A change, by the way the user chose
 * ([CompanyStore.autoShare]):
 *  - null, 바뀔 때마다 묻기 (the default): "바뀐 내용을 회사 광장에 나눌까요? (야간 50% → 60%)" ([ask]);
 *  - true, 저절로 나누기: it goes by itself, at most once a day ([OutboxJob]), and one line says so with 되돌리기 ([shared]);
 *  - false, 다시 묻지 않기: nothing is asked or sent. 설정 › 개인정보·앱 정보 changes it.
 * A public cycle's own 올리기 card ([com.bamnat.app.ui.company.PublicWayNotice]) asks first; this waits until it is done (AI-D11).
 *
 * [CALI-02] 1.30 BB: one per calendar ([CompanyScope.autoShare]), each with its own company, base and day's count, sent with
 * the calendar as `cal`. The ask, the one line and the 광장 to open are the phone's ([Hub]): one at a time on screen, and
 * the answer goes back to the calendar that asked ([Ask.cal]). The way it was chosen (묻기·저절로·나누지 않기) is the phone's.
 *
 * 2.0.0 [RVN-22] 검토 I10bc-03 (COMP-44·46): the way is chosen for each part ([ShareWays]: 근무주기·근무 시간·급여 계산 방식, each
 * 저절로·묻기·안 함). A change is split by part: 안 함 parts become the base (never sent, not asked again), 묻기 parts are asked with
 * their own lines, and 저절로 parts are held on the phone until the next morning (9시) with one line '내일 오전에 나눠요 · 취소'
 * ([CompanyStore.heldParts]); 취소 drops them on the phone, so nothing that went has to be taken back from the server. What goes
 * is the plaza's file with only those parts as they are now ([ShareFile.compose], [CompanyStore.plazaHas]). A plaza that has
 * nothing of the calendar yet gets it all as one, the cycle's way deciding.
 */
class AutoShare(private val c: AppContainer, val cal: String = com.bamnat.app.data.PersonalCalendar.MAIN, internal val hub: Hub = Hub()) {
    /** [CALI-02] What is on screen, shared by every calendar's [AutoShare]. */
    class Hub {
        internal val ask = MutableStateFlow<Ask?>(null)
        internal val shared = MutableStateFlow<Shared?>(null)
        internal val opened = MutableStateFlow<String?>(null)
    }

    /**
     * 1.37 PC fca16ba8 (현장 건별형을 고른 뒤 회사 광장에 나눌까요?): work written day by day (현장·자영업·투자) is never offered to a
     * company, even by a calendar that once had one. 교대·일근, and an 알바 that joined its company, are asked as before.
     */
    private fun companyWork(): Boolean = when (c.calendars.styleOf(cal, c.settingsRepository.current.workStyle)) {
        com.bamnat.core.work.WorkStyle.FIELD, com.bamnat.core.work.WorkStyle.BUSINESS, com.bamnat.core.work.WorkStyle.INVEST -> false
        else -> true
    }

    /** This calendar's company things. */
    private val scope: CompanyScope get() = c.companyOf(cal)
    private val store: CompanyStore get() = scope.store

    /** The ask on screen: the company, and what changed ([lines] empty for the first time, the whole setup). */
    data class Ask(
        val company: String,
        val rewardMonths: Int,
        val lines: List<String> = emptyList(),
        val more: Int = 0,
        /** The file as it was when asked, which becomes the base whatever the answer. */
        internal val now: String = "",
        /** [CALI-02] The calendar that asked. */
        val cal: String = com.bamnat.app.data.PersonalCalendar.MAIN,
        /** 2.0.0 [RVN-22] I10bc-03 The parts asked about: what a yes sends, a no keeps off and 다시 묻지 않기 turns to 안 함. */
        val parts: Set<SharePart> = SharePart.entries.toSet(),
    ) {
        val first: Boolean get() = lines.isEmpty()
        /** "근무주기·급여 계산 방식" for the words of what goes. */
        val partWords: String get() = ShareParts.words(parts)
        /** "야간 50% → 60% · 주간 07:00~19:00 → 08:00~20:00 외 1가지", null the first time. */
        val line: String? get() = ChangeAsk.line(if (first) ChangeAsk.Result.First else ChangeAsk.Result.Changed(lines, more))
    }

    /** [PLAZA-34] The one line after a change went (or waits for its day) by itself: what, and 되돌리기. */
    data class Shared(
        val company: String,
        val line: String,
        /** Waits for its day: 되돌리기 only takes it off the queue. */
        val queued: Boolean,
        internal val report: String? = null,
        internal val base: String? = null,
        internal val sentAt: Long = 0,
        /** [CALI-02] The calendar it went from. */
        val cal: String = com.bamnat.app.data.PersonalCalendar.MAIN,
        /** 2.0.0 [RVN-10] COMP-46 The file as it was when it went or was queued: a queued one taken back becomes the base. */
        internal val now: String? = null,
        /** 2.0.0 [RVN-22] What the plaza had before it went, for a 되돌리기 of one that went. */
        internal val has: String? = null,
        /** 2.0.0 [RVN-22] I10bc-03 When a held one goes (epoch ms); 0 once there is a connection. */
        val until: Long = 0,
        /** 2.0.0 [RVN-13] COMP-47 It could not go now for want of a connection: it goes once online, and the line says so. */
        val offline: Boolean = false,
        /**
         * 2.0.0 [RVN-13] COMP-47 The server would not take it (its error word, said by [com.bamnat.app.ui.company.companyProblem]):
         * nothing waits, and the line has no 되돌리기.
         */
        val refused: CompanyException? = null,
    )

    /** 2.0.0 [RVN-10] COMP-46 What 되돌리기 did: a queued change left the queue, a sent one came off the 광장, or it waits for a connection. */
    enum class Undone { DROPPED, TAKEN, WAITING }

    private val _ask get() = hub.ask
    val ask: StateFlow<Ask?> = hub.ask.asStateFlow()

    private val _shared get() = hub.shared
    val shared: StateFlow<Shared?> = hub.shared.asStateFlow()

    /** Where to go after yes: the company's 광장 (its id), once the server said which. */
    private val _opened get() = hub.opened
    val opened: StateFlow<String?> = hub.opened.asStateFlow()

    /** [CALI-02] The [AutoShare] of the calendar [of] came from, for an answer given through any calendar's. */
    private fun owner(of: String): AutoShare = if (of == cal) this else c.companyOf(of).autoShare

    /** One look at a time: two saves in a row ask once. */
    private val looking = Mutex()

    /**
     * The company the calendar's setup belongs to: the one applied, else the one joined (by sending, [PLAZA-30] 442: a
     * public cycle's 올리기 too), else the one typed during setup.
     */
    private fun company(): Pair<String, String>? {
        // [CALI-04] 예시 달력 (made up, on this phone only) never shares.
        if (cal == com.bamnat.app.data.PersonalCalendar.EXAMPLE) return null
        store.applied.value?.let { return it.company to it.dept }
        store.member.value?.takeIf { it.second.isNotBlank() }?.let { return it.second to "" }
        store.publicUse.value?.takeIf { it.sentId != null && it.company.isNotBlank() }?.let { return it.company to "" }
        store.draft.value?.takeIf { !it.fix && it.company.isNotBlank() }?.let { return it.company to it.dept }
        return null
    }

    /** [PLAZA-30] 442: a public cycle not yet put up asks with its own card first; one ask, not two. */
    private fun publicCardFirst(): Boolean {
        val u = store.publicUse.value ?: return false
        return u.sentId == null && !u.inviteNever && store.applied.value == null && store.member.value == null
    }

    /**
     * A cycle or pay way of this calendar was saved. True when this may take the moment (the ask may show), so the screen
     * does not ask its own question on top.
     */
    fun saved(): Boolean {
        if (!c.companyApi.enabled || !companyWork()) return false
        company() ?: return false
        val ways = store.shareWays.value
        if (ways.of(ShareWay.NEVER).size == SharePart.entries.size || publicCardFirst()) return false
        c.appScope.launch { runCatching { check() } }
        return ways.of(ShareWay.ASK).isNotEmpty()
    }

    /**
     * What [saved] does after the save, in its own time: asks, holds, or does nothing for the same values. [askToo] false (a way
     * changed in 설정) only holds what now goes by itself, with no question over the settings screen.
     */
    internal suspend fun check(askToo: Boolean = true) = looking.withLock {
        if (!c.companyApi.enabled || !companyWork() || publicCardFirst()) return@withLock
        val (name, dept) = company() ?: return@withLock
        val ways = store.shareWays.value
        val now = scope.share.exportForServer().toString()
        val base = store.autoBase
        val change = ChangeAsk.of(base, now)
        if (change == ChangeAsk.Result.Same) return@withLock
        // [PLZD-03] 1.30 BD: the department already has it, or its work has another shape: nothing is asked or sent.
        if (SendGuard(c, cal).skip(name, dept, now)) { store.autoBase = now; return@withLock }
        if (store.plazaHas == null) {
            // 2.0.0 [RVN-22] The plaza has nothing of this calendar: it goes as one (a pay way or hours alone cannot), the cycle's
            // way deciding; the parts set to 안 함 stay out (the hours go with the shifts: a shift is its hours).
            val parts = ShareParts.changed(null, now).filterTo(sortedSetOf()) { ways[it] != ShareWay.NEVER } + SharePart.CYCLE
            when (ways.cycle) {
                ShareWay.NEVER -> Unit
                ShareWay.ASK -> if (askToo) {
                    val changed = change as? ChangeAsk.Result.Changed
                    val months = if (changed == null) runCatching { c.companyApi.terms().firstMonths }.getOrDefault(0) else 0
                    _ask.value = Ask(name, months, changed?.lines.orEmpty(), changed?.more ?: 0, now, cal, parts)
                }
                ShareWay.AUTO -> hold(name, parts, now)
            }
            return@withLock
        }
        val changed = ShareParts.changed(base, now)
        val never = changed.filterTo(sortedSetOf()) { ways[it] == ShareWay.NEVER }
        val auto = changed.filterTo(sortedSetOf()) { ways[it] == ShareWay.AUTO }
        val ask = changed.filterTo(sortedSetOf()) { ways[it] == ShareWay.ASK }
        // 안 함: the base takes them, so they are neither sent nor asked about again.
        if (never.isNotEmpty()) store.autoBase = ShareFile.compose(now, base, never) ?: base
        if (auto.isNotEmpty()) hold(name, auto, now)
        if (ask.isNotEmpty() && askToo) {
            val lines = ShareParts.lines(base, now, ask) as? ChangeAsk.Result.Changed
            _ask.value = Ask(name, 0, lines?.lines.orEmpty(), lines?.more ?: 0, now, cal, ask)
        }
    }

    /**
     * 2.0.0 [RVN-22] I10bc-03 저절로: [parts] held on the phone until the next morning (with what was held already), one line that
     * says so with 취소, and the job for that morning.
     */
    private fun hold(name: String, parts: Set<SharePart>, now: String) {
        val held = store.heldParts
        val all = held + parts
        val line = ChangeAsk.line(ShareParts.lines(store.autoBase, now, all)) ?: ShareParts.words(all)
        val until = if (held.isNotEmpty()) store.heldUntil else nextMorning()
        store.hold(all, line, until)
        schedule(replace = held.isEmpty())
        c.telemetry.event("plaza_auto", "held")
        _shared.value = Shared(name, line, queued = true, cal = cal, now = now, until = until)
    }

    /**
     * 나눌게요: the parts asked about go now (an answer is not held to the morning), the first time the company's 광장 opens.
     * [always] (다음부터 묻지 않고 저절로 나누기) turns those parts to 저절로.
     */
    fun approve(always: Boolean = false) {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).approve(always)
        _ask.value = null
        val parts = a?.parts ?: SharePart.entries.toSet()
        if (always) store.setShareWays(store.shareWays.value.with(parts, ShareWay.AUTO))
        c.telemetry.event("plaza_auto", if (always) "yes_always" else "yes")
        c.appScope.launch {
            val base = store.autoBase
            val has = store.plazaHas
            val sentAt = store.autoSentAt
            var failed: Exception? = null
            val id = try { send(parts) } catch (e: Exception) { failed = e; null }
            // 2.0.0 [RVN-13] COMP-47 A yes without a connection is kept and goes once online; a refusal is said. [RVN-22] With a change
            // already held for the morning it goes with that one, as its line says.
            val refused = (failed as? CompanyException)?.takeIf { !it.retryable }
            if (failed != null && refused == null) {
                val held = store.heldParts
                store.hold(held + parts, a?.line ?: store.heldLine ?: ShareParts.words(parts), if (held.isEmpty()) 0 else store.heldUntil)
                if (held.isEmpty()) OutboxJob.scheduleShare(c.appContext, 0, cal, replace = true)
            }
            if (a == null || a.first) (id ?: store.applied.value?.companyId)?.let { _opened.value = it }
            if (failed != null) _shared.value = Shared(a?.company ?: company()?.first.orEmpty(), a?.line ?: a?.partWords ?: "근무주기·급여 계산 방식",
                queued = refused == null, base = base, has = has, sentAt = sentAt, cal = cal, offline = refused == null, refused = refused,
                until = if (refused == null) store.heldUntil else 0)
            else if (a != null && !a.first && id != null) _shared.value = Shared(a.company, a.line.orEmpty(), queued = false, report = lastReport,
                base = base, has = has, sentAt = sentAt, cal = cal)
        }
    }

    /** 이번엔 안 할게요: nothing goes, and the same values are not asked about again; the next change asks. */
    fun decline() {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).decline()
        _ask.value = null
        a?.let(::settle)
        c.telemetry.event("plaza_auto", "not_now")
    }

    /** 다시 묻지 않기: the parts asked about turn to 안 함 (설정 can turn them back). */
    fun never() {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).never()
        _ask.value = null
        a?.let(::settle)
        store.setShareWays(store.shareWays.value.with(a?.parts ?: SharePart.entries.toSet(), ShareWay.NEVER))
        c.telemetry.event("plaza_auto", "no")
    }

    /** The parts an ask was about take the values it asked about, as the base later changes are said against. */
    private fun settle(a: Ask) {
        if (a.now.isEmpty()) return
        store.autoBase = ShareFile.compose(a.now, store.autoBase, a.parts) ?: a.now
    }

    /** The screen went to the 광장 it was sent to. */
    fun openedDone() {
        _opened.value = null
    }

    /** The one line was read or closed. */
    fun sharedDone() {
        _shared.value = null
    }

    /**
     * 되돌리기 on the one line: a change waiting for its day leaves the queue; one already sent is taken back on the server, and
     * the base and the day's count are as they were, so the next change is held against what the plaza still has.
     * 2.0.0 [RVN-10] COMP-46 It says what happened, so the line never says 거뒀어요 when nothing came off: a queued change taken back
     * is the base from then on (the same values saved again are not asked or queued again), and a sent one the server could not be
     * reached for waits in [CompanyStore.takeBackLater] and goes once online ([OutboxJob]); the line says so with 다시 하기.
     * 2.0.0 [RVN-22] I10bc-03 What goes by itself is held on the phone first, so its 취소 never needs the server ([cancel]).
     */
    suspend fun undo(): Undone {
        val s = _shared.value ?: return Undone.DROPPED
        if (s.cal != cal) return owner(s.cal).undo()
        _shared.value = null
        c.telemetry.event("plaza_auto", "undo")
        if (s.queued) {
            cancel(s.now)
            return Undone.DROPPED
        }
        val id = s.report ?: return Undone.TAKEN
        // What the plaza will have once it is off: the next change is held against that, either way.
        store.autoBase = s.base
        store.plazaHas = s.has
        store.autoSentAt = s.sentAt
        return if (takeBack(id)) Undone.TAKEN else {
            store.takeBackLater(id, waiting = true)
            OutboxJob.schedule(c.appContext)
            c.telemetry.event("plaza_auto", "undo_waiting")
            Undone.WAITING
        }
    }

    /**
     * 2.0.0 [RVN-22] I10bc-03 취소 of what is held, on the phone only: nothing goes, and the held parts take the values they had then
     * ([now], else the calendar as it is) as the base, so the same values saved again are not held again.
     */
    suspend fun cancel(now: String? = null) {
        val parts = store.heldParts
        if (parts.isEmpty()) return
        val file = now ?: scope.share.exportForServer().toString()
        store.autoBase = ShareFile.compose(file, store.autoBase, parts) ?: file
        store.dropHeld()
        if (_shared.value?.queued == true && _shared.value?.cal == cal) _shared.value = null
        c.telemetry.event("plaza_auto", "cancel")
    }

    /** One report off the 광장 (gone already counts); false when the server could not be reached. */
    private suspend fun takeBack(id: String): Boolean {
        val ok = runCatching { c.companyApi.takeBack(store.device, id) }.isSuccess
        if (ok) {
            store.removeSent(id)
            store.takeBackLater(id, waiting = false)
        }
        return ok
    }

    /** COMP-46 The take-backs waiting for a connection, now (다시 하기, or [OutboxJob]); true when none is left. */
    suspend fun takeBackWaiting(): Boolean {
        for (id in store.takeBackLater) if (!takeBack(id)) return false
        return true
    }

    /**
     * 설정's choice as the one switch it was: null 바뀔 때마다 묻기, true 저절로 나누기 (근무 순서와 계산 방식, as its words said),
     * false 나누지 않기. [CALI-02] The phone's one choice.
     */
    fun set(mode: Boolean?) {
        store.setAutoShare(mode)
        // 2.0.0 [RVN-11] COMP-44 설정's switch says 근무 순서와 계산 방식: on there is a yes for both.
        if (mode == true) store.setAutoPay(true)
        afterWays()
    }

    /** 2.0.0 [RVN-22] I10bc-03 설정's choice for one part. */
    fun setWay(part: SharePart, way: ShareWay) {
        store.setShareWays(store.shareWays.value.with(part, way))
        afterWays()
    }

    /**
     * A part set to 안 함 leaves what was held of it in every calendar; a part set to 저절로 holds the change of it that waited (no
     * question over the settings screen: the next save asks what is 묻기).
     */
    private fun afterWays() {
        val ways = store.shareWays.value
        c.companyScopes().forEach { s ->
            val held = s.store.heldParts
            val keep = held.filterTo(sortedSetOf()) { ways[it] != ShareWay.NEVER }
            if (keep != held) if (keep.isEmpty()) s.store.dropHeld() else s.store.hold(keep, ShareParts.words(keep), s.store.heldUntil)
            if (ways.of(ShareWay.AUTO).isNotEmpty()) c.appScope.launch { runCatching { s.autoShare.check(askToo = false) } }
        }
    }

    /**
     * [PLAZA-34] What the plaza has of this calendar now: after a setup was applied from it or sent by hand, the same values
     * saved again ask nothing.
     */
    fun rebase() {
        c.appScope.launch { runCatching { store.markShared(scope.share.exportForServer().toString()) } }
    }

    /** Whether something of this calendar is held to be shared, for 서버 연결 점검's count. */
    val waiting: Boolean get() = store.heldParts.isNotEmpty()

    /** 2.0.0 [RVN-22] I10bc-03 What is held for a morning: the company, the words of what changed and when it goes. */
    data class Held(val company: String, val line: String, val until: Long, val cal: String)

    /**
     * What this calendar holds for its morning, for the line that stays on 광장 and 설정 with 취소 until it goes; null when nothing is
     * (or only a 나눌게요 waiting for a connection, which [SmallSends.waitingCount] counts).
     */
    fun held(): Held? {
        val parts = store.heldParts
        if (parts.isEmpty() || store.heldUntil == 0L) return null
        val name = company()?.first ?: return null
        return Held(name, store.heldLine ?: ShareParts.words(parts), store.heldUntil, cal)
    }

    /** COMP-46 Whether a 되돌리기 of this calendar waits for a connection, for 서버 연결 점검's count. */
    val takingBack: Boolean get() = store.takeBackLater.isNotEmpty()

    /** [CALI-02] Each calendar's held change waits for its own morning, in a job of its own. */
    private fun schedule(replace: Boolean = false) {
        OutboxJob.scheduleShare(c.appContext, (store.heldUntil - Today.millis()).coerceAtLeast(0), cal, replace)
    }

    /** The job again after a run that found its morning not come yet ([OutboxJob]). */
    internal fun reschedule() = schedule()

    /**
     * Sends what is held once its morning came (at once for a 나눌게요 that waited for a connection); true when nothing is left to
     * send now (also when it waits for its morning, which [OutboxJob] was scheduled for).
     */
    suspend fun flush(): Boolean {
        val parts = store.heldParts
        if (parts.isEmpty()) return true
        if (store.heldUntil > Today.millis()) {
            schedule()
            return true
        }
        return try {
            send(parts)
            store.dropHeld()
            true
        } catch (e: CompanyException) {
            // A refusal for what the file says would come back the same: dropped, the next change tries again.
            if (!e.retryable) store.dropHeld()
            !e.retryable
        }
    }

    /** The report [send] made last, for 되돌리기. */
    private var lastReport: String? = null

    /**
     * One report of [parts] as they are now and the rest as the plaza has them ([ShareFile.compose]), marked auto. The company's
     * id comes back; null when there was no company to send to. A plaza that has nothing of this calendar gets the cycle with it.
     */
    private suspend fun send(parts: Set<SharePart>): String? {
        if (c.flags.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) throw CompanyException("paused")
        val store = store
        val (name, dept) = company() ?: run { store.dropHeld(); return null }
        val now = scope.share.exportForServer().toString()
        val file = ShareFile.compose(now, store.plazaHas, parts) ?: ShareFile.compose(now, null, parts + SharePart.CYCLE) ?: now
        val status = c.companyApi.report(store.device, name, dept, org.json.JSONObject(file), scope.serverCal, auto = true)
        store.autoSentAt = Today.millis()
        store.autoBase = ShareFile.compose(now, store.autoBase, parts) ?: file
        store.plazaHas = file
        lastReport = status.id.takeIf { !status.again }
        if (!status.again) store.addSent(SentReport(status.id, status.company, status.dept, ask = false, at = Today.instant()))
        status.member?.takeIf { it.joined }?.let { store.setMember(status.companyId, status.company) }
        c.telemetry.event("plaza_auto", if (status.again) "again" else "sent")
        return status.companyId
    }

    companion object {
        /**
         * The main calendar's `cal` on the company server ("main"), and the server's word for a request that names none.
         * [CALI-02] An added calendar sends its own id ([CompanyScope.serverCal]).
         */
        const val CAL = com.bamnat.app.data.PersonalCalendar.MAIN
        const val DAY = 24 * 60 * 60 * 1000L

        /**
         * A day has passed since [sentAt]. 2.0.0 [RVN-22] 검토 COMP-60-3: a [sentAt] after now (sent with the phone's clock set ahead,
         * then put back) counts as long ago, so the share does not stop at 대기 중 for as long as the clock was ahead.
         */
        internal fun dayCame(sentAt: Long, now: Long = Today.millis()): Boolean = sentAt > now || now - sentAt >= DAY

        /** 2.0.0 [RVN-22] I10bc-03 The hour of the morning a held change goes. */
        const val MORNING_HOUR = 9

        /** The next day's [MORNING_HOUR] on the phone's clock: a change held today goes tomorrow morning, never sooner. */
        internal fun nextMorning(now: java.time.ZonedDateTime = Today.zoned()): Long =
            now.toLocalDate().plusDays(1).atTime(MORNING_HOUR, 0).atZone(now.zone).toInstant().toEpochMilli()
    }
}
