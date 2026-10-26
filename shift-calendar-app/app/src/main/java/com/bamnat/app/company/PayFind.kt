package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar

/**
 * [PLAZA-16] 1.27 묶음 AH (사용자 2026-10-25): 우리 회사 급여 방식 찾기 on the pay screen. Before it is used only that there is
 * one and how many said 맞아요 shows (결정 D79); using it joins the company's 광장 and brings the calculation in — the
 * amounts stay the user's own. With none, the pay is set up the easy way under the company's name, and the plaza's
 * question ("다른 분도 쓰게 나눌까요?") comes when it is saved.
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
    suspend fun use(hit: CompanyHit): Used {
        val api = c.companyApi
        val m = api.join(c.companyStore.device, hit.id, PersonalCalendar.MAIN)
        if (!m.joined) throw CompanyException(m.error ?: "member")
        val page = api.company(hit.id)
        c.companyStore.setMember(page.id, page.name)
        val lead = hit.lead?.setup
        val setup = page.setups.firstOrNull { it.id == lead && !it.payHidden && it.hasPay }
            ?: page.setups.filter { it.hasPay && !it.payHidden && !it.doubted }.maxByOrNull { if (it.lead) 1 else 0 }
            ?: return Used.Gone
        c.telemetry.event("pay_find", "use")
        // The company the pay is for, as a setup typed by hand would say, so a later share goes to the same 광장.
        if (c.companyStore.applied.value == null) c.companyStore.setDraft(CompanyDraft(page.name, setup.dept))
        return if (c.payStore.profile.value == null) {
            c.companyStore.pendingPay = setup.text
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
    fun known(): String = c.companyStore.applied.value?.company
        ?: c.companyStore.draft.value?.company
        ?: c.companyStore.publicUse.value?.company
        ?: ""
}
