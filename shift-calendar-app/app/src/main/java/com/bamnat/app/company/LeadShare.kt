package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.plaza.CycleMatch
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * [PLAZA-14] 1.27 묶음 AH (사용자 2026-10-25): a cycle picked after a company search is asked about once — "이 정보를 다른
 * 사람이 사용해도 될까요?". Yes makes it the company's 대표 주기 when it has none ("이제 ‘<회사>’의 대표 주기가 됐어요!"),
 * adds a 맞아요 when the same order is already its 대표 (AH-I2), and otherwise asks where the user works (공장·사업장·부서)
 * so the order leads under '<회사> · <사업장>'. Only the shifts, their times and their order go (no pay, no amount, no
 * name); yes is also the plaza's one-time approval (AF, 결정 D79), so the same is not asked twice (AH-D6).
 */
class LeadShare(private val c: AppContainer) {
    /** What was picked: the company as the user named it, the cycle as a company settings file, the calendar it went into. */
    data class Pick(
        val company: String,
        val file: String,
        val cal: String = PersonalCalendar.MAIN,
        /**
         * 1.38 통합 8차 [CYC-03]: a table not in the public list (처음 제공, '우리 회사 근무표 올리기'): coworkers find it at once,
         * the public list only after the review, which the dialog says apart.
         */
        val firstGiven: Boolean = false,
        /**
         * 2.0.0 [RVN-19] 검토 T10a-10: the 사업장 a first provider typed in its own box: it goes under it at once ('회사 · 사업장'),
         * not asked again.
         */
        val site: String = "",
    )

    sealed interface Step {
        val pick: Pick

        /** 이 정보를 다른 사람이 사용해도 될까요? */
        data class Ask(override val pick: Pick) : Step
        data class Working(override val pick: Pick) : Step
        /** 이제 ‘[name]’의 대표 주기가 됐어요! ([name] is "회사" or "회사 · 사업장"). */
        data class Made(override val pick: Pick, val name: String, val companyId: String?) : Step
        /** The same order already leads there: a 맞아요 was added instead of a second version. */
        data class Agreed(override val pick: Pick, val name: String, val companyId: String?) : Step
        /** Another order leads the company: where does the user work? [lead] is that order said in words. */
        data class NeedSite(override val pick: Pick, val name: String, val lead: String) : Step
        /** The 사업장 has another order leading already: this one waits for 맞아요. */
        data class Waiting(override val pick: Pick, val name: String, val companyId: String?) : Step
        data class Failed(override val pick: Pick, val message: String, val site: String? = null) : Step
    }

    private val _step = MutableStateFlow<Step?>(null)
    val step: StateFlow<Step?> = _step.asStateFlow()

    /** [CALI-02] The company things of the calendar [pick] went into. */
    private fun scopeOf(pick: Pick): CompanyScope = c.companyOf(pick.cal)

    /** A cycle was picked for [Pick.company]: asked, or shared at once when the user already said yes to the plaza. */
    fun offer(pick: Pick) {
        if (!c.companyApi.enabled || CompanyText.norm(pick.company).length < 2) return
        // [J133-17] A no to another share ask lately: not asked now (설정 › 광장 can still turn it on).
        if (c.companyStore.autoShare.value != true && !ShareAskGate.open(c.appContext)) return
        if (c.companyStore.autoShare.value == true) go(pick, null) else _step.value = Step.Ask(pick)
        c.telemetry.event("lead_share", "offer")
    }

    /**
     * 1.38 통합 8차 [CYC-03] ('우리 회사 근무표 올리기'로 왔는데 달력에 넣기만 있어요): the user asked to put it up, so it is asked
     * (or sent, after an earlier yes) even after a no to another share ask; false when there is no server to send it to.
     */
    fun upload(pick: Pick): Boolean {
        if (!c.companyApi.enabled || CompanyText.norm(pick.company).length < 2) return false
        if (c.companyStore.autoShare.value == true) go(pick, null) else _step.value = Step.Ask(pick)
        c.telemetry.event("lead_share", "upload")
        return true
    }

    /**
     * [EASE-03] 1.38 PC 994c2328 (저장한 뒤 '다른 사람이 사용해도 될까요?' 창이 끼어들어요): the first setup asks it on its summary
     * instead, as one box never ticked beforehand. True when that box is shown for [company]: the plaza is on, the name is a
     * name, and the user has not already said yes (then it goes on its own, as before).
     */
    fun canAsk(company: String): Boolean =
        c.companyApi.enabled && CompanyText.norm(company).length >= 2 && c.companyStore.autoShare.value != true && ShareAskGate.open(c.appContext)

    /**
     * The setup was saved: shared at once when the user already said yes to the plaza or ticked the summary's box ([ticked]);
     * otherwise nothing is sent and nothing is asked over the new calendar (설정 › 광장 can still turn it on).
     */
    fun afterSetup(pick: Pick, ticked: Boolean) {
        if (!c.companyApi.enabled || CompanyText.norm(pick.company).length < 2) return
        when {
            c.companyStore.autoShare.value == true -> go(pick, null)
            ticked -> { c.companyStore.setAutoShare(true); go(pick, null) }
            else -> return
        }
        c.telemetry.event("lead_share", if (ticked) "summary_yes" else "auto")
    }

