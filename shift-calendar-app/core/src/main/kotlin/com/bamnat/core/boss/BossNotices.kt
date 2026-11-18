package com.bamnat.core.boss

import com.bamnat.core.pay.RateTable
import com.bamnat.core.pay.Rates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 1.39 G2-M1·I3 법 바뀜 알림: what changes on the next [Rates] day and whom it reaches, said above the 대장 in the weeks before.
 * 최저임금 2027 = 10,700원: 고용노동부 2026-08-05 고시 (대한민국 정책브리핑 2026-08-12 "내년도 최저임금 '1만 700원'",
 * korea.kr policyNewsView newsId=148969730, opened 2026-11-07). The rate rows themselves stay in [RateTable].
 */
object LawChange {
    /** How long before the day the notice shows. */
    const val AHEAD_DAYS = 75L

    data class Notice(
        val from: LocalDate,
        /** "최저임금 10,320원 → 10,700원", "국민연금 9.5% → 10%". */
        val changes: List<String>,
        /** Staff paid under the next 최저임금, with the 시급 they get now. */
        val under: List<Pair<StaffMember, Long>>,
        /** The first month's 인건비 more if each of [under] is raised to it (사업주 몫까지). */
        val raise: Long,
    )

    /** The lowest 시급 a staff member is paid (월급 ÷ 209, 일급 ÷ 8), as [PayrollLedger.warnings] reads it. */
    fun hourly(s: StaffMember): Long = when (s.form) {
        WageForm.HOUR -> (s.dayRates.values + s.base).min()
        WageForm.DAY -> s.base / 8
        WageForm.MONTH -> s.base / 209
    }

    private fun raised(s: StaffMember, min: Long): StaffMember = when (s.form) {
        WageForm.HOUR -> s.copy(base = maxOf(s.base, min), dayRates = s.dayRates.mapValues { maxOf(it.value, min) })
        WageForm.DAY -> s.copy(base = maxOf(s.base, min * 8))
        WageForm.MONTH -> s.copy(base = maxOf(s.base, min * 209))
    }

    fun next(place: Workplace, staff: List<StaffMember>, today: LocalDate, table: List<Rates> = RateTable.current): Notice? {
        val next = table.filter { it.effectiveFrom.isAfter(today) }.minByOrNull { it.effectiveFrom } ?: return null
        if (ChronoUnit.DAYS.between(today, next.effectiveFrom) > AHEAD_DAYS) return null
        val now = RateTable.on(today, table)
        val changes = buildList {
            if (next.minimumHourlyWage != now.minimumHourlyWage)
                add("최저임금 시급 ${"%,d".format(now.minimumHourlyWage)}원 → ${"%,d".format(next.minimumHourlyWage)}원")
            fun pct(d: Double) = "%.2f".format(d * 100).trimEnd('0').trimEnd('.') + "%"
            if (now.pensionRate != null && next.pensionRate != null && now.pensionRate != next.pensionRate) add("국민연금 ${pct(now.pensionRate)} → ${pct(next.pensionRate)}")
            if (now.healthRate != null && next.healthRate != null && now.healthRate != next.healthRate) add("건강보험 ${pct(now.healthRate)} → ${pct(next.healthRate)}")
            if (now.longTermCareRate != null && next.longTermCareRate != null && now.longTermCareRate != next.longTermCareRate)
                add("장기요양 ${pct(now.longTermCareRate)} → ${pct(next.longTermCareRate)}")
        }
        if (changes.isEmpty()) return null
        val first = YearMonth.from(next.effectiveFrom)
        val working = staff.filter { it.countsFor(first) }
        val min = next.minimumHourlyWage
        val under = working.map { it to hourly(it) }.filter { it.second in 1 until min }
        val raise = if (under.isEmpty()) 0 else {
            val ids = under.map { it.first.id }.toSet()
            PayrollLedger.month(place, working.map { if (it.id in ids) raised(it, min) else it }, first, emptyList()).total -
                PayrollLedger.month(place, working, first, emptyList()).total
        }
        return Notice(next.effectiveFrom, changes, under, raise)
    }
}

/**
 * 1.39 G2-M2 두루누리 사회보험료 지원: the 대장 shows the 4대보험 before it, so a small place is told once that it may get some back.
 * 4대사회보험 정보연계센터 (4insure.or.kr/ptl/totalinfo/tab02/page01.html, opened 2026-11-07), 2026년 기준: "근로자 수가 10명 미만인
 * 사업장에 고용된 근로자 중 월 평균보수가 270만원 미만인 신규가입자와 그 사업주에게 국민연금·고용보험료의 80%를 지원",
 * 36개월까지, 신규가입자 = 지원신청일 직전 1년간 국민연금·고용보험 취득 이력이 없는 근로자. The 금액 is only said for a year checked.
 */
object Durunuri {
    /** Years whose 보수 기준 was read from the 원문, and that 기준 in won (미만). */
    val checked: Map<Int, Long> = mapOf(2026 to 2_700_000L)
    const val PLACE_UNDER = 10

    /** 2.0.0 [RVJ-04] 검토 PAYC-04: the year's 기준 from the signed rates file (that year's own row), else [checked]. */
    fun limit(year: Int): Long? = com.bamnat.core.pay.RateTable.current.firstOrNull { it.effectiveFrom.year == year && it.durunuriUnder != null }
        ?.durunuriUnder ?: checked[year]

    /** The people the hint is about: 국민연금 or 고용보험 ticked, paid under the year's 기준 (any pay when the year is not checked). */
    fun who(ledger: Ledger): List<LedgerLine> {
        val counted = ledger.lines.count { it.gross > 0 }
        if (counted == 0 || counted >= PLACE_UNDER) return emptyList()
        val limit = limit(ledger.month.year)
        return ledger.lines.filter { l ->
            val ins = l.staff.insurance
            (ins.pension || ins.employment) && l.staff.insurance.tax != StaffTax.BIZ && l.gross > 0 && (limit == null || l.gross < limit)
        }
    }

    fun hint(ledger: Ledger): String? {
        val people = who(ledger).takeIf { it.isNotEmpty() } ?: return null
        val limit = limit(ledger.month.year)
        val names = people.map { it.staff.name.ifBlank { "이름 없음" } }.let { if (it.size <= 3) it.joinToString(", ") else "${it.size}명" }
        val rule = if (limit != null) "직원 10명 미만 사업장에서 월 보수 ${limit / 10_000}만 원 미만인 새 가입 직원" else "직원 10명 미만 사업장의 새 가입 직원(보수 기준은 그해 공고)"
        // PC 5066b027: someone with neither 보험 ticked was left out without a word; the list now says who it looked at.
        return "두루누리 지원: ${rule}은 국민연금·고용보험료의 80%를 최대 36개월 지원받을 수 있어요(국민연금·고용보험을 고른 직원 가운데 $names). " +
            "지난 1년 안에 두 보험에 가입한 적이 없어야 하고, 사장님이 4대사회보험 정보연계센터에서 신청해요. 이 대장의 4대보험은 지원 전 금액이에요."
    }
}
