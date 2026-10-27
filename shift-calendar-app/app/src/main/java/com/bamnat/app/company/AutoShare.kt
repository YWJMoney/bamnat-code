package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.core.plaza.ChangeAsk
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
 */
class AutoShare(private val c: AppContainer, val cal: String = com.bamnat.app.data.PersonalCalendar.MAIN, internal val hub: Hub = Hub()) {
    /** [CALI-02] What is on screen, shared by every calendar's [AutoShare]. */
    class Hub {
        internal val ask = MutableStateFlow<Ask?>(null)
        internal val shared = MutableStateFlow<Shared?>(null)
        internal val opened = MutableStateFlow<String?>(null)
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
    ) {
        val first: Boolean get() = lines.isEmpty()
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
    )

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
        if (!c.companyApi.enabled) return false
        company() ?: return false
        val mode = store.autoShare.value
        if (mode == false || publicCardFirst()) return false
        c.appScope.launch { runCatching { check() } }
        return mode == null
    }

    /** What [saved] does after the save, in its own time: asks, sends, or does nothing for the same values. */
    internal suspend fun check() = looking.withLock {
        if (!c.companyApi.enabled || publicCardFirst()) return@withLock
        val (name, dept) = company() ?: return@withLock
        val mode = store.autoShare.value
        if (mode == false) return@withLock
        val now = scope.share.exportForServer().toString()
        val change = ChangeAsk.of(store.autoBase, now)
        if (change == ChangeAsk.Result.Same) return@withLock
        // [PLZD-03] 1.30 BD: the department already has it, or its work has another shape: nothing is asked or sent.
        if (SendGuard(c, cal).skip(name, dept, now)) { store.autoBase = now; return@withLock }
        val changed = change as? ChangeAsk.Result.Changed
        if (mode == null) {
            val months = if (changed == null) runCatching { c.companyApi.terms().firstMonths }.getOrDefault(0) else 0
            _ask.value = Ask(name, months, changed?.lines.orEmpty(), changed?.more ?: 0, now, cal)
            return@withLock
        }
        // 저절로 나누기: marked, sent now when its day has come, else waiting for it; one line either way.
        val base = store.autoBase
        val sentAt = store.autoSentAt
        store.autoDirty = true
        val id = if (Today.millis() - sentAt >= DAY) runCatching { send(force = false) }.getOrNull() else null
        if (id == null && store.autoDirty) schedule()
        _shared.value = Shared(name, ChangeAsk.line(change) ?: "근무 순서·계산 방식", queued = id == null,
            report = id?.let { lastReport }, base = base, sentAt = sentAt, cal = cal)
    }

    /**
     * 나눌게요: what is there goes now (an answer is not held to the day's one), the first time the company's 광장 opens.
     * [always] (다음부터 묻지 않고 저절로 나누기) remembers it as 저절로 나누기.
     */
    fun approve(always: Boolean = false) {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).approve(always)
        _ask.value = null
        if (always) store.setAutoShare(true)
        store.autoDirty = true
        c.telemetry.event("plaza_auto", if (always) "yes_always" else "yes")
        c.appScope.launch {
            val base = store.autoBase
            val sentAt = store.autoSentAt
            val id = runCatching { send(force = true) }.getOrNull()
            if (a == null || a.first) (id ?: store.applied.value?.companyId)?.let { _opened.value = it }
            else if (id != null) _shared.value = Shared(a.company, a.line.orEmpty(), queued = false, report = lastReport, base = base, sentAt = sentAt, cal = cal)
            if (store.autoDirty && store.autoShare.value == true) schedule()
        }
    }

    /** 이번엔 안 할게요: nothing goes, and the same values are not asked about again; the next change asks. */
    fun decline() {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).decline()
        _ask.value = null
        a?.now?.takeIf { it.isNotEmpty() }?.let { store.autoBase = it }
        c.telemetry.event("plaza_auto", "not_now")
    }

    /** 다시 묻지 않기: remembered; 설정 can turn it back on. */
    fun never() {
        val a = _ask.value
        if (a != null && a.cal != cal) return owner(a.cal).never()
        _ask.value = null
        a?.now?.takeIf { it.isNotEmpty() }?.let { store.autoBase = it }
        store.setAutoShare(false)
        c.telemetry.event("plaza_auto", "no")
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
     */
    fun undo() {
        val s = _shared.value ?: return
        if (s.cal != cal) return owner(s.cal).undo()
        _shared.value = null
        c.telemetry.event("plaza_auto", "undo")
        if (s.queued) {
            store.autoDirty = false
            return
        }
        c.appScope.launch {
            val id = s.report ?: return@launch
            runCatching { c.companyApi.takeBack(store.device, id) }.onSuccess {
                store.removeSent(id)
                store.autoBase = s.base
                store.autoSentAt = s.sentAt
            }
        }
    }

    /**
     * 설정's choice: null 바뀔 때마다 묻기, true 저절로 나누기, false 나누지 않기. [CALI-02] The phone's one choice: with 저절로
     * 나누기 each calendar's waiting change goes in its time.
     */
    fun set(mode: Boolean?) {
        store.setAutoShare(mode)
        if (mode == true) c.companyScopes().forEach { s ->
            s.store.autoDirty = true
            s.autoShare.schedule()
        }
    }

    /**
     * [PLAZA-34] What the plaza has of this calendar now: after a setup was applied from it or sent by hand, the same values
     * saved again ask nothing.
     */
    fun rebase() {
        c.appScope.launch { runCatching { store.autoBase = scope.share.exportForServer().toString() } }
    }

    /** Whether something of this calendar waits to be shared, for 서버 연결 점검's count. */
    val waiting: Boolean get() = store.autoShare.value == true && store.autoDirty

    /** [CALI-02] Each calendar's change waits for its own day, in a job of its own. */
    private fun schedule() {
        val wait = (store.autoSentAt + DAY - Today.millis()).coerceAtLeast(0)
        OutboxJob.scheduleShare(c.appContext, wait, cal)
    }

    /**
     * Sends the waiting change when a day has passed since the last; true when nothing is left to send now (also when it
     * waits for its day, which [OutboxJob] was scheduled for). The company's id comes back from [send].
     */
    suspend fun flush(): Boolean {
        if (!waiting) return true
        if (Today.millis() - store.autoSentAt < DAY) {
            schedule()
            return true
        }
        return try {
            send(force = false)
            true
        } catch (e: CompanyException) {
            !e.retryable
        }
    }

    /** The report [send] made last, for 되돌리기. */
    private var lastReport: String? = null

    /** One report of the setup as it is now, marked auto. The company's id comes back; null when there was nothing to send. */
    private suspend fun send(force: Boolean): String? {
        if (!force && !waiting) return null
        if (c.flags.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) throw CompanyException("paused")
        val store = store
        val (name, dept) = company() ?: run { store.autoDirty = false; return null }
        val setup = scope.share.exportForServer()
        val status = try {
            c.companyApi.report(store.device, name, dept, setup, scope.serverCal, auto = true)
        } catch (e: CompanyException) {
            // A refusal for what the file says would come back the same: dropped, the next change tries again.
            if (!e.retryable) store.autoDirty = false
            throw e
        }
        store.autoDirty = false
        store.autoSentAt = Today.millis()
        store.autoBase = setup.toString()
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
    }
}
