package com.bamnat.core.invest

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * [INV-03] The fee and tax a trade most likely paid, offered in the trade sheet and always editable:
 * the account's own fee rate, else the calendar's 기본 수수료 ([INV-38], 0.05%), and, on selling
 * Korean shares, 증권거래세 0.20% (2026: 코스피 0.05% + 농어촌특별세 0.15%, 코스닥 0.20%). Korean ETFs
 * pay no 거래세.
 */
object InvestFees {
    data class Suggestion(val fee: Double, val tax: Double)

    fun suggest(market: Market, side: Side, quantity: Double, price: Double, account: InvestAccount?, settings: InvestSettings? = null): Suggestion {
        val gross = quantity * price
        if (gross <= 0) return Suggestion(0.0, 0.0)
        val rate = rateFor(market, side, account, settings)
        val fee = round(gross * rate / 100, market)
        // 거래세 is cut to the won below, as brokers do.
        val tax = if (side == Side.SELL && market.sellTaxRate > 0) floor(gross * market.sellTaxRate / 100) else 0.0
        return Suggestion(fee, tax)
    }

    /**
     * [INV-33][INV-38] The fee rate in percent: the account's own fee for this market, else its one fee, else the
     * calendar's 기본 수수료 for a buy or a sell (기본 0.05%, 결정 D73), else the market's usual one.
     */
    fun rateFor(market: Market, side: Side, account: InvestAccount?, settings: InvestSettings?): Double =
        account?.ownFee(market) ?: settings?.feeRate(side) ?: market.defaultFeeRate

    /** [INV-38] "0.05" for 0.050%. */
    fun rateText(rate: Double): String = "%.4f".format(rate).trimEnd('0').trimEnd('.')

    /**
     * [INV-38] The fee and tax the trade sheet shows (사용자 U17: 매도 단가를 넣어도 세금이 안 채워짐). What the user typed in
     * this sheet wins; a trade being fixed keeps its stored costs only while nothing that decides them changed (and
     * they were not left at 0, as a trade from a 체결 알림 without a price was); otherwise the suggestion, worked out
     * again from the quantity, price, market, side and account as they are now.
     */
    fun shown(start: Trade?, typed: Suggestion?, market: Market, side: Side, quantity: Double, price: Double, accountId: Long, suggestion: Suggestion): Suggestion {
        if (typed != null) return typed
        val unchanged = start != null && start.quantity == quantity && start.price == price && start.market == market &&
            start.side == side && start.accountId == accountId
        return if (unchanged && (start!!.fee != 0.0 || start.tax != 0.0)) Suggestion(start.fee, start.tax) else suggestion
    }

    /** Won are whole; dollars keep cents. */
    fun round(v: Double, market: Market): Double = if (market.foreign) Math.round(v * 100) / 100.0 else floor(v)

    /** [INV-08] Units a plan's amount buys at [price]: whole shares, or coins to eight decimals. */
    fun unitsFor(amount: Long, price: Double, market: Market, fx: Double): Double? {
        if (amount <= 0 || price <= 0) return null
        val units = amount / (price * fx)
        val q = if (market == Market.CRYPTO) floor(units * 1e8) / 1e8 else floor(units)
        return q.takeIf { it > 0 }
    }
}

/** [INV-12] What tax this year's trading may come to, from the rules in force on 2026-10-20 or a newer `taxes` file (참고용). */
data class TaxEstimate(
    val year: Int,
    /** 해외 주식: gains less losses this year, matched oldest first. */
    val foreignGain: Long,
    val foreignTaxable: Long,
    val foreignTax: Long,
    /** How much more foreign gain stays inside the 250만 원 deduction. */
    val foreignDeductionLeft: Long,
    val cryptoGain: Long,
    /** False before 2027, when coins are not taxed yet. */
    val cryptoTaxed: Boolean,
    /** What coins would owe at the 2027 rules, for planning. */
    val cryptoTax: Long,
    val dividendsGross: Long,
    val dividendTax: Long,
    val interestGross: Long,
    /** 금융소득: dividends and interest before tax; over 2,000만 원 it is taxed with other income. */
    val financialIncome: Long,
    val tradeTax: Long,
    /** Net gain held in ISA accounts so far, against the 비과세 limit. */
    val isaGain: Long,
    val isaLimit: Long,
    val isaTaxOver: Long,
    val hasIsa: Boolean,
    /** Gains in 연금저축·IRP, taxed only when drawn. */
    val deferredGain: Long,
    val domesticGain: Long,
) {
    val financialNear: Boolean get() = financialIncome >= InvestTaxes.FINANCIAL_INCOME_LIMIT * 8 / 10
    val financialOver: Boolean get() = financialIncome > InvestTaxes.FINANCIAL_INCOME_LIMIT
}

