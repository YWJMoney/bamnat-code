package com.bamnat.core.tax

import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.TaxDeadlines
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * [TAX-05] 자영업·프리랜서 사업자 본인의 올해 세금 예상 (사용자 U9-라, research/1.25-W-조사.md 2장). Not the owner's payroll
 * for staff (Phase 3 M25, 결정 D72): no 인건비 원천징수 or employer insurance. It is 참고용 and always says so.
 *
 * From what the user wrote down (매출·일감·경비, and which costs have a 세금계산서·카드·현금영수증), it works out
 * 종합소득세 three ways the law allows: 장부 (the costs written down), 단순경비율 and 기준경비율 (소득세법 시행령 제143조
 * 제3항·제4항, the rates typed from 홈택스, 결정 W-D3), shows the lowest one that applies, adds 지방소득세, and works out
 * 부가가치세 as 일반·간이·면제.
 */
object BusinessTax {
    /** 과세 유형 for 부가가치세. */
    enum class VatType(val label: String) {
        GENERAL("일반과세자"),
        SIMPLE("간이과세자"),
        EXEMPT("면세사업자"),
    }

    /** Where the 장부 and 경비율 thresholds put a business (시행령 제208조 제5항 제2호 가·나·다목). */
    enum class Group(val bookLimit: Long, val simpleLimit: Long) {
        A(300_000_000L, 60_000_000L),
        B(150_000_000L, 36_000_000L),
        C(75_000_000L, 24_000_000L),
    }

    /** Kinds of business, each with its 장부 group and 간이과세 부가가치율 (부가가치세법 시행령 제111조). */
    enum class Industry(val label: String, val group: Group, val valueAdded: Double) {
        RETAIL("소매업", Group.A, 0.15),
        RESTAURANT("음식점업", Group.B, 0.15),
        FARM("농업·임업·어업", Group.A, 0.20),
        MANUFACTURE("제조업", Group.B, 0.20),
        LODGING("숙박업", Group.B, 0.25),
        CONSTRUCTION("건설·인테리어 시공", Group.B, 0.30),
        TRANSPORT("운수·화물·대리운전", Group.B, 0.30),
        IT("정보통신 (앱·영상 제작 등)", Group.B, 0.30),
        PROFESSIONAL("전문 서비스 (디자인·설계·컨설팅)", Group.C, 0.40),
        RENTAL("부동산 임대", Group.C, 0.40),
        EDUCATION("교육 서비스 (강사·학원)", Group.C, 0.30),
        PERSONAL("수리·개인 서비스 (미용·세탁 등)", Group.C, 0.30),
        OTHER("그 밖의 서비스", Group.C, 0.30),
    }

    /** [TAX-06] The user's own answers, kept in `tax_settings` (DB 21). */
    data class Profile(
        val vat: VatType = VatType.GENERAL,
        val industry: Industry = Industry.OTHER,
        /** 단순경비율 %, from 홈택스; null when not looked up. */
        val simpleRate: Double? = null,
        /** 기준경비율 %, from 홈택스; null when not looked up. */
        val standardRate: Double? = null,
        /** 직전 연도 수입금액; null for a new business or unknown (taken as below the lines). */
        val lastYearIncome: Long? = null,
        /** Last year's 종합소득세 (결정세액), for the 11월 중간예납; null when unknown. */
        val lastYearTax: Long? = null,
        /** 기본공제 people (본인 포함, 소득세법 제50조 연 150만 원씩). */
        val family: Int = 1,
        /** 성실사업자: 표준세액공제 12만 원 instead of 7만 원 (제59조의4 제9항 제2호). */
        val sincere: Boolean = false,
        val newBusiness: Boolean = false,
    ) {
        val bookSmall: Boolean get() = newBusiness || (lastYearIncome ?: 0) < industry.group.bookLimit
    }

    enum class Method(val label: String) {
        BOOK("장부 (적은 경비)"),
        SIMPLE("단순경비율"),
        STANDARD("기준경비율"),
    }

    data class Option(val method: Method, val income: Long, val tax: Long, val allowed: Boolean, val why: String)

