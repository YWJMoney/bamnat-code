package com.bamnat.core.pay

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * [PAYX-42] 연말정산 미리보기: the year's 결정세액 worked out roughly from the 세전 of each month, against the 소득세
 * withheld by the 간이세액표, so the user sees early whether February brings money back or takes more. Only the
 * deductions every 근로자 has are in: 근로소득공제, 기본공제, 국민연금·건강·고용보험료 공제, 근로소득세액공제,
 * 자녀세액공제 (or the 표준세액공제 when it is more). 카드·의료비·교육비·기부금·연금저축 and the like are not
 * asked and are named in [LEFT_OUT]. Articles: 소득세법 제47조·제50조·제51조의3·제52조·제55조·제59조·제59조의2·
 * 제59조의4 (2025년 귀속 기준, 확인 필요).
 */
object YearEndPreview {
    data class Result(
        val year: Int,
        val salary: Long,
        val earnedDeduction: Long,
        val taxBase: Long,
        val calculated: Long,
        val credits: Long,
        /** 결정세액 (소득세), and 지방소득세 its 10%. */
        val determined: Long,
        val local: Long,
        /** 소득세 withheld (간이세액표), 지방소득세 not included. */
        val withheld: Long,
        /** Months of [year] counted; the rest of the year is taken as the months so far went. */
        val months: Int,
        /** F 1.32 [TAXP-03] 신용카드 등 소득공제 (조세특례제한법 제126조의2). */
        val cardDeduction: Long = 0,
        /** F 1.32 [TAXP-03] 보장성보험·의료비·교육비·기부금·월세 세액공제, or 0 when the 표준세액공제 left less tax. */
        val specialCredits: Long = 0,
        /** F 1.32 [TAXP-03] 연금계좌 세액공제 (제59조의3). */
        val pensionCredit: Long = 0,
        val standard: Boolean = true,
        /** F 1.32 [TAXP-03] What could still bring more back, with the won each one is worth. */
        val tips: List<Tip> = emptyList(),
        val extras: Extras = Extras(),
    ) {
        /** Positive: money back in February (소득세 + 지방소득세); negative: more to pay. */
        val refund: Long get() = ((withheld - determined) * 1.1).roundToLong()
    }

    /**
     * F 1.32 [TAXP-03] 594: the year's amounts beyond pay, whole-year won (the app runs the ledger's year so far on to
     * December; the user can type over each). 주택자금·벤처투자 and the like are still not asked ([LEFT_OUT_EXTRAS]).
     */
    data class Extras(
        /** 신용카드 (15%). */
        val credit: Long = 0,
        /** 체크카드·현금영수증·페이 (30%). */
        val debit: Long = 0,
        /** 전통시장·대중교통 (40~80%, taken as 40%: 확인 필요). */
        val market: Long = 0,
        /** 보장성보험료 (12%, 100만 원까지). */
        val insurance: Long = 0,
        /** 의료비: 총급여 3% 넘는 몫의 15%. */
        val medical: Long = 0,
        /** 교육비 15% (본인 한도 없음, 자녀 한 명 300만 원: 여기서는 한도 없이). */
        val education: Long = 0,
        /** 기부금 15%, 1,000만 원 넘는 몫 30%. */
        val donation: Long = 0,
        /** 월세 (무주택 세대주, 총급여 8,000만 원 이하): 17% 또는 15%, 1,000만 원까지. */
        val rent: Long = 0,
        /** 연금저축 (600만 원까지) and IRP (합쳐 900만 원까지). */
        val pensionSavings: Long = 0,
        val irp: Long = 0,
    ) {
        val any: Boolean get() = listOf(credit, debit, market, insurance, medical, education, donation, rent, pensionSavings, irp).any { it > 0 }
    }

    data class Tip(val text: String, val worth: Long)

    const val LEFT_OUT_EXTRAS = "주택자금·주택청약, 벤처 투자, 중소기업 취업자 감면은 넣지 않았어요. 해당되면 더 돌려받아요."

