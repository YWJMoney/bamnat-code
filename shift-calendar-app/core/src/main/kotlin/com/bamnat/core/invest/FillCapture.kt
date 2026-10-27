package com.bamnat.core.invest

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

/** [INV-40] The arithmetic 409 checks every price with: 수량 × 단가 ≈ 금액. */
object FillMath {
    /** A price worked out from an amount: whole won stay whole, anything else keeps four places. */
    fun round(v: Double): Double {
        val whole = v.roundToLong().toDouble()
        if (abs(v - whole) < 0.0005) return whole
        return (v * 10_000).roundToLong() / 10_000.0
    }

    /** How far apart 수량 × 단가 and 금액 may be: a won (or a cent) of rounding per share, and 0.2% at most. */
    fun tolerance(amount: Double, quantity: Double): Double = maxOf(1.0, quantity * 0.01, amount * 0.002)

    fun agrees(quantity: Double, price: Double, amount: Double): Boolean =
        quantity > 0 && price > 0 && amount > 0 && abs(quantity * price - amount) <= tolerance(amount, quantity)

    /**
     * [INVA-02] 1.29 AR: agreeing to the last won (or cent): only the rounding of an average price, half a won (half a cent)
     * a share, at least one won (cent). A digit misread anywhere in 수량 or 단가 moves 수량 × 단가 further than this, while
     * [agrees]' 0.2% lets a price's last digits slip. What goes in without a question is checked with this.
     */
    fun exact(quantity: Double, price: Double, amount: Double, market: Market): Boolean {
        if (quantity <= 0 || price <= 0 || amount <= 0) return false
        val unit = if (market.foreign) 0.01 else 1.0
        return abs(quantity * price - amount) <= maxOf(unit, quantity * unit / 2) + 1e-9
    }
}

/**
 * [INV-40] 409 C: a broker's or exchange's '체결 내역' screen read from a screenshot on the phone (결정 D68): the text
 * of the picture, row by row, becomes [Fill]s. Every number is checked against the others (수량 × 단가 ≈ 금액); a
 * field that is not sure says so ([Fill.unsure]) and the screen asks about it in yellow instead of filling it quietly.
 * Layouts differ by app (a table row per fill, a card per fill, a header with columns, dollars, coins), so this reads
 * the parts wherever they sit rather than one app's layout.
 */
object FillSheetReader {
    enum class Field { NAME, QUANTITY, PRICE, AMOUNT }

    data class Fill(
        val side: Side,
        val name: String,
        val symbol: String,
        val quantity: Double,
        val price: Double,
        /** 0 when the screen did not show it. */
        val amount: Double,
        val market: Market,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        /** Fields to ask about: missing, read without a label, or not agreeing with the others. */
        val unsure: Set<Field> = emptySet(),
        /** The price was 금액 ÷ 수량. */
        val priceFromAmount: Boolean = false,
    ) {
        val title: String get() = name.ifBlank { symbol }
        val sure: Boolean get() = unsure.isEmpty()
        /** 수량 × 단가 and 금액 agree; null without an amount. */
        val checked: Boolean? get() = if (amount > 0) FillMath.agrees(quantity, price, amount) else null
    }

