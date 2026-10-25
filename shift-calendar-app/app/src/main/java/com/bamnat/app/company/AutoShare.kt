package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [PLAZA-07] 결정 D79: the work order and pay way shared with the company's plaza by themselves. The first time a cycle or
 * pay way is saved for a known company (one applied, or typed during setup), the user is asked once ([ask]); after yes,
 * each change marks the setup to share and [OutboxJob] sends it at most once a day, through the same file as 보내기
 * (CompanyShare.exportForServer: no amounts, no names, nothing personal). 설정 › 개인정보·앱 정보 turns it off.
 */
class AutoShare(private val c: AppContainer) {
    /** The one-time ask on screen: the company it is about. */
    data class Ask(val company: String, val rewardMonths: Int)

    private val _ask = MutableStateFlow<Ask?>(null)
    val ask: StateFlow<Ask?> = _ask.asStateFlow()

    /** Where to go after yes: the company's 광장 (its id), once the server said which. */
    private val _opened = MutableStateFlow<String?>(null)
    val opened: StateFlow<String?> = _opened.asStateFlow()

    /** The company the main calendar's setup belongs to: the one applied, else the one typed during setup. */
    private fun company(): Pair<String, String>? {
        c.companyStore.applied.value?.let { return it.company to it.dept }
        c.companyStore.draft.value?.takeIf { !it.fix && it.company.isNotBlank() }?.let { return it.company to it.dept }
        return null
    }

    /**
     * A cycle or pay way of the main calendar was saved. True when this took the moment (the one-time ask is shown), so
     * the screen does not ask its own question on top.
     */
    fun saved(): Boolean {
        if (!c.companyApi.enabled) return false
        val (name, _) = company() ?: return false
        when (c.companyStore.autoShare.value) {
            null -> {
                c.appScope.launch {
                    val months = runCatching { c.companyApi.terms().firstMonths }.getOrDefault(0)
                    _ask.value = Ask(name, months)
                }
                return true
            }
            true -> {
                c.companyStore.autoDirty = true
                schedule()
            }
            false -> Unit
        }
        return false
    }

    /** 나눌게요: remembered; what is there goes now, and the company's 광장 opens. */
    fun approve() {
        _ask.value = null
        c.companyStore.setAutoShare(true)
        c.companyStore.autoDirty = true
        c.telemetry.event("plaza_auto", "yes")
        c.appScope.launch {
            val id = runCatching { send(force = true) }.getOrNull()
            (id ?: c.companyStore.applied.value?.companyId)?.let { _opened.value = it }
            if (c.companyStore.autoDirty) schedule()
        }
    }

    /** 나누지 않을래요: remembered, never asked again; 설정 can still turn it on. */
    fun decline() {
        _ask.value = null
        c.companyStore.setAutoShare(false)
        c.telemetry.event("plaza_auto", "no")
    }

    /** The screen went to the 광장 it was sent to. */
    fun openedDone() {
        _opened.value = null
    }

    /** 설정's switch. */
    fun set(on: Boolean) {
        c.companyStore.setAutoShare(on)
        if (on) {
            c.companyStore.autoDirty = true
            schedule()
        }
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

    /** One report of the setup as it is now, marked auto. The company's id comes back; null when there was nothing to send. */
    private suspend fun send(force: Boolean): String? {
        if (!force && !waiting) return null
        if (c.remoteConfig.current.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) throw CompanyException("paused")
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
