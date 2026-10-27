package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar

/**
 * [PLAZA-16] 1.27 묶음 AH (사용자 2026-10-25): 우리 회사 급여 방식 찾기 on the pay screen. Before it is used only that there is
 * one and how many said 맞아요 shows (결정 D79); using it joins the company's 광장 and brings the calculation in — the
 * amounts stay the user's own. With none, the pay is set up the easy way under the company's name, and the plaza's
 * question ("다른 분도 쓰게 나눌까요?") comes when it is saved.
 *
 * [PAYF-01][PAYF-04] 1.29 묶음 AP: using it is remembered ([PayWayStore]) and counted on the server as this phone using that
 * setup, so the pay screen keeps 맞아요·달라요 on it and the search says "n명 쓰고 있어요" truly.
 *
 * [CALI-04] 1.30 BB: for one calendar's pay ([CompanyScope.find]); it joins the company for that calendar and remembers the
 * way in that calendar's stores.
 *
 * [PAYG-01] 1.30 묶음 BA: "이 급여 방식 쓰기" only joins and reads the way: it waits as a [PendingWay] until the 기본급 is given
 * ([commit]), and nothing of the pay, the pay way in use, the company record or the server's '쓰고 있어요' changes before.
 */
class PayFind(private val c: AppContainer, val calendar: String = PersonalCalendar.MAIN) {
    private val scope: CompanyScope get() = c.companyOf(calendar)

    sealed interface Used {
        /** The way is read and waits for the 기본급 ([PendingWays.pending]); nothing is put in yet. */
        data class Ready(val way: PendingWay) : Used
        /** The server had no pay way to give after joining (taken down meanwhile). */
        data object Gone : Used
    }

    /** 이 급여 방식 쓰기 on [hit]'s 대표 주기. */
    suspend fun use(hit: CompanyHit): Used = use(hit.id, hit.lead?.let { l -> l.payWay?.takeIf { !l.pay }?.setup ?: l.setup })

    /** [PAYF-01] 이 급여 방식 쓰기 on company [id], its 대표 주기 [lead] first when the search named one. */
    suspend fun use(id: String, lead: String?): Used {
        val api = c.companyApi
        val s = scope
        val store = s.store
        // [PAYG-09] SP1: the join answers with the page as a member reads it (one round trip less); an older server does not.
        val (m, joined) = api.joinWithPage(store.device, id, s.serverCal)
        if (!m.joined) throw CompanyException(m.error ?: "member")
        val page = joined ?: api.company(id)
        runCatching { c.companySearch.keepPage(page) }
        // The server keeps a join for 30 days whatever happens next, so the phone says it truly (결정 D79: members see the way).
        store.setMember(page.id, page.name)
        val setup = page.setups.firstOrNull { it.id == lead && !it.payHidden && it.hasPay }
            ?: page.setups.filter { it.hasPay && !it.payHidden && !it.doubted }.maxByOrNull { if (it.lead) 1 else 0 }
            ?: return Used.Gone
        c.telemetry.event("pay_find", "use")
        val way = PendingWay(page.id, page.name, setup.dept, setup.id, setup.text, PendingWay.Source.FIND)
        s.pendingWays.offer(way)
        return Used.Ready(way)
    }

    /** None found: the easy setup under [company]'s name; saving it asks the one-time plaza question (AutoShare). */
    fun none(company: String) {
        val name = company.trim().take(40)
        val store = scope.store
        c.telemetry.event("pay_find", "none")
        if (store.applied.value != null) return
        // [PAYG-12] No name to go by (R1 2장): made by hand all the same, so the plaza asks with the company's name then.
        if (CompanyText.norm(name).length < 2) {
            if (store.draft.value == null) store.setHandMade(true)
            return
        }
        store.setDraft(CompanyDraft(name))
    }

    /** The company the user is known to work for, to start the search from. */
    fun known(): String = knownAll().firstOrNull().orEmpty()

    /** [PAYF-02] Every company name this phone already has for the user, the surest first, for the rows shown before any answer. */
    fun knownAll(): List<String> = scope.let { s ->
        listOfNotNull(s.payWays.way.value?.company, s.store.applied.value?.company, s.store.draft.value?.company, s.store.publicUse.value?.company)
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