    private const val NUM = "(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    // [INVA-03] 1.29: 카카오페이 '샀어요·팔았어요'.
    private val SIDE = Regex("매수|매도|구매|판매|샀|팔았|\\bbuy\\b|\\bsell\\b", RegexOption.IGNORE_CASE)
    private val SKIP = Regex("미체결|취소|거부|정정|접수|예약")
    /** [INVA-03] What was ordered, the order's number, the account: never the fill's quantity, never a loose number. */
    private val ORDERED = listOf(
        Regex("주문\\s*번호\\s*[:：]?\\s*[\\d\\-]+"),
        Regex("계좌(?:번호)?\\s*[:：]?\\s*[\\d*\\-]+"),
        Regex("주문\\s*(?:수량)?\\s*[:：]?\\s*$NUM\\s*(?:주|개)?"),
        Regex("$NUM\\s*%"),
    )
    /** [INVA-03] A number with 원·KRW·$ after it is money, never a quantity (a label beside the next row's value). */
    private const val NOT_MONEY = "(?![\\d.,])(?!\\s*(?:원|KRW|USD|달러))"
    // [INVA-03] 체결수량·체결량·거래수량 before a bare 수량, and never 주문수량.
    private val QTY = listOf(
        Regex("체결\\s*(?:수량|량)\\s*[:：]?\\s*$NUM$NOT_MONEY"),
        Regex("거래\\s*(?:수량|량)\\s*[:：]?\\s*$NUM$NOT_MONEY"),
        Regex("(?<!주문)(?<!주문\\s)수량\\s*[:：]?\\s*$NUM$NOT_MONEY"),
        Regex("체결\\s+$NUM\\s*(?:주|개)(?![가-힣])"),
        Regex("$NUM\\s*(?:주|株)(?![가-힣])"),
        Regex("$NUM\\s*개(?![가-힣])"),
    )
    /** [INVA-03] A coin's quantity with its code after it: "0.00123 BTC". */
    private val QTY_COIN = Regex("(?<![\\d.,])$NUM\\s*([A-Z]{2,6})(?![A-Za-z/\\-])")
    private val NOT_COINS = setOf("KRW", "USD", "USDT", "ETF", "LTE", "BUY", "SELL", "NYSE", "AMEX")
    /** [INVA-03] The price of one share said with the share: "1주당 71,500원", "1주 71,500원". */
    private val PER_SHARE = Regex("(?<![\\d,.])1\\s*주\\s*(?:당)?\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val PRICE = listOf(
        Regex("(?:체결\\s*)?(?:단가|가격|평균가|평단가?|체결가|주당)\\s*[:：]?\\s*\\$?\\s*$NUM", RegexOption.IGNORE_CASE),
        Regex("[@＠]\\s*\\$?\\s*$NUM"),
    )
    /** [INVA-03] 정산금액 has the fee in it: taken out, never read as the fill's amount. */
    private val SETTLED = Regex("정산\\s*(?:금액|대금)\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val AMOUNT = Regex("(?:(?:체결|약정|거래|결제|매수|매도|구매|판매)\\s*(?:금액|대금)|총\\s*(?:체결\\s*)?(?:금액|액)|총액|합계|금액|총)\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val FEE = Regex("(?:수수료|제세금|세금|거래세|농특세|정산\\s*수수료)\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val TIME = Regex("(?<![\\d:.])([01]?\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?(?![\\d:])")
    /** [INVA-03] 2026.10.27, 2026-10-27, 2026년 10월 27일. */
    private val DATE_LONG = Regex("(?<!\\d)(20\\d{2})\\s*[./\\-년]\\s*(\\d{1,2})\\s*[./\\-월]\\s*(\\d{1,2})(?!\\d)\\s*일?")
    /** [INVA-03] 10월 27일. */
    private val DATE_KO = Regex("(?<!\\d)(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    /**
     * [INVA-03] 10.27 or 10/27, only with a time or a weekday after it, or alone at the line's end: "1.5 XRP" and a
     * price of 3.14 are never a day.
     */
    private val DATE_SHORT = Regex("(?<![\\d.,$])(0?[1-9]|1[0-2])[./](0?[1-9]|[12]\\d|3[01])(?![\\d.,])(?=\\s*(?:\\([월화수목금토일]\\))?\\s*(?:\\d{1,2}:\\d{2}|$))", RegexOption.MULTILINE)
    private val MONEY_ONLY = Regex("\\$?\\s*$NUM\\s*(?:원|KRW|USD)?")
    private val WEEKDAY = Regex("[(（][월화수목금토일][)）]")
    private val CODE_KR = Regex("(?<![\\d,.])(\\d{6})(?![\\d,.])")
    private val CODE_COIN = Regex("([A-Z0-9]{2,10})[-/](?:KRW|USDT)|(?:KRW|USDT)[-/]([A-Z0-9]{2,10})")
    private val TICKER = Regex("(?<![A-Za-z])([A-Z]{1,5})(?![A-Za-z])")
    private val NUMBER = Regex("(?<![\\d.,:])\\$?$NUM(?![\\d:])")
    private val HEADER_WORDS = listOf("종목", "구분", "수량", "단가", "가격", "금액", "체결가", "체결시간", "시간", "시각", "일자", "상태")
    private val LABELS = Regex("수량|단가|가격|금액|수수료|체결가|체결량|평균가|정산")

    private val NOT_NAMES = setOf(
        "매수", "매도", "구매", "판매", "체결", "체결완료", "완료", "수량", "체결수량", "단가", "체결단가", "체결가", "체결가격", "가격", "금액",
        "체결금액", "거래금액", "약정금액", "정산금액", "총액", "합계", "총", "주문", "주문번호", "구분", "종목", "종목명", "시장가", "지정가",
        "보통", "현금", "신용", "국내", "해외", "주식", "원", "KRW", "USD", "USDT", "BTC", "달러", "수수료", "세금", "제세금", "거래세", "평균가",
        "평단", "주당", "체결내역", "거래내역", "주문내역", "내역", "전체", "오늘", "체결시간", "시간", "일자", "날짜", "주", "개", "BUY", "SELL",
        "Buy", "Sell", "ETF", "체결일", "체결일자", "정산", "결제", "매매", "내", "계좌", "투자내역", "체결통보",
        // [INVA-03] 1.29: screen words seen around 체결 lists.
        "상태", "시각", "체결시각", "종목코드", "주문수량", "체결량", "거래수량", "거래단가", "미체결", "주문체결", "일별", "해외주식", "평균체결가",
        "LTE", "체결/미체결", "샀어요", "팔았어요", "어제", "오전", "오후",
    )

    /** OCR slips inside numbers: O for 0, l·I for 1, "71, 500" and "71.500" for 71,500. */
    fun clean(text: String): String {
        var t = text.replace('，', ',').replace('．', '.').replace(' ', ' ').replace('＄', '$')
        t = Regex("(?<=\\d)[Oo](?=[\\d,])|(?<=[\\d,])[Oo](?=\\d)").replace(t, "0")
        t = Regex("(?<=\\d)[lI|](?=[\\d,])|(?<=[\\d,])[lI|](?=\\d)").replace(t, "1")
        // An O or l closing a number ("6,118,0O0" read as "6,118,0O") is a 0 or 1 too.
        t = Regex("(?<=\\d)[Oo](?![A-Za-z가-힣])").replace(t, "0")
        t = Regex("(?<=,\\d{1,2})[lI|](?![A-Za-z가-힣])").replace(t, "1")
        t = Regex("(?<=\\d),\\s+(?=\\d{3}(?!\\d))").replace(t, ",")
        // [INVA-03] 오전 9:12 · 오후 2:03 are 09:12 · 14:03.
        t = Regex("(오전|오후)\\s*(\\d{1,2}):(\\d{2})").replace(t) { m ->
            val h = m.groupValues[2].toInt() % 12 + if (m.groupValues[1] == "오후") 12 else 0
            "%02d:%s".format(h, m.groupValues[3])
        }
        // On a line in 원 (no $, no coin), "185.750" is 185,750: a Korean share's price and amount have no decimals.
        // A wrong guess here still has to agree with 수량 × 단가 = 금액, or the fill is asked about.
        t = t.lines().joinToString("\n") { line ->
            if (Regex("[$]|USD|달러|USDT|BTC|ETH|XRP|SOL|DOGE|개|[A-Z]{2,10}[-/]KRW|KRW[-/][A-Z]|\\d\\s+[A-Z]{2,6}(?![A-Za-z])").containsMatchIn(line)) line
            // A day (2026.10.27, 10.27 09:12) is not a number in 원.
            else Regex("(?<![\\d.,$])([1-9]\\d{0,2}(?:\\.\\d{3})+)(?![\\d.])(?!\\s*(?:%|배))").replace(line) { m ->
                if (Regex("^20\\d{2}\\.").containsMatchIn(m.value)) m.value else m.value.replace(".", ",")
            }
        }
        // A period between groups of three, with no other decimal after it, is a thousands mark in 원.
        t = Regex("(?<![\\d.])(\\d{1,3}(?:\\.\\d{3}){1,3})(?=\\s*(?:원|KRW))").replace(t) { it.value.replace(".", ",") }
        return t
    }

    private enum class Lead { WHEN, NAME, NAMED, AMOUNT }

    /** [INVA-03] A day and time alone on its line ("2026.10.27 09:12", "10월 27일", "10.27(월)"). */
    private fun whenOnly(line: String): Boolean {
        var rest = line
        var found = false
        for (re in listOf(DATE_LONG, DATE_KO, DATE_SHORT, TIME)) re.findAll(rest).toList().forEach { found = true; rest = rest.replace(it.value, " ") }
        rest = rest.replace(WEEKDAY, " ").replace(Regex("오늘|어제|체결완료|체결|[·|,\\-]"), " ")
        return found && rest.isBlank()
    }

    private fun headerLike(line: String) = HEADER_WORDS.count { it in line } >= 3 && !SIDE.containsMatchIn(line)

    /**
     * [INVA-03] What a line just above a side line is, when it starts the fill below: its day and time, its name alone, or
     * its name with the amount on the right (토스, 해외 주식).
     */
    private fun leadOf(line: String): Lead? {
        if (SIDE.containsMatchIn(line) || headerLike(line)) return null
        if (whenOnly(line)) return Lead.WHEN
        if (looksLikeName(line)) return Lead.NAME
        if (MONEY_ONLY.matches(line)) return Lead.AMOUNT
        val name = nameOf(line)
        if (name.isEmpty() || !line.startsWith(name) || LABELS.containsMatchIn(line)) return null
        // The name with its amount, or with the day (a photo of the screen puts the day beside it).
        var rest = line.removePrefix(name)
        for (re in listOf(DATE_LONG, DATE_KO, DATE_SHORT, TIME)) rest = re.replace(rest, " ")
        return if (NUMBER.findAll(rest).count() <= 1) Lead.NAMED else null
    }

    /**
     * [INVA-03] 1.29: [captured] is the day of the capture (its file's day, else today): a day without a year (10.27, 10월
     * 27일) is the latest one not after it. A day said once above the list (a section's head) is each fill's below it.
     */
    fun read(text: String, captured: LocalDate? = null): List<Fill> {
        val ref = captured ?: com.bamnat.core.time.Today.date()
        val lines = clean(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val headers = lines.indices.filter { headerLike(lines[it]) }
        val columns = headers.firstOrNull()?.let { columnsOf(lines[it]) }
        val sides = lines.indices.filter { i -> i !in headers && SIDE.containsMatchIn(lines[i]) }
        if (sides.isEmpty()) return emptyList()
        // Where each fill starts: its side line, and up to two lines above it that lead into it (its day, its name).
        val starts = IntArray(sides.size)
        // [INVA-03] A day and time on its own line goes above each fill (업비트) or under it (KB). The first fill and the
        // last tell which; when neither does, a line between two fills is nobody's (its time is asked, never guessed).
        fun timed(i: Int) = i in lines.indices && i !in headers && whenOnly(lines[i]) && TIME.containsMatchIn(lines[i])
        val above = timed(sides[0] - 1) || timed(sides[0] - 2) && leadOf(lines[sides[0] - 1]).let { it == Lead.NAME || it == Lead.NAMED }
        val under = !above && timed(lines.lastIndex) && lines.lastIndex > sides.last()
        val nobody = HashSet<Int>()
        for ((k, s) in sides.withIndex()) {
            val floor = if (k == 0) (headers.lastOrNull { it < s }?.plus(1) ?: 0) else sides[k - 1] + 1
            val own = lines[s]
            var st = s
            var at = s
            var leads = 0
            while (at - 1 >= floor && leads < 3) {
                val line = lines[at - 1]
                val lead = leadOf(line) ?: break
                val before = if (k == 0) emptyList() else lines.subList(starts[k - 1], at - 1)
                val go = when (lead) {
                    // A day alone (a section's head) leads in; a day and time closing the fill above stays with it,
                    // unless that one has its own time already.
                    Lead.WHEN -> when {
                        !TIME.containsMatchIn(line) || k == 0 -> !TIME.containsMatchIn(line) || !TIME.containsMatchIn(own)
                        TIME.containsMatchIn(own) -> false
                        above -> true
                        under -> false
                        before.any { TIME.containsMatchIn(it) } -> true
                        else -> { nobody += at - 1; false }
                    }
                    Lead.NAME, Lead.NAMED -> nameOf(own).isEmpty() && (k == 0 || before.any { nameOf(it).isNotEmpty() })
                    // An amount alone on its line leads in only between the name above and the side line.
                    Lead.AMOUNT -> true
                }
                if (!go) break
                at--; leads++
                if (lead != Lead.AMOUNT) st = at
            }
            starts[k] = st
        }
        val fills = ArrayList<Fill>()
        for (k in sides.indices) {
            val stop = (starts.getOrNull(k + 1) ?: lines.size) - 1
            val record = lines.subList(starts[k], stop + 1).filterIndexed { i, _ -> starts[k] + i !in headers && starts[k] + i !in nobody }
            if (SKIP.containsMatchIn(record.joinToString(" "))) continue
            // The nearest day said above the fill: a section's head, or the list's title line.
            val section = (starts[k] - 1 downTo 0).firstNotNullOfOrNull { i ->
                val l = lines[i]
                if (DATE_LONG.containsMatchIn(l) || DATE_KO.containsMatchIn(l) || whenOnly(l)) dayIn(l, ref) else null
            }
            fill(record, columns, section, ref)?.let(fills::add)
        }
        return fills
    }

    /** The first day [line] says. */
    private fun dayIn(line: String, ref: LocalDate): LocalDate? =
        DATE_LONG.find(line)?.let { m -> runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
            ?: DATE_KO.find(line)?.let { m -> noYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), ref) }
            ?: DATE_SHORT.find(line)?.let { m -> noYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), ref) }

    /** A month and day without a year: this year's, or last year's when this year's would be after [ref] (a January capture of December). */
    private fun noYear(month: Int, day: Int, ref: LocalDate): LocalDate? = runCatching {
        LocalDate.of(ref.year, month, day).let { if (it.isAfter(ref.plusDays(1))) it.minusYears(1) else it }
    }.getOrNull()

    private fun looksLikeName(line: String): Boolean {
        if (SIDE.containsMatchIn(line) || headerLike(line) || line.any { it.isDigit() } && !CODE_KR.containsMatchIn(line) && !CODE_COIN.containsMatchIn(line)) return false
        return nameOf(line).isNotEmpty()
    }

    private enum class Col { QTY, PRICE, AMOUNT }

    private fun columnsOf(header: String): List<Col> {
        val found = listOfNotNull(
            Regex("(?<!주문)수량").find(header)?.let { it.range.first to Col.QTY },
            Regex("단가|체결가|가격|평균가").find(header)?.let { it.range.first to Col.PRICE },
            Regex("금액").find(header)?.let { it.range.first to Col.AMOUNT },
        )
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun fill(record: List<String>, columns: List<Col>?, section: LocalDate?, ref: LocalDate): Fill? {
        val all = record.joinToString("\n")
        val sideWord = SIDE.find(all)?.value?.lowercase() ?: return null
        val side = if (sideWord in setOf("매수", "구매", "샀", "buy")) Side.BUY else Side.SELL
        var rest = all
        fun cut(r: IntRange) { rest = rest.replaceRange(r, " ".repeat(r.last - r.first + 1)) }
        // Dates, times, fees and codes are taken out first, so no number is read twice.
        var date: LocalDate? = null
        for (re in listOf(DATE_LONG, DATE_KO, DATE_SHORT)) re.findAll(rest).toList().forEach { m ->
            val d = if (re === DATE_LONG) runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
                else noYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), ref)
            if (d != null) { if (date == null) date = d; cut(m.range) }
        }
        val timeMatch = TIME.find(rest)
        val time = timeMatch?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull() ?: 0) }
        timeMatch?.let { cut(it.range) }
        // Every other time on the card (주문 시각) is no number either.
        TIME.findAll(rest).toList().forEach { cut(it.range) }
        FEE.findAll(rest).toList().forEach { cut(it.range) }
        SETTLED.findAll(rest).toList().forEach { cut(it.range) }
        for (re in ORDERED) re.findAll(rest).toList().forEach { cut(it.range) }
        var coinCode = CODE_COIN.find(rest)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
        CODE_COIN.find(rest)?.let { cut(it.range) }
        val krCode = CODE_KR.find(rest)?.groupValues?.get(1)
        CODE_KR.find(rest)?.let { cut(it.range) }
        val dollars = all.contains('$') || Regex("USD|달러").containsMatchIn(all)
        val nameAt = record.indexOfFirst { !headerLike(it) && nameOf(it).isNotEmpty() }
        val name = record.getOrNull(nameAt)?.let(::nameOf).orEmpty()
        if (name.isNotEmpty()) rest.indexOf(name).takeIf { it >= 0 }?.let { cut(it until it + name.length) }

