package com.bamnat.app.data

import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyDraft
import com.bamnat.app.company.CompanyScope
import com.bamnat.app.company.PayWay
import com.bamnat.app.company.PublicCycleFiles
import com.bamnat.app.company.PublicUse
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import com.bamnat.core.time.Today

/**
 * [SETUP-01] Saves the first setup at once: the shifts, the order and the painted days in one
 * transaction, then a shared setup's kinds of extra work and pay rules, what the company screens need
 * and last the kind of work, which is what tells the app the setup is done. The draft is marked first,
 * so a save cut short is finished on the next start instead of being asked again.
 */
class FirstSetup(
    private val schedule: ScheduleRepository,
    private val settings: SettingsRepository,
    private val drafts: SetupDraftStore,
    /** [CALI-05] The main calendar's company things ([com.bamnat.app.AppContainer.companyOf]). */
    private val company: () -> CompanyScope,
    /** [CALI-05] A coworkers' setup from the server is in use now: counted on the server for that calendar. */
    private val used: (CompanyScope, AppliedSetup) -> Unit = { _, _ -> },
    private val today: () -> LocalDate = Today::date,
) {
    private val lock = Mutex()

    private val _saved = MutableStateFlow<SetupDraft?>(null)
    /** The setup just saved, for "예상 급여도 계산할까요?" on the calendar; taken once. */
    val saved: StateFlow<SetupDraft?> = _saved.asStateFlow()

    fun consumeSaved() {
        _saved.value = null
    }

    suspend fun save(draft: SetupDraft): SetupDraft = lock.withLock {
        val style = requireNotNull(draft.style) { "A setup without a kind of work" }
        val marked = draft.copy(committing = true, later = false)
        withContext(Dispatchers.IO) {
            drafts.save(marked)
            drafts.flush()
        }
        val saved = schedule.applySetup(marked, today())
        keepCompany(company(), marked, used)
        settings.update { it.copy(workStyle = style, rosterMonthly = marked.rosterMonthly && style == WorkStyle.SHIFT) }
        drafts.clear()
        _saved.value = saved
        saved
    }

    /** A save the app was closed in the middle of is finished, never asked again. */
    suspend fun finishInterrupted() {
        val d = drafts.draft.value ?: return
        if (d.committing && d.style != null) save(d)
    }

    companion object {
        /**
         * The rest of a setup once its shifts and order are saved into the calendar of [scope]: a shared setup's kinds of
         * extra work and pay rules, and what the company screens need. [CALI-05] 1.30 BB: the first setup and a calendar being
         * added alike, each into its own calendar (an added calendar's company used to be left out). A coworkers' setup that
         * came with its pay is remembered as the calendar's pay way and as already chosen ([com.bamnat.app.company.PayWayStore.decided]),
         * so the pay screen asks for the amounts only, not "이 회사 급여 설정을 쓸까요?" again, and keeps 맞아요·달라요 on it.
         */
        suspend fun keepCompany(scope: CompanyScope, marked: SetupDraft, used: (CompanyScope, AppliedSetup) -> Unit = { _, _ -> }) {
            val source = marked.source
            val store = scope.store
            val companyId = source?.id
            val setupId = source?.setupId
            val fromServer = source?.kind == SetupSource.Kind.SERVER && companyId != null && setupId != null
            val withPay = source != null && runCatching { org.json.JSONObject(source.text).has("pay") }.getOrDefault(false)
            // [FIN130-07] A coworkers' setup with its pay, on a calendar whose pay is already set up (설정 다시 하기, a calendar
            // that had its pay): the pay way is never merged into that pay without a word. Its kinds of extra work go in now, the
            // way waits on the pay screen for its 기본급 and goes in all at once there, or not at all (묶음 BA's PendingWay).
            val payWaits = fromServer && withPay && scope.pay.profile.value != null
            val text = if (payWaits) org.json.JSONObject(source!!.text).apply { remove("pay") }.toString() else source?.text
            // A broken part of a shared file was turned down when it was read; the shifts are saved either way.
            val pendingPay = text != null && runCatching { scope.share.applyRules(text) }.getOrDefault(false)
            if (fromServer && source != null && companyId != null && setupId != null) {
                // [COMM-03] 맞았어요·달라요 is asked about the company's setup in use.
                val applied = AppliedSetup(companyId, source.name, source.dept, setupId, source.version, source.adopted, Today.instant())
                store.setApplied(applied)
                store.setDraft(null)
                store.setHandMade(false)
                if (payWaits) {
                    com.bamnat.app.company.PendingWays.of(scope).offer(com.bamnat.app.company.PendingWay(companyId, source.name, source.dept, setupId,
                        source.text, com.bamnat.app.company.PendingWay.Source.FIRST_SETUP))
                    scope.payWays.askBase(true)
                } else if (withPay) {
                    scope.payWays.use(PayWay(companyId, source.name, source.dept, setupId))
                    if (pendingPay) scope.payWays.decided = true
                }
                used(scope, applied)
            } else if (source?.kind == SetupSource.Kind.PUBLIC) {
                // [SPAT-03] From a public row: its own way on (pay, then the plaza as '공개 자료 확인·고침'), not the hand-made invite,
                // so one card asks, not two. The order is the one saved, as the user may have changed it in the setup.
                val cycle = PublicCycles.byId(source.id.orEmpty()) ?: PublicCycles.provided(source.name, source.dept)
                val codes = PublicCycleFiles.codesOf(cycle, marked)
                store.setPublicUse(PublicUse(cycle.id, cycle.company, cycle.formKey, codes, PublicCycleFiles.picked(cycle, codes), Today.millis()))
                store.setDraft(null)
                store.setHandMade(false)
            } else if (marked.companyName.isNotBlank()) {
                // [COMM-02] Set up by hand under a company's name: the user is invited to share it later.
                store.setDraft(CompanyDraft(marked.companyName.trim().take(40)))
            } else if (source == null) {
                // [PLAZA-04] By hand with no company at all: invited too, the company name asked when they share (U2, U26).
                store.setHandMade(true)
            }
            if (pendingPay) store.pendingPay = source!!.text
        }
    }
}
