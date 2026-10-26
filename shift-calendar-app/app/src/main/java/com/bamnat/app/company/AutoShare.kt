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
 */
class AutoShare(private val c: AppContainer) {
    /** The ask on screen: the company, and what changed ([lines] empty for the first time, the whole setup). */
    data class Ask(
        val company: String,
        val rewardMonths: Int,
        val lines: List<String> = emptyList(),
        val more: Int = 0,
        /** The file as it was when asked, which becomes the base whatever the answer. */
        internal val now: String = "",
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
    )

    private val _ask = MutableStateFlow<Ask?>(null)
    val ask: StateFlow<Ask?> = _ask.asStateFlow()

    private val _shared = MutableStateFlow<Shared?>(null)
    val shared: StateFlow<Shared?> = _shared.asStateFlow()

    /** Where to go after yes: the company's 광장 (its id), once the server said which. */
    private val _opened = MutableStateFlow<String?>(null)
    val opened: StateFlow<String?> = _opened.asStateFlow()

    /** One look at a time: two saves in a row ask once. */
    private val looking = Mutex()

    /**
     * The company the main calendar's setup belongs to: the one applied, else the one joined (by sending, [PLAZA-30] 442: a
     * public cycle's 올리기 too), else the one typed during setup.
     */
    private fun company(): Pair<String, String>? {
        c.companyStore.applied.value?.let { return it.company to it.dept }
        c.companyStore.member.value?.takeIf { it.second.isNotBlank() }?.let { return it.second to "" }
        c.companyStore.publicUse.value?.takeIf { it.sentId != null && it.company.isNotBlank() }?.let { return it.company to "" }
        c.companyStore.draft.value?.takeIf { !it.fix && it.company.isNotBlank() }?.let { return it.company to it.dept }
        return null
    }

    /** [PLAZA-30] 442: a public cycle not yet put up asks with its own card first; one ask, not two. */
    private fun publicCardFirst(): Boolean {
        val u = c.companyStore.publicUse.value ?: return false
        return u.sentId == null && !u.inviteNever && c.companyStore.applied.value == null && c.companyStore.member.value == null
    }

    /**
     * A cycle or pay way of the main calendar was saved. True when this may take the moment (the ask may show), so the screen
     * does not ask its own question on top.
     */
    fun saved(): Boolean {
        if (!c.companyApi.enabled) return false
        company() ?: return false
        val mode = c.companyStore.autoShare.value
        if (mode == false || publicCardFirst()) return false
        c.appScope.launch { runCatching { check() } }
        return mode == null
    }

    /** What [saved] does after the save, in its own time: asks, sends, or does nothing for the same values. */
    internal suspend fun check() = looking.withLock {
        if (!c.companyApi.enabled || publicCardFirst()) return@withLock
        val (name, _) = company() ?: return@withLock
        val mode = c.companyStore.autoShare.value
        if (mode == false) return@withLock
        val now = c.companyShare.exportForServer().toString()
        val change = ChangeAsk.of(c.companyStore.autoBase, now)
        if (change == ChangeAsk.Result.Same) return@withLock
        val changed = change as? ChangeAsk.Result.Changed
        if (mode == null) {
            val months = if (changed == null) runCatching { c.companyApi.terms().firstMonths }.getOrDefault(0) else 0
            _ask.value = Ask(name, months, changed?.lines.orEmpty(), changed?.more ?: 0, now)
            return@withLock
        }
        // 저절로 나누기: marked, sent now when its day has come, else waiting for it; one line either way.
        val base = c.companyStore.autoBase
        val sentAt = c.companyStore.autoSentAt
        c.companyStore.autoDirty = true
        val id = if (Today.millis() - sentAt >= DAY) runCatching { send(force = false) }.getOrNull() else null
        if (id == null && c.companyStore.autoDirty) schedule()
        _shared.value = Shared(name, ChangeAsk.line(change) ?: "근무 순서·계산 방식", queued = id == null,
            report = id?.let { lastReport }, base = base, sentAt = sentAt)
    }