    /** 신용카드 등 소득공제: the 25% 문턱 taken from the 신용카드 first, then the rest at their rates, under the 한도. */
    fun cardDeduction(salary: Long, x: Extras): Long {
        var left = (salary * 0.25).roundToLong()
        fun over(v: Long): Long { val o = (v - left).coerceAtLeast(0); left = (left - v).coerceAtLeast(0); return o }
        val c = over(x.credit); val d = over(x.debit); val m = over(x.market)
        val main = (c * 0.15 + d * 0.30).roundToLong()
        val cap = if (salary <= 70_000_000) 3_000_000L else 2_500_000L
        val marketCap = if (salary <= 70_000_000) 3_000_000L else 2_000_000L
        return minOf(main, cap) + minOf((m * 0.40).roundToLong(), marketCap)
    }

    /** 보장성보험·의료비·교육비·기부금·월세 세액공제 (제59조의4, 조세특례제한법 제95조의2). */
    fun specialCredits(salary: Long, x: Extras): Long {
        val insurance = minOf(x.insurance, 1_000_000L) * 0.12
        val medical = (x.medical - (salary * 0.03).roundToLong()).coerceAtLeast(0) * 0.15
        val education = x.education * 0.15
        val donation = minOf(x.donation, 10_000_000L) * 0.15 + (x.donation - 10_000_000L).coerceAtLeast(0) * 0.30
        val rentRate = when { salary <= 55_000_000 -> 0.17; salary <= 80_000_000 -> 0.15; else -> 0.0 }
        val rent = minOf(x.rent, 10_000_000L) * rentRate
        return (insurance + medical + education + donation + rent).roundToLong()
    }

    /** 연금계좌 세액공제 (제59조의3): 연금저축 600만 원, IRP 합쳐 900만 원까지, 총급여 5,500만 원 이하 15%, 넘으면 12%. */
    fun pensionCredit(salary: Long, x: Extras): Long {
        val savings = minOf(x.pensionSavings, 6_000_000L)
        val all = minOf(savings + x.irp, 9_000_000L)
        return (all * if (salary <= 55_000_000) 0.15 else 0.12).roundToLong()
    }

    /** Said when no [Extras] were given (the free look and older callers). */
    const val LEFT_OUT = "신용카드 등 사용액, 의료비·교육비·보험료·기부금, 월세, 연금저축·IRP, 주택자금은 넣지 않았어요. 넣으면 대개 더 돌려받아요."

    /** 근로소득공제 (제47조), capped at 2,000만 원. */
    fun earnedDeduction(salary: Long): Long {
        val s = salary.toDouble()
        return when {
            s <= 5_000_000 -> s * 0.7
            s <= 15_000_000 -> 3_500_000 + (s - 5_000_000) * 0.4
            s <= 45_000_000 -> 7_500_000 + (s - 15_000_000) * 0.15
            s <= 100_000_000 -> 12_000_000 + (s - 45_000_000) * 0.05
            else -> minOf(14_750_000 + (s - 100_000_000) * 0.02, 20_000_000.0)
        }.roundToLong()
    }

    /** 기본세율 (제55조 제1항). */
    fun basicTax(base: Long): Long {
        val b = base.toDouble()
        return when {
            b <= 0 -> 0.0
            b <= 14_000_000 -> b * 0.06
            b <= 50_000_000 -> b * 0.15 - 1_260_000
            b <= 88_000_000 -> b * 0.24 - 5_760_000
            b <= 150_000_000 -> b * 0.35 - 15_440_000
            b <= 300_000_000 -> b * 0.38 - 19_940_000
            b <= 500_000_000 -> b * 0.40 - 25_940_000
            b <= 1_000_000_000 -> b * 0.42 - 35_940_000
            else -> b * 0.45 - 65_940_000
        }.roundToLong()
    }

    /** 근로소득세액공제 (제59조) with its cap by 총급여. */
    fun earnedCredit(tax: Long, salary: Long): Long {
        val credit = if (tax <= 1_300_000) tax * 0.55 else 715_000 + (tax - 1_300_000) * 0.30
        val s = salary.toDouble()
        val cap = when {
            s <= 33_000_000 -> 740_000.0
            s <= 70_000_000 -> maxOf(660_000.0, 740_000 - (s - 33_000_000) * 0.008)
            s <= 120_000_000 -> maxOf(500_000.0, 660_000 - (s - 70_000_000) * 0.5)
            else -> maxOf(200_000.0, 500_000 - (s - 120_000_000) * 0.5)
        }
        return minOf(credit, cap).roundToLong()
    }

