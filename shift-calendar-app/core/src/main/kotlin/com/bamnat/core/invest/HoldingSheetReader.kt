package com.bamnat.core.invest

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 2.0.0 [RVK-26] (검토 U06b-01·I06bb-01) 잔고 캡처: a broker's or exchange's '잔고·보유 종목' screen read from a screenshot on the
 * phone, the way a 체결 capture is ([FillSheetReader], 결정 D68): each holding's name, 보유 수량, 현재가 and 평가금액. Nothing is
 * fetched (the prices stay the user's own); the picture only saves typing them. Every row is checked, 수량 × 현재가 ≈ 평가금액, and
 * 평균단가·매입금액·손익·수익률 are read too so they are never taken for the price. A row read without its words, or whose numbers
 * do not agree, says so ([Row.unsure]) and is shown in yellow to look at, never used quietly.
 *
 * Layouts differ by app (a card per holding with its words, a short list of name · 수량 · 평가금액, a table under a header, coins,
 * dollars), so the parts are read wherever they sit rather than one app's layout. The examples in the tests are written for this
 * reader, not taken from any app.
 */
object HoldingSheetReader {
    data class Row(
        val name: String,
        val symbol: String,
        /** 보유 수량; 0 when not read. */
        val quantity: Double,
        /** 현재가 in the holding's currency; 0 when not read. */
        val price: Double,
        /** 평가금액 in the same currency; 0 when not shown. */
        val value: Double,
        /** 평균단가; 0 when not shown. */
        val average: Double,
        val market: Market,
        /** The price is 평가금액 ÷ 수량. */
        val priceFromValue: Boolean = false,
        /** Read without its words, or not adding up: looked at before it is used. */
        val unsure: Boolean = false,
    ) {
        val title: String get() = name.ifBlank { symbol }
        /** 수량 × 현재가 agrees with 평가금액; null without all three. */
        val checked: Boolean? get() = if (value > 0 && quantity > 0 && price > 0) FillMath.agrees(quantity, price, value) else null
    }

    private const val NUM = "(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private const val SEP = "\\s*[:：]?\\s*"
    private const val CUR = "(?:\\$|US\\$|₩)?\\s*"

    private val QTY = listOf(
        Regex("(?:보유|잔고)\\s*(?:수량|량)$SEP$NUM"),
        Regex("(?<!주문)(?<!매도\\s?가능)(?<!가능)수량$SEP$NUM"),
        Regex("보유$SEP$NUM\\s*(?:주|개|株)"),
    )
    private val QTY_UNIT = Regex("(?<![\\d.,])$NUM\\s*(?:주|株)(?![가-힣])")
    private val QTY_COUNT = Regex("(?<![\\d.,])$NUM\\s*개(?![가-힣])")
    private val QTY_COIN = Regex("(?<![\\d.,])$NUM\\s*([A-Z]{2,6})(?![A-Za-z/\\-])")
    private val NOT_COINS = setOf("KRW", "USD", "USDT", "ETF", "LTE", "NYSE", "AMEX", "WON")
    private val PRICE = Regex("(?:현재\\s*가격|현재가|현재\\s*시세|시세|현재)$SEP$CUR$NUM")
    private val AVERAGE = Regex("(?:평균\\s*(?:매수|매입)?\\s*(?:단가|가격|가)|(?:매수|매입)\\s*평균\\s*가?|(?:매수|매입)\\s*(?:단가|가)|평단가?|평균)$SEP$CUR$NUM")
    private val VALUE = Regex("(?:평가\\s*(?:금액|액)|보유\\s*금액|평가(?!\\s*손익)(?!\\s*수익))$SEP$CUR$NUM")
    /** What was paid, never the value: taken out. */
    private val COST = Regex("(?:매입|매수|투자|원)\\s*(?:금액|원금|금)$SEP$CUR$NUM")
    private val PNL = Regex("(?:평가\\s*)?(?:손익|수익금?|이익|손실)(?!\\s*률)$SEP[+\\-−▲▼]?\\s*$CUR$NUM")
    private val RATE = Regex("(?:수익률$SEP)?[+\\-−▲▼]?\\s*$NUM\\s*%")
    private val SIGNED = Regex("(?<![\\d,.\\w])[+\\-−▲▼]\\s*$CUR$NUM")
    private val WON = Regex("(?<![\\d.,])$NUM\\s*(?:원|KRW)")
    private val DATE = Regex("(?<!\\d)20\\d{2}\\s*[./\\-년]\\s*\\d{1,2}\\s*[./\\-월]\\s*\\d{1,2}\\s*일?|(?<![\\d.,:])\\d{1,2}:\\d{2}(?::\\d{2})?(?![\\d:])")
    private val CODE_KR = Regex("(?<![\\d,.])(\\d{6})(?![\\d,.]|\\s*(?:원|주|개))")
    private val CODE_COIN = Regex("([A-Z0-9]{2,10})[-/](?:KRW|USDT)|(?:KRW|USDT)[-/]([A-Z0-9]{2,10})")
    private val TICKER = Regex("(?<![A-Za-z])([A-Z]{1,5})(?![A-Za-z])")
    private val NUMBER = Regex("(?<![\\d.,:])$CUR$NUM(?![\\d:])")
    /** A line that sums the account, which ends the holding above it ("총 평가금액", "합계", "예수금"). */
    private val TOTAL = Regex("^(?:총|합계|전체|예수금|주문\\s*가능|출금\\s*가능|D\\+\\d|총\\s*자산|내\\s*자산|원화|추정\\s*자산)")
    private val ETF_BRANDS = setOf("KODEX", "TIGER", "KBSTAR", "RISE", "ACE", "SOL", "HANARO", "ARIRANG", "KOSEF", "PLUS", "TIMEFOLIO", "KIWOOM")

    private enum class Col { QTY, AVERAGE, PRICE, VALUE, COST, PNL, RATE }
    /** A header's words in the order they sit, longest first where one holds another (평가손익 before 평가). */
    private val HEADER = Regex("평가\\s*손익|평가\\s*금액|평가액|보유\\s*수량|잔고\\s*수량|매입\\s*금액|매수\\s*금액|매입\\s*단가|매입가|평균\\s*단가|평균가|매수\\s*평균가|평단가?|현재가|수익률|손익|수량|보유량")

    private val LABEL_WORDS = setOf(
        "보유", "보유수량", "보유량", "수량", "잔고", "잔고수량", "현재가", "현재", "시세", "평균", "평균단가", "평균가", "평단", "평단가", "매입",
        "매입가", "매입금액", "매입단가", "매수", "매수금액", "매수평균가", "매수단가", "평가", "평가금액", "평가액", "평가손익", "손익", "수익",
        "수익률", "원금", "투자원금", "총", "합계", "총평가", "총매입", "예수금", "주", "개", "원", "달러", "KRW", "USD", "USDT", "국내", "해외",
        "주식", "전체", "보유종목", "종목", "종목명", "내", "계좌", "기준", "실시간", "정렬", "이름순", "평가순", "수익률순", "가나다순", "더보기",
        "투자", "자산", "내자산", "총자산", "현금", "원화", "환율", "증거금", "대출", "신용", "일반", "ISA", "연금", "연금저축", "IRP", "보유자산",
        "거래내역", "입출금", "투자내역", "코인", "주문가능", "출금가능", "매도가능", "평가금", "매수가", "잔고평가", "종목별", "수익금", "손실",
    )

    /** [FillSheetReader.clean]'s number fixes (O for 0, l for 1, "71, 500"), then each line trimmed. */
    fun read(text: String): List<Row> {
        val lines = FillSheetReader.clean(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val headers = lines.indices.filter { i -> HEADER.findAll(lines[i]).count() >= 3 && NUMBER.findAll(lines[i]).none() }
        val columns = headers.flatMap { columnsOf(lines[it]) }.takeIf { it.size >= 3 }
        // Where each holding starts: a line with its name, or after a line that sums the account.
        class Block(val name: String, val lines: MutableList<String>)
        val blocks = ArrayList<Block>()
        var current: Block? = null
        for ((i, line) in lines.withIndex()) {
            if (i in headers) { current = null; continue }
            if (TOTAL.containsMatchIn(line)) { current = null; continue }
            val name = nameOf(line)
            if (name.isNotEmpty()) {
                current = Block(name, mutableListOf(line)).also(blocks::add)
            } else current?.lines?.add(line)
        }
        return blocks.mapNotNull { b -> row(b.name, b.lines, columns) }
    }

    private fun columnsOf(header: String): List<Col> = HEADER.findAll(header).map { m ->
        val w = m.value.replace(Regex("\\s"), "")
        when {
            w.startsWith("평가손익") || w == "손익" -> Col.PNL
            w.startsWith("평가") -> Col.VALUE
            w == "수익률" -> Col.RATE
            w == "매입금액" || w == "매수금액" -> Col.COST
            w == "현재가" -> Col.PRICE
            w.contains("수량") || w == "보유량" -> Col.QTY
            else -> Col.AVERAGE
        }
    }.toList()

    private fun row(name: String, lines: List<String>, columns: List<Col>?): Row? {
        val all = lines.joinToString("\n")
        var rest = all
        fun cut(r: IntRange) { rest = rest.replaceRange(r, " ".repeat(r.last - r.first + 1)) }
        // The name first, so a number in it (KODEX 200) is no number of the row.
        rest.indexOf(name).takeIf { it >= 0 }?.let { cut(it until it + name.length) }
        DATE.findAll(rest).toList().forEach { cut(it.range) }
        var coin = CODE_COIN.find(rest)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
        CODE_COIN.find(rest)?.let { cut(it.range) }
        val krCode = CODE_KR.find(rest)?.groupValues?.get(1)
        CODE_KR.find(rest)?.let { cut(it.range) }
        val dollars = Regex("\\$|USD|달러").containsMatchIn(all)

        if (columns != null) {
            // A table under a header: every number in screen order, signs and % kept, fills the header's columns.
            val nums = Regex("[+\\-−▲▼]?\\s*$CUR$NUM\\s*%?").findAll(rest).map { m -> m.value }.toList()
            val got = HashMap<Col, Double>()
            columns.zip(nums).forEach { (c, v) -> number(v)?.let { got[c] = it } }
            val q = got[Col.QTY] ?: 0.0
            val p = got[Col.PRICE] ?: 0.0
            val v = got[Col.VALUE] ?: 0.0
            if (q > 0 && (p > 0 || v > 0)) return finish(name, krCode ?: coin, q, p, v, got[Col.AVERAGE] ?: 0.0, dollars, coin, all, labelled = true)
            // The columns did not line up (a row over two lines that the header does not show): read it as words.
        }

        // Words first: each taken out so no number is read twice.
        fun take(re: Regex): Double? = re.find(rest)?.let { m -> number(m.groupValues.last { g -> g.isNotEmpty() && g.first().isDigit() }).also { cut(m.range) } }
        RATE.findAll(rest).toList().forEach { cut(it.range) }
        PNL.findAll(rest).toList().forEach { cut(it.range) }
        COST.findAll(rest).toList().forEach { cut(it.range) }
        val average = take(AVERAGE) ?: 0.0
        val priceL = take(PRICE)
        val valueL = take(VALUE)
        var qtyL = QTY.firstNotNullOfOrNull { re -> re.find(rest)?.let { m -> number(m.groupValues[1])?.also { cut(m.range) } } }
        var unit = false
        if (qtyL == null) qtyL = (QTY_UNIT.find(rest) ?: QTY_COUNT.find(rest))?.let { m -> number(m.groupValues[1])?.also { cut(m.range); unit = true } }
        if (qtyL == null) QTY_COIN.findAll(rest).firstOrNull { it.groupValues[2] !in NOT_COINS }?.let { m ->
            qtyL = number(m.groupValues[1]); coin = coin ?: m.groupValues[2]; cut(m.range); unit = true
        }
        // A coin's code after its labelled quantity names the coin ("보유수량 0.0123 BTC").
        if (coin == null && krCode == null) QTY_COIN.findAll(all).firstOrNull { it.groupValues[2] !in NOT_COINS }?.let { coin = it.groupValues[2] }
        coin?.let { c -> Regex("(?<![A-Za-z])${Regex.escape(c)}(?![A-Za-z])").findAll(rest).toList().forEach { cut(it.range) } }
        SIGNED.findAll(rest).toList().forEach { cut(it.range) }
        // In dollars, a number in 원 beside it is the same value in won: not this row's currency.
        if (dollars) WON.findAll(rest).toList().forEach { cut(it.range) }
        val loose = NUMBER.findAll(rest).mapNotNull { number(it.groupValues[1]) }.filter { it > 0 && abs(it - average) > 1e-9 }.toList()

        var q = qtyL ?: 0.0
        var p = priceL ?: 0.0
        var v = valueL ?: 0.0
        var guessed = false
        when {
            q > 0 && p > 0 && v > 0 -> Unit
            q > 0 && p > 0 -> loose.firstOrNull { FillMath.agrees(q, p, it) }?.let { v = it }
            q > 0 && v > 0 -> loose.firstOrNull { FillMath.agrees(q, it, v) }?.let { p = it }
            q > 0 -> {
                // 수량 with two numbers that agree (현재가 and 평가금액), else one number: 평가금액 in a short list, asked.
                val pair = loose.flatMap { a -> loose.filter { it != a && it >= a }.map { a to it } }.firstOrNull { (a, b) -> FillMath.agrees(q, a, b) }
                if (pair != null) { p = pair.first; v = pair.second }
                else loose.singleOrNull()?.let { v = it; guessed = q != 1.0 }
            }
            p > 0 && v > 0 -> loose.firstOrNull { FillMath.agrees(it, p, v) }?.let { q = it; guessed = true }
            else -> {
                // Nothing said with its word: three numbers that agree, asked.
                val t = loose.indices.firstNotNullOfOrNull { i ->
                    loose.indices.firstNotNullOfOrNull { j ->
                        loose.indices.firstOrNull { k -> i != j && j != k && i != k && loose[k] >= loose[j] && FillMath.agrees(loose[i], loose[j], loose[k]) }
                            ?.let { k -> Triple(loose[i], loose[j], loose[k]) }
                    }
                }
                if (t != null) { q = t.first; p = t.second; v = t.third; guessed = true }
            }
        }
        if (q <= 0 || p <= 0 && v <= 0) return null
        return finish(name, krCode ?: coin ?: ticker(all, name), q, p, v, average, dollars, coin, all, labelled = !guessed && (qtyL != null || unit))
    }

    private fun finish(name: String, symbol: String?, q: Double, price: Double, value: Double, average: Double, dollars: Boolean, coin: String?,
                       all: String, labelled: Boolean): Row {
        val market = when {
            coin != null || Regex("\\d\\s*개").containsMatchIn(all) -> Market.CRYPTO
            dollars -> Market.US_STOCK
            name.substringBefore(' ').uppercase() in ETF_BRANDS -> Market.KR_ETF
            else -> Market.KR_STOCK
        }
        var p = price
        var fromValue = false
        if (p <= 0 && value > 0) { p = FillMath.round(value / q); fromValue = true }
        var unsure = !labelled
        if (value > 0 && price > 0 && !FillMath.agrees(q, price, value)) unsure = true
        // A Korean share is never a fraction, and its price is whole won.
        if ((market == Market.KR_STOCK || market == Market.KR_ETF) && (q != Math.floor(q) || fromValue && p != Math.floor(p))) unsure = true
        return Row(name, symbol.orEmpty(), q, p, value, average, market, fromValue, unsure)
    }

    private fun ticker(all: String, name: String): String? =
        TICKER.findAll(all.replace(name, " ")).map { it.groupValues[1] }.firstOrNull { it !in NOT_COINS && it !in LABEL_WORDS && it.length >= 2 }

    private fun number(s: String): Double? = s.replace(Regex("[^\\d.]"), "").toDoubleOrNull()?.let { v ->
        if (Regex("^\\s*[\\-−▼]").containsMatchIn(s)) -v else v
    }

    private fun isWord(w: String): Boolean =
        w.length >= 2 && w.any { it.isLetter() } && w.replace(Regex("[:：]"), "") !in LABEL_WORDS && !w.matches(Regex("[A-Z]{2,6}[-/][A-Z]{2,6}"))

    /**
     * The holding's name at the start of [line]: its first words that are not a label or a number, with an ETF brand's number
     * (KODEX 200). Empty for a line of numbers or words like 보유수량·평가금액.
     */
    internal fun nameOf(line: String): String {
        val tokens = line.replace(Regex("[()\\[\\]【】（）|·]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = tokens.firstOrNull() ?: return ""
        if (!isWord(first) || !first.first().isLetter() || LABEL_WORDS.any { first.startsWith(it) && first.length <= it.length + 1 && it.length >= 2 }) return ""
        // A code or ticker alone (BTC, AAPL) is a name only with nothing after it but its numbers.
        val words = mutableListOf(first)
        var j = 1
        while (j < tokens.size && words.size < 4) {
            val w = tokens[j]
            val goesOn = (first.uppercase() in ETF_BRANDS && j == 1 && w.all(Char::isDigit) && w.length <= 3) ||
                isWord(w) && !w.contains(',') && w.first().isLetter() && !Regex("[A-Z]{2,6}").matches(w) && !Regex("[\\d.]+(?:주|개|원)").matches(w) ||
                isWord(w) && Regex("[가-힣A-Za-z&]+\\d*[가-힣A-Za-z&]*").matches(w) && w.any { it in '가'..'힣' }
            if (!goesOn) break
            words += w; j++
        }
        return words.joinToString(" ")
    }
}

/**
 * 2.0.0 [RVK-26] Which holding of the book each 잔고 row is, and what the capture says that the book does not: a quantity that
 * differs ('빠진 매매'), or one that a whole split or merge explains ('분할인가요?', [RVK-27] 수량 바뀜).
 */
object HoldingShots {
    data class Found(val row: HoldingSheetReader.Row, val holding: Holding?, val price: Double, val unsure: Boolean) {
        /** The capture's quantity less the book's; null when they agree. */
        val gap: Double? get() = holding?.let { row.quantity - it.quantity }?.takeIf { abs(it) > 1e-6 }

        /**
         * New shares for one old one when a whole split (2, 5, 10…) or merge (1/2, 1/10…) explains the gap: 50주 in the capture
         * against 10 in the book is 5; 2 against 20 is 0.1.
         */
        val ratio: Double? get() {
            val h = holding ?: return null
            if (gap == null || h.quantity <= EPS) return null
            val r = row.quantity / h.quantity
            fun whole(x: Double) = x >= 2 - 1e-9 && abs(x - x.roundToLong()) < 1e-6
            return when {
                whole(r) -> r.roundToLong().toDouble()
                r > 0 && whole(1 / r) -> 1.0 / (1 / r).roundToLong()
                else -> null
            }
        }
    }

    /** Each row to the held holding with its code, else its name; each holding once. */
    fun match(rows: List<HoldingSheetReader.Row>, held: List<Holding>): List<Found> {
        val left = held.toMutableList()
        return rows.map { r ->
            val h = left.firstOrNull { it.symbol.isNotBlank() && r.symbol.isNotBlank() && it.symbol.equals(r.symbol, ignoreCase = true) }
                ?: left.firstOrNull { h ->
                    val a = FillMatcher.key(h.name.ifBlank { h.symbol })
                    val b = FillMatcher.key(r.name.ifBlank { r.symbol })
                    a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
                }
            h?.let(left::remove)
            var price = r.price
            var unsure = r.unsure
            if (h != null) {
                // One number beside the quantity was taken for 평가금액 (asked already); when the number itself is nearer what a share
                // cost (the book's cost over the capture's quantity, so a split is allowed for), it was the price.
                if (r.priceFromValue && r.unsure && h.costNative > 0 && r.quantity > 0 && r.value > 0) {
                    val expected = h.costNative / r.quantity
                    fun off(x: Double) = abs(kotlin.math.ln(x / expected))
                    if (off(r.value) < off(price)) price = r.value
                }
                // A US share read in won (or a Korean one in dollars): asked.
                if (h.market.foreign != r.market.foreign) unsure = true
            }
            Found(r, h, price, unsure)
        }
    }
}