    /**
     * 나눌게요: what is there goes now (an answer is not held to the day's one), the first time the company's 광장 opens.
     * [always] (다음부터 묻지 않고 저절로 나누기) remembers it as 저절로 나누기.
     */
    fun approve(always: Boolean = false) {
        val a = _ask.value
        _ask.value = null
        if (always) c.companyStore.setAutoShare(true)
        c.companyStore.autoDirty = true
        c.telemetry.event("plaza_auto", if (always) "yes_always" else "yes")
        c.appScope.launch {
            val base = c.companyStore.autoBase
            val sentAt = c.companyStore.autoSentAt
            val id = runCatching { send(force = true) }.getOrNull()
            if (a == null || a.first) (id ?: c.companyStore.applied.value?.companyId)?.let { _opened.value = it }
            else if (id != null) _shared.value = Shared(a.company, a.line.orEmpty(), queued = false, report = lastReport, base = base, sentAt = sentAt)
            if (c.companyStore.autoDirty && c.companyStore.autoShare.value == true) schedule()
        }
    }

    /** 이번엔 안 할게요: nothing goes, and the same values are not asked about again; the next change asks. */
    fun decline() {
        val a = _ask.value
        _ask.value = null
        a?.now?.takeIf { it.isNotEmpty() }?.let { c.companyStore.autoBase = it }
        c.telemetry.event("plaza_auto", "not_now")
    }

    /** 다시 묻지 않기: remembered; 설정 can turn it back on. */
    fun never() {
        val a = _ask.value
        _ask.value = null
        a?.now?.takeIf { it.isNotEmpty() }?.let { c.companyStore.autoBase = it }
        c.companyStore.setAutoShare(false)
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
        _shared.value = null
        c.telemetry.event("plaza_auto", "undo")
        if (s.queued) {
            c.companyStore.autoDirty = false
            return
        }
        c.appScope.launch {
            val id = s.report ?: return@launch
            runCatching { c.companyApi.takeBack(c.companyStore.device, id) }.onSuccess {
                c.companyStore.removeSent(id)
                c.companyStore.autoBase = s.base
                c.companyStore.autoSentAt = s.sentAt
            }
        }
    }

    /** 설정's choice: null 바뀔 때마다 묻기, true 저절로 나누기, false 나누지 않기. */
    fun set(mode: Boolean?) {
        c.companyStore.setAutoShare(mode)
        if (mode == true) {
            c.companyStore.autoDirty = true
            schedule()
        }
    }

    /**
     * [PLAZA-34] What the plaza has of this phone now: after a setup was applied from it or sent by hand, the same values
     * saved again ask nothing.
     */
    fun rebase() {
        c.appScope.launch { runCatching { c.companyStore.autoBase = c.companyShare.exportForServer().toString() } }
    }

    /** Whether something waits to be shared, for 서버 연결 점검's count. */
    val waiting: Boolean get() = c.companyStore.autoShare.value == true && c.companyStore.autoDirty

    private fun schedule() {
        val wait = (c.companyStore.autoSentAt + DAY - Today.millis()).coerceAtLeast(0)
        OutboxJob.scheduleShare(c.appContext, wait)
    }

    /**
     * Sends the waiting change when a day has passed since the last; true when nothing is left to send now (also when it
     * waits for its day, which [OutboxJob] was scheduled for). The company's id comes back from [send].
     */
    suspend fun flush(): Boolean {
        if (!waiting) return true
        if (Today.millis() - c.companyStore.autoSentAt < DAY) {
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
        val (name, dept) = company() ?: run { c.companyStore.autoDirty = false; return null }
        val setup = c.companyShare.exportForServer()
        val status = try {
            c.companyApi.report(c.companyStore.device, name, dept, setup, CAL, auto = true)
        } catch (e: CompanyException) {
            // A refusal for what the file says would come back the same: dropped, the next change tries again.
            if (!e.retryable) c.companyStore.autoDirty = false
            throw e
        }
        c.companyStore.autoDirty = false
        c.companyStore.autoSentAt = Today.millis()
        c.companyStore.autoBase = setup.toString()
        lastReport = status.id.takeIf { !status.again }
        if (!status.again) c.companyStore.addSent(SentReport(status.id, status.company, status.dept, ask = false, at = Today.instant()))
        status.member?.takeIf { it.joined }?.let { c.companyStore.setMember(status.companyId, status.company) }
        c.telemetry.event("plaza_auto", if (status.again) "again" else "sent")
        return status.companyId
    }

    companion object {
        /** The calendar the company's setup is for: the main one, the only one a company's setup goes into. */
        const val CAL = com.bamnat.app.data.PersonalCalendar.MAIN
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