    /** 자녀세액공제 (제59조의2, 8세 이상 자녀): 1명 25만, 2명 55만, 셋째부터 1명마다 40만 원 (2025년 귀속, 확인 필요). */
    fun childCredit(children: Int): Long = when {
        children <= 0 -> 0
        children == 1 -> 250_000
        else -> 550_000L + 400_000L * (children - 2)
    }

    const val STANDARD_CREDIT = 130_000L

    /**
     * The year from its 세전 [gross] (taxable part only: 비과세 left out), the 국민연금 and 건강·장기요양·고용보험 the
     * worker paid, and the 소득세 withheld, all for the [months] counted so far, run on to twelve.
     */
    fun estimate(year: Int, gross: Long, insurance: Long, pension: Long, withheld: Long, months: Int, dependents: Int, children: Int,
                 extras: Extras = Extras(), withTips: Boolean = true): Result {
        val k = 12.0 / months.coerceIn(1, 12)
        val salary = (gross * k).roundToLong()
        val earned = earnedDeduction(salary)
        val card = cardDeduction(salary, extras)
        val base = (salary - earned - 1_500_000L * dependents.coerceAtLeast(1) - (pension * k).roundToLong() - card).coerceAtLeast(0)
        // 보험료 특별소득공제 with the 특별세액공제, or the 표준세액공제: whichever leaves less tax.
        val withInsurance = (base - (insurance * k).roundToLong()).coerceAtLeast(0)
        val special = specialCredits(salary, extras)
        val pensionCr = pensionCredit(salary, extras)
        fun determined(taxBase: Long, standard: Boolean): Triple<Long, Long, Long> {
            val calc = basicTax(taxBase)
            val credits = earnedCredit(calc, salary) + childCredit(children) + pensionCr + if (standard) STANDARD_CREDIT else special
            return Triple(calc, minOf(credits, calc), (calc - credits).coerceAtLeast(0))
        }
        val a = determined(withInsurance, standard = false)
        val b = determined(base, standard = true)
        val useA = a.third <= b.third
        val (taxBase, best) = if (useA) withInsurance to a else base to b
        val w = (withheld * k).roundToLong()
        val r = Result(year, salary, earned, taxBase, best.first, best.second, best.third / 10 * 10, best.third / 100 * 10,
            w, months.coerceIn(1, 12), card, if (useA) special else 0, pensionCr, !useA, emptyList(), extras)
        if (!withTips) return r
        return r.copy(tips = tips(r, year, gross, insurance, pension, withheld, months, dependents, children))
    }

    /**
     * F 1.32 [TAXP-03] What one more step is worth: 연금저축 up to its 한도, and the 신용카드 spending of the rest of the year
     * on a 체크카드 instead. Each is worked out by running the year again, so the 한도 and the tax left are kept.
     */
    private fun tips(r: Result, year: Int, gross: Long, insurance: Long, pension: Long, withheld: Long, months: Int, dependents: Int, children: Int): List<Tip> {
        if (r.determined <= 0) return emptyList()
        fun again(x: Extras) = estimate(year, gross, insurance, pension, withheld, months, dependents, children, x, withTips = false).let { it.refund - r.refund }
        val x = r.extras
        val out = mutableListOf<Tip>()
        val room = (9_000_000L - minOf(x.pensionSavings, 6_000_000L) - x.irp).coerceAtLeast(0)
        if (room > 0) {
            val add = minOf(room, 3_000_000L)
            val worth = again(if (x.pensionSavings < 6_000_000L) x.copy(pensionSavings = x.pensionSavings + add) else x.copy(irp = x.irp + add))
            if (worth > 0) out += Tip("12월 말까지 연금저축·IRP에 ${"%,d".format(add)}원 더 넣으면 약 ${"%,d".format(worth)}원 더 돌려받아요", worth)
        }
        val rest = (x.credit * (12 - months.coerceIn(1, 12)) / 12)
        if (rest > 0 && x.credit + x.debit + x.market > r.salary / 4) {
            val worth = again(x.copy(credit = x.credit - rest, debit = x.debit + rest))
            if (worth > 0) out += Tip("남은 달은 신용카드 대신 체크카드를 쓰면 약 ${"%,d".format(worth)}원 더 돌려받아요", worth)
        }
        return out.sortedByDescending { it.worth }
    }