    /** 같이 쓸게요: the plaza's one-time yes too (AH-D6), then sent. */
    fun yes() {
        val pick = _step.value?.pick ?: return
        c.companyStore.setAutoShare(true)
        c.telemetry.event("lead_share", "yes")
        go(pick, null)
    }

    /** 내 달력에만: nothing is sent, and the plaza does not ask the same today (설정 can still turn it on). */
    fun no() {
        if (c.companyStore.autoShare.value == null) c.companyStore.setAutoShare(false)
        // The same question is not asked again by the public cycle's share card (one consent, AH-D6); its pay card stays.
        // [CALI-02] The card of the calendar it went into.
        _step.value?.pick?.let { p -> scopeOf(p).store.updatePublicUse { it.copy(inviteNever = true) } }
        ShareAskGate.declined(c.appContext)
        c.telemetry.event("lead_share", "no")
        _step.value = null
    }

    /** The 사업장 the user works at, after [Step.NeedSite]. */
    fun site(name: String) {
        val pick = _step.value?.pick ?: return
        val site = name.trim().take(40)
        if (CompanyText.norm(site).isEmpty()) return
        go(pick, site)
    }

    /**
     * [SPAT-07] 1.27 AH-D3 (사용자 2026-10-25): the cycle of [cal] was deleted, so that calendar is as the first time — the
     * company setup it used, the public row it came from, the company typed for it and the plaza it joined are let go
     * (on the server too: no longer counted as using it, no longer a member). What was shared stays shared; 설정 ›
     * 개인정보 takes that back. [CALI-02] 1.30 BB: each calendar its own; the company stays joined while another calendar of
     * this phone is in it (the server counts a phone once).
     */
    fun forget(cal: String) {
        if (_step.value?.pick?.cal == cal) _step.value = null
        val scope = c.companyOf(cal).takeIf { it.calendarId == cal } ?: return
        val store = scope.store
        val used = store.applied.value
        val joined = store.member.value
        store.setApplied(null)
        store.setPublicUse(null)
        store.setDraft(null)
        store.setHandMade(false)
        store.setMember(null)
        c.telemetry.event("lead_share", "forget")
        if (!c.companyApi.enabled) return
        c.appScope.launch {
            val others = c.companyScopes().filter { it.calendarId != cal }
            // 2.0.0 [RVN-13] COMP-48 kept for later when the server could not be reached: 개인정보 takes it back for sure.
            used?.let { SmallSends.send(c, store, SmallSends.use(it.companyId, it.setup, false, scope.serverCal)) }
            joined?.takeIf { j -> others.none { it.store.member.value?.first == j.first } }?.let { SmallSends.send(c, store, SmallSends.leave(it.first)) }
        }
    }

    /** 다시 해 보기 after a failure that had no 사업장 yet. */
    fun retry() {
        val pick = _step.value?.pick ?: return
        go(pick, null)
    }

    fun close() {
        _step.value = null
    }

    /**
     * 2.0.0 [RVN-18] 검토 COMP-22-4: '나누는 중이에요' had no button and back was held, so a slow connection could only be waited out.
     * Now it has 뒤에서 계속하기: the dialog goes, the sending goes on, and what came of it is one line at the bottom (a 사업장 still
     * to tell, or a failure, opens the dialog again: those need the user).
     */
    fun later() {
        if (_step.value !is Step.Working) return
        quiet = true
        _step.value = null
    }

    private var quiet = false

    private fun go(pick: Pick, asked: String?) {
        quiet = false
        _step.value = Step.Working(pick)
        // 2.0.0 [RVN-19] The 사업장 typed with the name, unless one was just told.
        val site = asked ?: pick.site.trim().takeIf { CompanyText.norm(it).isNotEmpty() }
        c.appScope.launch {
            val next = try {
                decide(pick, site)
            } catch (e: CompanyException) {
                Step.Failed(pick, com.bamnat.app.ui.company.companyProblem(e), site)
            } catch (e: org.json.JSONException) {
                Step.Failed(pick, "이 근무주기는 나누지 못했어요", site)
            }
            val line = if (quiet) doneLine(next) else null
            quiet = false
            if (line != null) {
                _step.value = null
                com.bamnat.app.ui.components.AppSnack.show(line)
            } else _step.value = next
        }
    }

    /** What a finished share says on one line once its dialog was let go; null when the user still has something to answer. */
    private fun doneLine(s: Step): String? = when (s) {
        is Step.Made -> if (s.pick.firstGiven) "‘${s.name}’ 근무표를 올렸어요" else "‘${s.name}’의 대표 주기가 됐어요"
        is Step.Agreed -> "‘${s.name}’에 같은 주기가 있어 맞아요 한 표를 더했어요"
        is Step.Waiting -> "‘${s.name}’에 나눴어요 · 맞아요가 모이면 대표가 돼요"
        else -> null
    }

