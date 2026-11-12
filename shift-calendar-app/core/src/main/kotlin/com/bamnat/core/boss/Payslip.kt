package com.bamnat.core.boss

import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.report.ReportDoc
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [P3-03][BOSS-08..10] B2 임금명세서 (순번 504~506, 설계 2-2): the ledger line, what comes off it (the same [TakeHome] as the
 * user's own 실수령, only the 4대보험 the staff member is in, and the 소득세 way they are paid), and the 필수 기재사항 of 근로기준법
 * 시행령 제27조의2 checked one by one before it is sent — only to that staff member, through the phone's share sheet.
 */
data class Payslip(
    val place: Workplace,
    val line: LedgerLine,
    val payDate: LocalDate?,
    val deductions: List<Pair<String, Long>>,
) {
    val month: YearMonth get() = line.month
    val gross: Long get() = line.gross
    val deducted: Long get() = deductions.sumOf { it.second }
    val net: Long get() = gross - deducted

    /** One 필수 기재사항 and whether this slip has it; [fix] says where to fill it when it has not. */
    data class Item(val name: String, val ok: Boolean, val fix: String = "")

    /** 시행령 제27조의2 제1~6호. */
    val checks: List<Item> get() = listOf(
        Item("성명·생년월일 등 근로자를 특정할 수 있는 정보", line.staff.name.isNotBlank(), "직원 정보에 이름을 적어 주세요"),
        Item("임금지급일", payDate != null, "사업장 설정에서 급여 주는 날을 골라 주세요"),
        Item("임금 총액", gross > 0, "이 달 근무가 없어요"),
        Item("기본급·수당 등 구성항목별 금액", line.lines.isNotEmpty()),
        Item("구성항목별 계산방법(연장·야간·휴일 시간 수 포함)", line.lines.all { it.formula.isNotBlank() }),
        Item("공제 항목별 금액과 총액", true),
    )
    val ready: Boolean get() = checks.all { it.ok }

    /** The slip as a page: facts, 지급 with 계산방법, 공제, 실지급액, and the fixed 참고 line. */
    fun doc(): ReportDoc {
        val s = line.staff
        val won = { v: Long -> "%,d원".format(v) }
        val h = { m: Int -> if (m % 60 == 0) "${m / 60}시간" else "%.1f시간".format(m / 60.0) }
        return ReportDoc(
            title = "${month.year}년 ${month.monthValue}월 임금명세서",
            blocks = listOf(
                ReportDoc.Facts(listOfNotNull(
                    "사업장" to place.name,
                    "성명" to s.name,
                    s.birth?.let { "생년월일" to "${it.year}.${it.monthValue}.${it.dayOfMonth}" },
                    s.job.takeIf { it.isNotBlank() }?.let { "업무" to it },
                    "지급일" to (payDate?.let { "${it.year}.${it.monthValue}.${it.dayOfMonth}" } ?: "-"),
                    "근로일수·시간" to "${line.workDays}일 · ${h(line.workMinutes)}",
                    "연장·야간·휴일" to "${h(line.overtimeMinutes)} · ${h(line.nightMinutes)} · ${h(line.holidayMinutes)}",
                )),
                ReportDoc.Heading("지급"),
                ReportDoc.Table(listOf("항목", "금액", "계산방법"), line.lines.map { listOf(it.label, won(it.amount), it.formula) },
                    listOf(0.25f, 0.2f, 0.55f), right = setOf(1), total = listOf("지급 합계", won(gross), "")),
                ReportDoc.Heading("공제"),
                ReportDoc.Table(listOf("항목", "금액"), deductions.map { listOf(it.first, won(it.second)) }.ifEmpty { listOf(listOf("공제 없음", won(0))) },
                    listOf(0.6f, 0.4f), right = setOf(1), total = listOf("공제 합계", won(deducted))),
                ReportDoc.Facts(listOf("실지급액" to won(net))),
                ReportDoc.Paragraph(NOTE, small = true),
            ),
            footer = "${place.name} · 밤낮으로 만든 임금명세서",
        )
    }

    /** The slip as a message, for a staff member reading it in a chat. */
    fun text(): String = buildString {
        val won = { v: Long -> "%,d원".format(v) }
        appendLine("[${place.name}] ${month.monthValue}월 임금명세서 · ${line.staff.name}")
        payDate?.let { appendLine("지급일 ${it.monthValue}월 ${it.dayOfMonth}일") }
        appendLine("근무 ${line.workDays}일")
        line.lines.forEach { appendLine("· ${it.label} ${won(it.amount)} (${it.formula})") }
        appendLine("지급 합계 ${won(gross)}")
        deductions.forEach { appendLine("- ${it.first} ${won(it.second)}") }
        appendLine("실지급액 ${won(net)}")
        append(NOTE)
    }

    companion object {
        const val NOTE = "근로기준법 제48조 제2항에 따라 드리는 임금명세서예요. 계산이 다르면 사장님께 말씀해 주세요."

        /**
         * 2.0.0 [RVJ-10] 검토 PAYC-08·PAYU-14: the 비과세 is the staff's 비과세 수당 (식대 up to its 한도) and the 가족 수 theirs, unless
         * given; [RVJ-11] an 일용근로자's 소득세 is worked out day by day ([com.bamnat.core.money.DailyWorkerTax]).
         */
        fun of(place: Workplace, line: LedgerLine, nonTaxable: Long? = null, dependents: Int? = null): Payslip {
            val ins = InsuranceRules.of(place, line.staff, line.month).effective
            val on = line.month.atEndOfMonth()
            val tax = line.staff.insurance.tax
            val mode = when (tax) { StaffTax.BIZ -> TaxMode.WITHHOLDING_3_3; StaffTax.WAGE, StaffTax.DAILY -> TaxMode.INSURANCE_4; StaffTax.NONE -> TaxMode.NONE }
            val free = (nonTaxable ?: freeOf(place, line)).coerceIn(0, line.gross.coerceAtLeast(0))
            val all = TakeHome.estimate(line.gross, mode, dependents ?: line.staff.insurance.family, on, line.staff.insurance.children,
                nonTaxable = free).deductions
            // Only the 4대보험 they are in; the 소득세 always as its way says.
            var kept = all.filter { (name, _) ->
                when (name) {
                    "국민연금" -> ins.pension
                    "건강보험", "장기요양" -> ins.health
                    "고용보험" -> ins.employment
                    else -> true
                }
            }
            if (tax == StaffTax.DAILY) {
                val t = dailyTax(line, free)
                kept = kept.filter { it.first != TakeHome.TAX_LABEL && it.first != TakeHome.LOCAL_TAX_LABEL } +
                    listOf(TakeHome.TAX_LABEL to t.income, TakeHome.LOCAL_TAX_LABEL to t.local).filter { it.second > 0 }
            }
            // G4 건강보험 보수월액 하한 28만 원 (시행령 제32조): 1.39 [PAYX-59] [TakeHome.estimate] floors it itself, with the exact won × rate.
            return Payslip(place, line, place.payDate(line.month), kept)
        }

        /** [RVJ-10] The month's 비과세: the staff's allowances marked 세금 없는 돈, each kind up to its 한도 ([TakeHome.nonTaxable]). */
        fun freeOf(place: Workplace, line: LedgerLine): Long {
            if (line.staff.allowances.none { it.taxFree } || line.gross <= 0) return 0
            return TakeHome.nonTaxable(PayrollLedger.profile(place, line.staff, line.month), line.month, line.pay)
        }

        /**
         * [RVJ-11] 일용근로 소득세: each working day's pay (the month's 지급 spread over the days by what each day earned, 비과세 taken
         * off), paid together for the month ([com.bamnat.core.money.DailyWorkerTax.paidTogether]).
         */
        fun dailyTax(line: LedgerLine, free: Long = 0): com.bamnat.core.money.DailyWorkerTax.Tax {
            val taxable = (line.gross - free).coerceAtLeast(0)
            val days = line.pay?.days.orEmpty().map { d -> d.parts.sumOf { it.amount } }.filter { it > 0 }
            if (taxable == 0L) return com.bamnat.core.money.DailyWorkerTax.Tax(0, 0)
            if (days.isEmpty()) return com.bamnat.core.money.DailyWorkerTax.paidTogether(List(line.workDays.coerceAtLeast(1)) { taxable / line.workDays.coerceAtLeast(1) })
            val sum = days.sum().toDouble()
            return com.bamnat.core.money.DailyWorkerTax.paidTogether(days.map { Math.round(it * taxable / sum) })
        }
    }
}

/** [BOSS-10] 지난달 비교: how each staff member's pay moved, for "왜 달라졌어요?" before it is asked. */
object PayrollCompare {
    data class Row(
        val staff: StaffMember, val now: Long, val before: Long?, val minutesNow: Int, val minutesBefore: Int?,
        /** Each pay line's amount by its label, this month and last, for the line that moved most. */
        val linesNow: Map<String, Long> = emptyMap(), val linesBefore: Map<String, Long> = emptyMap(),
    ) {
        val diff: Long? get() = before?.let { now - it }

        /**
         * Why, in the order a boss asks: the hours (with their real sign) and the pay line that moved most. 1.38 PC 1차: "−247,680원 · 일한 시간
         * 8시간 늘었어요" said the opposite of the money; now both are said, so they never seem to disagree.
         */
        val why: String get() {
            if (before == null) return "지난달 기록 없음"
            if (diff == 0L) return "지난달과 같아요"
            val parts = ArrayList<String>()
            if (minutesBefore != null && minutesNow != minutesBefore) {
                val d = minutesNow - minutesBefore
                val h = kotlin.math.abs(d) / 60.0
                val hText = if (kotlin.math.abs(d) % 60 == 0) "${kotlin.math.abs(d) / 60}" else "%.1f".format(h)
                parts += "일한 시간 ${hText}시간 ${if (d > 0) "늘었어요" else "줄었어요"}"
            }
            val moved = (linesNow.keys + linesBefore.keys).map { it to (linesNow[it] ?: 0L) - (linesBefore[it] ?: 0L) }
                .filter { it.second != 0L }.maxByOrNull { kotlin.math.abs(it.second) }
            moved?.let { (label, d) -> parts += "$label ${if (d > 0) "+" else "−"}${"%,d".format(kotlin.math.abs(d))}원" }
            return parts.ifEmpty { listOf("시간은 같고 수당·요율이 달라졌어요") }.joinToString(" · ")
        }
    }

    fun rows(now: Ledger, before: Ledger?): List<Row> {
        val last = before?.lines.orEmpty().associateBy { it.staff.id }
        fun amounts(l: LedgerLine?) = l?.lines.orEmpty().groupBy { it.label }.mapValues { e -> e.value.sumOf { it.amount } }
        return now.lines.map { l ->
            val b = last[l.staff.id]
            Row(l.staff, l.gross, b?.gross, l.workMinutes, b?.workMinutes, amounts(l), amounts(b))
        }
    }
}
