package com.bamnat.app.company

import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.pay.PayStore

/**
 * [CALI-01] 1.30 묶음 BB (사용자 2026-10-27: "다른 달력으로 회사를 선택하면 전체로 적용이 되고 있다. 달력은 개별적으로 …
 * 온전한 독립 달력이다"): everything about one calendar's company, as one thing — the company it uses and joined, what it
 * sent ([store]), the company pay way its pay uses ([payWays]), putting a company's setup into its own shifts and pay
 * ([share]), sharing its changes ([autoShare]) and 우리 회사 급여 방식 찾기 ([find]).
 *
 * [com.bamnat.app.AppContainer.companyOf] gives it. The main calendar's is made of the very objects the app always had
 * (`companyStore`, `payWays`, `companyShare`, `autoShare`) over the preferences files it always had, so a phone that only
 * uses 내 달력 reads, writes and waits for exactly what it did before 1.30. An added calendar's keeps its own two small
 * files in its own folder ([CompanyScopeStorage]) and works on its own shifts and pay.
 */
class CompanyScope(
    val calendarId: String,
    val store: CompanyStore,
    val payWays: PayWayStore,
    private val shareOf: () -> CompanyShare,
    val autoShare: AutoShare,
    private val payOf: () -> PayStore,
    private val scheduleOf: () -> ScheduleRepository,
    findOf: (CompanyScope) -> PayFind,
) {
    val isMain: Boolean get() = calendarId == PersonalCalendar.MAIN

    /**
     * The calendar's shifts and pay as a company file reads and writes them. Asked each time rather than kept: a restore
     * opens an added calendar's database anew, and the file must go into that one ([com.bamnat.app.data.CalendarBook]).
     */
    val share: CompanyShare get() = shareOf()

    /** The calendar's pay setup (the main one's is [com.bamnat.app.AppContainer.payStore]). */
    val pay: PayStore get() = payOf()

    /** The calendar's shifts and order. */
    val schedule: ScheduleRepository get() = scheduleOf()

    /** 우리 회사 급여 방식 찾기 for this calendar's pay. */
    val find: PayFind by lazy { findOf(this) }

    /** [CALI-02] What the company server's `cal` says for this calendar: "main", or the calendar's id. */
    val serverCal: String get() = CompanyScopeStorage.serverCal(calendarId)

    /** What a 명세서 tick rests on for this calendar's pay ([com.bamnat.app.AppContainer.slipProof] for the main one). */
    fun slipProof(): com.bamnat.core.pay.SlipProof? =
        com.bamnat.core.pay.SlipProof.best(pay.profile.value, java.time.YearMonth.from(com.bamnat.core.time.Today.date()))

    /** The company this calendar is known to be in: the one in use, else joined, else typed for it; null for none. */
    fun companyName(): String? = store.applied.value?.company
        ?: store.member.value?.second?.takeIf { it.isNotBlank() }
        ?: store.draft.value?.company?.takeIf { it.isNotBlank() }
        ?: store.publicUse.value?.company?.takeIf { it.isNotBlank() }
}
