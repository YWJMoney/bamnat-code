package com.bamnat.core.boss

import com.bamnat.core.pay.TakeHome
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 1.38 [P3-X-08] 신고·지급 달력 (사용자 2026-11-06 혁신안 X8): the days a small employer must not miss, from the ledger and the staff
 * rows: 급여 지급일, 원천세 신고·납부(지급한 달의 다음 달 10일, 소득세법 제128조·지방세법 제103조의13), 4대보험 자격 취득·상실 신고
 * (건강보험 14일 안, 국민연금·고용·산재는 다음 달 15일까지), 퇴사자 금품 청산(14일 안, 근로기준법 제36조), 사업소득 간이지급명세서(다음 달
 * 말일), 근로소득 지급명세서(다음 해 3월 10일). 금액은 대장과 같은 계산이고 참고예요. 제출은 홈택스·위택스·4대사회보험 정보연계센터에서 해요.
 */
object BossFilings {
    enum class Kind { PAY, WITHHOLD, BIZ_STATEMENT, WAGE_STATEMENT, INSURE_IN, INSURE_OUT, SETTLE }

    data class Due(
        val date: LocalDate,
        val kind: Kind,
        val title: String,
        val detail: String,
        val amount: Long? = null,
        val staffIds: List<String> = emptyList(),
        /** Where it is filed or done. */
        val where: String = "",
        /** For a 했어요 tick: stays the same for the same duty. */
        val key: String,
    ) {
        fun daysFrom(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
    }

    /** The 원천세 of one paid month, by staff: 소득세 and 지방소득세 (사업소득 3.3% split 3% and 0.3%). */
    data class Withheld(val staff: StaffMember, val gross: Long, val tax: Long, val local: Long, val biz: Boolean)

    fun withheld(place: Workplace, ledger: Ledger): List<Withheld> = ledger.lines.filter { it.gross > 0 }.map { l ->
        val d = Payslip.of(place, l).deductions.toMap()
        val biz = l.staff.insurance.tax == StaffTax.BIZ
        val both = d["원천징수 3.3%"]
        if (biz && both != null) {
            // 3% 소득세, 그 10% 지방소득세, each cut to 10원 (소득세법 제129조, 지방세법 제103조의13).
            val tax = l.gross * 3 / 100 / 10 * 10
            Withheld(l.staff, l.gross, tax, tax / 10 / 10 * 10, true)
        } else Withheld(l.staff, l.gross, d[TakeHome.TAX_LABEL] ?: 0, d[TakeHome.LOCAL_TAX_LABEL] ?: 0, biz)
    }

    private fun day(d: LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일"
    private fun won(v: Long) = "%,d원".format(v)
    private fun nextMonth15(d: LocalDate) = YearMonth.from(d).plusMonths(1).atDay(15)

    /**
     * What falls between [today] − [behind] days and [today] + [ahead] days, soonest first. [ledgerOf] gives a month's ledger (the app
     * builds it with the month's 달라진 날); the months whose pay goes out in the window are asked for.
     */
    fun upcoming(place: Workplace, staff: List<StaffMember>, today: LocalDate, ledgerOf: (YearMonth) -> Ledger, ahead: Long = 40, behind: Long = 7): List<Due> {
        val from = today.minusDays(behind)
        val to = today.plusDays(ahead)
        fun inside(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        val out = ArrayList<Due>()
        // Pay and the taxes on it: the months worked whose pay day or 10일 falls in the window.
        val first = YearMonth.from(today).minusMonths(3)
        for (i in 0..4) {
            val worked = first.plusMonths(i.toLong())
            val paid = place.payDate(worked) ?: worked.plusMonths(1).atDay(1)
            val taxDay = YearMonth.from(paid).plusMonths(1).atDay(10)
            val bizDay = YearMonth.from(paid).plusMonths(1).atEndOfMonth()
            if (!inside(paid) && !inside(taxDay) && !inside(bizDay)) continue
            // PC 10차(중): a staff member with no 입사일 counts as always employed in the ledger; a month before this one is not
            // filed for them (they may have just been added), and the card asks for the date instead.
            val full = ledgerOf(worked)
            val ledger = if (worked < YearMonth.from(today)) full.copy(lines = full.lines.filter { it.staff.hiredOn != null }) else full
            val lines = ledger.lines.filter { it.gross > 0 }
            if (lines.isEmpty()) continue
            val w = withheld(place, ledger)
            if (place.payDate(worked) != null && inside(paid)) out += Due(paid, Kind.PAY, "${worked.monthValue}월분 급여 지급",
                "${lines.size}명 · 세전 ${won(ledger.gross)}. 명세서도 같이 줘요(근로기준법 제48조).", ledger.gross, lines.map { it.staff.id },
                "급여 대장", "pay:${place.id}:$worked")
            val tax = w.sumOf { it.tax }
            val local = w.sumOf { it.local }
            if (inside(taxDay) && (tax > 0 || local > 0 || w.isNotEmpty())) out += Due(taxDay, Kind.WITHHOLD, "원천세 신고·납부 (${YearMonth.from(paid).monthValue}월 지급분)",
                "소득세 ${won(tax)} · 지방소득세 ${won(local)}" + (if (tax == 0L) ". 떼지 않은 달도 지급한 사람이 있으면 신고는 해요" else "") +
                    ". 반기별 납부를 승인받았으면 7월·1월 10일에 모아서 해요.", tax + local, w.map { it.staff.id },
                "홈택스(소득세) · 위택스(지방소득세)", "withhold:${place.id}:${YearMonth.from(paid)}")
            val biz = w.filter { it.biz }
            if (biz.isNotEmpty() && inside(bizDay)) out += Due(bizDay, Kind.BIZ_STATEMENT, "사업소득 간이지급명세서 (${YearMonth.from(paid).monthValue}월 지급분)",
                "3.3% 떼고 준 ${biz.size}명, 지급 ${won(biz.sumOf { it.gross })}", biz.sumOf { it.gross }, biz.map { it.staff.id }, "홈택스",
                "bizst:${place.id}:${YearMonth.from(paid)}")
        }
        // 근로소득 지급명세서: last year's 근로소득, 3월 10일.
        val march = LocalDate.of(today.year, 3, 10)
        if (inside(march)) {
            val wage = staff.filter { it.insurance.tax == StaffTax.WAGE && (it.leftOn == null || it.leftOn.year >= today.year - 1) && (it.hiredOn == null || it.hiredOn.year < today.year) }
            if (wage.isNotEmpty()) out += Due(march, Kind.WAGE_STATEMENT, "근로소득 지급명세서 (${today.year - 1}년분)",
                "연말정산을 마친 ${wage.size}명의 한 해 근로소득을 내요. 연말정산 결과는 2월분 원천세에 같이 신고해요.", null, wage.map { it.id }, "홈택스",
                "wagest:${place.id}:${today.year - 1}")
        }
        // 4대보험 자격 취득·상실, and the 14 days to settle with someone who left.
        for (s in staff) {
            s.hiredOn?.let { h ->
                val ins = InsuranceRules.of(place, s, YearMonth.from(h)).effective
                val health = h.plusDays(14)
                if (ins.health && inside(health)) out += Due(health, Kind.INSURE_IN, "건강보험 자격 취득 신고 · ${s.name}",
                    "${day(h)} 입사. 입사한 날부터 14일 안이에요.", null, listOf(s.id), "4대사회보험 정보연계센터", "in-health:${s.id}:$h")
                val rest = listOfNotNull("국민연금".takeIf { ins.pension }, "고용보험".takeIf { ins.employment }, "산재보험")
                val restDay = nextMonth15(h)
                if (inside(restDay)) out += Due(restDay, Kind.INSURE_IN, "${rest.joinToString("·")} 취득 신고 · ${s.name}",
                    "${day(h)} 입사. 입사한 달의 다음 달 15일까지예요.", null, listOf(s.id), "4대사회보험 정보연계센터", "in-rest:${s.id}:$h")
            }
            s.leftOn?.let { l ->
                val lost = l.plusDays(1)
                val ins = InsuranceRules.of(place, s, YearMonth.from(l)).effective
                val health = lost.plusDays(14)
                if (ins.health && inside(health)) out += Due(health, Kind.INSURE_OUT, "건강보험 자격 상실 신고 · ${s.name}",
                    "${day(l)} 퇴사(상실일 ${day(lost)}). 14일 안이에요.", null, listOf(s.id), "4대사회보험 정보연계센터", "out-health:${s.id}:$l")
                val rest = listOfNotNull("국민연금".takeIf { ins.pension }, "고용보험".takeIf { ins.employment }, "산재보험")
                val restDay = nextMonth15(lost)
                if (inside(restDay)) out += Due(restDay, Kind.INSURE_OUT, "${rest.joinToString("·")} 상실 신고 · ${s.name}",
                    "${day(l)} 퇴사. 상실일이 있는 달의 다음 달 15일까지예요" + (if (ins.employment) ". 고용보험은 이직확인서도 같이 내요" else "") + ".",
                    null, listOf(s.id), "4대사회보험 정보연계센터", "out-rest:${s.id}:$l")
                val settle = l.plusDays(14)
                val longEnough = s.hiredOn?.let { !it.plusYears(1).isAfter(l.plusDays(1)) } == true
                if (inside(settle)) out += Due(settle, Kind.SETTLE, "퇴사 정산 · ${s.name}",
                    "남은 임금" + (if (longEnough) "과 퇴직금(1년 넘게 일했고 4주 평균 주 15시간 이상이면)" else "") +
                        "을 퇴사일부터 14일 안에 줘요(근로기준법 제36조). 서로 합의하면 미룰 수 있어요.", null, listOf(s.id), "급여 대장", "settle:${s.id}:$l")
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.kind.ordinal }))
    }

    /** Staff with no 입사일: the past months' 신고 leaves them out until it is put in. */
    fun noHireDate(staff: List<StaffMember>, today: LocalDate): List<StaffMember> = staff.filter { it.hiredOn == null && it.employedOn(today) }

    /** 제출할 표: 원천세 of a paid month, one row per person, for 홈택스 입력 or the 세무사. */
    fun withheldCsv(place: Workplace, worked: YearMonth, rows: List<Withheld>): String = buildString {
        append('﻿')
        append("사업장,귀속 연월,이름,생년월일,소득 구분,지급액,소득세,지방소득세\r\n")
        rows.forEach { r ->
            append(listOf(place.name, worked.toString(), r.staff.name, r.staff.birth?.toString().orEmpty(), if (r.biz) "사업소득" else "근로소득",
                r.gross, r.tax, r.local).joinToString(",") { csv(it.toString()) }).append("\r\n")
        }
        append(listOf(place.name, worked.toString(), "합계", "", "", rows.sumOf { it.gross }, rows.sumOf { it.tax }, rows.sumOf { it.local }).joinToString(",") { csv(it.toString()) })
        append("\r\n")
    }

    /** 제출할 표: who joins or leaves 4대보험, with what the 신고서 asks that the app knows (주민등록번호는 앱에 없어요). */
    fun insuranceCsv(place: Workplace, staff: List<StaffMember>, ledgerOf: (YearMonth) -> Ledger, today: LocalDate): String = buildString {
        append('﻿')
        append("사업장,구분,이름,생년월일,날짜,하는 일,월 보수(예상),국민연금,건강보험,고용보험\r\n")
        staff.forEach { s ->
            listOfNotNull(s.hiredOn?.let { "취득" to it }, s.leftOn?.let { "상실" to it.plusDays(1) }).filter { (_, d) -> !d.isBefore(today.minusDays(60)) && !d.isAfter(today.plusDays(40)) }
                .forEach { (k, d) ->
                    val m = YearMonth.from(if (k == "상실") d.minusDays(1) else d)
                    val ins = InsuranceRules.of(place, s, m).effective
                    val pay = ledgerOf(m).lines.firstOrNull { it.staff.id == s.id }?.gross ?: 0
                    append(listOf(place.name, k, s.name, s.birth?.toString().orEmpty(), d.toString(), s.job, pay,
                        if (ins.pension) "예" else "아니요", if (ins.health) "예" else "아니요", if (ins.employment) "예" else "아니요").joinToString(",") { csv(it.toString()) }).append("\r\n")
                }
        }
    }

    private fun csv(v: String): String {
        // 수식으로 읽히지 않게 (=, +, -, @ 앞에 ').
        val safe = if (v.isNotEmpty() && v[0] in "=+-@" && v.toLongOrNull() == null) "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
