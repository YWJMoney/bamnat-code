package com.bamnat.core.work

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.32 [WTYP-24] (근무유형 조사 8장 공무원) 시간외 신청/인정: each extra work keeps the time asked for (신청, what was worked)
 * and, when the office approved less, the time 인정 ([com.bamnat.core.model.ExtraEntry.approvedMinutes]). The month shows both
 * and what was not approved; 공무원 시간외근무수당 is paid on the approved minutes, the hours worked (52시간, 연속 근무) on all of them.
 */
object OvertimeApproval {
    data class Month(val askedMinutes: Int, val approvedMinutes: Int, val days: Int) {
        val notApprovedMinutes: Int get() = (askedMinutes - approvedMinutes).coerceAtLeast(0)
        val anyCut: Boolean get() = notApprovedMinutes > 0
    }

    fun month(month: YearMonth, resolve: (LocalDate) -> ResolvedDay): Month {
        var asked = 0
        var approved = 0
        var days = 0
        for (d in 1..month.lengthOfMonth()) {
            val extras = resolve(month.atDay(d)).extras
            if (extras.isEmpty()) continue
            val a = extras.sumOf { it.minutes }
            val ok = extras.sumOf { e -> e.entry.approvedMinutes?.coerceIn(0, e.minutes) ?: e.minutes }
            asked += a
            approved += ok
            if (ok < a) days++
        }
        return Month(asked, approved, days)
    }

    fun line(m: Month): String? = if (!m.anyCut) null else
        "신청 ${com.bamnat.core.hours.HoursFormat.korean(m.askedMinutes)} · 인정 ${com.bamnat.core.hours.HoursFormat.korean(m.approvedMinutes)} · " +
            "인정 안 됨 ${com.bamnat.core.hours.HoursFormat.korean(m.notApprovedMinutes)}(${m.days}일)"
}
