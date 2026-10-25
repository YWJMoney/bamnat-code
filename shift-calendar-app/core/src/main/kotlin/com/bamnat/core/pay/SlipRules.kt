package com.bamnat.core.pay

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * [PAYS-14] What a payslip and its month's calendar say about the company's rules, worked out on the phone (D68:
 * nothing is sent anywhere). [profile] is the setup they come to; [changes] says each change in words for the one page
 * the user checks before it is applied. [total] is that month by [profile], against [gross] on the payslip.
 */
data class SlipRulesResult(
    val month: YearMonth,
    val profile: PayProfile,
    val changes: List<String>,
    val total: Long,
    val gross: Long,
    val hints: List<String>,
) {
    val gap: Long get() = gross - total
    val gapPercent: Double get() = if (gross == 0L) 0.0 else gap * 100.0 / gross
    /** Within 1% of the payslip: close enough to trust the rules worked out (계획/1.25-V-개발계획.md 376). */
    val close: Boolean get() = abs(gap) <= maxOf(PayslipMatch.TOLERANCE_WON, (gross * CLOSE_SHARE).roundToLong())

    companion object {
        const val CLOSE_SHARE = 0.01
    }
}

/**
 * [PAYS-14] 명세서 → 회사 규칙 (research/1.25-V-조사.md 5장). [PayslipMatcher] fits the calculation choices; this goes
 * first and works out what it leaves alone, from the payslip's lines and the month's work:
 * the 기본급 or 시급, the night, 연장 and 휴일 premium rates (snapped to the usual ones), a 1회 amount for 잔업·특근·대근
 * paid as a line of its own, the allowances the setup does not have (by the shifts they follow), and the 빨간날 choice.
 */
object SlipRules {
    private val NIGHT_RATES = listOf(0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 1.0)
    private val OVERTIME_RATES = listOf(0.5, 0.75, 1.0)
    private const val SNAP = 0.03

    fun infer(
        profile: PayProfile,
        month: YearMonth,
        resolve: (LocalDate) -> ResolvedDay,
        read: PayslipRead,
        isHoliday: ((LocalDate) -> Boolean)? = null,
        calculator: PayCalculator = PayCalculator(),
    ): SlipRulesResult? {
        val slip = read.payslip() ?: return null
        fun run(p: PayProfile) = calculator.calculate(p, month, resolve, checks = false, isHoliday = isHoliday)
        val from = profile.period.start(month)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(profile.period.end(month)) }.map(resolve).toList()
        val changes = mutableListOf<String>()
        val hints = mutableListOf<String>()
        var p = profile

        // 1. 기본급 or 시급.
        val hourlyOnSlip = read.amount(SlipRole.HOURLY)?.takeIf { it in 1..99_999 }
        when {
            hourlyOnSlip != null && slip.base == null || hourlyOnSlip != null && p.wageType == WageType.HOURLY -> {
                if (p.wageType != WageType.HOURLY || p.base.on(month).value != hourlyOnSlip) changes += "시급 ${won(hourlyOnSlip)}"
                p = p.copy(wageType = WageType.HOURLY, base = p.base.with(DatedValue(from, hourlyOnSlip)), annual = null)
            }
            slip.base != null && p.wageType == WageType.MONTHLY -> {
                if (p.base.on(month).value != slip.base) changes += "월 기본급 ${won(slip.base)}"
                p = p.copy(base = p.base.with(DatedValue(from, slip.base)), annual = null)
            }
        }
        p = p.copy(estimatedFields = p.estimatedFields - Unknowns.BASE)

