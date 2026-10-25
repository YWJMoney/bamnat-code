package com.bamnat.core

import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Side
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [INV-40] 1.25 통합 직접 시험 (사용자 2026-10-25 "캡쳐는 명확히 인식을 해서 입력 버그가 존재해서는 안돼"): 체결 내역 캡처 글
 * 900가지 — 표 한 줄, 이름 위 카드, 머리글 표(칸 순서 섞음), 달러, 코인 — 에 날짜·수수료 줄과 글자 인식 흔들림(쉼표가 점, 쉼표 뒤 띄어쓰기,
 * O↔0, l↔1)을 섞어요. 깨끗한 글은 모두 맞아야 하고, 흔들린 글에서도 틀린 숫자가 '확인 없이'(노란 표시 없이) 들어가면 안 돼요.
 */
class FillStressTest {
    data class Truth(val side: Side, val name: String, val code: String, val q: Double, val p: Double, val a: Double)

    private val kr = listOf("삼성전자" to "005930", "카카오" to "035720", "현대차" to "005380", "SK하이닉스" to "000660",
        "LG에너지솔루션" to "373220", "NAVER" to "035420", "POSCO홀딩스" to "005490", "기아" to "000270", "셀트리온" to "068270")
    private val us = listOf("애플" to "AAPL", "엔비디아" to "NVDA", "테슬라" to "TSLA", "마이크로소프트" to "MSFT")
    private val coins = listOf("비트코인" to "BTC", "이더리움" to "ETH", "리플" to "XRP", "솔라나" to "SOL")

    private fun won(v: Double) = "%,d".format(v.toLong())
    private fun usd(v: Double) = "%,.2f".format(v)

    /** OCR noise in one number token. */
    private fun shake(r: Random, s: String, noise: Boolean): String {
        if (!noise) return s
        var t = s
        when (r.nextInt(10)) {
            0 -> t = t.replace(",", ".").let { if (s.contains('.')) s else it } // 71,500 → 71.500 (won only)
            1 -> t = t.replaceFirst(",", ", ")
            2 -> t = t.replaceFirst(Regex("(?<=\\d)0"), "O")
            3 -> t = t.replaceFirst(Regex("(?<=\\d)1"), "l")
        }
        return t
    }

    private fun krRow(r: Random, noise: Boolean, out: StringBuilder, truths: MutableList<Truth>, layout: Int) {
        val (name, code) = kr[r.nextInt(kr.size)]
        val q = (1 + r.nextInt(200)).toDouble()
        val p = ((1 + r.nextInt(8000)) * 50).toDouble()
        val a = q * p
        val side = if (r.nextBoolean()) Side.BUY else Side.SELL
        val sw = if (side == Side.BUY) listOf("매수", "구매").random(r) else listOf("매도", "판매").random(r)
        val hh = 9 + r.nextInt(6); val mm = r.nextInt(60)
        val time = "%02d:%02d:%02d".format(hh, mm, r.nextInt(60))
        truths += Truth(side, name, code, q, p, a)
        when (layout) {
            0 -> out.appendLine("$name $code $sw ${q.toLong()} ${shake(r, won(p), noise)} ${shake(r, won(a), noise)} $time")
            1 -> {
                out.appendLine(name)
                out.appendLine("$sw 체결" + if (r.nextBoolean()) " 2026.10.${10 + r.nextInt(15)} $time" else "")
                val unit = if (r.nextBoolean()) "원" else ""
                val lines = mutableListOf("체결수량 ${q.toLong()}주", "체결단가 ${shake(r, won(p), noise)}$unit", "체결금액 ${shake(r, won(a), noise)}$unit")
                if (r.nextBoolean()) lines += "수수료 ${won((a * 0.00015).coerceAtLeast(1.0))}원"
                if (r.nextInt(3) == 0) lines.shuffle(r)
                lines.forEach(out::appendLine)
            }
            else -> {
                out.appendLine("$name ($code) $sw ${q.toLong()}주 @${shake(r, won(p), noise)} 총 ${shake(r, won(a), noise)}원")
            }
        }
    }

    private fun header(r: Random, noise: Boolean, out: StringBuilder, truths: MutableList<Truth>) {
        val order = listOf("Q", "P", "A").shuffled(r)
        val words = mapOf("Q" to "체결수량", "P" to listOf("체결가", "단가", "체결단가").random(r), "A" to "체결금액")
        out.appendLine("종목명 구분 " + order.joinToString(" ") { words.getValue(it) } + " 시간")
        repeat(1 + r.nextInt(4)) {
            val (name, code) = kr[r.nextInt(kr.size)]
            val q = (1 + r.nextInt(300)).toDouble(); val p = ((1 + r.nextInt(9000)) * 50).toDouble(); val a = q * p
            val side = if (r.nextBoolean()) Side.BUY else Side.SELL
            truths += Truth(side, name, "", q, p, a)
            val v = mapOf("Q" to q.toLong().toString(), "P" to shake(r, won(p), noise), "A" to shake(r, won(a), noise))
            out.appendLine("$name ${if (side == Side.BUY) "매수" else "매도"} " + order.joinToString(" ") { v.getValue(it) } + " %02d:%02d".format(9 + r.nextInt(6), r.nextInt(60)))
        }
    }

