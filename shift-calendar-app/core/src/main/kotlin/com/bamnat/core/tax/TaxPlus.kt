package com.bamnat.core.tax

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.CardCycle
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyAccounts
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.TaxDeadlines
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.WithholdingTable
import com.bamnat.core.report.ReportDoc
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * F 1.32 세금·리포트 ([TAXP-01]..[TAXP-11], 계획 1.32 592~602, 조사 `조사/1.32/가계부-하이엔드-조사.md` 3~5장). All of it is
 * worked out on the phone from what is already in the calendar; nothing new leaves it (결정: 서버로 가는 것은 늘지 않아요).
 */
object TaxPlus {
    private fun won(v: Long) = "%,d원".format(v)

    // ---- [TAXP-01] 592 실수령 on the summary ----

    /** Where a month's 실수령 came from, best first: the payslip, the money that came, the user's own word, the app's guess. */
    enum class NetSource(val label: String) {
        PAYSLIP("명세서"),
        DEPOSIT("입금"),
        TYPED("직접 적음"),
        ESTIMATE("추정"),
    }

    data class NetLine(val month: YearMonth, val gross: Long?, val net: Long, val source: NetSource) {
        /** Only the app's own guess is 프로 (결정 1.32: the user's own numbers are theirs, free). */
        val estimated: Boolean get() = source == NetSource.ESTIMATE
        val deducted: Long? get() = gross?.let { (it - net).coerceAtLeast(0) }
    }

    /**
     * [month]'s 세전 and 세후 for one line: a payslip read for it, else the pay deposits seen for it (added up, a month can
     * come in two), else a typed record, else the [forecast]. The 세전 is the record's own, else the pay engine's [gross].
     */
    fun netLine(month: YearMonth, records: List<PayObservation>, gross: Long?, forecast: TaxLearner.Forecast?): NetLine? {
        val mine = records.filter { it.payMonth == month }
        mine.filter { it.source == PayObservation.Source.PAYSLIP }.maxByOrNull { it.date }?.let { return NetLine(month, it.gross ?: gross, it.net, NetSource.PAYSLIP) }
        mine.filter { it.source == PayObservation.Source.DEPOSIT }.takeIf { it.isNotEmpty() }?.let { d ->
            return NetLine(month, gross, d.sumOf { it.net }, NetSource.DEPOSIT)
        }
        mine.filter { it.source == PayObservation.Source.TYPED }.maxByOrNull { it.date }?.let { return NetLine(month, it.gross ?: gross, it.net, NetSource.TYPED) }
        return forecast?.let { NetLine(month, it.gross, it.net, NetSource.ESTIMATE) }
    }

    /** "세전 3,000,000원 · 세후 2,640,000원 (입금)" */
    fun netText(l: NetLine): String = listOfNotNull(l.gross?.let { "세전 ${won(it)}" }, "세후 ${won(l.net)} (${l.source.label})").joinToString(" · ")

    // ---- [TAXP-02] 593 세금 설명 ----

    data class Step(val label: String, val amount: Long, val how: String)

    /**
     * How each 4대 보험 and the 소득세 of [taxable] (비과세 뺀 월급) come about on [on]: the rate and its base, and for the
     * 소득세 the 간이세액표 row the pay falls in and the column of [family], less the 자녀 amount.
     */
    fun explain(taxable: Long, family: Int, children: Int, on: LocalDate): List<Step> {
        val r = TakeHome.estimate(taxable, com.bamnat.core.pay.TaxMode.INSURANCE_4, family, on, children)
        val month = YearMonth.from(on)
        return r.deductions.map { (name, amount) ->
            val how = if (name == TakeHome.TAX_LABEL) bracket(taxable, family, children, on) else com.bamnat.core.pay.SlipAudit.explain(name, taxable, month)
            Step(name, amount, how)
        }
    }

