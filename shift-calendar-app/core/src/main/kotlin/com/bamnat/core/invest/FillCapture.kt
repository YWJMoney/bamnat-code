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
    private val SIDE = Regex("매수|매도|구매|판매|\\bbuy\\b|\\bsell\\b", RegexOption.IGNORE_CASE)
    private val SKIP = Regex("미체결|취소|거부|정정|접수|예약")
    private val QTY = listOf(
        Regex("(?:체결\\s*)?수량\\s*[:：]?\\s*$NUM"),
        Regex("$NUM\\s*(?:주|株)(?![가-힣])"),
        Regex("$NUM\\s*개(?![가-힣])"),
    )
    private val PRICE = listOf(
        Regex("(?:체결\\s*)?(?:단가|가격|평균가|평단가?|체결가|주당)\\s*[:：]?\\s*\\$?\\s*$NUM", RegexOption.IGNORE_CASE),
        Regex("[@＠]\\s*\\$?\\s*$NUM"),
    )
    private val AMOUNT = Regex("(?:(?:체결|약정|거래|정산|결제|매수|매도|구매|판매)\\s*(?:금액|대금)|총\\s*(?:체결\\s*)?(?:금액|액)|총액|합계|금액|총)\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val FEE = Regex("(?:수수료|제세금|세금|거래세|농특세|정산\\s*수수료)\\s*[:：]?\\s*\\$?\\s*$NUM")
    private val TIME = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?(?![\\d:])")
    private val DATE = Regex("(?<!\\d)(20\\d{2})[./\\-년]\\s*(\\d{1,2})[./\\-월]\\s*(\\d{1,2})일?|(?<![\\d.])(\\d{1,2})[./](\\d{1,2})(?![\\d.])")
    private val CODE_KR = Regex("(?<![\\d,.])(\\d{6})(?![\\d,.])")
    private val CODE_COIN = Regex("([A-Z0-9]{2,10})[-/](?:KRW|USDT)|(?:KRW|USDT)[-/]([A-Z0-9]{2,10})")
    private val TICKER = Regex("(?<![A-Za-z])([A-Z]{1,5})(?![A-Za-z])")
    private val LETTERS = Regex("[가-힣A-Za-z][가-힣A-Za-z0-9&.]*(?:\\s?[가-힣A-Za-z0-9&.]+)?")
    private val NUMBER = Regex("(?<![\\d.,:])\\$?$NUM(?![\\d:])")
    private val HEADER_WORDS = listOf("종목", "구분", "수량", "단가", "가격", "금액", "체결가", "체결시간", "시간")

    private val NOT_NAMES = setOf(
        "매수", "매도", "구매", "판매", "체결", "체결완료", "완료", "수량", "체결수량", "단가", "체결단가", "체결가", "체결가격", "가격", "금액",
        "체결금액", "거래금액", "약정금액", "정산금액", "총액", "합계", "총", "주문", "주문번호", "구분", "종목", "종목명", "시장가", "지정가",
        "보통", "현금", "신용", "국내", "해외", "주식", "원", "KRW", "USD", "USDT", "BTC", "달러", "수수료", "세금", "제세금", "거래세", "평균가",
        "평단", "주당", "체결내역", "거래내역", "주문내역", "내역", "전체", "오늘", "체결시간", "시간", "일자", "날짜", "주", "개", "BUY", "SELL",
        "Buy", "Sell", "ETF", "체결일", "체결일자", "정산", "결제", "매매", "내", "계좌", "투자내역", "체결통보",
    )

    /** OCR slips inside numbers: O for 0, l·I for 1, "71, 500" and "71.500" for 71,500. */
    fun clean(text: String): String {
        var t = text.replace('，', ',').replace('．', '.').replace(' ', ' ').replace('＄', '$')
        t = Regex("(?<=\\d)[Oo](?=[\\d,])|(?<=[\\d,])[Oo](?=\\d)").replace(t, "0")
        t = Regex("(?<=\\d)[lI|](?=[\\d,])|(?<=[\\d,])[lI|](?=\\d)").replace(t, "1")
        t = Regex("(?<=\\d),\\s+(?=\\d{3}(?!\\d))").replace(t, ",")
        // A period between groups of three, with no other decimal after it, is a thousands mark in 원.
        t = Regex("(?<![\\d.])(\\d{1,3}(?:\\.\\d{3}){1,3})(?=\\s*(?:원|KRW))").replace(t) { it.value.replace(".", ",") }
        return t
    }

    fun read(text: String): List<Fill> {
        val lines = clean(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.indexOfFirst { l -> HEADER_WORDS.count { it in l } >= 3 }.takeIf { it >= 0 }
        val columns = header?.let { columnsOf(lines[it]) }
        val sides = lines.indices.filter { i -> i != header && SIDE.containsMatchIn(lines[i]) }
        if (sides.isEmpty()) return emptyList()
        val fills = ArrayList<Fill>()
        val taken = BooleanArray(lines.size)
        header?.let { taken[it] = true }
        for ((k, i) in sides.withIndex()) {
            val end = (sides.getOrNull(k + 1) ?: lines.size) - 1
            // A name on its own line just above the side ("삼성전자" over "매수 체결") belongs to this fill.
            val start = if (i > 0 && !taken[i - 1] && looksLikeName(lines[i - 1]) && !looksLikeName(lines[i]) &&
                (sides.getOrNull(k + 1)?.let { it - 1 } ?: -1) != i - 1) i - 1 else i
            var stop = end
            // The next fill's name line stays with it.
            sides.getOrNull(k + 1)?.let { n -> if (n - 1 > i && looksLikeName(lines[n - 1]) && !looksLikeName(lines[n])) stop = n - 2 }
            val record = lines.subList(start, stop + 1)
            for (j in start..stop) taken[j] = true
            if (SKIP.containsMatchIn(record.joinToString(" "))) continue
            fill(record, columns)?.let(fills::add)
        }
        return fills
    }

    private fun looksLikeName(line: String): Boolean {
        if (SIDE.containsMatchIn(line) || line.any { it.isDigit() } && !CODE_KR.containsMatchIn(line) && !CODE_COIN.containsMatchIn(line)) return false
        return nameOf(line).isNotEmpty()
    }

    private enum class Col { QTY, PRICE, AMOUNT }

    private fun columnsOf(header: String): List<Col> {
        val found = listOfNotNull(
            Regex("수량").find(header)?.let { it.range.first to Col.QTY },
            Regex("단가|체결가|가격|평균가").find(header)?.let { it.range.first to Col.PRICE },
            Regex("금액").find(header)?.let { it.range.first to Col.AMOUNT },
        )
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun fill(record: List<String>, columns: List<Col>?): Fill? {
        val all = record.joinToString(" ")
        val sideWord = SIDE.find(all)?.value?.lowercase() ?: return null
        val side = if (sideWord in setOf("매수", "구매", "buy")) Side.BUY else Side.SELL
        var rest = all
        fun cut(r: IntRange) { rest = rest.replaceRange(r, " ".repeat(r.last - r.first + 1)) }
        // Dates, times, fees and codes are taken out first, so no number is read twice.
        var date: LocalDate? = null
        DATE.findAll(rest).toList().forEach { m ->
            if (date == null) date = runCatching {
                if (m.groupValues[1].isNotEmpty()) LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                else LocalDate.of(com.bamnat.core.time.Today.date().year, m.groupValues[4].toInt(), m.groupValues[5].toInt())
            }.getOrNull()
            if (m.groupValues[1].isNotEmpty()) cut(m.range)
        }
        val timeMatch = TIME.find(rest)
        val time = timeMatch?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull() ?: 0) }
        timeMatch?.let { cut(it.range) }
        if (date == null) DATE.find(rest)?.let { m -> if (m.groupValues[4].isNotEmpty()) cut(m.range) }
        FEE.findAll(rest).toList().forEach { cut(it.range) }
        val coinCode = CODE_COIN.find(rest)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
        CODE_COIN.find(rest)?.let { cut(it.range) }
        val krCode = CODE_KR.find(rest)?.groupValues?.get(1)
        CODE_KR.find(rest)?.let { cut(it.range) }
        val dollars = all.contains('$') || Regex("USD|달러").containsMatchIn(all)
        val name = record.asSequence().map(::nameOf).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (name.isNotEmpty()) rest.indexOf(name).takeIf { it >= 0 }?.let { cut(it until it + name.length) }

        fun labeled(res: List<Regex>): Pair<Double, IntRange>? = res.firstNotNullOfOrNull { re ->
            re.find(rest)?.let { m -> num(m.groupValues[1])?.takeIf { it > 0 }?.let { it to m.range } }
        }
        val qtyL = labeled(QTY)?.also { cut(it.second) }?.first
        val priceL = labeled(PRICE)?.also { cut(it.second) }?.first
        val amountL = labeled(listOf(AMOUNT))?.also { cut(it.second) }?.first
        // What is left: numbers without a word, in the order they sit.
        val loose = NUMBER.findAll(rest).mapNotNull { num(it.groupValues[1]) }.filter { it > 0 }.toList()

        var q = qtyL
        var p = priceL
        var a = amountL
        val guessed = HashSet<Field>()
        if (columns != null && loose.isNotEmpty()) {
            // A table under a header: the loose numbers fill the header's columns in order.
            val free = columns.filter { c -> when (c) { Col.QTY -> q == null; Col.PRICE -> p == null; Col.AMOUNT -> a == null } }
            free.zip(loose).forEach { (c, v) -> when (c) { Col.QTY -> q = v; Col.PRICE -> p = v; Col.AMOUNT -> a = v } }
        } else if (loose.isNotEmpty()) {
            val pool = loose.toMutableList()
            fun take(v: Double) { pool.remove(v) }
            when {
                q != null && p != null && a == null -> pool.firstOrNull { FillMath.agrees(q!!, p!!, it) }?.let { a = it; take(it) }
                q != null && p == null && a != null -> pool.firstOrNull { FillMath.agrees(q!!, it, a!!) }?.let { p = it; take(it) }
                q == null && p != null && a != null -> pool.firstOrNull { FillMath.agrees(it, p!!, a!!) }?.let { q = it; take(it) }
                q != null && p == null && a == null -> pairFor(pool, q!!)?.let { (pp, aa) -> p = pp; a = aa; guessed += setOf(Field.PRICE, Field.AMOUNT) }
                q == null && p == null && a == null -> tripleAny(pool)?.let { (qq, pp, aa) ->
                    q = qq; p = pp; a = aa; guessed += setOf(Field.QUANTITY, Field.PRICE, Field.AMOUNT)
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
            TICKER.findAll(all).map { it.groupValues[1] }.firstOrNull { it !in NOT_NAMES && it !in setOf("KRW", "USD", "ETF", "NYSE", "NASDAQ", "AMEX") }
        else null
        val market = when {
            coinCode != null || Regex("\\d\\s*(?:개|BTC|ETH|XRP|SOL|DOGE)\\b").containsMatchIn(all) -> Market.CRYPTO
            dollars -> Market.US_STOCK
            else -> Market.KR_STOCK
        }
        val unsure = HashSet<Field>(guessed)
        if (name.isEmpty() && krCode == null && coinCode == null && ticker == null) unsure += Field.NAME
        if (price <= 0) unsure += Field.PRICE
        if (amount > 0 && price > 0 && !FillMath.agrees(quantity, price, amount)) unsure += setOf(Field.PRICE, Field.AMOUNT)
        // A share is never a fraction on a Korean screen: a decimal there is a misread.
        if (market == Market.KR_STOCK && quantity != Math.floor(quantity)) unsure += Field.QUANTITY
        return Fill(side, name, krCode ?: coinCode ?: ticker.orEmpty(), quantity, price, amount, market, date, time, unsure, fromAmount)
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
        val tokens = line.replace(Regex("[()\\[\\]【】（）|·:：]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val i = tokens.indexOfFirst { w -> isWord(w) && (w.any { it in '가'..'힣' } || w.length >= 3 && !w.matches(Regex("[A-Z]{1,5}")) || w in ETF_BRANDS || w.all { it.isUpperCase() || it.isDigit() } && w.length >= 4) }
        if (i < 0) return ""
        val words = mutableListOf(tokens[i])
        var j = i + 1
        while (j < tokens.size && words.size < 4) {
            val w = tokens[j]
            val goesOn = (tokens[i] in ETF_BRANDS && (w.all(Char::isDigit) && w.length <= 3 || isWord(w) && !SIDE.matches(w))) ||
                (words.last() in ETF_BRANDS && isWord(w) && !SIDE.matches(w))
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