        fun labeled(res: List<Regex>): Pair<Double, IntRange>? = res.firstNotNullOfOrNull { re ->
            re.find(rest)?.let { m -> num(m.groupValues[1])?.takeIf { it > 0 }?.let { it to m.range } }
        }
        val perShare = labeled(listOf(PER_SHARE))?.also { cut(it.second) }?.first
        var qtyL = labeled(QTY)?.also { cut(it.second) }?.first
        if (qtyL == null) QTY_COIN.findAll(rest).firstOrNull { it.groupValues[2] !in NOT_COINS }?.let { m ->
            qtyL = num(m.groupValues[1])?.takeIf { it > 0 }
            if (qtyL != null) { coinCode = coinCode ?: m.groupValues[2]; cut(m.range) }
        }
        // A coin's unit after a labelled quantity names the coin too ("체결수량 0.00123 BTC").
        if (coinCode == null && krCode == null) QTY_COIN.findAll(all).firstOrNull { it.groupValues[2] !in NOT_COINS && it.groupValues[2] !in setOf("PM", "AM") }
            ?.let { m -> if (Regex("[가-힣]").containsMatchIn(name) || name.isEmpty() || name == m.groupValues[2]) coinCode = m.groupValues[2] }
        coinCode?.let { c -> Regex("(?<![A-Za-z])${Regex.escape(c)}(?![A-Za-z])").findAll(rest).toList().forEach { cut(it.range) } }
        val priceL = perShare ?: labeled(PRICE)?.also { cut(it.second) }?.first
        val amountL = labeled(listOf(AMOUNT))?.also { cut(it.second) }?.first
        // What is left: numbers without a word, in the order they sit.
        val loose = NUMBER.findAll(rest).mapNotNull { num(it.groupValues[1]) }.filter { it > 0 }.toList()