    data class Year(
        val year: Int,
        /** Through [until]; the rest of the year is projected at the same pace. */
        val until: LocalDate,
        val sales: Long,
        val costs: Long,
        /** Costs with a 세금계산서·카드·현금영수증 (부가세 매입세액과 기준경비율 주요경비에 써요). */
        val proved: Long,
        val withheld: Long,
        /** The whole year at this pace. */
        val projectedSales: Long,
        val projectedCosts: Long,
        val projectedProved: Long,
        val options: List<Option>,
        val best: Option,
        /** 종합소득세 + 지방소득세 for the year, less what was already held back (3.3%). */
        val incomeTaxDue: Long,
        val vat: Long,
        val vatExempt: Boolean,
        val dues: List<Due>,
        /** F 1.32 [TAXP-07] 신용카드매출전표 등 발행세액공제 taken off [vat] (부가가치세법 제46조). */
        val cardCredit: Long = 0,
        /** F 1.32 [TAXP-07] Said when the year's pace crosses the 간이과세 line, or comes near it. */
        val lineNote: String? = null,
    ) {
        val total: Long get() = incomeTaxDue.coerceAtLeast(0) + vat.coerceAtLeast(0)
        /** What to put aside each month of the year for both. */
        val perMonth: Long get() = (total / 12) / 100 * 100

        /**
         * F 1.32 [TAXP-07] 598 세금 통장: of a day's [daySales], what to put aside for 부가세 and 종소세 at the year's
         * pace, to the 100원. Zero before any sales.
         */
        fun setAside(daySales: Long): Pair<Long, Long> {
            if (projectedSales <= 0 || daySales <= 0) return 0L to 0L
            val v = (daySales.toDouble() * vat.coerceAtLeast(0) / projectedSales).roundToLong() / 100 * 100
            val i = (daySales.toDouble() * incomeTaxDue.coerceAtLeast(0) / projectedSales).roundToLong() / 100 * 100
            return v to i
        }
    }

    /**
     * F 1.32 [TAXP-07] 발행세액공제 rate and yearly cap by the year (제46조 제1항, 부칙): 1.3%·1,000만 원 through 2026. 2027 is
     * 정부 개편안 (1.2%·500만 원 안, 2029년까지) not yet law when this was written: 확인 필요, the 2026 numbers stay until then.
     */
    @Suppress("UNUSED_PARAMETER")
    fun cardRate(year: Int): Pair<Double, Long> = 0.013 to 10_000_000L
    const val CARD_RATE_CHECKED = "2026년 1.3%·연 1,000만 원 확인. 2027년부터는 국회 통과 뒤 다시 확인 필요"

    /** 간이과세 기준 (부가가치세법 시행령 제109조, 2024-07-01부터 1억 400만 원). */
    const val SIMPLE_LINE = 104_000_000L

    /** F 1.32 [TAXP-07] The 발행세액공제 for [cardSales] (card and 배달앱 sales, VAT in), within the cap and the VAT itself. */
    fun cardCredit(p: Profile, year: Int, cardSales: Long, vat: Long): Long {
        if (p.vat == VatType.EXEMPT || (p.lastYearIncome ?: 0) > 1_000_000_000L) return 0
        val (rate, cap) = cardRate(year)
        return minOf((cardSales * rate).roundToLong(), cap, vat.coerceAtLeast(0))
    }

    /** [TAX-06] One payment ahead: when, what, and how much when it can be told. */
    data class Due(val date: LocalDate, val name: String, val amount: Long?, val note: String)

    /** Proofs that carry 부가세 매입세액 (세금계산서·신용카드·현금영수증); a 간이영수증 does not (조문 대조는 확인 필요, 조사 2-3). */
    val VAT_PROOFS = setOf("세금계산서", "카드 영수증", "현금영수증")

    /**
     * The year [year] from the money written down until [today] (or the whole year once it is over). [jobs] and
     * [sales] are the field work and shop days; [entries] the ledger (its 지출 are costs).
     */
    fun year(year: Int, today: LocalDate, profile: Profile, jobs: List<Job>, sales: List<DailySales>, entries: List<MoneyEntry>): Year {
        val until = if (today.year > year) LocalDate.of(year, 12, 31) else today
        val j = jobs.filter { it.date.year == year && !it.cancelled && !it.date.isAfter(until) }
        val s = sales.filter { it.date.year == year && !it.date.isAfter(until) }
        val spent = entries.filter { it.date.year == year && it.kind == MoneyKind.EXPENSE && !it.date.isAfter(until) }
        val income = j.sumOf { it.amount } + s.sumOf { it.sales }
        val costs = spent.sumOf { it.amount } + j.sumOf { it.costs } + s.sumOf { it.costs }
        val proved = spent.filter { it.proof in VAT_PROOFS }.sumOf { it.amount }
        val withheld = j.sumOf { it.withheld }
        // 1.36 실사용 S16 (3.3% 떼고 받은 현장 일감에 부가세 87,834원): pay with 3.3% taken off is a 인적용역's, free of 부가세
        // (부가가치세법 제26조 제1항 제15호), whatever the user's 과세 type says for the rest.
        val vatFree = j.filter { it.withheld > 0 }.sumOf { it.amount }
        return of(year, until, profile, income, costs, proved, withheld, cardSales = s.sumOf { it.card + it.delivery }, vatFree = vatFree)
    }