    private fun usRow(r: Random, noise: Boolean, out: StringBuilder, truths: MutableList<Truth>) {
        val (name, tk) = us[r.nextInt(us.size)]
        val q = (1 + r.nextInt(40)).toDouble()
        val p = (1000 + r.nextInt(90000)) / 100.0
        val a = Math.round(q * p * 100) / 100.0
        val side = if (r.nextBoolean()) Side.BUY else Side.SELL
        truths += Truth(side, name, tk, q, p, a)
        val sw = if (side == Side.BUY) "매수" else "매도"
        val pn = usd(p).let { if (noise && r.nextInt(4) == 0) it.replaceFirst(Regex("(?<=\\d)0"), "O") else it }
        if (r.nextBoolean()) out.appendLine("$name $tk $sw ${q.toLong()}주 \$$pn \$${usd(a)} %02d:%02d".format(22 + r.nextInt(2), r.nextInt(60)))
        else {
            out.appendLine("$name $tk"); out.appendLine("$sw 체결 ${q.toLong()}주"); out.appendLine("체결가 \$$pn"); out.appendLine("체결금액 \$${usd(a)}")
        }
    }

    private fun coinRow(r: Random, out: StringBuilder, truths: MutableList<Truth>) {
        val (name, c) = coins[r.nextInt(coins.size)]
        val big = c == "BTC" || c == "ETH"
        val q = if (big) (1 + r.nextInt(99999)) / 100000.0 else (1 + r.nextInt(500)).toDouble()
        val p = if (big) ((50 + r.nextInt(100)) * 1_000_000).toDouble() else (100 + r.nextInt(300000)) / 10.0
        val a = Math.round(q * p).toDouble()
        val side = if (r.nextBoolean()) Side.BUY else Side.SELL
        truths += Truth(side, name, c, q, p, a)
        out.appendLine("$name $c/KRW ${if (side == Side.BUY) "매수" else "매도"} 2026.10.24 14:%02d".format(r.nextInt(60)))
        out.appendLine("체결수량 ${q.toBigDecimal().stripTrailingZeros().toPlainString()} $c")
        out.appendLine("체결가격 ${if (p == Math.floor(p)) won(p) else "%,.1f".format(p)} KRW")
        out.appendLine("체결금액 ${won(a)} KRW")
        out.appendLine("수수료 ${"%.2f".format(a * 0.0005)} KRW")
    }

    private fun near(x: Double, y: Double) = abs(x - y) <= maxOf(0.011, abs(y) * 1e-9)

    @Test fun nineHundredCapturesNeverFillAWrongNumberQuietly() {
        val r = Random(409)
        var cases = 0; var perfect = 0; var silentWrong = 0; var missed = 0; var cleanBad = 0
        val bad = mutableListOf<String>()
        repeat(900) { n ->
            val noise = n % 3 != 0
            val out = StringBuilder(listOf("체결내역", "주문체결", "거래내역", "체결 내역 오늘").random(r) + "\n")
            val truths = mutableListOf<Truth>()
            when (val kind = r.nextInt(6)) {
                0, 1 -> repeat(1 + r.nextInt(4)) { krRow(r, noise, out, truths, kind) }
                2 -> header(r, noise, out, truths)
                3 -> repeat(1 + r.nextInt(3)) { krRow(r, noise, out, truths, 2) }
                4 -> repeat(1 + r.nextInt(3)) { usRow(r, noise, out, truths) }
                else -> repeat(1 + r.nextInt(3)) { coinRow(r, out, truths) }
            }
            val got = FillSheetReader.read(out.toString())
            cases++
            var allRight = got.size == truths.size
            // The reader keeps screen order and may leave a row out (it then stays for the user to type): line them up by holding.
            var j = 0
            for (t in truths) {
                val f = got.getOrNull(j)?.takeIf { it.name == t.name || it.symbol == t.code }
                if (f == null) { missed++; allRight = false; continue }
                j++
                val right = f.side == t.side && near(f.quantity, t.q) && near(f.price, t.p) && (f.amount == 0.0 || near(f.amount, t.a))
                if (!right || !f.sure) allRight = false
                if (!right && f.sure) { silentWrong++; if (bad.size < 25) bad += "#$n $t → $f\n$out" }
            }
            if (got.size > j && got.drop(j).any { it.sure }) { silentWrong++; if (bad.size < 25) bad += "#$n extra ${got.drop(j)}\n$out" }
            if (allRight) perfect++ else if (!noise) { cleanBad++; if (bad.size < 25) bad += "#$n CLEAN $truths → $got\n$out" }
        }
        println("fill stress: cases=$cases perfect=$perfect silentWrong=$silentWrong missed=$missed cleanBad=$cleanBad")
        bad.forEach(::println)
        assertEquals("깨끗한 캡처가 틀림", 0, cleanBad)
        assertEquals("틀린 숫자가 확인 없이 들어감", 0, silentWrong)
    }
}