        var q = qtyL
        var p = priceL
        var a = amountL
        val guessed = HashSet<Field>()
        val pool = loose.toMutableList()
        fun take(v: Double) { pool.remove(v) }
        if (columns != null && loose.isNotEmpty()) {
            // A table under a header: the loose numbers fill the header's columns in order.
            val free = columns.filter { c -> when (c) { Col.QTY -> q == null; Col.PRICE -> p == null; Col.AMOUNT -> a == null } }
            free.zip(loose).forEach { (c, v) -> take(v); when (c) { Col.QTY -> q = v; Col.PRICE -> p = v; Col.AMOUNT -> a = v } }
            // [INVA-03] A header over two screen rows (키움): the amount is further on; one that agrees is found.
            if (q != null && p != null && a == null) pool.firstOrNull { FillMath.agrees(q!!, p!!, it) }?.let { a = it; take(it) }
            // Columns that do not add up, while three loose numbers do: the numbers are not in the header's order.
            if (q != null && p != null && a != null && !FillMath.agrees(q!!, p!!, a!!) && qtyL == null && priceL == null && amountL == null)
                tripleAny(loose)?.let { t -> q = t.first; p = t.second; a = t.third }
        } else if (loose.isNotEmpty()) {
            when {
                q != null && p != null && a == null -> pool.firstOrNull { FillMath.agrees(q!!, p!!, it) }?.let { a = it; take(it) }
                q != null && p == null && a != null -> pool.firstOrNull { FillMath.agrees(q!!, it, a!!) }?.let { p = it; take(it) }
                q == null && p != null && a != null -> pool.firstOrNull { FillMath.agrees(it, p!!, a!!) }?.let { q = it; take(it) }
                q != null && p == null && a == null -> pairFor(pool, q!!)?.let { (pp, aa) -> p = pp; a = aa; guessed += setOf(Field.PRICE, Field.AMOUNT) }
                q == null && p == null && a == null -> {
                    val t = tripleAny(pool)
                    if (t != null) { q = t.first; p = t.second; a = t.third }
                    // Nothing agrees: the row still shows, its numbers in screen order, every one asked about.
                    else if (pool.size >= 2) { q = pool[0]; p = pool[1]; a = pool.getOrNull(2) }
                    guessed += setOf(Field.QUANTITY, Field.PRICE, Field.AMOUNT)
                }
            }
            // Agreeing numbers found by the check are as good as labelled ones; anything else stays a guess.
            if (q != null && p != null && a != null && FillMath.agrees(q!!, p!!, a!!)) guessed.clear()
        }
        var fromAmount = false
        if (p == null && q != null && a != null) { p = FillMath.round(a!! / q!!); fromAmount = true }
        val quantity = q ?: return null
        val price = p ?: 0.0
        val amount = a ?: 0.0

