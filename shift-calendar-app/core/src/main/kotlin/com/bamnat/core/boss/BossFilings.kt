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
    enum class Kind { PAY, WITHHOLD, BIZ_STATEMENT, WAGE_STATEMENT, INSURE_IN, INSURE_OUT, SETTLE,
        /** 2.0.0 [RVJ-14] 검토 BOSS-58: 근로소득 간이지급명세서 (2026년 지급분까지 반기, 2027년 지급분부터 매월). */
        WAGE_BRIEF,
        /** [RVJ-14] 일용근로소득 지급명세서 (매월, 다음 달 말일)와 고용·산재 근로내용확인신고 (다음 달 15일). */
        DAILY_STATEMENT }

    /**
     * 2.0.0 [RVJ-38] (검토 PC-100) The people a past month's line leaves out because they have no 입사일 (the ledger counts them as
     * always employed, so someone added today would land in last month's 신고), and what the line would come to with them in.
     */
    data class Missing(val staffIds: List<String>, val names: List<String>, val amount: Long? = null) {
        /** One line said right by the amount: who is left out, and what putting the 입사일 in would make it. */
        fun line(shown: Long?): String = "입사일 없는 ${names.joinToString(", ")}님 빼고예요 · 그 달에 일했으면 입사일을 넣어요" +
            (amount?.takeIf { it != (shown ?: 0L) && it > 0 }?.let { "(넣으면 ${"%,d원".format(it)})" } ?: "")
    }

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
        /** [RVJ-38] Who a past month's line leaves out for want of an 입사일. */
        val missing: Missing? = null,
        /** [RVJ-38] The month worked that the line is about (the 원천세 표 is made for it). */
        val worked: YearMonth? = null,
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
     * 2.0.0 [RVJ-14] 검토 BOSS-57·PAYC-09: a 신고·납부 기한 on a 토·일·공휴일 is the next working day (국세기본법 제5조 제1항; 4대보험 신고도
     * 민법 제161조로 같아요). The card says why the day moved.
     */
    fun deadline(d: LocalDate): LocalDate = com.bamnat.core.money.TaxDeadlines.workday(d)

    private val weekday = listOf("월", "화", "수", "목", "금", "토", "일")
    private fun moved(due: LocalDate): String {
        val real = deadline(due)
        if (real == due) return ""
        val why = if (due.dayOfWeek.value >= 6) "${weekday[due.dayOfWeek.value - 1]}요일" else "공휴일"
        return " (${due.dayOfMonth}일이 ${why}이라 ${day(real)}까지)"
    }

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
            val taxDue = YearMonth.from(paid).plusMonths(1).atDay(10)
            val bizDue = YearMonth.from(paid).plusMonths(1).atEndOfMonth()
            val taxDay = deadline(taxDue)
            val bizDay = deadline(bizDue)
            // [RVJ-14] 근로소득 간이지급명세서: 2027년 지급분부터 지급한 달의 다음 달 말일(매달). 2026년 지급분까지의 반기분은 아래
            // [halfBriefs]에서 직원 줄로 봐요. 출처는 [halfBriefs].
            val monthly = paid.year >= 2027
            if (!inside(paid) && !inside(taxDay) && !inside(bizDay)) continue
            // PC 10차(중): a staff member with no 입사일 counts as always employed in the ledger; a month before this one is not
            // filed for them (they may have just been added), and the card asks for the date instead.
            val full = ledgerOf(worked)
            val ledger = filed(full, worked, today)
            val lines = ledger.lines.filter { it.gross > 0 }
            val fullLines = full.lines.filter { it.gross > 0 }
            // [RVJ-38] (검토 PC-100) A month whose only paid people have no 입사일 still has its lines, said as left out: the 신고 is not
            // hidden by a missing date.
            if (fullLines.isEmpty()) continue
            val w = withheld(place, ledger)
            val wFull = withheld(place, full)
            // 1.39 G2 PC ef175fbf · [RVJ-38]: the amount without the people left out read as a wrong sum; each line says right by its amount
            // who is not in it and what it comes to with them.
            val leftLines = fullLines.filter { l -> lines.none { it.staff.id == l.staff.id } }
            fun missing(of: (LedgerLine) -> Boolean, amount: Long?): Missing? =
                leftLines.filter(of).takeIf { it.isNotEmpty() }?.let { m -> Missing(m.map { it.staff.id }, m.map { it.staff.name.ifBlank { "이름 없음" } }, amount) }
            // [RVJ-39] (검토 PC-101) Which month's work a 지급분 is, when it is not the month it was paid.
            val span = "${YearMonth.from(paid).monthValue}월 지급분" + if (YearMonth.from(paid) != worked) " · ${worked.monthValue}월 일한 몫" else ""
            // [RVJ-09] 검토 BOSS-57: the pay day stays; on a day off it says the usual earlier day.
            val early = place.payDateWorkday(worked)?.takeIf { it != paid }
                ?.let { " 지급일이 ${weekday[paid.dayOfWeek.value - 1]}요일${if (paid.dayOfWeek.value < 6) "(공휴일)" else ""}이에요. 보통 ${day(it)}(${weekday[it.dayOfWeek.value - 1]})에 미리 줘요." }.orEmpty()
            if (place.payDate(worked) != null && inside(paid)) out += Due(paid, Kind.PAY, "${worked.monthValue}월분 급여 지급",
                (if (lines.isEmpty()) "" else "${lines.size}명 · 세전 ${won(ledger.gross)}. ") + "명세서도 같이 줘요(근로기준법 제48조).$early", ledger.gross, lines.map { it.staff.id },
                "급여 대장", "pay:${place.id}:$worked", missing({ true }, full.gross), worked)
            val tax = w.sumOf { it.tax }
            val local = w.sumOf { it.local }
            if (inside(taxDay) && wFull.isNotEmpty()) out += Due(taxDay, Kind.WITHHOLD, "원천세 신고·납부 ($span)",
                (if (w.isEmpty()) "" else "소득세 ${won(tax)} · 지방소득세 ${won(local)}" + (if (tax == 0L) ". 떼지 않은 달도 지급한 사람이 있으면 신고는 해요" else "") + ". ") +
                    "반기별 납부를 승인받았으면 7월·1월 10일에 모아서 해요." + moved(taxDue), tax + local, w.map { it.staff.id },
                "홈택스(소득세) · 위택스(지방소득세)", "withhold:${place.id}:${YearMonth.from(paid)}",
                missing({ true }, wFull.sumOf { it.tax + it.local }), worked)
            val biz = w.filter { it.biz }
            val bizFull = wFull.filter { it.biz }
            if (bizFull.isNotEmpty() && inside(bizDay)) out += Due(bizDay, Kind.BIZ_STATEMENT, "사업소득 간이지급명세서 ($span)",
                (if (biz.isEmpty()) "" else "3.3% 떼고 준 ${biz.size}명, 지급 ${won(biz.sumOf { it.gross })}. ") + "3.3%로 주는 분은 4대보험 신고가 없어요" + moved(bizDue) + ".", biz.sumOf { it.gross }, biz.map { it.staff.id }, "홈택스",
                "bizst:${place.id}:${YearMonth.from(paid)}", missing({ it.staff.insurance.tax == StaffTax.BIZ }, bizFull.sumOf { it.gross }), worked)
            // [RVJ-14] 검토 BOSS-58: 근로소득 (간이세액표) 직원의 간이지급명세서, 2027년 지급분부터 매달.
            val wageLines = lines.filter { it.staff.insurance.tax == StaffTax.WAGE }
            val wageMissing = missing({ it.staff.insurance.tax == StaffTax.WAGE }, null)
            if (monthly && (wageLines.isNotEmpty() || wageMissing != null) && inside(bizDay)) out += Due(bizDay, Kind.WAGE_BRIEF, "근로소득 간이지급명세서 ($span)",
                listOfNotNull(if (wageLines.isEmpty()) "근로소득으로 준 돈을 내요" else "근로소득으로 준 ${wageLines.size}명",
                    "2027년 지급분은 반기 기한(7월·1월 말일)까지 내도 가산세가 없어요".takeIf { paid.year == 2027 }).joinToString(". ") + moved(bizDue) + ".", null, wageLines.map { it.staff.id }, "홈택스", "wagebrief:${place.id}:${YearMonth.from(paid)}", wageMissing, worked)
            // [RVJ-14] 일용근로자: 일용근로소득 지급명세서 (지급한 달의 다음 달 말일, 소득세법 제164조 제1항) and 고용·산재 근로내용확인신고
            // (일한 달의 다음 달 15일, 고용보험 및 산업재해보상보험의 보험료징수 등에 관한 법률 제16조의10·시행령 제19조의7).
            val isDaily = { l: LedgerLine -> l.staff.insurance.tax == StaffTax.DAILY }
            val daily = lines.filter(isDaily)
            val dailyFull = fullLines.filter(isDaily)
            if (dailyFull.isNotEmpty() && inside(bizDay)) out += Due(bizDay, Kind.DAILY_STATEMENT, "일용근로소득 지급명세서 ($span)",
                (if (daily.isEmpty()) "일용근로자에게 준 돈을 내요" else "일용으로 준 ${daily.size}명, 지급 ${won(daily.sumOf { it.gross })}") + moved(bizDue) + ".", daily.sumOf { it.gross }, daily.map { it.staff.id }, "홈택스",
                "dailyst:${place.id}:${YearMonth.from(paid)}", missing(isDaily, dailyFull.sumOf { it.gross }), worked)
            val reportDue = worked.plusMonths(1).atDay(15)
            if (dailyFull.isNotEmpty() && inside(deadline(reportDue))) out += Due(deadline(reportDue), Kind.DAILY_STATEMENT, "근로내용확인신고 (${worked.monthValue}월 일한 일용직)",
                "고용·산재보험 일용" + (if (daily.isEmpty()) "" else " ${daily.size}명") + ", 일한 날과 받은 돈을 내요. 지급명세서와 같이 내면 한 번에 끝나요" + moved(reportDue) + ".", null, daily.map { it.staff.id },
                "근로복지공단 고용·산재 토탈서비스", "dailyreport:${place.id}:$worked", missing(isDaily, null), worked)
        }
        out += halfBriefs(place, staff, ::inside)
        // 근로소득 지급명세서: last year's 근로소득, 3월 10일.
        val marchDue = LocalDate.of(today.year, 3, 10)
        val march = deadline(marchDue)
        if (inside(march)) {
            val wage = staff.filter { it.insurance.tax == StaffTax.WAGE && (it.leftOn == null || it.leftOn.year >= today.year - 1) && (it.hiredOn == null || it.hiredOn.year < today.year) }
            if (wage.isNotEmpty()) out += Due(march, Kind.WAGE_STATEMENT, "근로소득 지급명세서 (${today.year - 1}년분)",
                "연말정산을 마친 ${wage.size}명의 한 해 근로소득을 내요. 연말정산 결과는 2월분 원천세에 같이 신고해요." + moved(marchDue), null, wage.map { it.id }, "홈택스",
                "wagest:${place.id}:${today.year - 1}")
        }
        // 4대보험 자격 취득·상실, and the 14 days to settle with someone who left.
        for (s in staff) {
            // [RVJ-14] 검토 BOSS-59: a 사업소득자 (3.3%) is not a 근로자, so no 4대보험·산재 신고 (the ledger's 산재 is 0 for them too).
            val biz = s.insurance.tax == StaffTax.BIZ
            // [RVJ-14] An 일용근로자's 고용·산재 go by the monthly 근로내용확인신고 above, not 취득·상실.
            val day1 = s.insurance.tax == StaffTax.DAILY
            if (!biz) s.hiredOn?.let { h ->
                val ins = InsuranceRules.of(place, s, YearMonth.from(h)).effective
                val healthDue = h.plusDays(14)
                val health = deadline(healthDue)
                if (ins.health && inside(health)) out += Due(health, Kind.INSURE_IN, "건강보험 자격 취득 신고 · ${s.name}",
                    "${day(h)} 입사. 입사한 날부터 14일 안이에요." + moved(healthDue), null, listOf(s.id), "4대사회보험 정보연계센터", "in-health:${s.id}:$h")
                val rest = listOfNotNull("국민연금".takeIf { ins.pension }, "고용보험".takeIf { ins.employment && !day1 }, "산재보험".takeIf { !day1 })
                val restDue = nextMonth15(h)
                val restDay = deadline(restDue)
                if (rest.isNotEmpty() && inside(restDay)) out += Due(restDay, Kind.INSURE_IN, "${rest.joinToString("·")} 취득 신고 · ${s.name}",
                    "${day(h)} 입사. 입사한 달의 다음 달 15일까지예요." + moved(restDue), null, listOf(s.id), "4대사회보험 정보연계센터", "in-rest:${s.id}:$h")
            }
            s.leftOn?.let { l ->
                val lost = l.plusDays(1)
                val ins = InsuranceRules.of(place, s, YearMonth.from(l)).effective
                if (!biz) {
                val healthDue = lost.plusDays(14)
                val health = deadline(healthDue)
                if (ins.health && inside(health)) out += Due(health, Kind.INSURE_OUT, "건강보험 자격 상실 신고 · ${s.name}",
                    "${day(l)} 퇴사(상실일 ${day(lost)}). 14일 안이에요." + moved(healthDue), null, listOf(s.id), "4대사회보험 정보연계센터", "out-health:${s.id}:$l")
                val rest = listOfNotNull("국민연금".takeIf { ins.pension }, "고용보험".takeIf { ins.employment && !day1 }, "산재보험".takeIf { !day1 })
                val restDue = nextMonth15(lost)
                val restDay = deadline(restDue)
                if (rest.isNotEmpty() && inside(restDay)) out += Due(restDay, Kind.INSURE_OUT, "${rest.joinToString("·")} 상실 신고 · ${s.name}",
                    "${day(l)} 퇴사. 상실일이 있는 달의 다음 달 15일까지예요" + (if (ins.employment && !day1) ". 고용보험은 이직확인서도 같이 내요" else "") + moved(restDue) + ".",
                    null, listOf(s.id), "4대사회보험 정보연계센터", "out-rest:${s.id}:$l")
                }
                // 금품 청산 14일은 기한 넘김 규정이 아니라 기간이라 그대로 둬요(근로기준법 제36조).
                val settle = l.plusDays(14)
                val longEnough = s.hiredOn?.let { !it.plusYears(1).isAfter(l.plusDays(1)) } == true
                if (inside(settle)) out += Due(settle, Kind.SETTLE, "퇴사 정산 · ${s.name}",
                    (if (biz) "남은 금액을 14일 안에 정리해요." + (if (longEnough) " 3.3%로 줬어도 정해진 시간에 시키는 대로 일했다면 근로자라 퇴직금이 생길 수 있어요." else "")
                    else "남은 임금" + (if (longEnough) "과 퇴직금(1년 넘게 일했고 4주 평균 주 15시간 이상이면)" else "") +
                        "을 퇴사일부터 14일 안에 줘요(근로기준법 제36조). 서로 합의하면 미룰 수 있어요."), null, listOf(s.id), "급여 대장", "settle:${s.id}:$l")
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.kind.ordinal }))
    }

    /**
     * [RVJ-38] The ledger a month's 신고 is worked out from: a month before this one leaves out those with no 입사일 (PC 10차: someone
     * added today is not in last month's 원천세), this month and later count everyone as the ledger does. The card and its 원천세 표 both
     * use it, so the two agree.
     */
    fun filed(full: Ledger, worked: YearMonth, today: LocalDate): Ledger =
        if (worked < YearMonth.from(today)) full.copy(lines = full.lines.filter { it.staff.hiredOn != null }) else full

    /**
     * 2.0.0 [RVJ-39] 근로소득 간이지급명세서, 2026년 지급분까지: 지급일이 속하는 반기의 마지막 달의 다음 달 말일(상반기분 7월 말일, 하반기분
     * 이듬해 1월 말일). 2027년 1월 지급분부터는 매달, 지급한 달의 다음 달 말일이고 위 [upcoming]에서 봐요. 2027년 지급분은 종전 반기 기한까지
     * 내면 미제출 가산세가 없어요(직전 연도 상시 종업원 20명 이하이고 원천세를 반기별로 내는 사업자는 2028년 지급분까지).
     * 출처: 소득세법 제164조의3 제1항 제1호(현행 조문, '제1호의 소득' 개정 부분 시행 2027. 1. 1.), 국세청 '간이지급명세서(근로소득)
     * 제출기한 적용 사례'(www.nts.go.kr, cntntsId 239032, 2026-11-12 확인). 시행일을 미룬 개정 법률의 번호는 확인 필요.
     * The half is read from the staff rows (근로소득으로 일한 달의 지급일이 그 반기에 있는 사람), so someone who left before the last month
     * of the half is still in it, which the five months of ledgers the card reads would miss.
     */
    internal fun halfBriefs(place: Workplace, staff: List<StaffMember>, inside: (LocalDate) -> Boolean): List<Due> {
        val out = ArrayList<Due>()
        for (year in 2024..2026) for (second in listOf(false, true)) {
            val due = if (second) LocalDate.of(year + 1, 1, 31) else LocalDate.of(year, 7, 31)
            val day = deadline(due)
            if (!inside(day)) continue
            val from = LocalDate.of(year, if (second) 7 else 1, 1)
            val to = if (second) LocalDate.of(year, 12, 31) else LocalDate.of(year, 6, 30)
            // The months worked whose pay goes out in the half (다음 달 지급이면 그 앞달부터).
            val months = (0L..6L).map { YearMonth.from(from).minusMonths(1).plusMonths(it) }
                .filter { m -> (place.payDate(m) ?: m.plusMonths(1).atDay(1)).let { !it.isBefore(from) && !it.isAfter(to) } }
            val who = staff.filter { s -> s.insurance.tax == StaffTax.WAGE && months.any { m ->
                (s.hiredOn == null || !s.hiredOn.isAfter(m.atEndOfMonth())) && (s.leftOn == null || !s.leftOn.isBefore(m.atDay(1))) } }
            if (who.isEmpty()) continue
            val half = if (second) "하반기(7~12월 지급분)" else "상반기(1~6월 지급분)"
            out += Due(day, Kind.WAGE_BRIEF, "근로소득 간이지급명세서 (${year}년 $half)",
                "근로소득으로 준 ${who.size}명. 2027년 1월 지급분부터는 매달 내요" + moved(due) + ".", null, who.map { it.id }, "홈택스",
                "wagebrief:${place.id}:$year-H${if (second) 2 else 1}")
        }
        return out
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
