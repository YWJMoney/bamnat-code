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
    /** One person to add: the member as it will be saved, and the fields to look at ("시급·월급", "급여 형태", "근무 요일·시간"). */
    data class Line(val member: StaffMember, val check: List<String>, val already: Boolean)

    private fun norm(s: String) = s.replace(" ", "").lowercase()

    fun lines(rows: List<StaffRow>, existing: List<StaffMember>, on: LocalDate, id: () -> String = { java.util.UUID.randomUUID().toString() },
              legalBreaks: Boolean = true): List<Line> {
        val have = existing.map { norm(it.name) }.toMutableSet()
        val minimum = RateTable.on(on).minimumHourlyWage
        return rows.map { r ->
            val form = r.form ?: WageForm.HOUR
            val check = buildList {
                if (r.base == null || "base" in r.doubtful) add(if (r.base == null) "금액이 없어 최저시급으로 넣었어요" else "금액")
                if (r.form == null || "form" in r.doubtful) add("시급·월급·일급")
                if (r.weekly.isEmpty() || "weekly" in r.doubtful) add(if (r.weekly.isEmpty()) "근무 요일·시간이 없어요" else "근무 요일·시간")
            }
            val bare = StaffMember(
                id(), r.name.trim(), form, r.base ?: minimum, hiredOn = r.hiredOn, job = r.job.orEmpty(),
                weekly = r.weekly.mapValues { (_, t) -> StaffShift(t.first, t.second) },
            )
            // 2.0.0 [RVJ-16] 검토 BOSS-60: a person brought in by a list gets the 4대보험 the law puts them in, as one added by hand
            // ([InsuranceRules.defaultFor]); the 대장 says why any is left out.
            val m = bare.copy(insurance = InsuranceRules.defaultFor(bare, legalBreaks, on))
            Line(m, check, already = !have.add(norm(r.name)))
        }
    }
}
