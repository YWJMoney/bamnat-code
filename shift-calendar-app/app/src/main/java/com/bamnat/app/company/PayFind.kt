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
 * setup, so the pay screen keeps 맞아요·달라요 on it and the search says "n명 쓰고 있어요" truly; the amounts screen that
 * follows does not ask the same question again.
 */
class PayFind(private val c: AppContainer) {
    sealed interface Used {
        /** The rules went onto the pay setup the user had. */
        data object Applied : Used
        /** No pay setup yet: the company's way waits for the user's own amounts (the pay screen asks only those). */
        data object Amounts : Used
        /** The server had no pay way to give after joining (taken down meanwhile). */
        data object Gone : Used
    }

    /** 이 급여 방식 쓰기 on [hit]'s 대표 주기. */
    suspend fun use(hit: CompanyHit): Used = use(hit.id, hit.lead?.let { l -> l.payWay?.takeIf { !l.pay }?.setup ?: l.setup })

    /** [PAYF-01] 이 급여 방식 쓰기 on company [id], its 대표 주기 [lead] first when the search named one. */
    suspend fun use(id: String, lead: String?): Used {
        val api = c.companyApi
        val m = api.join(c.companyStore.device, id, PersonalCalendar.MAIN)
        if (!m.joined) throw CompanyException(m.error ?: "member")
        val page = api.company(id)
        c.companyStore.setMember(page.id, page.name)
        val setup = page.setups.firstOrNull { it.id == lead && !it.payHidden && it.hasPay }
            ?: page.setups.filter { it.hasPay && !it.payHidden && !it.doubted }.maxByOrNull { if (it.lead) 1 else 0 }
            ?: return Used.Gone
        c.telemetry.event("pay_find", "use")
        // The company the pay is for, as a setup typed by hand would say, so a later share goes to the same 광장.
        if (c.companyStore.applied.value == null) c.companyStore.setDraft(CompanyDraft(page.name, setup.dept))
        // [PAYF-04] Remembered for 맞아요·달라요 on the pay screen, and counted as one more phone using it ("n명 쓰고 있어요").
        c.payWays.use(PayWay(page.id, page.name, setup.dept, setup.id))
        runCatching { api.use(c.companyStore.device, page.id, setup.id, true, AutoShare.CAL) }
        return if (c.payStore.profile.value == null) {
            c.companyStore.pendingPay = setup.text
            c.payWays.decided = true
            Used.Amounts
        } else {
            c.companyShare.applyRules(setup.text)
            Used.Applied
        }
    }

    /** None found: the easy setup under [company]'s name; saving it asks the one-time plaza question (AutoShare). */
    fun none(company: String) {
        val name = company.trim().take(40)
        if (CompanyText.norm(name).length < 2) return
        if (c.companyStore.applied.value == null) c.companyStore.setDraft(CompanyDraft(name))
        c.telemetry.event("pay_find", "none")
    }

    /** The company the user is known to work for, to start the search from. */
    fun known(): String = knownAll().firstOrNull().orEmpty()

    /** [PAYF-02] Every company name this phone already has for the user, the surest first, for the rows shown before any answer. */
    fun knownAll(): List<String> = listOfNotNull(
        c.payWays.way.value?.company,
        c.companyStore.applied.value?.company,
        c.companyStore.draft.value?.company,
        c.companyStore.publicUse.value?.company,
    ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