object InvestTaxes {
    /**
     * [INV-36] The numbers of the tax law that change with it, from `remote/v1/taxes.json` when a newer signed file
     * came (세법은 해마다 바뀌어요), else these. 코인 과세 2027 has been put off twice already (확인 필요).
     */
    data class Rules(
        val foreignDeduction: Long = 2_500_000L,
        val capitalGainsRate: Double = 0.22,
        val cryptoDeduction: Long = 2_500_000L,
        val cryptoFromYear: Int = 2027,
        val isaLimit: Long = 2_000_000L,
        val isaLimitLowIncome: Long = 4_000_000L,
        val isaOverRate: Double = 0.099,
        val financialIncomeLimit: Long = 20_000_000L,
        /** 배당소득세: 14% and 지방소득세 1.4%. */
        val dividendRate: Double = 0.154,
        /** US dividends are taxed 15% there, and nothing more here below the 2,000만 원 line. */
        val usDividendRate: Double = 0.15,
    )

    val BUILT_IN = Rules()
    const val BUILT_IN_VERSION = 2026102301L

    @Volatile var rules: Rules = BUILT_IN
        private set

    /** [INV-36] A newer signed `taxes` file's numbers; null goes back to the built-in ones. */
    fun use(file: com.bamnat.core.remote.RemoteFile?) {
        rules = file?.takeIf { it.version > BUILT_IN_VERSION }?.let(::read) ?: BUILT_IN
    }

    /** Reads a `taxes` file strictly: rates between 0 and 1, amounts in whole won, a year from 2025. */
    fun read(f: com.bamnat.core.remote.RemoteFile): Rules {
        fun bad(why: String): Nothing = throw com.bamnat.core.remote.RemoteFileException(why)
        if (f.kind != com.bamnat.core.remote.RemoteFiles.TAXES) bad("kind ${f.kind}")
        val d = f.data
        fun rate(k: String) = d.double(k).also { if (it < 0 || it >= 1) bad(k) }
        fun won(k: String) = d.long(k).also { if (it < 0 || it > 1_000_000_000) bad(k) }
        return Rules(
            foreignDeduction = won("foreignDeduction"), capitalGainsRate = rate("capitalGainsRate"),
            cryptoDeduction = won("cryptoDeduction"), cryptoFromYear = d.int("cryptoFromYear").also { if (it !in 2025..2100) bad("cryptoFromYear") },
            isaLimit = won("isaLimit"), isaLimitLowIncome = won("isaLimitLowIncome"), isaOverRate = rate("isaOverRate"),
            financialIncomeLimit = won("financialIncomeLimit"), dividendRate = rate("dividendRate"), usDividendRate = rate("usDividendRate"),
        )
    }

    val FOREIGN_DEDUCTION: Long get() = rules.foreignDeduction
    val CAPITAL_GAINS_RATE: Double get() = rules.capitalGainsRate
    val CRYPTO_DEDUCTION: Long get() = rules.cryptoDeduction
    val CRYPTO_FROM_YEAR: Int get() = rules.cryptoFromYear
    val ISA_LIMIT: Long get() = rules.isaLimit
    val ISA_LIMIT_LOW_INCOME: Long get() = rules.isaLimitLowIncome
    val ISA_OVER_RATE: Double get() = rules.isaOverRate
    val FINANCIAL_INCOME_LIMIT: Long get() = rules.financialIncomeLimit
    val DIVIDEND_RATE: Double get() = rules.dividendRate
    val US_DIVIDEND_RATE: Double get() = rules.usDividendRate

