package com.bamnat.core.pay

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * [PHOTO-11] 1.29 AQ: the 통상시급 a company used, worked out from its own payslip (사용자 2026-10-27, 화학 공장 명세서: 휴일근로
 * 81시간 2,626,592원, 휴일연장 32시간 1,383,555원, 야간 64시간 691,778원). Each premium line is hours × 배율 × 시급, its 원 cut one
 * way; the one 시급 (to the 전, 0.01원) and the one way of cutting that give every line to the won is the company's: here
 * 21,618.04원, 원 미만 올림. With it next month's lines come out as the payslip's do ([PayCalculator.lineWon]).
 *
 * 배율: 휴일 8시간 넘는 시간 (휴일연장) 2배, 휴일 1.5배, 연장 1.5배, 야간 0.5배 (the 가산 only), unless the company's rules say
 * otherwise ([multipliers]). Plain arithmetic on the phone (D68).
 */
object SlipHourly {
    enum class Cut(val label: String) { UP("올림"), NEAREST("반올림"), DOWN("버림") }

    /** The 시급, how the lines cut their 원, and the lines that agree with it (all of them, or it is not given). */
    data class Fit(val hourly: Double, val cut: Cut, val lines: List<SlipLine>) {
        /** The 시급 to the won, as the app says it. */
        val won: Long get() = hourly.roundToLong()
    }

    data class Multipliers(val overtime: Double = 1.5, val night: Double = 0.5, val holiday: Double = 1.5, val holidayOver8h: Double = 2.0)

    /** The 배율 the company's rules read with the payslip say (휴일 ×1.5, 휴일연장 ×2, 야간 ×0.5), the law's otherwise. */
    fun multipliers(rules: List<RuleFinding>): Multipliers = rules.map { it.change }.filterIsInstance<RuleChange.Premium>().fold(Multipliers()) { m, p ->
        when (p.target) {
            PremiumTarget.OVERTIME -> m.copy(overtime = 1 + p.premium)
            PremiumTarget.NIGHT -> m.copy(night = p.premium)
            PremiumTarget.HOLIDAY -> if (p.over8h) m.copy(holidayOver8h = 1 + p.premium) else m.copy(holiday = 1 + p.premium)
        }
    }

    /** How many times the 시급 a line pays per hour, by its name; null for a line that is not a premium. */
    fun multiplier(line: SlipLine, m: Multipliers = Multipliers()): Double? {
        val n = PayslipReader.ocrFix(line.name.filter { it.isLetter() })
        return when {
            "휴일" in n && ("연장" in n || "초과" in n) -> m.holidayOver8h
            "휴일" in n || "특근" in n && line.role == SlipRole.HOLIDAY -> m.holiday
            "야간" in n || "심야" in n -> m.night
            "연장" in n || "시간외" in n -> m.overtime
            else -> null
        }
    }

    /** The company's 시급 from [read]'s premium lines with hours; null when fewer than two such lines or none fits them all. */
    fun of(read: PayslipRead, m: Multipliers = Multipliers()): Fit? {
        val lines = read.lines.filter { it.amount != null && it.amount > 0 && (it.minutes ?: 0) > 0 && multiplier(it, m) != null }
        if (lines.size < 2) return null
        for (cut in listOf(Cut.NEAREST, Cut.UP, Cut.DOWN)) {
            // Each line allows a range of 시급; the 시급 is in all of them.
            var lo = Double.NEGATIVE_INFINITY
            var hi = Double.POSITIVE_INFINITY
            for (l in lines) {
                val k = l.minutes!! / 60.0 * multiplier(l, m)!!
                val a = l.amount!!.toDouble()
                val (from, to) = when (cut) {
                    Cut.UP -> (a - 1) / k to a / k
                    Cut.NEAREST -> (a - 0.5) / k to (a + 0.5) / k
                    Cut.DOWN -> a / k to (a + 1) / k
                }
                lo = max(lo, from); hi = min(hi, to)
            }
            if (lo >= hi) continue
            // The fewest decimals: 원, then 10전, then 전.
            for (scale in listOf(1.0, 10.0, 100.0)) {
                var v = ceil(lo * scale - 1e-9) / scale
                while (v <= hi + 1e-9) {
                    if (v > 0 && lines.all { won(it, v, cut, m) == it.amount }) return Fit(v, cut, lines)
                    v = (floor(v * scale + 1e-9) + 1) / scale
                }
            }
        }
        return null
    }

    private fun won(l: SlipLine, hourly: Double, cut: Cut, m: Multipliers): Long {
        val v = l.minutes!! / 60.0 * hourly * multiplier(l, m)!!
        return when (cut) {
            Cut.UP -> ceil(v - 1e-6).toLong()
            Cut.NEAREST -> v.roundToLong()
            Cut.DOWN -> floor(v + 1e-6).toLong()
        }
    }

    /** The fit as a rule to tick on the photo's result: "명세서로 보면 통상시급 21,618원이에요". */
    fun finding(read: PayslipRead, m: Multipliers = Multipliers()): RuleFinding? {
        val fit = of(read, m) ?: return null
        val source = fit.lines.joinToString(", ") { "${it.name} ${PayCalculator.hours(it.minutes!!)} ${"%,d".format(it.amount)}원" }
        return RuleFinding(source, RuleChange.OrdinaryHourly(fit.hourly, fit.cut == Cut.UP), 0.8)
    }
}