    /** The year from totals so far; projected to the year's end at the pace of the days gone. */
    fun of(year: Int, until: LocalDate, profile0: Profile, sales: Long, costs: Long, proved: Long, withheld: Long, cardSales: Long = 0, vatFree: Long = 0): Year {
        // Income that is all 부가세-free counts as a 면세 one's; part of it, and only the rest carries 부가세.
        val profile = if (sales > 0 && vatFree >= sales) profile0.copy(vat = VatType.EXEMPT) else profile0
        val start = LocalDate.of(year, 1, 1)
        val gone = (until.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1).toDouble()
        val days = start.lengthOfYear().toDouble()
        val f = if (gone >= days) 1.0 else days / gone
        val ps = (sales * f).roundToLong()
        val pc = (costs * f).roundToLong()
        val pp = (proved * f).roundToLong()
        val pw = (withheld * f).roundToLong()
        val pf = (vatFree.coerceAtMost(sales) * f).roundToLong()
        // 일반과세자 wrote 매출 with 부가세 in it: 수입금액 is the 공급가액. 간이·면세 keep the whole amount.
        val revenue = if (profile.vat == VatType.GENERAL) (ps - pf) * 10 / 11 + pf else ps
        val bookCosts = if (profile.vat == VatType.GENERAL) pc - pp / 11 else pc
        val options = options(profile, revenue, bookCosts, if (profile.vat == VatType.GENERAL) pp * 10 / 11 else pp)
        val best = options.filter { it.allowed }.minBy { it.tax }
        val vat0 = vat(profile, ps - pf, pp)
        val card = if (vat0.second) 0L else cardCredit(profile, year, (cardSales * f).roundToLong(), vat0.first)
        val vat = (vat0.first - card) to vat0.second
        val due = best.tax - pw
        val line = when {
            profile.vat == VatType.SIMPLE && ps >= SIMPLE_LINE -> "이대로면 올해 매출이 간이과세 기준(1억 400만 원)을 넘어요. 다음 해 7월부터 일반과세자가 될 수 있어요"
            profile.vat == VatType.SIMPLE && ps >= SIMPLE_LINE * 9 / 10 -> "올해 매출이 간이과세 기준(1억 400만 원)에 가까워요"
            else -> null
        }
        return Year(year, until, sales, costs, proved, withheld, ps, pc, pp, options, best, due, vat.first, vat.second,
            dues(year, profile, due, vat.first, ps - pf, pp), card, line)
    }

    private fun options(p: Profile, revenue: Long, costs: Long, proved: Long): List<Option> {
        val book = (revenue - costs).coerceAtLeast(0)
        val simpleOk = p.simpleRate != null && (p.newBusiness || (p.lastYearIncome ?: 0) < p.industry.group.simpleLimit) &&
            revenue < p.industry.group.bookLimit
        val simple = p.simpleRate?.let { (revenue * (1 - it / 100)).roundToLong().coerceAtLeast(0) }
        val standard = p.standardRate?.let { r ->
            val rate = if (p.bookSmall) r else r / 2
            val base = (revenue - proved - (revenue * rate / 100).roundToLong()).coerceAtLeast(0)
            // 시행령 제143조 제3항 제1호 단서, 시행규칙 제67조: at most 단순경비율 소득 × 2.8 (간편장부대상자) or 3.4.
            simple?.let { minOf(base, (it * if (p.bookSmall) 2.8 else 3.4).roundToLong()) } ?: base
        }
        return listOfNotNull(
            Option(Method.BOOK, book, tax(book, p), true, "적은 매출에서 적은 경비를 뺐어요"),
            simple?.let { Option(Method.SIMPLE, it, tax(it, p), simpleOk,
                if (simpleOk) "매출의 ${pct(p.simpleRate!!)}%를 경비로 봐요" else "직전 연도 수입이 ${won(p.industry.group.simpleLimit)} 이상이면 못 써요") },
            standard?.let { Option(Method.STANDARD, it, tax(it, p), true, "증빙 있는 경비 + 매출의 ${pct(p.standardRate!!)}%") },
        )
    }

    /** 종합소득세 + 지방소득세 on [income]: 기본공제, 제55조 세율, 표준세액공제 (제59조의4 제9항). */
    fun tax(income: Long, p: Profile): Long {
        val base = (income - 1_500_000L * p.family.coerceAtLeast(1)).coerceAtLeast(0)
        val credit = if (p.sincere) 120_000L else 70_000L
        val t = (rate(base) - credit).coerceAtLeast(0)
        return t + t / 10
    }

