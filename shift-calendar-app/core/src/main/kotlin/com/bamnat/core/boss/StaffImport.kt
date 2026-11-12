package com.bamnat.core.boss

import com.bamnat.core.imports.StaffRow
import com.bamnat.core.pay.RateTable
import com.bamnat.core.work.StaffShift
import java.time.LocalDate

/**
 * 1.38 가져오기 (사용자 2026-11-05 "종이 사진이나 엑셀 표, PDF 등 어떤 형식이든"): a staff list read from any file (the 인식 갈래's
 * [StaffRow]) into the 급여 대장. Someone already in the ledger by the same name is not added twice; a field the reader was not
 * sure of, or did not find, is named so the 사장님 looks at it before the month is worked out. Nothing is sent anywhere.
 */
object StaffImport {
    /**
     * One person to add: the member as it will be saved, and the fields to look at ("시급·월급", "급여 형태", "근무 요일·시간").
     * 2.0.0 [RVJ-40] 검토 PC-102: [noAmount] when the list gave no sum the reader could read. The member's base is 0 then, not the
     * 최저시급 (an 일급·월급 person came in at 10,320원 an hour), and the preview asks for it before the line can be added.
     */
    data class Line(val member: StaffMember, val check: List<String>, val already: Boolean, val noAmount: Boolean = false)

    private fun norm(s: String) = s.replace(" ", "").lowercase()

    fun lines(rows: List<StaffRow>, existing: List<StaffMember>, on: LocalDate, id: () -> String = { java.util.UUID.randomUUID().toString() },
              legalBreaks: Boolean = true): List<Line> {
        val have = existing.map { norm(it.name) }.toMutableSet()
        val minimum = RateTable.on(on).minimumHourlyWage
        return rows.map { r ->
            val form = r.form ?: WageForm.HOUR
            val noAmount = r.base == null || r.base <= 0
            val check = buildList {
                // [RVJ-40] Said as it is, the cell itself when there was one: no guess at the 최저시급.
                if (noAmount) add(if (r.unreadCells.isEmpty()) "금액이 없어요 · 적어 주세요" else "금액을 못 읽었어요 · 적어 주세요")
                else if ("base" in r.doubtful) add("금액")
                if (!noAmount && form == WageForm.HOUR && r.base!! < minimum) add("${on.year}년 최저시급 ${"%,d원".format(minimum)}보다 적어요")
                if (r.form == null || "form" in r.doubtful) add("시급·월급·일급")
                if (r.weekly.isEmpty() || "weekly" in r.doubtful) add(if (r.weekly.isEmpty()) "근무 요일·시간이 없어요" else "근무 요일·시간")
                if ("tax" in r.doubtful) add("소득세 떼는 법")
                if ("foreign" in r.doubtful) add("외국인인지")
                r.unreadCells.forEach { add("$it 못 읽음") }
            }
            val bare = StaffMember(
                id(), r.name.trim(), form, if (noAmount) 0L else r.base!!, hiredOn = r.hiredOn, job = r.job.orEmpty(),
                weekly = r.weekly.mapValues { (_, t) -> StaffShift(t.first, t.second) },
                insurance = Insurance(tax = r.tax ?: if (form == WageForm.DAY) StaffTax.DAILY else StaffTax.WAGE, foreign = r.foreign ?: false),
            )
            // 2.0.0 [RVJ-16] 검토 BOSS-60: a person brought in by a list gets the 4대보험 the law puts them in, as one added by hand
            // ([InsuranceRules.defaultFor]); the 대장 says why any is left out. [RVJ-40] The list's 소득세 칸 decides first: 3.3%는 근로자가
            // 아니라 4대보험이 없고, 일용은 고용보험부터 (국민연금·건강보험은 한 달 넘게 월 8일 이상이면, [InsuranceRules.of]).
            val law = InsuranceRules.defaultFor(bare, legalBreaks, on)
            val ins = when (bare.insurance.tax) {
                StaffTax.BIZ -> bare.insurance
                StaffTax.DAILY -> bare.insurance.copy(employment = true)
                else -> law.copy(foreign = bare.insurance.foreign)
            }
            Line(bare.copy(insurance = ins), check, already = !have.add(norm(r.name)), noAmount = noAmount)
        }
    }
}
