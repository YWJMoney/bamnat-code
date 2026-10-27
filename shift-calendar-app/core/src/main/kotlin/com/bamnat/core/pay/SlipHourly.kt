package com.bamnat.core.pay

import kotlin.math.abs
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
 *
 * 1.30 AV (사용자 2026-10-27 "LG화학은 근로시수 180시간으로 통상임금이 적용된다 … 명세서와 회사 규칙을 사진으로 보내면 해당 내용의
 * 근거로만 가지고도 월급을 정확하게 계산해 낼 수 있어야 한다"):
 * - [HOURLY-01] [hoursOf]: the company's 기준 시간 worked back from the same payslip: (기본급 + 통상 수당 + 정기 상여의 월 몫) ÷ 시급.
 * - [HOURLY-02] one premium line is enough for a guess ([Fit.sure] false, looked at before it is used).
 * - [HOURLY-03] a company paying another 배율 than the law's (휴일연장 2.5배) is found by trying the usual ones, one at a time.
 */
object SlipHourly {
    enum class Cut(val label: String) { UP("올림"), NEAREST("반올림"), DOWN("버림") }

    /**
     * The 시급, how the lines cut their 원, and the lines that agree with it (all of them, or it is not given). [sure] is
     * false when one line alone gave it ([HOURLY-02]); [multipliers] are the 배율 it was found with ([HOURLY-03]).
     */
    data class Fit(
        val hourly: Double, val cut: Cut, val lines: List<SlipLine>, val sure: Boolean = true,
        val multipliers: Multipliers = Multipliers(),
    ) {
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

    /**
     * The company's 시급 from [read]'s premium lines with hours. Two lines or more must agree to the won; when they do not on
     * the law's 배율 (or [m], the company's), each usual other 배율 is tried in turn ([HOURLY-03]) and the one that makes them
     * agree is taken, when only one does. One line alone gives a guess ([HOURLY-02]). null when none.
     */
    fun of(read: PayslipRead, m: Multipliers = Multipliers()): Fit? {
        val lines = read.lines.filter { it.amount != null && it.amount > 0 && (it.minutes ?: 0) > 0 && multiplier(it, m) != null }
        if (lines.isEmpty()) return null
        if (lines.size == 1) return guess(lines.single(), m)
        fit(lines, m)?.let { return it }
        val others = tries(m).mapNotNull { t -> fit(lines, t) }
        return others.singleOrNull()
    }

    /** [HOURLY-03] The usual other 배율, one changed at a time: 휴일연장 2.5·3배, 휴일 2배, 야간 1배 (가산 100%), 연장 2배. */
    private fun tries(m: Multipliers): List<Multipliers> = listOf(
        m.copy(holidayOver8h = 2.5), m.copy(holidayOver8h = 3.0), m.copy(holiday = 2.0), m.copy(night = 1.0), m.copy(overtime = 2.0),
    ).filter { it != m }

    private fun fit(lines: List<SlipLine>, m: Multipliers): Fit? {
        // [HOURLY-03] 1.30: the way of cutting whose 시급 has the fewest decimals (원 미만 내림 at 12,345.5 rather than 반올림 at
        // 12,345.47), 반올림 first on a tie.
        var best: Fit? = null
        var bestScale = Int.MAX_VALUE
        for (cut in listOf(Cut.NEAREST, Cut.UP, Cut.DOWN)) {
            // Each line allows a range of 시급; the 시급 is in all of them.
            var lo = Double.NEGATIVE_INFINITY
            var hi = Double.POSITIVE_INFINITY
            for (l in lines) {
                val (from, to) = range(l, cut, m)
                lo = max(lo, from); hi = min(hi, to)
            }
            if (lo >= hi) continue
            for ((i, scale) in listOf(1.0, 10.0, 100.0).withIndex()) {
                if (i >= bestScale) break
                val v = fewestAt(lo, hi, scale) { v -> lines.all { won(it, v, cut, m) == it.amount } } ?: continue
                best = Fit(v, cut, lines, multipliers = m); bestScale = i
                break
            }
        }
        return best
    }

    /**
     * [HOURLY-02] 1.30 AV: one premium line (a payslip with 야간 only, a common one): the 시급 with the fewest decimals that
     * gives the line to the won, on the way of cutting that needs the fewest. A guess: the next payslip makes it sure.
     */
    private fun guess(line: SlipLine, m: Multipliers): Fit? {
        var best: Pair<Double, Cut>? = null
        var bestScale = Int.MAX_VALUE
        for (cut in listOf(Cut.NEAREST, Cut.UP, Cut.DOWN)) {
            val (lo, hi) = range(line, cut, m)
            for ((i, scale) in listOf(1.0, 10.0, 100.0).withIndex()) {
                if (i >= bestScale) break
                val v = fewestAt(lo, hi, scale) { won(line, it, cut, m) == line.amount } ?: continue
                best = v to cut; bestScale = i
                break
            }
        }
        val (v, cut) = best ?: return null
        return Fit(v, cut, listOf(line), sure = false, multipliers = m)
    }

    /** The 시급 a line of [cut] allows, [from] to [to] (exclusive). */
    private fun range(l: SlipLine, cut: Cut, m: Multipliers): Pair<Double, Double> {
        val k = l.minutes!! / 60.0 * multiplier(l, m)!!
        val a = l.amount!!.toDouble()
        return when (cut) {
            Cut.UP -> (a - 1) / k to a / k
            Cut.NEAREST -> (a - 0.5) / k to (a + 0.5) / k
            Cut.DOWN -> a / k to (a + 1) / k
        }
    }

    /** The lowest 시급 in [lo]..[hi] at [scale] (1: 원, 10: 10전, 100: 전) that [ok]. */
    private fun fewestAt(lo: Double, hi: Double, scale: Double, ok: (Double) -> Boolean): Double? {
        var v = ceil(lo * scale - 1e-9) / scale
        var n = 0
        while (v <= hi + 1e-9 && n++ < 10_000) {
            if (v > 0 && ok(v)) return v
            v = (floor(v * scale + 1e-9) + 1) / scale
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
    fun finding(read: PayslipRead, m: Multipliers = Multipliers()): RuleFinding? = of(read, m)?.let(::finding)

    private fun finding(fit: Fit): RuleFinding {
        val source = fit.lines.joinToString(", ") { "${it.name} ${PayCalculator.hours(it.minutes!!)} ${"%,d".format(it.amount)}원" }
        val change = RuleChange.OrdinaryHourly(fit.hourly, fit.cut == Cut.UP, roundDown = fit.cut == Cut.DOWN, estimated = !fit.sure)
        return RuleFinding(source, change, if (fit.sure) 0.8 else 0.5)
    }

    /**
     * [HOURLY-01][HOURLY-02][HOURLY-03] 1.30 AV: everything the payslip says about the company's 통상시급, as rules to tick on
     * the photo's result: the 시급 (a guess from one line starts unticked), a 배율 other than the law's or the rules' it was
     * found with ("명세서로 보면 휴일연장 2.5배예요"), and the 기준 시간 ("명세서로 보면 통상임금 기준 180시간이에요"). [rules]
     * are those read from the company's 계산 방법 photo with it: their 배율 and their 통상임금 items are used.
     */
    fun findings(read: PayslipRead, rules: List<RuleFinding> = emptyList()): List<RuleFinding> {
        val m = multipliers(rules)
        val fit = of(read, m) ?: return emptyList()
        val out = mutableListOf(finding(fit))
        premiumChanged(m, fit.multipliers)?.let { (change, label) ->
            out += RuleFinding("명세서로 보면 $label (${fit.lines.joinToString(", ") { it.name }})", change, 0.7)
        }
        val items = rules.map { it.change }.filterIsInstance<RuleChange.OrdinaryItems>().lastOrNull()?.names
        // The 기준 시간 the rules state, to check against: the payslip's own agrees, or is offered.
        val stated = rules.map { it.change }.filterIsInstance<RuleChange.MonthlyHours>().lastOrNull()?.hours
        hoursOf(read, fit.hourly, items, prefer = stated)?.let { h -> if (h.hours != stated) out += hoursFinding(h, fit) }
        return out
    }

    /** The one 배율 [found] changed from [given], as a rule and its words. */
    private fun premiumChanged(given: Multipliers, found: Multipliers): Pair<RuleChange.Premium, String>? = when {
        found.holidayOver8h != given.holidayOver8h -> RuleChange.Premium(PremiumTarget.HOLIDAY, found.holidayOver8h - 1, over8h = true) to "휴일연장 ${times(found.holidayOver8h)}배예요"
        found.holiday != given.holiday -> RuleChange.Premium(PremiumTarget.HOLIDAY, found.holiday - 1) to "휴일근로 ${times(found.holiday)}배예요"
        found.night != given.night -> RuleChange.Premium(PremiumTarget.NIGHT, found.night) to "야간 가산 ${times(found.night * 100)}%예요"
        found.overtime != given.overtime -> RuleChange.Premium(PremiumTarget.OVERTIME, found.overtime - 1) to "연장 ${times(found.overtime)}배예요"
        else -> null
    }

    private fun times(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()

    /**
     * [HOURLY-01] The 기준 시간 worked back: [ordinary] 통상임금 a month (the lines it is made of, [lines], and the share of a
     * 정기 상여 of [bonusPercent]% a year of 기본급, [bonusShare]) ÷ the payslip's 시급 = [hours], [off] won from a whole hour.
     */
    data class Hours(val hours: Int, val ordinary: Long, val lines: List<SlipLine>, val bonusPercent: Int, val bonusShare: Long, val off: Double)

    /** 월 소정근로시간 companies use most; one of these is taken before any other that fits as well. */
    val USUAL_HOURS = listOf(209, 226, 243, 180, 174, 183, 195, 200, 220, 240)

    /**
     * [HOURLY-01] 1.30 AV: the company's 기준 시간 from its payslip and the 시급 its premium lines were paid on ([of]). 통상임금 is
     * 기본급 and the 수당 the rules name ([items], 계산 방법 사진의 "통상임금: 기본급, 직책수당, 자격수당 …"), or without them the
     * ones that usually are ([OrdinaryGuess]: 자격·직책·근속 …, each tried in and out), with or without a 정기 상여's monthly
     * share (연 100~1200% of 기본급, by 50%). The hours that make (통상임금 ÷ 시급) whole to within a few won are the company's:
     * 화학 공장 명세서 (2,555,830 + 57,500 + 상여 600%의 1/12 1,277,915) ÷ 21,618.04 = 180시간. When several fit, the usual hours
     * ([USUAL_HOURS], or [prefer] the rules state) and a round 상여 first; null when none fits.
     */
    fun hoursOf(read: PayslipRead, hourly: Double, items: List<String>? = null, prefer: Int? = null): Hours? {
        if (hourly <= 0) return null
        val pay = read.lines.filter { it.amount != null && it.amount > 0 && it.isEarning }
        val base = pay.filter { it.role == SlipRole.BASE }
        if (base.isEmpty()) return null
        val baseSum = base.sumOf { it.amount!! }
        fun stem(s: String) = PayslipReader.ocrFix(s.filter { it in '가'..'힣' })
        val wanted = items?.map(::stem)?.filter { it.isNotEmpty() }
        val sure: List<SlipLine>
        val maybe: List<SlipLine>
        val usual: Set<SlipLine>
        if (wanted != null) {
            sure = pay.filter { l -> l.role in ITEM_ROLES && wanted.any { w -> stem(l.name).startsWith(w) || w.startsWith(stem(l.name)) && stem(l.name).length >= 2 } }
                .let { if (it.none { l -> l.role == SlipRole.BASE }) it + base else it }
            maybe = emptyList()
            usual = emptySet()
        } else {
            sure = base
            maybe = pay.filter { l -> l.role == SlipRole.ALLOWANCE && "소급" !in l.name && OrdinaryGuess.of(l.name, Cadence.MONTHLY) != false }.take(6)
            usual = maybe.filter { OrdinaryGuess.of(it.name, Cadence.MONTHLY) == true }.toSet()
        }
        val sureSum = sure.sumOf { it.amount!! }
        val fits = mutableListOf<Pair<Hours, Int>>()
        for (mask in 0 until (1 shl maybe.size)) {
            val chosen = maybe.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
            val fixed = sureSum + chosen.sumOf { it.amount!! }
            // How far the lines chosen are from the usual guess: fewer is likelier.
            val astray = maybe.count { (it in chosen) != (it in usual) }
            for (percent in listOf(0) + (100..1200 step 50)) {
                val share = baseSum * percent / 1200.0
                val ordinary = fixed + share
                val h = ordinary / hourly
                val whole = Math.round(h).toInt()
                if (whole !in 100..320) continue
                // The 시급 is to the 전: (통상임금 ÷ 시간) cut to the 전 is at most 0.01 off, × the hours; a share of 상여 cut
                // to the won by the company, a won or two.
                val off = abs(ordinary - whole * hourly)
                if (off > 0.0101 * whole + 2.5) continue
                val rank = (if (whole == prefer) 0 else if (whole in USUAL_HOURS) 1 else 3) * 100 + (if (percent % 100 == 0) 0 else 10) + astray
                fits += Hours(whole, Math.round(ordinary), sure + chosen, percent, Math.round(share), off) to rank
            }
        }
        if (fits.isEmpty()) return null
        val best = fits.minWith(compareBy<Pair<Hours, Int>> { it.second }.thenBy { it.first.off })
        // Hours no company is known to use, from one fit among others: not offered.
        if (best.second >= 300 && fits.count { it.second < 400 } > 1) return null
        return best.first
    }

    private val ITEM_ROLES = setOf(SlipRole.BASE, SlipRole.ALLOWANCE)

    private fun hoursFinding(h: Hours, fit: Fit): RuleFinding {
        val parts = h.lines.joinToString(" + ") { "${it.name} ${"%,d".format(it.amount)}원" } +
            (if (h.bonusPercent > 0) " + 상여 연 ${h.bonusPercent}%의 1/12 ${"%,d".format(h.bonusShare)}원" else "")
        val source = "$parts = ${"%,d".format(h.ordinary)}원 ÷ 통상시급 ${"%,.2f".format(fit.hourly)}원 = ${h.hours}시간"
        val confidence = if (!fit.sure) 0.5 else if (h.hours in USUAL_HOURS) 0.8 else 0.6
        return RuleFinding(source, RuleChange.MonthlyHours(h.hours, HourlySource.SLIP), confidence)
    }
}