    /** 소득세법 제55조 제1항 (2026-01-01 시행). */
    fun rate(base: Long): Long {
        val b = base.toDouble()
        val t = when {
            b <= 14_000_000 -> b * 0.06
            b <= 50_000_000 -> 840_000 + (b - 14_000_000) * 0.15
            b <= 88_000_000 -> 6_240_000 + (b - 50_000_000) * 0.24
            b <= 150_000_000 -> 15_360_000 + (b - 88_000_000) * 0.35
            b <= 300_000_000 -> 37_060_000 + (b - 150_000_000) * 0.38
            b <= 500_000_000 -> 94_060_000 + (b - 300_000_000) * 0.40
            b <= 1_000_000_000 -> 174_060_000 + (b - 500_000_000) * 0.42
            else -> 384_060_000 + (b - 1_000_000_000) * 0.45
        }
        return t.toLong()
    }

    /** 간이과세 납부 면제 (부가가치세법 제69조 제1항). */
    const val SIMPLE_EXEMPT = 48_000_000L

    /** The year's 부가세 and whether it is 면제: 일반 10/110 out less in; 간이 ×부가가치율×10% less 0.5% of proved. */
    fun vat(p: Profile, sales: Long, proved: Long): Pair<Long, Boolean> = when (p.vat) {
        VatType.EXEMPT -> 0L to true
        VatType.GENERAL -> (sales * 10 / 110 - proved * 10 / 110) to false
        VatType.SIMPLE -> if (sales < SIMPLE_EXEMPT) 0L to true
            else ((sales * p.industry.valueAdded * 0.10).roundToLong() - proved * 5 / 1000).coerceAtLeast(0) to false
    }

    /** [TAX-06] The payments from now to the next 5월: 부가세 by type, 중간예납 and the 확정신고. */
    fun dues(year: Int, p: Profile, incomeTax: Long, vat: Long, sales: Long, proved: Long): List<Due> {
        val w = TaxDeadlines::workday
        val list = mutableListOf<Due>()
        when (p.vat) {
            VatType.GENERAL -> {
                val half = vat / 2
                list += Due(w(LocalDate.of(year, 4, 25)), "부가세 예정 고지", null, "세무서가 지난 하반기 부가세의 절반을 고지해요 (부가가치세법 제48조 제3항)")
                list += Due(w(LocalDate.of(year, 7, 25)), "부가세 1기 확정", half.coerceAtLeast(0), "1~6월 몫. 4월에 낸 예정 고지분은 빼고 내요")
                list += Due(w(LocalDate.of(year, 10, 25)), "부가세 예정 고지", null, "세무서가 1기 부가세의 절반을 고지해요")
                list += Due(w(LocalDate.of(year + 1, 1, 25)), "부가세 2기 확정", (vat - half).coerceAtLeast(0), "7~12월 몫. 10월에 낸 예정 고지분은 빼고 내요")
            }
            VatType.SIMPLE -> list += Due(w(LocalDate.of(year + 1, 1, 25)), "간이과세 부가세 신고", vat.coerceAtLeast(0),
                if (sales < SIMPLE_EXEMPT) "1년 매출이 4,800만 원보다 적으면 신고만 하고 내지 않아요" else "1~12월 몫을 한 번에 내요 (부가가치세법 제67조)")
            VatType.EXEMPT -> list += Due(LocalDate.of(year + 1, 2, 10), "면세사업자 사업장현황신고", 0L, "매출·경비를 신고해요. 낼 세금은 없어요 (소득세법 제78조)")
        }
        if (!p.newBusiness) list += Due(w(LocalDate.of(year, 11, 30)), "종합소득세 중간예납", p.lastYearTax?.let { it / 2 / 1000 * 1000 },
            "지난해 종합소득세의 절반을 11월에 고지해요 (소득세법 제65조)" + if (p.lastYearTax == null) ". 지난해 세금을 적으면 금액이 나와요" else "")
        val prepaid = if (p.newBusiness) 0L else p.lastYearTax?.let { it / 2 / 1000 * 1000 } ?: 0L
        list += Due(w(LocalDate.of(year + 1, 5, 31)), "종합소득세 확정신고", (incomeTax - prepaid).coerceAtLeast(0),
            "올해 소득을 다음 해 5월에 신고·납부해요 (소득세법 제70조)" + (if (prepaid > 0) ". 11월 중간예납분을 뺐어요" else "") +
                // Withheld 3.3% or 중간예납 above the year's tax comes back after this 신고.
                if (incomeTax - prepaid < 0) ". 미리 낸 돈이 더 많아 약 ${won(prepaid - incomeTax)}을 돌려받아요" else "")
        return list.sortedBy { it.date }
    }

    private fun pct(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)
    private fun won(v: Long): String = "%,d원".format(v)

    /** Said under every number. */
    const val NOT_ADVICE = "기록한 매출·경비로 어림한 참고용이에요. 세무 조언이 아니고, 실제 세금은 신고 방법·공제·감면에 따라 달라요. " +
        "장부를 쓰지 않으면 무기장 가산세가 붙을 수 있어요. 홈택스나 국세상담센터(126)에서 확인해 주세요."
}