    /** Where the pick goes, as AH-D4·D5 say; one report or one vote at most. */
    suspend fun decide(pick: Pick, site: String?): Step {
        val api = c.companyApi
        val setup = serverSetup(pick.file)
        val want = order(setup) ?: emptyList()
        val norm = CompanyText.norm(pick.company)
        val hit = api.search(pick.company).firstOrNull { CompanyText.norm(it.name) == norm || it.alias?.let(CompanyText::norm) == norm }
        val dept = site.orEmpty()
        val title = { name: String -> if (dept.isEmpty()) name else "$name · $dept" }
        if (hit == null) return send(pick, dept, setup).let { Step.Made(pick, title(it.company), it.companyId) }
        val page = api.company(hit.id)
        val leads = page.setups.filter { it.lead }
        // The same order leads somewhere in the company: one more 맞아요 there, not a second version.
        val same = leads.filter { site == null || CompanyText.norm(it.dept) == CompanyText.norm(dept) }
            .firstOrNull { l -> want.isNotEmpty() && order(JSONObject(l.text))?.let { CycleMatch.same(it, want) } == true }
        if (same != null) {
            val scope = scopeOf(pick)
            api.vote(scope.store.device, page.id, same.id, ok = true)
            runCatching { api.use(scope.store.device, page.id, same.id, true, scope.serverCal) }
            // [CALI-02] The calendar it went into uses it (before 1.30 only the main one remembered it).
            scope.store.setApplied(AppliedSetup(page.id, page.name, same.dept, same.id, same.version, same.adopted, Today.instant(), vote = true))
            // [SETUP-16] 1.37 PC 0068727b (근무 시간을 바꾼 뒤 묻는 창에 바뀐 내용이 없음): what the plaza has now is the base later changes
            // are said against ("주간 19:00 → 18:30").
            scope.autoShare.rebase()
            return Step.Agreed(pick, if (same.dept.isEmpty()) page.name else "${page.name} · ${same.dept}", page.id)
        }
        val here = leads.filter { CompanyText.norm(it.dept) == CompanyText.norm(dept) }
        if (site == null && leads.isNotEmpty()) {
            val lead = here.firstOrNull() ?: leads.first()
            return Step.NeedSite(pick, page.name, spoken(JSONObject(lead.text)))
        }
        val sent = send(pick, dept, setup)
        return if (here.isEmpty()) Step.Made(pick, title(page.name), sent.companyId) else Step.Waiting(pick, title(page.name), sent.companyId)
    }

    private suspend fun send(pick: Pick, dept: String, setup: JSONObject): ReportStatus {
        if (c.flags.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) throw CompanyException("paused")
        val scope = scopeOf(pick)
        val store = scope.store
        val status = c.companyApi.report(store.device, pick.company.trim().take(40), dept, setup, scope.serverCal, auto = false)
        // [CALI-08] Kept with the calendar it went from, so that calendar's 광장 shows it.
        if (!status.again) store.addSent(SentReport(status.id, status.company, status.dept, ask = false, at = Today.instant()))
        status.member?.takeIf { it.joined }?.let { store.setMember(status.companyId, status.company) }
        // The calendar uses what it shared: later changes go to the same company (AF) and the plaza invite stops.
        store.setApplied(AppliedSetup(status.companyId, status.company, status.dept, status.setup, null, false, Today.instant()))
        store.updatePublicUse { it.copy(sentId = status.id, companyId = status.companyId) }
        scope.autoShare.rebase()
        c.telemetry.event("lead_share", if (dept.isEmpty()) "sent" else "site")
        return status
    }

    companion object {
        /**
         * The company settings file as the server takes it: the shifts, their order (from today when it says no start) and
         * the days of the week; no company name, no public row id, never pay (근무주기만).
         */
        fun serverSetup(file: String): JSONObject {
            val o = JSONObject(file)
            val out = JSONObject().put("app", o.optString("app", "com.bamnat.app")).put("format", o.getString("format")).put("v", o.optInt("v", 2))
                .put("shiftTypes", o.getJSONArray("shiftTypes"))
            o.optJSONObject("rotation")?.let { r ->
                out.put("rotation", JSONObject().put("anchor", r.optString("anchor").ifEmpty { Today.date().toString() }).put("steps", r.getJSONArray("steps")))
            }
            o.optJSONArray("weekly")?.let { out.put("weekly", it) }
            if (o.optBoolean("roster")) out.put("roster", true)
            return out
        }

        /** The order of a company file by day kind ([CycleMatch.day]); the week's days when it has no rotation; null with neither. */
        fun order(o: JSONObject): List<String>? {
            val off = o.optJSONArray("shiftTypes").objectsOf().associate { it.optString("name") to it.optBoolean("off") }
            val steps = o.optJSONObject("rotation")?.optJSONArray("steps") ?: o.optJSONArray("weekly") ?: return null
            return (0 until steps.length()).map { i ->
                val name = if (steps.isNull(i)) null else steps.getString(i)
                CycleMatch.day(name, name == null || off[name] == true)
            }
        }

        /** "주주휴휴야야휴휴": an order as people say it, by each shift's first letter. */
        fun spoken(o: JSONObject): String = order(o)?.joinToString("") { it.take(1) }.orEmpty()

        private fun JSONArray?.objectsOf(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
