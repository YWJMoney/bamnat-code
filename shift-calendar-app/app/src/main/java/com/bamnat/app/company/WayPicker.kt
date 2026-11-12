package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.pay.named
import com.bamnat.app.ui.pay.hourlyWeek
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.WayPick
import kotlinx.coroutines.flow.first

/**
 * [PAYP-18] 1.40 대표 기능 (공유급여 4-1, 사용자 결정 2026-11-07 19:21): a payslip picks the company's version of its pay way that fits
 * it best. Up to [WayPick.MAX_TRIED] versions with a pay way, the department the user picked first and the server's order (맞아요
 * 순) after, are each taken ([CompanyApi.take], 하루 12번 안), put on this calendar's pay for the payslip's month with the payslip's
 * 기본급 and allowances (nothing saved), and ranked by how close they come. What is shown is the gap alone; the ways stay hidden.
 */
class WayPicker(private val c: AppContainer, private val scope: CompanyScope) {
    /** One version tried: the [way] to put in when picked and the [profile] it came to with the payslip's amounts. */
    data class Option(val way: PendingWay, val profile: PayProfile, val tried: WayPick.Tried, val label: String)

    sealed interface Result {
        /** The versions tried, the closest first. [skipped] versions could not be taken (taken down, or 하루 한도). */
        data class Picked(val options: List<Option>, val skipped: Int) : Result
        /** The company has no version with a pay way to try. */
        data object None : Result
        /** No version could be taken today (하루 한도) or read. */
        data class Failed(val reason: String) : Result
    }

    /** [have]: versions already taken (the one waiting to go in), by setup id, so they are not taken again. */
    suspend fun pick(companyId: String, dept: String, slip: WayPick.Slip, have: Map<String, String> = emptyMap()): Result {
        val page = c.companySearch.page(companyId) ?: c.companyApi.company(companyId).also(c.companySearch::keepPage)
        val usable = page.setups.filter { it.hasPay && (!it.payHidden || it.payPrivate) && !it.doubted }
        if (usable.isEmpty()) return Result.None
        val mine = CompanyText.norm(dept)
        val chosen = (usable.filter { CompanyText.norm(it.dept) == mine } + usable.filter { CompanyText.norm(it.dept) != mine })
            .take(WayPick.MAX_TRIED)
        val snapshot = scope.schedule.snapshot.first()
        val settings = c.settingsRepository.current
        val calc = PayCalculator()
        val base = slip.base ?: return Result.Failed("기본급을 넣어 주세요")
        var skipped = 0
        var limited = false
        val options = mutableListOf<Option>()
        for (s in chosen) {
            val text = have[s.id] ?: taken[s.id] ?: try {
                wayText(c, scope.store.device, page.id, s)?.also { taken[s.id] = it }
            } catch (e: CompanyException) {
                if (e.code == "rate_limited") limited = true
                null
            }
            if (text == null) {
                skipped++
                continue
            }
            val tried = runCatching {
                val p0 = scope.share.tryWay(text, base, slip.month) ?: return@runCatching null
                fun run(p: PayProfile) = calc.calculate(p.named(settings.extraKinds).hourlyWeek(settings), slip.month, snapshot.resolver::resolve, checks = false)
                val p = WayPick.filled(p0, slip, run(p0))
                p to WayPick.tried(s.id, run(p), slip)
            }.getOrNull()
            if (tried == null) {
                skipped++
                continue
            }
            val way = PendingWay(page.id, page.name, s.dept, s.id, text, PendingWay.Source.FIND)
            options += Option(way, tried.first, tried.second, label(s, chosen))
        }
        c.telemetry.event("pay_pick", if (options.isEmpty()) "none" else "tried_${options.size}")
        if (options.isEmpty()) return Result.Failed(
            if (limited) "오늘은 급여 방식을 더 받을 수 없어요. 내일 다시 해 주세요" else "이 회사 급여 방식을 지금은 받을 수 없어요",
        )
        val byId = options.associateBy { it.tried.id }
        return Result.Picked(WayPick.rank(options.map { it.tried }).map { byId.getValue(it.id) }, skipped)
    }

    /** "생산1팀 · 2판 · 맞아요 8": what tells versions apart without their values (결정 D79). */
    private fun label(s: SharedSetup, all: List<SharedSetup>): String = listOfNotNull(
        s.dept.ifEmpty { "부서 없음" },
        s.version?.takeIf { all.count { o -> o.dept == s.dept } > 1 }?.let { "${it}판" },
        s.persons.takeIf { it > 0 }?.let { "맞아요 $it" },
    ).joinToString(" · ")

    companion object {
        /** Taken in this app run, so trying another payslip takes nothing again (a restart forgets them, as [PendingWays]). */
        private val taken = java.util.Collections.synchronizedMap(LinkedHashMap<String, String>())
    }
}