    /** "월급 3,000천~3,020천원 줄, 가족 1명 칸: 74,350원. 자녀 1명 −20,830원" */
    fun bracket(taxable: Long, family: Int, children: Int, on: LocalDate): String {
        val t = WithholdingTable.on(on) ?: return "근로소득 간이세액표로 정해요"
        val f = family.coerceAtLeast(1)
        val head = if (taxable >= com.bamnat.core.pay.WithholdingYear.TOP) "월급 1,000만 원이 넘어 표 아래 계산식을 써요(1,000만 원 칸 + 넘는 몫 × 세율)"
        else t.rows.lastOrNull { taxable >= it[0] * 1_000 }?.let { row ->
            "월급 ${"%,d".format(row[0])}천~${"%,d".format(row[1])}천원 줄, 공제대상가족 ${f}명 칸: ${won(t.tax(taxable, f))}"
        } ?: "월급이 표의 첫 줄보다 적어 소득세가 없어요"
        val kids = if (children > 0) ". 8~20세 자녀 ${children}명 −${won(t.tax(taxable, f) - t.tax(taxable, f, children))}" else ""
        return "$head$kids (${t.source}). 회사가 80·100·120% 중 고를 수 있고, 1년치는 연말정산에서 맞춰요."
    }

    // ---- [TAXP-04] 595 다음 달 카드값 ----

    data class CardForecast(val card: MoneyAccount, val payDate: LocalDate, val now: Long, val projected: Long, val installments: Long, val open: Boolean)

    /**
     * Each credit card's next bill not yet paid: what is on it now ([CardCycle.bill], 할부 회차 included) and, while its
     * period is still open, the rest of the period at the pace of the days gone (할부 left out of the pace).
     */
    fun cardForecast(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, today: LocalDate): List<CardForecast> =
        accounts.filter { it.hasBill && !it.hidden }.flatMap { card ->
            // The bill still to pay and the one this month's spending is going on, when they differ.
            generateSequence(YearMonth.from(today)) { it.plusMonths(1) }.take(3).filter { m ->
                !CardCycle.payDate(card, m).isBefore(today) && !CardCycle.paid(card, m, entries) && !CardCycle.period(card, m).first.isAfter(today)
            }.mapNotNull { m ->
                val bill = CardCycle.bill(card, m, entries, accounts)
                val (from, to) = CardCycle.period(card, m)
                val inst = entries.filter { it.kind == MoneyKind.EXPENSE && it.installments > 1 && MoneyAccounts.of(it, accounts) == card.id }
                    .sumOf { CardCycle.portion(it, CardCycle.billMonth(card, it.date), m) }
                val open = !today.isAfter(to)
                val projected = if (!open) bill.amount else {
                    val gone = ChronoUnit.DAYS.between(from, today) + 1
                    val left = ChronoUnit.DAYS.between(today, to)
                    bill.amount + ((bill.amount - inst).toDouble() / gone * left).roundToLong()
                }
                if (bill.amount <= 0 && projected <= 0) null else CardForecast(card, CardCycle.payDate(card, m), bill.amount, projected, inst, open)
            }.toList()
        }.sortedBy { it.payDate }

    // ---- [TAXP-05] 596 근무별 소비 ----

    data class ShiftSpend(val label: String, val days: Int, val total: Long) {
        val perDay: Long get() = if (days == 0) 0 else total / days
    }

