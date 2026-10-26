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
    data class Pick(val company: String, val file: String, val cal: String = PersonalCalendar.MAIN)

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

    /** A cycle was picked for [Pick.company]: asked, or shared at once when the user already said yes to the plaza. */
    fun offer(pick: Pick) {
        if (!c.companyApi.enabled || CompanyText.norm(pick.company).length < 2) return
        if (c.companyStore.autoShare.value == true) go(pick, null) else _step.value = Step.Ask(pick)
        c.telemetry.event("lead_share", "offer")
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
        if (_step.value?.pick?.cal == PersonalCalendar.MAIN) c.companyStore.updatePublicUse { it.copy(inviteNever = true) }
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
     * 개인정보 takes that back. Added calendars keep nothing of the company on the phone.
     */
    fun forget(cal: String) {
        if (_step.value?.pick?.cal == cal) _step.value = null
        if (cal != PersonalCalendar.MAIN) return
        val store = c.companyStore
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
            used?.let { runCatching { c.companyApi.use(store.device, it.companyId, it.setup, false, cal) } }
            joined?.let { runCatching { c.companyApi.leave(store.device, it.first) } }
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

    private fun go(pick: Pick, site: String?) {
        _step.value = Step.Working(pick)
        c.appScope.launch {
            _step.value = try {
                decide(pick, site)
            } catch (e: CompanyException) {
                Step.Failed(pick, com.bamnat.app.ui.company.companyProblem(e.code, e.field), site)
            } catch (e: org.json.JSONException) {
                Step.Failed(pick, "이 근무주기는 나누지 못했어요", site)
            }
        }
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
            api.vote(c.companyStore.device, page.id, same.id, ok = true)
            runCatching { api.use(c.companyStore.device, page.id, same.id, true, pick.cal) }
            if (pick.cal == PersonalCalendar.MAIN) {
                c.companyStore.setApplied(AppliedSetup(page.id, page.name, same.dept, same.id, same.version, same.adopted, Today.instant(), vote = true))
            }
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
        if (c.remoteConfig.current.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) throw CompanyException("paused")
        val status = c.companyApi.report(c.companyStore.device, pick.company.trim().take(40), dept, setup, pick.cal, auto = false)
        if (!status.again) c.companyStore.addSent(SentReport(status.id, status.company, status.dept, ask = false, at = Today.instant()))
        status.member?.takeIf { it.joined && pick.cal == PersonalCalendar.MAIN }?.let { c.companyStore.setMember(status.companyId, status.company) }
        if (pick.cal == PersonalCalendar.MAIN) {
            // The main calendar uses what it shared: later changes go to the same company (AF) and the plaza invite stops.
            c.companyStore.setApplied(AppliedSetup(status.companyId, status.company, status.dept, status.setup, null, false, Today.instant()))
            c.companyStore.updatePublicUse { it.copy(sentId = status.id, companyId = status.companyId) }
        }
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