    /**
     * [INV-35] Why the 보유 tab's 손익 and the tax preview can differ: 평균단가 is a moving average, the 해외 주식 tax
     * preview matches each sale with the oldest shares first. Shown under both.
     */
    const val AVERAGE_VS_FIFO = "보유 화면의 평균단가 손익은 이동평균이고, 해외 주식 세금 미리 보기는 먼저 산 주식부터 판 것으로(선입선출) 계산해요. " +
        "그래서 두 손익이 다를 수 있어요. 신고는 증권사 양도소득세 자료로 확인해 주세요."

    /** [INV-09] The tax usually withheld from a dividend of [gross] won from a holding on [market]. */
    fun dividendTax(gross: Long, market: Market): Long =
        floor(gross * (if (market.foreign) US_DIVIDEND_RATE else DIVIDEND_RATE)).toLong()

    /**
     * [INV-39] What one sell of 해외 주식 adds to this year's 양도소득세 before it is saved (사용자 U17): this year's gain
     * matched oldest first without and with it, less the 250만 원 기본공제 (소득세법 제103조), at 22% (제104조 제1항
     * 제12호 20% + 지방소득세). Null for anything else: 국내 주식 owe it only as 대주주 (종목당 50억 원 등, 확인 필요),
     * coins not before [CRYPTO_FROM_YEAR], and ISA·연금 accounts not at all.
     */
    data class SellPreview(val gain: Long, val yearGain: Long, val taxBefore: Long, val taxAfter: Long, val deductionLeft: Long) {
        val extraTax: Long get() = taxAfter - taxBefore
    }

    fun sellPreview(book: InvestBook, trade: Trade): SellPreview? {
        if (trade.side != Side.SELL || !trade.market.foreign || trade.quantity <= 0 || trade.price <= 0) return null
        val type = book.account(trade.accountId)?.type
        if (type != null && (type.taxDeferred || type == AccountType.ISA)) return null
        val sheltered = book.accounts.filter { it.type.taxDeferred || it.type == AccountType.ISA }.map { it.id }.toSet()
        val others = book.trades.filter { trade.id == 0L || it.id != trade.id }
        fun gain(list: List<Trade>) = Portfolio.fifo(list)
            .filter { it.market.foreign && it.date.year == trade.date.year && it.accountId !in sheltered }.sumOf { it.pnlWon }.roundToLong()
        val before = gain(others)
        val after = gain(others + trade.copy(id = if (trade.id == 0L) Long.MAX_VALUE else trade.id))
        fun tax(g: Long) = ((g - FOREIGN_DEDUCTION).coerceAtLeast(0) * CAPITAL_GAINS_RATE).roundToLong()
        return SellPreview(after - before, after, tax(before), tax(after), (FOREIGN_DEDUCTION - after).coerceIn(0, FOREIGN_DEDUCTION))
    }

    /**
     * [INV-41] 해외 주식 양도소득세 신고 (5월 1~31일, 소득세법 제110조; 마감이 주말·공휴일이면 다음 평일): a reminder on 5월 1일 and
     * a week before the deadline, when last year's 해외 주식 이익 (oldest first, ISA·연금 빼고) is over the 250만 원 공제, so
     * there is tax to report. Shown as dates of the investor calendar (ids below zero, never saved): the coming May only.
     */
    fun reportReminders(book: InvestBook, today: LocalDate): List<InvestEvent> {
        val thisYear = com.bamnat.core.money.TaxDeadlines.workday(LocalDate.of(today.year, 5, 31))
        val due = if (today.isAfter(thisYear)) com.bamnat.core.money.TaxDeadlines.workday(LocalDate.of(today.year + 1, 5, 31)) else thisYear
        val year = due.year - 1
        val sheltered = book.accounts.filter { it.type.taxDeferred || it.type == AccountType.ISA }.map { it.id }.toSet()
        val gain = Portfolio.fifo(book.trades).filter { it.market.foreign && it.date.year == year && it.accountId !in sheltered }
            .sumOf { it.pnlWon }.roundToLong()
        val taxable = (gain - FOREIGN_DEDUCTION).coerceAtLeast(0)
        if (taxable <= 0) return emptyList()
        val tax = (taxable * CAPITAL_GAINS_RATE).roundToLong()
        val memo = "${year}년 해외 주식 이익 ${"%,d".format(gain)}원 → 예상 세금 ${"%,d".format(tax)}원(250만 원 공제 뒤 22%). ${due.monthValue}월 ${due.dayOfMonth}일까지 홈택스나 증권사 신고 대행으로 신고해요. 자료는 투자 › 내보내기 › 해외 주식 세금 신고 자료"
        return listOf(LocalDate.of(due.year, 5, 1) to "해외 주식 양도소득세 신고 시작", due.minusDays(7) to "해외 주식 양도소득세 신고 일주일 남음")
            .filter { (d, _) -> !d.isBefore(today) }
            .mapIndexed { i, (d, title) -> InvestEvent(id = REPORT_EVENT_ID - i, date = d, kind = EventKind.OTHER, title = title, memo = memo) }
    }