    /**
     * Spending per day by what the day was ([dayLabel]: a shift's name, "쉬는 날", or "야간 다음 날" which the caller
     * gives for the day after a night): over [from]..[to], every day counted even with nothing spent. Labels seen on
     * fewer than [MIN_DAYS] days are left out.
     */
    fun byShift(entries: List<MoneyEntry>, from: LocalDate, to: LocalDate, dayLabel: (LocalDate) -> String?): List<ShiftSpend> {
        val spent = entries.filter { it.kind == MoneyKind.EXPENSE && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val days = HashMap<String, Int>(); val total = HashMap<String, Long>()
        var d = from
        while (!d.isAfter(to)) {
            dayLabel(d)?.let { l -> days.merge(l, 1, Int::plus); total.merge(l, spent[d] ?: 0, Long::plus) }
            d = d.plusDays(1)
        }
        return days.filter { it.value >= MIN_DAYS }.map { (l, n) -> ShiftSpend(l, n, total[l] ?: 0) }.sortedByDescending { it.perDay }
    }

    const val MIN_DAYS = 3
    const val AFTER_NIGHT = "야간 다음 날"
    const val OFF = "쉬는 날"

    /** What a day was for [byShift]: after a night shift, a day off, or the shift's name; null with nothing on the calendar. */
    fun dayLabel(resolve: (LocalDate) -> com.bamnat.core.schedule.ResolvedDay): (LocalDate) -> String? = { d ->
        val t = runCatching { resolve(d).shiftType }.getOrNull()
        val before = runCatching { resolve(d.minusDays(1)).shiftType }.getOrNull()
        when {
            com.bamnat.core.schedule.DutyCheck.isNight(before) && !com.bamnat.core.schedule.DutyCheck.isNight(t) -> AFTER_NIGHT
            t == null || t.isOff -> OFF
            else -> t.name
        }
    }

    /** One sentence on the biggest gap: "야간 다음 날은 쉬는 날보다 하루 12,300원 더 써요". */
    fun shiftWords(rows: List<ShiftSpend>): String? {
        if (rows.size < 2) return null
        val hi = rows.first(); val lo = rows.last()
        val gap = hi.perDay - lo.perDay
        return if (gap < 1_000) null else "${hi.label}은 ${lo.label}보다 하루 ${won(gap)} 더 써요"
    }

    /** Months with more 연장 (top half by minutes) against the rest, by spending: (많은 달 평균, 나머지 평균), null under 4 months. */
    fun overtimeMonths(spent: Map<YearMonth, Long>, overtime: Map<YearMonth, Int>): Pair<Long, Long>? {
        val ms = spent.keys.filter { it in overtime }.sortedByDescending { overtime[it] ?: 0 }
        if (ms.size < 4) return null
        val hi = ms.take(ms.size / 2); val lo = ms.drop(ms.size / 2)
        return hi.map { spent[it] ?: 0 }.average().roundToLong() to lo.map { spent[it] ?: 0 }.average().roundToLong()
    }

    // ---- [TAXP-06] 597 매출 나누기 ----

    data class SalesSplit(val card: Long, val cash: Long, val transfer: Long, val delivery: Long, val unsplit: Long, val days: Int, val credit: Long = 0) {
        val total: Long get() = card + cash + transfer + delivery + credit + unsplit
        fun share(v: Long): Int = if (total <= 0) 0 else (v * 100 / total).toInt()
        /** (이름, 금액) with nothing at 0. */
        val rows: List<Pair<String, Long>> get() = listOf("카드" to card, "현금" to cash, "이체" to transfer, "배달앱" to delivery, "외상" to credit, "나누지 않음" to unsplit).filter { it.second > 0 }
    }

    /** [creditOn]: 1.36 실사용 S20, what of a day's sales was written as 외상, shown as its own row. */
    fun salesSplit(sales: List<DailySales>, from: LocalDate, to: LocalDate, creditOn: (LocalDate) -> Long = { 0 }): SalesSplit {
        val s = sales.filter { !it.date.isBefore(from) && !it.date.isAfter(to) && it.sales > 0 }
        fun rest(d: DailySales) = (d.sales - d.byMethod).coerceAtLeast(0)
        fun credit(d: DailySales) = creditOn(d.date).coerceIn(0, rest(d))
        return SalesSplit(s.sumOf { it.card }, s.sumOf { it.cash }, s.sumOf { it.transfer }, s.sumOf { it.delivery },
            s.sumOf { rest(it) - credit(it) }, s.size, s.sumOf(::credit))
    }

    // ---- [TAXP-08] 599 카드 매출 입금 대조 ----

    /** Names a card company's settlement deposit carries. */
    private val CARD_PAYERS = Regex("카드|KB국민|신한|삼성|현대|롯데|하나|우리|비씨|BC|NH|농협|씨티|카카오페이|네이버페이|토스페이먼츠|배달의민족|배민|쿠팡이츠|요기요")

    data class CardMatch(val date: LocalDate, val card: Long, val paid: Long, val deposits: List<MoneyEntry>, val late: Boolean,
                         /** 1.32 [TAXP-15] What should come in after the fee the user set; [card] when none was set. */
                         val expected: Long = card) {
        val fee: Long get() = (card - paid).coerceAtLeast(0)
        val matched: Boolean get() = paid > 0 && !late
        /** 1.32 [TAXP-15] Taken beyond the set fee: worth a look at the card company's 가맹점 page. */
        val overFee: Long get() = if (matched && expected < card) (expected - paid).coerceAtLeast(0) else 0
    }

    /** 1.32 [TAXP-15] Below this, a difference against the set fee is rounding and 부가세 on the fee, not news. */
    const val OVER_FEE_NOTE = 1_000L

    /**
     * The card (and 배달앱) sales of each day against the settlement deposits that followed: deposits from a card or
     * delivery company within [WINDOW] working days after the day are taken in order until they reach [MIN_SHARE] of the
     * day's card sales. A day already marked 입금 확인 is left out. A day with nothing found after the window is late.
     */
    fun cardDeposits(sales: List<DailySales>, entries: List<MoneyEntry>, today: LocalDate, feeBp: Int = 0): List<CardMatch> {
        val pool = entries.filter { it.kind == MoneyKind.INCOME && CARD_PAYERS.containsMatchIn(it.merchant + " " + it.note) }
            .sortedBy { it.date }.toMutableList()
        val out = mutableListOf<CardMatch>()
        for (s in sales.filter { it.card + it.delivery > 0 && !it.cardSettled && it.date.isBefore(today) }.sortedBy { it.date }) {
            val want = s.card + s.delivery
            val end = workdays(s.date, WINDOW)
            val taken = mutableListOf<MoneyEntry>()
            var sum = 0L
            for (e in pool.toList()) {
                if (!e.date.isAfter(s.date) || e.date.isAfter(end)) continue
                if (sum + e.amount > want + 1_000) continue
                taken += e; sum += e.amount; pool.remove(e)
                if (sum >= want * MIN_SHARE) break
            }
            if (sum < want * MIN_SHARE) { pool.addAll(taken); pool.sortBy { it.date }; taken.clear(); sum = 0 }
            out += CardMatch(s.date, want, sum, taken, late = sum == 0L && today.isAfter(end),
                expected = want - (want * feeBp.coerceIn(0, 1_000) / 10_000))
        }
        return out
    }

    const val WINDOW = 5
    const val MIN_SHARE = 0.9

    private fun workdays(from: LocalDate, n: Int): LocalDate {
        var d = from; var k = 0
        while (k < n) { d = d.plusDays(1); if (TaxDeadlines.workday(d) == d) k++ }
        return d
    }

    // ---- [TAXP-09] 600 연간 돈 리포트 ----

    /** The year's ledger as CSV (free): date, kind, category, place, amount, account, memo. */
    fun yearCsv(year: Int, entries: List<MoneyEntry>, accounts: List<MoneyAccount>): String {
        val names = accounts.associate { it.id to it.label }
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val head = "날짜,구분,분류,가게,금액,결제수단,메모"
        val rows = entries.filter { it.date.year == year }.sortedBy { it.date }.map { e ->
            val kind = when (e.kind) { MoneyKind.EXPENSE -> "지출"; MoneyKind.INCOME -> "수입"; MoneyKind.TRANSFER -> "이체" }
            listOf(e.date.toString(), kind, q(e.category), q(e.merchant), e.amount.toString(), q(MoneyAccounts.of(e, accounts)?.let(names::get) ?: ""), q(e.note)).joinToString(",")
        }
        return (listOf(head) + rows).joinToString("\n")
    }

    /**
     * The year's money as a PDF (프로): month by month in and out, by category, by way of paying, by shift when given,
     * and the totals the 연말정산 asks for. Names of places are kept out of the summary tables; only amounts.
     */
    fun yearDoc(year: Int, entries: List<MoneyEntry>, accounts: List<MoneyAccount>, shifts: List<ShiftSpend> = emptyList(),
                extras: com.bamnat.core.pay.YearEndPreview.Extras? = null): ReportDoc {
        val lines = entries.filter { it.date.year == year }
        val out = lines.filter { it.kind == MoneyKind.EXPENSE }
        val inn = lines.filter { it.kind == MoneyKind.INCOME }
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOf("들어온 돈" to won(inn.sumOf { it.amount }), "나간 돈" to won(out.sumOf { it.amount }),
            "남은 돈" to won(inn.sumOf { it.amount } - out.sumOf { it.amount }), "기록" to "${lines.size}건"))
        blocks += ReportDoc.Heading("달마다")
        blocks += ReportDoc.Table(listOf("달", "들어온 돈", "나간 돈", "남은 돈"), (1..12).mapNotNull { m ->
            val i = inn.filter { it.date.monthValue == m }.sumOf { it.amount }; val o = out.filter { it.date.monthValue == m }.sumOf { it.amount }
            if (i == 0L && o == 0L) null else listOf("${m}월", won(i), won(o), won(i - o))
        }, listOf(1f, 2f, 2f, 2f), right = setOf(1, 2, 3))
        blocks += ReportDoc.Heading("분류별 나간 돈")
        val total = out.sumOf { it.amount }.coerceAtLeast(1)
        blocks += ReportDoc.Table(listOf("분류", "금액", "비율"), out.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }
            .sortedByDescending { it.second }.map { (c, v) -> listOf(c, won(v), "${v * 100 / total}%") }, listOf(2f, 2f, 1f), right = setOf(1, 2))
        blocks += ReportDoc.Heading("결제수단별")
        val byId = accounts.associateBy { it.id }
        blocks += ReportDoc.Table(listOf("결제수단", "금액"), out.groupBy { MoneyAccounts.of(it, accounts)?.let(byId::get)?.type }
            .map { (t, l) -> (t?.label ?: "정하지 않음") to l.sumOf { it.amount } }.sortedByDescending { it.second }
            .map { listOf(it.first, won(it.second)) }, listOf(2f, 2f), right = setOf(1))
        if (shifts.isNotEmpty()) {
            blocks += ReportDoc.Heading("근무별 하루 소비")
            blocks += ReportDoc.Table(listOf("날", "날 수", "하루 평균"), shifts.map { listOf(it.label, "${it.days}일", won(it.perDay)) }, listOf(2f, 1f, 2f), right = setOf(1, 2))
        }
        extras?.let { x ->
            blocks += ReportDoc.Heading("연말정산에 쓰는 합계 (12월까지 어림)")
            blocks += ReportDoc.Facts(listOf("신용카드" to won(x.credit), "체크카드·현금영수증·페이" to won(x.debit), "의료비" to won(x.medical),
                "교육비" to won(x.education), "보장성보험료" to won(x.insurance), "기부금" to won(x.donation), "월세" to won(x.rent)))
            blocks += ReportDoc.Paragraph("가계부에 적은 것으로 어림했어요. 실제 공제는 홈택스 간소화 자료로 확인해 주세요.", small = true)
        }
        return ReportDoc("${year}년 가계부 리포트", blocks, footer = "밤낮 · ${year}년 가계부 리포트")
    }

    // ---- [TAXP-10] 601 프리랜서 3.3%와 5월 신고 ----

    data class Payer(val name: String, val count: Int, val amount: Long, val withheld: Long)

    data class FreelanceYear(val year: Int, val payers: List<Payer>, val costs: Long, val tax: BusinessTax.Year?) {
        val amount: Long get() = payers.sumOf { it.amount }
        val withheld: Long get() = payers.sumOf { it.withheld }
        /** Positive: money back after the 5월 신고 (3.3% held more than the year's tax). */
        val refund: Long? get() = tax?.let { -it.incomeTaxDue }
    }

    /**
     * The year's 3.3% work: jobs with 원천징수 by client, and 프리랜서 pay records (세전 − 실수령) by month; [tax] is the
     * 사업자 estimate when the calendar has one, so the 5월 refund or payment shows.
     */
    fun freelance(year: Int, jobs: List<Job>, records: List<PayObservation>, entries: List<MoneyEntry>, tax: BusinessTax.Year?): FreelanceYear {
        val fromJobs = jobs.filter { it.date.year == year && it.withholding && !it.cancelled }.groupBy { it.client.ifBlank { it.title } }
            .map { (n, l) -> Payer(n, l.size, l.sumOf { it.amount }, l.sumOf { it.withheld }) }
        val fromPay = records.filter { it.payMonth.year == year && it.kind == IncomeKind.FREELANCE && it.gross != null }
            .takeIf { it.isNotEmpty() }?.let { l -> listOf(Payer("월급처럼 받은 3.3%", l.size, l.sumOf { it.gross!! }, l.sumOf { it.deducted ?: 0 })) } ?: emptyList()
        val costs = entries.filter { it.date.year == year && it.kind == MoneyKind.EXPENSE && it.proof in BusinessTax.VAT_PROOFS }.sumOf { it.amount }
        return FreelanceYear(year, (fromJobs + fromPay).sortedByDescending { it.amount }, costs, tax)
    }

    /** The 5월 신고 준비 묶음: who paid what, what was held, the costs with proof, the estimate, and what to bring. */
    fun mayPack(f: FreelanceYear): ReportDoc {
        val blocks = mutableListOf<ReportDoc.Block>()
        blocks += ReportDoc.Facts(listOf("받기로 한 돈" to won(f.amount), "뗀 3.3%" to won(f.withheld), "증빙 있는 경비" to won(f.costs)) +
            listOfNotNull(f.refund?.let { if (it >= 0) "5월에 돌려받을 돈 (어림)" to won(it) else "5월에 더 낼 돈 (어림)" to won(-it) }))
        blocks += ReportDoc.Heading("일을 준 곳")
        blocks += ReportDoc.Table(listOf("곳", "건수", "금액", "뗀 3.3%"), f.payers.map { listOf(it.name, "${it.count}", won(it.amount), won(it.withheld)) },
            listOf(3f, 1f, 2f, 2f), right = setOf(1, 2, 3), total = listOf("합계", "${f.payers.sumOf { it.count }}", won(f.amount), won(f.withheld)))
        blocks += ReportDoc.Heading("5월에 준비할 것")
        blocks += ReportDoc.Paragraph("1. 홈택스(손택스) › 종합소득세 신고 › 지급명세서 조회로 위 금액과 맞는지 봐요.\n" +
            "2. 경비 증빙(카드 영수증, 현금영수증, 세금계산서)을 모아요.\n" +
            "3. 장부·단순경비율·기준경비율 중 세금이 적은 방법을 골라요. 수입이 적으면 모두채움 안내문이 와요.\n" +
            "4. 신고·납부는 5월 31일까지예요(주말이면 다음 평일). 돌려받는 돈은 보통 6월 말에 들어와요.")
        blocks += ReportDoc.Paragraph(BusinessTax.NOT_ADVICE, small = true)
        return ReportDoc("${f.year}년 3.3% 소득 · 5월 신고 준비", blocks, footer = "밤낮 · 참고용")
    }

    // ---- [TAXP-11] 602 요율 원문 확인 ----

    data class Source(val name: String, val value: String, val where: String, val checked: Boolean)

    /** What each rate the app uses says and where it was checked (2026-10-28); unchecked ones say 확인 필요 on screen. */
    val SOURCES: List<Source> = listOf(
        Source("건강보험료율", "7.19% (근로자 3.595%)", "국민건강보험공단 2026년 달라지는 제도 안내", true),
        Source("장기요양보험료율", "0.9448% (건강보험료의 13.14%)", "국민건강보험공단 2026년 안내", true),
        Source("국민연금 보험료율", "9.5% (근로자 4.75%)", "국민연금법 개정 2026-01-01 시행", true),
        Source("고용보험료율", "근로자 0.9%", "고용보험 및 산재보험의 보험료징수 등에 관한 법률 시행령", true),
        Source("근로소득 간이세액표", "2026-03-01 이후 원천징수분", "소득세법 시행령 별표 2 (2026-02-27 개정)", true),
        Source("신용카드 발행세액공제", BusinessTax.CARD_RATE_CHECKED, "부가가치세법 제46조", false),
        Source("간이과세 기준", "1억 400만 원", "부가가치세법 시행령 제109조 (2024-07-01)", true),
        Source("신용카드 소득공제 전통시장·대중교통", "40%로 계산 (대중교통 80% 기간 따로)", "조세특례제한법 제126조의2", false),
    )

    fun sourceLine(s: Source): String = "${s.name}: ${s.value} · ${s.where}" + if (s.checked) "" else " (확인 필요)"

    /** Accounts kind, for the ledger cards that need it. */
    fun isCredit(e: MoneyEntry, accounts: List<MoneyAccount>): Boolean =
        MoneyAccounts.of(e, accounts)?.let { id -> accounts.firstOrNull { it.id == id }?.type == AccountType.CREDIT } ?: false
}
