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

        fun of(place: Workplace, line: LedgerLine, nonTaxable: Long = 0, dependents: Int = 1): Payslip {
            val ins = InsuranceRules.of(place, line.staff, line.month).effective
            val on = line.month.atEndOfMonth()
            val mode = when (line.staff.insurance.tax) { StaffTax.BIZ -> TaxMode.WITHHOLDING_3_3; StaffTax.WAGE -> TaxMode.INSURANCE_4; StaffTax.NONE -> TaxMode.NONE }
            val all = TakeHome.estimate(line.gross, mode, dependents, on, nonTaxable = nonTaxable).deductions
            // Only the 4대보험 they are in; the 소득세 always as its way says.
            val kept = all.filter { (name, _) ->
                when (name) {
                    "국민연금" -> ins.pension
                    "건강보험", "장기요양" -> ins.health
                    "고용보험" -> ins.employment
                    else -> true
                }
            }
            // G4 건강보험 보수월액 하한 28만 원: a small month still pays on 28만 원 (시행령 제32조).
            val taxable = (line.gross - nonTaxable).coerceAtLeast(0)
            val floored = if (ins.health && taxable in 1 until InsuranceRules.HEALTH_BASE_MIN) {
                val sh = TakeHome.sharesOn(on)
                val health = (InsuranceRules.HEALTH_BASE_MIN * sh.health).toLong() / 10 * 10
                val care = (health * sh.careOfHealth).toLong() / 10 * 10
                kept.map { (n, v) -> when (n) { "건강보험" -> n to health; "장기요양" -> n to care; else -> n to v } }
            } else kept
            return Payslip(place, line, place.payDate(line.month), floored)
        }
    }
}

/** [BOSS-10] 지난달 비교: how each staff member's pay moved, for "왜 달라졌어요?" before it is asked. */
object PayrollCompare {
    data class Row(val staff: StaffMember, val now: Long, val before: Long?, val minutesNow: Int, val minutesBefore: Int?) {
        val diff: Long? get() = before?.let { now - it }
        val why: String get() = when {
            before == null -> "지난달 기록 없음"
            diff == 0L -> "지난달과 같아요"
            minutesBefore != null && minutesNow != minutesBefore -> "일한 시간 ${(minutesNow - minutesBefore) / 60}시간 ${if (minutesNow > minutesBefore) "늘었어요" else "줄었어요"}"
            else -> "시간은 같고 수당·요율이 달라졌어요"
        }
    }

    fun rows(now: Ledger, before: Ledger?): List<Row> {
        val last = before?.lines.orEmpty().associateBy { it.staff.id }
        return now.lines.map { l -> Row(l.staff, l.gross, last[l.staff.id]?.gross, l.workMinutes, last[l.staff.id]?.workMinutes) }
    }
}