    /** [INV-41] Ids of [reportReminders]: below zero, apart from saved dates. */
    const val REPORT_EVENT_ID = -5_31_000L

    /** [INV-39] Said under 국내 주식 sells. */
    const val DOMESTIC_NOTE = "국내 상장주식은 대주주(종목당 50억 원 이상 등)가 아니면 양도소득세가 없어요. 거래세는 위에 넣었어요."

    fun estimate(book: InvestBook, result: PortfolioResult, year: Int): TaxEstimate {
        val inYear = { d: LocalDate -> d.year == year }
        val deferred = book.accounts.filter { it.type.taxDeferred }.map { it.id }.toSet()
        val isa = book.accounts.filter { it.type == AccountType.ISA }.map { it.id }.toSet()
        val taxable = { r: Realization -> r.accountId !in deferred && r.accountId !in isa }
        // 해외 주식 are matched oldest first, as the tax office does.
        val fifo = Portfolio.fifo(book.trades)
        val foreignGain = fifo.filter { it.market.foreign && inYear(it.date) && taxable(it) }.sumOf { it.pnlWon }.roundToLong()
        val foreignTaxable = (foreignGain - FOREIGN_DEDUCTION).coerceAtLeast(0)
        val cryptoGain = result.realizations.filter { it.market == Market.CRYPTO && inYear(it.date) && taxable(it) }.sumOf { it.pnlWon }.roundToLong()
        val cryptoTax = ((cryptoGain - CRYPTO_DEDUCTION).coerceAtLeast(0) * CAPITAL_GAINS_RATE).roundToLong()
        val cash = book.cash.filter { inYear(it.date) }
        val dividends = cash.filter { it.kind == CashKind.DIVIDEND }
        val interest = cash.filter { it.kind == CashKind.INTEREST }
        // ISA tax is on everything it made over its life, gains and losses together.
        val isaGain = result.realizations.filter { it.accountId in isa }.sumOf { it.pnlWon }.roundToLong() +
            book.cash.filter { it.accountId in isa && it.kind.isIncome }.sumOf { it.amount } -
            book.cash.filter { it.accountId in isa && it.kind == CashKind.FEE }.sumOf { it.amount }
        val limit = book.settings.isaLimit
        return TaxEstimate(
            year = year,
            foreignGain = foreignGain,
            foreignTaxable = foreignTaxable,
            foreignTax = (foreignTaxable * CAPITAL_GAINS_RATE).roundToLong(),
            foreignDeductionLeft = (FOREIGN_DEDUCTION - foreignGain).coerceIn(0, FOREIGN_DEDUCTION),
            cryptoGain = cryptoGain,
            cryptoTaxed = year >= CRYPTO_FROM_YEAR,
            cryptoTax = cryptoTax,
            dividendsGross = dividends.sumOf { it.amount },
            dividendTax = dividends.sumOf { it.tax } + interest.sumOf { it.tax },
            interestGross = interest.sumOf { it.amount },
            financialIncome = dividends.sumOf { it.amount } + interest.sumOf { it.amount },
            tradeTax = book.trades.filter { inYear(it.date) }.sumOf { it.tax * it.fxRate }.roundToLong(),
            isaGain = isaGain,
            isaLimit = limit,
            isaTaxOver = ((isaGain - limit).coerceAtLeast(0) * ISA_OVER_RATE).roundToLong(),
            hasIsa = isa.isNotEmpty(),
            deferredGain = result.realizations.filter { it.accountId in deferred && inYear(it.date) }.sumOf { it.pnlWon }.roundToLong(),
            domesticGain = result.realizations.filter { !it.market.foreign && it.market != Market.CRYPTO && inYear(it.date) && taxable(it) }
                .sumOf { it.pnlWon }.roundToLong(),
        )
    }
}