    /**
     * The year so far worked out month by month with [calculator]: each month's 세전 and the 간이세액표 withholding
     * on it ([TakeHome.estimate]), from January to [upTo]. Null when no month has pay.
     */
    fun ofYear(profile: PayProfile, upTo: YearMonth, resolve: (LocalDate) -> ResolvedDay, calculator: PayCalculator = PayCalculator(),
               extras: Extras = Extras()): Result? {
        var gross = 0L; var ins = 0L; var pension = 0L; var tax = 0L; var n = 0
        for (m in 1..upTo.monthValue) {
            val ym = YearMonth.of(upTo.year, m)
            val pay = runCatching { calculator.calculate(profile, ym, resolve, checks = false) }.getOrNull() ?: continue
            if (pay.total <= 0) continue
            val free = TakeHome.nonTaxable(profile, ym, pay)
            val r = TakeHome.estimate(pay.total, TaxMode.INSURANCE_4, profile.taxFamily, ym.atDay(1), profile.taxChildren, free).deductions.toMap()
            gross += pay.total - free
            pension += r["국민연금"] ?: 0
            ins += (r["건강보험"] ?: 0) + (r["장기요양"] ?: 0) + (r["고용보험"] ?: 0)
            tax += r[TakeHome.TAX_LABEL] ?: 0
            n++
        }
        if (n == 0) return null
        return estimate(upTo.year, gross, ins, pension, tax, n, profile.taxFamily, profile.taxChildren, extras)
    }

    /**
     * F 1.32 [TAXP-03] The year's [Extras] read from the ledger so far ([entries] of [year] up to [today]), run on to
     * December: cards by their kind ([accounts]), 의료·교육 by category, 월세 from 주거 lines that say 월세, 기부 from
     * lines that say 기부·후원. Whatever the user typed in [typed] wins over the ledger's number.
     */
    fun extrasFrom(entries: List<com.bamnat.core.money.MoneyEntry>, accounts: List<com.bamnat.core.money.MoneyAccount>, year: Int,
                   today: LocalDate, typed: Extras = Extras()): Extras {
        val until = if (today.year > year) LocalDate.of(year, 12, 31) else today
        val lines = entries.filter { it.kind == com.bamnat.core.money.MoneyKind.EXPENSE && it.date.year == year && !it.date.isAfter(until) }
        val k = 365.0 / (until.dayOfYear.coerceAtLeast(1))
        fun run(v: Long) = (v * k).roundToLong()
        val byId = accounts.associateBy { it.id }
        var credit = 0L; var debit = 0L; var medical = 0L; var education = 0L; var donation = 0L; var rent = 0L; var insurance = 0L
        for (e in lines) {
            val a = com.bamnat.core.money.MoneyAccounts.of(e, accounts)?.let(byId::get)
            val text = e.merchant + " " + e.note
            when {
                e.category == "의료" -> medical += e.amount
                e.category == "교육" -> education += e.amount
                e.category == "보험" -> insurance += e.amount
                e.category == "주거" && text.contains("월세") -> rent += e.amount
                text.contains("기부") || text.contains("후원") -> donation += e.amount
            }
            // 보험료·공과금·월세·해외 are not 신용카드 등 사용액 (조세특례제한법 제126조의2 제4항, 확인 필요).
            if (e.category in setOf("보험", "공과금") || (e.category == "주거" && text.contains("월세"))) continue
            when (a?.type) {
                com.bamnat.core.money.AccountType.CREDIT -> credit += e.amount
                com.bamnat.core.money.AccountType.CHECK, com.bamnat.core.money.AccountType.PAY -> debit += e.amount
                else -> if (e.cardLast4.isNotBlank()) credit += e.amount
            }
        }
        fun pick(t: Long, v: Long) = if (t > 0) t else run(v)
        return Extras(
            credit = pick(typed.credit, credit), debit = pick(typed.debit, debit), market = typed.market,
            insurance = pick(typed.insurance, insurance), medical = pick(typed.medical, medical), education = pick(typed.education, education),
            donation = pick(typed.donation, donation), rent = pick(typed.rent, rent), pensionSavings = typed.pensionSavings, irp = typed.irp,
        )
    }
}