        val ticker = if (krCode == null && coinCode == null)
            TICKER.findAll(all).map { it.groupValues[1] }.firstOrNull { it !in NOT_NAMES && it !in setOf("KRW", "USD", "ETF", "NYSE", "NASDAQ", "AMEX", "LTE") }
        else null
        val market = when {
            coinCode != null || Regex("\\d\\s*(?:개|BTC|ETH|XRP|SOL|DOGE)\\b").containsMatchIn(all) -> Market.CRYPTO
            dollars -> Market.US_STOCK
            else -> Market.KR_STOCK
        }
        val unsure = HashSet<Field>(guessed)
        if (name.isEmpty() && krCode == null && coinCode == null && ticker == null) unsure += Field.NAME
        // [INVA-03] A name only on the card's last line, under its numbers, may be the next card's (a tilted photo of the
        // screen moves lines): asked.
        if (nameAt > 0 && nameAt == record.lastIndex && record.size > 2 && looksLikeName(record[nameAt]) &&
            record.indexOfFirst { SIDE.containsMatchIn(it) } < nameAt) unsure += Field.NAME
        if (price <= 0) unsure += Field.PRICE
        if (amount > 0 && price > 0 && !FillMath.agrees(quantity, price, amount)) unsure += setOf(Field.PRICE, Field.AMOUNT)
        // A share is never a fraction on a Korean screen: a decimal there is a misread.
        if (market == Market.KR_STOCK && quantity != Math.floor(quantity)) unsure += Field.QUANTITY
        return Fill(side, name, krCode ?: coinCode ?: ticker.orEmpty(), quantity, price, amount, market, date ?: section, time, unsure, fromAmount)
    }

    /** A price and an amount among the loose numbers that agree with quantity [q]. */
    private fun pairFor(pool: List<Double>, q: Double): Pair<Double, Double>? {
        for (i in pool.indices) for (j in pool.indices) if (i != j && FillMath.agrees(q, pool[i], pool[j])) return pool[i] to pool[j]
        return null
    }

    /** Three loose numbers that agree (수량 × 단가 ≈ 금액); the amount is never smaller than the price. */
    private fun tripleAny(pool: List<Double>): Triple<Double, Double, Double>? {
        for (i in pool.indices) for (j in pool.indices) for (k in pool.indices) {
            if (i == j || j == k || i == k) continue
            if (pool[k] >= pool[j] && FillMath.agrees(pool[i], pool[j], pool[k])) return Triple(pool[i], pool[j], pool[k])
        }
        return null
    }

    /** ETF brands whose names go on with a number (KODEX 200, TIGER 200). */
    private val ETF_BRANDS = setOf("KODEX", "TIGER", "KBSTAR", "RISE", "ACE", "SOL", "HANARO", "ARIRANG", "KOSEF", "PLUS", "TIMEFOLIO", "KIWOOM")

    private fun isWord(w: String): Boolean =
        w.length >= 2 && w.any { it.isLetter() } && w !in NOT_NAMES && !w.matches(Regex("[A-Z]{2,5}/[A-Z]{3,4}|[A-Z]{3,4}-[A-Z0-9]{2,10}")) &&
            NOT_NAMES.none { it.length >= 2 && w.startsWith(it) && w.length <= it.length + 2 && it.any { c -> c in '가'..'힣' } }

    /** The holding's name in [line]: its first word that is not a label, with the words that go on with it. */
    private fun nameOf(line: String): String {
        val tokens = line.replace(Regex("[()\\[\\]【】（）|·:：/]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val i = tokens.indexOfFirst { w -> isWord(w) && w.first().isLetter() && (w.any { it in '가'..'힣' } || w.length >= 3 && !w.matches(Regex("[A-Z]{1,5}")) || w in ETF_BRANDS || w.all { it.isUpperCase() || it.isDigit() } && w.length >= 4) }
        if (i < 0) return ""
        val words = mutableListOf(tokens[i])
        var j = i + 1
        while (j < tokens.size && words.size < 4) {
            val w = tokens[j]
            // [INVA-03] An amount beside the name ("KODEX 200  15,716,250원") or a quantity never goes on with it.
            val word = isWord(w) && !SIDE.containsMatchIn(w) && !w.contains(',') && (w.first().isLetter() || Regex("\\d{1,3}[가-힣A-Za-z&]+").matches(w)) &&
                !Regex("[\\d.]+(?:주|개|원)").matches(w)
            val goesOn = (tokens[i] in ETF_BRANDS && (w.all(Char::isDigit) && w.length <= 3 || word)) || (words.last() in ETF_BRANDS && word)
            if (!goesOn) break
            words += w; j++
        }
        return words.joinToString(" ")
    }

    private fun num(s: String) = s.replace(",", "").toDoubleOrNull()
}

/**
 * [INV-40] Which waiting alert a read fill is (종목·방향·수량·시각), and the price it gives. A fill split into
 * several rows on the screen adds up to the alert's quantity and gives their average price.
 */
object FillMatcher {
    /** A fill this far from the notification's time is another trade. */
    const val MINUTES = 30L

    data class Match(
        val alert: TradeAlerts.Pending,
        val fills: List<FillSheetReader.Fill>,
        val price: Double,
        val amount: Double,
    ) {
        /** Every number behind it agreed and was read with its word. */
        val sure: Boolean get() = fills.all { it.sure }
    }

    data class Result(val matches: List<Match>, val unmatched: List<FillSheetReader.Fill>)

    fun key(s: String): String = s.lowercase().replace(Regex("[\\s()\\[\\]·.&-]"), "").removeSuffix("우").take(20)

    fun sameHolding(a: TradeAlerts.Pending, f: FillSheetReader.Fill): Boolean {
        if (a.symbol.isNotEmpty() && f.symbol.isNotEmpty()) return a.symbol.equals(f.symbol, ignoreCase = true)
        val an = key(a.name.ifBlank { a.symbol })
        val fn = key(f.name.ifBlank { f.symbol })
        return an.isNotEmpty() && fn.isNotEmpty() && (an.contains(fn) || fn.contains(an))
    }

    private fun near(a: TradeAlerts.Pending, f: FillSheetReader.Fill, zone: ZoneId): Boolean {
        val day = a.day(zone)
        if (f.date != null && f.date != day) return false
        val t = f.time ?: return true
        return abs(a.minute(zone) - (t.hour * 60 + t.minute)) <= MINUTES
    }

    /** Fills [alerts] (those still without a price first) from [fills]; each fill is used once. */
    fun match(alerts: List<TradeAlerts.Pending>, fills: List<FillSheetReader.Fill>, zone: ZoneId): Result {
        val left = fills.toMutableList()
        val matches = ArrayList<Match>()
        for (a in alerts.sortedBy { if (it.price > 0) 1 else 0 }) {
            val same = left.filter { it.side == a.side && sameHolding(a, it) && near(a, it, zone) && it.price > 0 }
            val one = same.filter { abs(it.quantity - a.quantity) < 1e-9 }
                .minByOrNull { f -> f.time?.let { abs(a.minute(zone) - (it.hour * 60 + it.minute)) } ?: 0 }
            val used = one?.let { listOf(it) } ?: parts(same, a.quantity) ?: continue
            val amount = used.sumOf { if (it.amount > 0 && FillMath.agrees(it.quantity, it.price, it.amount)) it.amount else it.quantity * it.price }
            matches += Match(a, used, FillMath.round(amount / a.quantity), amount)
            left.removeAll(used)
        }
        return Result(matches, left)
    }

    /** Up to four rows of one order that add up to [quantity]. */
    private fun parts(same: List<FillSheetReader.Fill>, quantity: Double): List<FillSheetReader.Fill>? {
        val c = same.take(8)
        val n = c.size
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) < 2 || Integer.bitCount(mask) > 4) continue
            val pick = (0 until n).filter { mask and (1 shl it) != 0 }.map { c[it] }
            if (abs(pick.sumOf { it.quantity } - quantity) < 1e-9) return pick
        }
        return null
    }
}