        // 2. Allowances the setup does not have, by what they follow.
        val first = run(p)
        val nights = days.count { it.isWorkDay && isNightShift(it) }
        val workDays = days.count { it.isWorkDay }
        for ((name, amount) in slip.allowances) {
            if (p.items.any { norm(it.name) == norm(name) } || p.customRules.any { norm(it.name) == norm(name) }) continue
            val role = read.lines.firstOrNull { it.name == name }?.role
            val item = if (role == SlipRole.BONUS) {
                PayItem(id(name), name, ValueTimeline(listOf(DatedValue(from, amount))), Cadence.BONUS, months = setOf(month.monthValue))
            } else {
                val n = norm(name)
                val byType = p.items.none { it.cadence == Cadence.PER_SHIFT_TYPE } && days.filter { it.isWorkDay }
                    .groupBy { it.shiftType }.entries.firstOrNull { (t, ds) -> t != null && t.name.isNotBlank() && n.contains(norm(t.name)) && amount % ds.size == 0L }
                when {
                    byType != null -> PayItem(id(name), name, ValueTimeline(listOf(DatedValue(from, amount / byType.value.size))),
                        Cadence.PER_SHIFT_TYPE, shiftTypeIds = setOf(byType.key!!.id))
                    Regex("나이트|야간|밤").containsMatchIn(n) && nights > 0 && amount % nights == 0L ->
                        PayItem(id(name), name, ValueTimeline(listOf(DatedValue(from, amount / nights))), Cadence.PER_NIGHT_SHIFT)
                    Regex("식대|식비|교통|출근").containsMatchIn(n) && workDays > 0 && amount % workDays == 0L && amount / workDays <= 30_000 ->
                        PayItem(id(name), name, ValueTimeline(listOf(DatedValue(from, amount / workDays))), Cadence.PER_WORK_DAY)
                    // Fixed every month for the job (직책·자격·면허·근속·교대): 통상임금 (대법원 2013다4174 전원합의체 기준의 고정성).
                    else -> PayItem(id(name), name, ValueTimeline(listOf(DatedValue(from, amount))), Cadence.MONTHLY,
                        ordinary = Regex("직책|자격|면허|근속|교대|직무|위험").containsMatchIn(n))
                }
            }
            p = p.copy(items = p.items + item)
            changes += "‘$name’ " + when (item.cadence) {
                Cadence.MONTHLY -> "매달 ${won(amount)}"
                Cadence.BONUS -> "${month.monthValue}월 ${won(amount)}"
                else -> "${item.cadence.label} ${won(item.amount.on(month).value)}"
            }
        }

        // 3. The premium rates behind the payslip's lines, on 통상시급.
        val hourly = run(p).ordinaryHourly.takeIf { it > 0 } ?: first.ordinaryHourly
        if (hourly > 0 && p.fiveOrMoreEmployees) {
            val nightMin = slip.nightMinutes ?: first.minutes.sumOf { it.night }
            val law = RateTable.on(month.atDay(1))
            slip.nightPay?.takeIf { nightMin > 0 }?.let { pay ->
                snap(pay / (nightMin / 60.0 * hourly), NIGHT_RATES)?.let { r ->
                    if (abs(r - (p.rules.nightPremium ?: law.nightPremium)) > 1e-9) {
                        p = p.withRules { it.copy(nightPremium = r) }
                        changes += "야간 가산 ${PayCalculator.pct(r)}"
                    }
                }
            }
            val otMin = slip.overtimeMinutes ?: first.minutes.sumOf { it.overtime }
            slip.overtimePay?.takeIf { otMin > 0 }?.let { pay ->
                snap(pay / (otMin / 60.0 * hourly) - 1.0, OVERTIME_RATES)?.let { r ->
                    if (abs(r - (p.rules.overtimePremium ?: law.overtimePremium)) > 1e-9) {
                        p = p.withRules { it.copy(overtimePremium = r) }
                        changes += "연장 가산 ${PayCalculator.pct(r)}"
                    }
                }
            }
        }

