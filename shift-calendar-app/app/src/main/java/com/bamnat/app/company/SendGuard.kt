package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.core.plaza.CompareRow
import com.bamnat.core.plaza.DeptGroups
import com.bamnat.core.plaza.ProposalDraft
import com.bamnat.core.plaza.SameSetup
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.app.data.CompanyShare
import com.bamnat.core.time.Today
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * [PLZD-01][PLZD-02][PLZD-03][PLZD-05] 1.30 묶음 BD (사용자 2026-10-27: "광장에 이전 버젼과 동일한 내용이면 다시 올리지 못하게 …
 * 이미 해당 규칙이 존재해요, 규칙 적용해 드릴까요? … 근무주기가 2교대인데 3교대를 넣어서 … 비정상 사용을 금지. 이것은 광장에 해당 회사가
 * 있을때 만 적용"): what 내 설정 나누기 asks before a calendar's setup goes to a company the plaza already has.
 *
 *  - [Same]: the department already has this very rule. Nothing new goes; "이미 같은 규칙이 있어요" and one tap uses it.
 *  - [Shape]: the department's 대표 has work of another shape (2교대 against 3교대, another order length, 요일별, 근무표). It goes
 *    only under another department's name; the server holds it for the developer otherwise ([PLZD-04]).
 *  - [Differ]: the same shape with other values. 달라진 점만 제안으로 보내기 is the first choice ([draft]), a new version the second.
 *  - [Clear]: the company is not on the plaza, or the department has nothing yet: it goes as before.
 * [Verdict.resent] is this phone's version of the company sent within a day, which a new one replaces on the server ([PLZD-05]).
 *
 * The page comes from [CompanySearch]'s kept pages when there, so the check costs no extra round most times. A failed read
 * never stops a send: the server guards the same ([PLZD-04]).
 */
class SendGuard(private val c: AppContainer, val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN) {
    sealed interface Verdict {
        val resent: SentReport?
    }

    data class Clear(override val resent: SentReport? = null) : Verdict

    /** [withPay]: the plaza's has the pay way this calendar lacks; using it puts the pay in as well. */
    data class Same(val page: CompanyPage, val setup: SharedSetup, val withPay: Boolean, override val resent: SentReport? = null) : Verdict

    /** [mine] and [theirs] in words ("3교대 · 12일 순서"). */
    data class Shape(val page: CompanyPage, val lead: SharedSetup, val mine: String, val theirs: String, override val resent: SentReport? = null) : Verdict

    /** [rows]: what differs, the 대표 first; [draft] the proposal on the 대표 (null when its pay is not shown to this phone). */
    data class Differ(val page: CompanyPage, val lead: SharedSetup, val rows: List<CompareRow>, val draft: ProposalDraft?,
        override val resent: SentReport? = null) : Verdict

    private val scope: CompanyScope get() = c.companyOf(calendar)

    /** What [mine] (the file about to go) meets on the plaza under [company] and [dept]. [note] starts a proposal's why. */
    suspend fun check(company: String, dept: String, mine: String, note: String = "", now: Instant = Today.instant()): Verdict {
        val resent = resent(company, now)
        val page = runCatching { find(company) }.getOrNull() ?: return Clear(resent)
        return decide(page, dept, mine, note, resent)
    }

    /**
     * [PLZD-03] For a change that would go by itself ([AutoShare]): true when the department already has it or its work has
     * another shape, so nothing is asked or sent.
     */
    suspend fun skip(company: String, dept: String, mine: String): Boolean {
        val page = runCatching { find(company) }.getOrNull() ?: return false
        val v = decide(page, dept, mine)
        return v is Same || v is Shape
    }

    /** The company's page when the plaza has [name] (or an alias of it); null when it does not. */
    private suspend fun find(name: String): CompanyPage? {
        val key = CompanyText.norm(name)
        if (key.length < 2) return null
        val hit = c.companySearch.search(name).firstOrNull { h ->
            CompanyText.norm(h.name) == key || h.alias?.let(CompanyText::norm) == key
        } ?: return null
        return c.companySearch.page(hit.id) ?: c.companyApi.company(hit.id).also(c.companySearch::keepPage)
    }

    /** This phone's newest version of [company] sent within a day, from any calendar (the server's replacing is per phone). */
    private fun resent(company: String, now: Instant): SentReport? {
        val key = CompanyText.norm(company)
        return c.companyScopes().flatMap { it.store.sent.value }
            .filter { !it.ask && CompanyText.norm(it.company) == key && Duration.between(it.at, now) < DAY }
            .maxByOrNull { it.at }
    }

    /**
     * 이 규칙 쓰기 on [Same]: the calendar now uses the plaza's rule (its shifts are already these), says 맞아요 for it and, when
     * [Same.withPay], takes its pay way too. Nothing new goes to the plaza.
     */
    suspend fun useSame(v: Same, verified: Boolean = false): CompanyShare.Applied? {
        val s = v.setup
        val store = scope.store
        val result = if (v.withPay) scope.share.apply(s.text, rotation = false) else null
        if (result != null && (result.rules || result.pendingPay)) {
            scope.payWays.use(PayWay(v.page.id, v.page.name, s.dept, s.id))
            scope.payWays.decided = true
            if (result.rules) scope.payWays.askBase(true)
        }
        val before = store.applied.value
        if (before != null && before.setup != s.id) runCatching { c.companyApi.use(store.device, before.companyId, before.setup, false, scope.serverCal) }
        runCatching { c.companyApi.use(store.device, v.page.id, s.id, true, scope.serverCal) }
        runCatching { c.companyApi.vote(store.device, v.page.id, s.id, ok = true, verified = verified) }
        store.setApplied(AppliedSetup(v.page.id, v.page.name, s.dept, s.id, s.version, s.adopted, Today.instant(), vote = true))
        store.setMember(v.page.id, v.page.name)
        store.setDraft(null)
        if (result?.pendingPay == true) store.pendingPay = s.text
        // What the calendar has is what the plaza has: saving it again asks nothing.
        scope.autoShare.rebase()
        c.telemetry.event("company_apply", if (v.withPay) "same_pay" else "same")
        return result
    }

    companion object {
        val DAY: Duration = Duration.ofHours(24)

        /** [check] on a page already read: pure, for tests and screenshots. */
        fun decide(page: CompanyPage, dept: String, mine: String, note: String = "", resent: SentReport? = null): Verdict {
            val key = DeptGroups.key(dept)
            val inDept = page.setups.filter { DeptGroups.key(it.dept) == key }
            if (inDept.isEmpty()) return Clear(resent)
            val verdicts = inDept.map { it to SameSetup.compare(mine, it.text, it.payHidden) }
            verdicts.firstOrNull { (_, v) -> v.same && !v.payUnknown }?.let { (s, _) ->
                return Same(page, s, withPay = s.hasPay && !s.payHidden && !hasPay(mine), resent)
            }
            val lead = DeptGroups.of(inDept.map(::item)).first().lead.id.let { id -> inDept.first { it.id == id } }
            val v = verdicts.first { it.first === lead }.second
            return when (v.kind) {
                // The work is the same and the pay cannot be told apart: it goes, and the server counts it.
                SameSetup.Kind.SAME -> Clear(resent)
                // People say the 대표 is wrong: another shape is how it gets fixed (the server lets it through too).
                SameSetup.Kind.SHAPE -> if (lead.doubted) Clear(resent)
                    else Shape(page, lead, v.mine?.words.orEmpty(), v.theirs?.words.orEmpty(), resent)
                SameSetup.Kind.DIFFERENT -> if (v.rows.isEmpty()) Clear(resent)
                    else Differ(page, lead, v.rows, if (v.payUnknown) null else draft(lead.text, mine, note), resent)
            }
        }

        fun item(s: SharedSetup) = DeptGroups.Item(s.id, s.dept, s.adopted, s.lead, s.doubted, s.support, s.persons)

        private fun hasPay(text: String): Boolean = runCatching { JSONObject(text).has("pay") }.getOrDefault(false)

        /**
         * [PLZD-03] The 대표 as [mine] would have it: [mine]'s values, the 대표's pay way when [mine] went without one, and the
         * 대표's order when [mine] is the same order from another day. Only what really differs is proposed.
         */
        fun proposalText(lead: String, mine: String): String {
            val m = JSONObject(mine)
            val l = JSONObject(lead)
            if (!m.has("pay") && l.has("pay")) m.put("pay", l.get("pay"))
            val ms = m.optJSONObject("rotation")?.optJSONArray("steps")
            val ls = l.optJSONObject("rotation")?.optJSONArray("steps")
            if (ms != null && ls != null && SameSetup.sameTurn(steps(ms), steps(ls))) m.put("rotation", l.getJSONObject("rotation"))
            return m.toString()
        }

        private fun steps(a: JSONArray): List<String?> = (0 until a.length()).map { if (a.isNull(it)) null else a.optString(it) }

        /** 달라진 점만 제안으로 보내기: the 대표 with [mine]'s values, the lines that changed and [note] as the why. */
        fun draft(lead: String, mine: String, note: String = ""): ProposalDraft? = runCatching {
            val text = proposalText(lead, mine)
            val lines = SetupCompare.proposalLines(SetupCompare.rows(lead, text))
            ProposalDraft(lead, text, lines, note.trim()).takeIf { lines.isNotEmpty() }
        }.getOrNull()
    }
}