        // 4. 잔업·특근·대근 paid as a line of its own: a 1회 amount when the law's way does not come to it.
        val extraLines = read.lines.filter { it.role == SlipRole.EXTRA_WORK && it.amount != null }
        val holidayShifts = days.filter { it.isWorkDay && isPublic(it.date, p, isHoliday) }
        var holidayExtra: Long? = null
        for (line in extraLines) {
            val n = norm(line.name)
            val kind = when {
                "대근" in n -> ExtraKind.COVER_ID
                "특근" in n -> ExtraKind.SPECIAL_ID
                "잔업" in n || "조출" in n -> ExtraKind.OVERTIME_ID
                else -> continue
            }
            val times = days.sumOf { d -> d.extras.count { it.kind.id == kind } }
            if (times == 0) {
                // Paid with no extra work on the calendar: 빨간날 shifts paid as 특근·대근 (step 5).
                if (holidayShifts.isNotEmpty() && kind != ExtraKind.OVERTIME_ID) holidayExtra = (holidayExtra ?: 0) + line.amount!!
                else hints += "명세서의 ‘${line.name}’ ${won(line.amount!!)}에 맞는 ${p.rules.extraName(kind)}이 달력에 없어요. 그날을 달력에 넣어 주세요"
                continue
            }
            val each = line.amount!! / times
            val withRule = p.withRules { it.copy(extra = it.extra + (kind to ExtraRule(basis = ExtraBasis.FIXED_PER_TIME, amount = each))) }
            if (abs(run(withRule).total - slip.gross) < abs(run(p).total - slip.gross)) {
                p = withRule
                changes += "${p.rules.extraName(kind)} 1회 ${won(each)}"
            }
        }

        // 5. The 빨간날 choice that comes closest, when a shift fell on one.
        if (holidayShifts.isNotEmpty() && p.fiveOrMoreEmployees) {
            val candidates = buildList {
                add(p.rules.withHolidayWork(HolidayWork.PREMIUM))
                add(p.rules.withHolidayWork(HolidayWork.PREMIUM).copy(holidayByShiftStart = true))
                add(p.rules.withHolidayWork(HolidayWork.SHIFT_NORMAL))
                holidayExtra?.let { pay ->
                    val each = pay / holidayShifts.size
                    var r = p.rules.withHolidayWork(HolidayWork.AS_EXTRA, ExtraKind.SPECIAL_ID)
                    holidayShifts.mapNotNull { it.shiftType?.id }.distinct().forEach { id -> r = r.withHolidayShiftPay(id, each) }
                    add(r)
                }
            }
            val best = candidates.minBy { r -> abs(run(p.copy(rules = r)).total - slip.gross) }
            if (best != p.rules) {
                val before = p.rules.holidayWork
                p = p.copy(rules = best, estimatedFields = p.estimatedFields - Unknowns.PUBLIC_HOLIDAYS)
                if (best.holidayWork != before || best.holidayByShiftStart != profile.rules.holidayByShiftStart || best.holidayWork == HolidayWork.AS_EXTRA) {
                    changes += best.holidayWorkText(days.mapNotNull { it.shiftType }.associate { it.id to it.name })
                }
            }
        }

        // 6. The calculation choices: 월 소정근로시간, 통상임금, 야간 시간대, 연장 끝처리 …
        val matched = PayslipMatcher.match(p, month, resolve, slip, isHoliday, calculator)
        matched.changes.forEach { c -> if (changes.none { it.startsWith(c.label) }) changes += "${c.label}: ${c.to}" }
        hints += matched.hints.map { it.text }
        val total = run(matched.profile).total
        return SlipRulesResult(month, matched.profile, changes, total, slip.gross, hints)
    }

    private fun snap(rate: Double, usual: List<Double>): Double? = usual.firstOrNull { abs(rate - it) <= SNAP }

    private fun isNightShift(d: ResolvedDay): Boolean {
        val s = d.start ?: return false
        val e = d.end ?: return false
        return e <= s || s.hour >= 21
    }

    private fun isPublic(date: LocalDate, p: PayProfile, isHoliday: ((LocalDate) -> Boolean)?): Boolean =
        isHoliday?.invoke(date) ?: (KoreanHolidays.isHoliday(date) || KoreanHolidays.isLaborDay(date))

    private fun norm(s: String) = s.filterNot { it.isWhitespace() || it == '(' || it == ')' }.lowercase()

    private fun id(name: String) = "slip-" + norm(name).hashCode().toUInt().toString(16)
}
