package com.bamnat.core

import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlertParser
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [INVA-04] 1.29 AR (사용자 2026-10-27 "투자기록이 제대로 잡히지 않고"): 체결 알림 글을 증권사·거래소마다 지어낸 틀(실제 알림을 옮긴
 * 것이 아니라 그 앱들이 쓰는 말투를 흉내 낸 것)로 앱마다 40개씩 만들어 읽어요. 매수/매도·종목·수량·단가·시장이 모두 맞아야 맞음,
 * 체결이 아닌 알림(접수·취소·미체결·예약·목표가)은 하나도 체결로 읽으면 안 돼요. 1.28(바꾸기 전) 숫자는 계획 문서 3장.
 */
class TradeAlertCorpusTest {
    private data class Case(val app: String, val pkg: String, val text: String, val side: Side?, val name: String, val code: String,
                            val q: Double, val p: Double, val market: Market)

    private val kr = listOf("삼성전자" to "005930", "카카오" to "035720", "현대차" to "005380", "SK하이닉스" to "000660", "NAVER" to "035420",
        "LG에너지솔루션" to "373220", "셀트리온" to "068270", "기아" to "000270")
    private val us = listOf("애플" to "AAPL", "엔비디아" to "NVDA", "테슬라" to "TSLA")
    private val coins = listOf("비트코인" to "BTC", "이더리움" to "ETH", "리플" to "XRP", "솔라나" to "SOL")

    private fun won(v: Double) = "%,d".format(v.toLong())
    private fun coinQty(v: Double) = v.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        val apps = listOf("키움", "키움2", "미래에셋", "삼성", "NH", "KB", "한국투자", "신한", "토스", "토스2", "카카오페이", "카카오페이2",
            "업비트", "업비트2", "빗썸", "코인원", "코빗", "토스해외", "키움해외")
        for ((ai, app) in apps.withIndex()) repeat(40) { n ->
            val r = Random(4000 + ai * 100 + n)
            val buy = r.nextBoolean()
            val side = if (buy) Side.BUY else Side.SELL
            fun s(b: String, sl: String) = if (buy) b else sl
            val (name, code) = kr[r.nextInt(kr.size)]
            val q = (1 + r.nextInt(150)).toDouble()
            val p = ((20 + r.nextInt(8000)) * 50).toDouble()
            val (cn, cc) = coins[r.nextInt(coins.size)]
            val cq = (1 + r.nextInt(99999)) / 100000.0
            val cp = (50 + r.nextInt(100)) * 1_000_000.0
            val (un, uc) = us[r.nextInt(us.size)]
            val up = (2000 + r.nextInt(60000)) / 100.0
            out += when (app) {
                "키움" -> Case(app, "com.kiwoom.heromts", "[키움] $name ${s("매수", "매도")}체결 ${won(q)}주 ${won(p)}원", side, name, "", q, p, Market.KR_STOCK)
                "키움2" -> Case(app, "com.kiwoom.heromts", "[체결]$name($code) ${s("매수", "매도")} ${won(q)}주 @${won(p)}", side, name, code, q, p, Market.KR_STOCK)
                "미래에셋" -> Case(app, "com.miraeasset.trade", "[미래에셋증권] 주식 체결 안내\n종목명: $name\n매매구분: ${s("매수", "매도")}\n체결수량: ${won(q)}주\n체결단가: ${won(p)}원",
                    side, name, "", q, p, Market.KR_STOCK)
                "삼성" -> Case(app, "com.samsungpop.android.mpop", "$name ${s("매수", "매도")} 체결 ${won(q)}주 / ${won(p)}원", side, name, "", q, p, Market.KR_STOCK)
                "NH" -> Case(app, "com.nh.mobilenh", "[NH투자증권] 체결통보 $name 현금${s("매수", "매도")} ${won(q)}주 ${won(p)}원", side, name, "", q, p, Market.KR_STOCK)
                "KB" -> Case(app, "com.kbsec.mts.iplustar", "[KB증권] $name ${s("매수", "매도")} ${won(q)}주 체결(평균가 ${won(p)}원)", side, name, "", q, p, Market.KR_STOCK)
                "한국투자" -> Case(app, "com.truefriend.neosmarta", "[한국투자증권] 체결안내 계좌:***-**-${1000 + n} 종목:$name($code) ${s("매수", "매도")} 체결수량:${won(q)}주 체결단가:${won(p)}원",
                    side, name, code, q, p, Market.KR_STOCK)
                "신한" -> Case(app, "com.shinhaninvest.nsmts", "[신한투자증권] $name ${s("매수", "매도")} 체결\n체결가 ${won(p)}원 체결량 ${won(q)}주", side, name, "", q, p, Market.KR_STOCK)
                "토스" -> Case(app, "viva.republica.toss", "$name ${won(q)}주 ${s("구매", "판매")} 완료\n1주당 ${won(p)}원", side, name, "", q, p, Market.KR_STOCK)
                "토스2" -> Case(app, "viva.republica.toss", "$name ${won(q)}주를 ${s("구매했어요", "판매했어요")} · 주당 ${won(p)}원", side, name, "", q, p, Market.KR_STOCK)
                "카카오페이" -> Case(app, "com.kakaopay.app", "$name ${won(q)}주를 ${won(p)}원에 ${s("샀어요", "팔았어요")}", side, name, "", q, p, Market.KR_STOCK)
                "카카오페이2" -> Case(app, "com.kakaopay.app", "[카카오페이증권] $name ${s("매수", "매도")} 체결 ${won(q)}주 · 총 ${won(q * p)}원", side, name, "", q, p, Market.KR_STOCK)
                "업비트" -> Case(app, "com.dunamu.exchange.upbit", "[업비트] $cn($cc) ${s("매수", "매도")} 체결\n체결수량 ${coinQty(cq)} $cc\n체결가격 ${won(cp)} KRW",
                    side, cn, cc, cq, cp, Market.CRYPTO)
                "업비트2" -> Case(app, "com.dunamu.exchange.upbit", "$cn($cc) ${s("매수", "매도")} 주문이 체결되었습니다. 체결수량: ${coinQty(cq)} $cc, 체결가격: ${won(cp)} KRW",
                    side, cn, cc, cq, cp, Market.CRYPTO)
                "빗썸" -> Case(app, "com.btckorea.bithumb", "[빗썸] $cn ${s("매수", "매도")} 체결\n수량 ${coinQty(cq)} $cc\n가격 ${won(cp)}원", side, cn, cc, cq, cp, Market.CRYPTO)
                "코인원" -> Case(app, "coinone.co.kr.official", "$cc ${s("매수", "매도")} 주문 체결 ${coinQty(cq)} $cc @ ${won(cp)} KRW", side, "", cc, cq, cp, Market.CRYPTO)
                "코빗" -> Case(app, "com.korbit.exchange", "$cc/KRW ${s("매수", "매도")} 체결 완료 수량 ${coinQty(cq)} 가격 ${won(cp)}원", side, "", cc, cq, cp, Market.CRYPTO)
                "토스해외" -> Case(app, "viva.republica.toss", "$un($uc) ${won(q % 20 + 1)}주 ${s("구매", "판매")} 완료 · 주당 \$${"%.2f".format(up)}",
                    side, un, uc, q % 20 + 1, up, Market.US_STOCK)
                else -> Case(app, "com.kiwoom.heromts", "[키움] 해외주식 체결 $un $uc ${s("매수", "매도")} ${won(q % 20 + 1)}주 \$${"%.2f".format(up)}",
                    side, un, uc, q % 20 + 1, up, Market.US_STOCK)
            }
        }
        // Not a fill: never read as one.
        val notFills = listOf(
            "[키움] 삼성전자 매수 주문 접수 10주 71,500원", "[미래에셋증권] 미체결 안내 카카오 5주", "[NH투자증권] 삼성전자 매수 주문이 취소되었습니다 10주",
            "삼성전자 예약 매수 주문 10주", "[업비트] 비트코인 목표가 도달 98,500,000 KRW", "[KB증권] 삼성전자 정정 주문 10주 71,000원",
            "카카오 10주 구매 주문을 접수했어요", "[토스증권] 삼성전자 가격 알림: 71,500원에 도달했어요", "[빗썸] 비트코인 매수 주문 접수 0.001 BTC",
        )
        notFills.forEach { out += Case("체결 아님", "com.kiwoom.heromts", it, null, "", "", 0.0, 0.0, Market.OTHER) }
        return out
    }

    private fun near(a: Double, b: Double) = abs(a - b) <= maxOf(1e-9, abs(b) * 1e-9)

    @Test fun everyBrokersAlertReads() {
        val byApp = LinkedHashMap<String, IntArray>()
        val bad = mutableListOf<String>()
        var wrongFills = 0
        for (c in cases()) {
            val a = TradeAlertParser.parse(c.text, c.pkg)
            val score = byApp.getOrPut(c.app) { IntArray(2) }
            score[0]++
            val ok = if (c.side == null) a == null else a != null && a.side == c.side && near(a.quantity, c.q) && near(a.price, c.p) && a.market == c.market &&
                (c.name.isNotEmpty() && a.name == c.name || c.code.isNotEmpty() && a.symbol == c.code)
            if (ok) score[1]++ else if (bad.size < 40) bad += "[${c.app}] ${c.text.replace("\n", "\\n")} → $a"
            if (c.side == null && a != null) wrongFills++
        }
        println("app\tcases\tright")
        byApp.forEach { (app, s) -> println("$app\t${s[0]}\t${s[1]}") }
        println("total\t${byApp.values.sumOf { it[0] }}\t${byApp.values.sumOf { it[1] }}")
        bad.forEach(::println)
        assertEquals("체결 아닌 알림을 체결로 읽음", 0, wrongFills)
        byApp.forEach { (app, s) -> assertTrue("$app ${s[1]}/${s[0]}", s[1] * 100 >= s[0] * 95) }
    }

    /** [INVA-06] A notification for a trade a capture wrote first is not a second trade. */
    @Test fun aNotificationForATradeWrittenAlreadyIsNotASecond() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val day = java.time.LocalDate.of(2026, 10, 27)
        val at = day.atTime(9, 14).atZone(zone).toInstant().toEpochMilli()
        val p = com.bamnat.core.invest.TradeAlerts.Pending(at, "키움", Side.BUY, "", "삼성전자", 10.0, 71_500.0, Market.KR_STOCK)
        fun t(minute: Int?, q: Double = 10.0, date: java.time.LocalDate = day) =
            com.bamnat.core.invest.Trade(id = 1, accountId = 1, date = date, minute = minute, symbol = "005930", name = "삼성전자", side = Side.BUY, quantity = q, price = 71_500.0)
        val w = com.bamnat.core.invest.TradeAlerts
        assertTrue(w.writtenAlready(p, listOf(t(9 * 60 + 12)), zone))
        assertTrue(w.writtenAlready(p.copy(price = 0.0), listOf(t(9 * 60 + 12)), zone))
        // Another quantity, another day, an hour apart, or a trade without its time: a trade of its own.
        assertTrue(!w.writtenAlready(p, listOf(t(9 * 60 + 12, q = 5.0)), zone))
        assertTrue(!w.writtenAlready(p, listOf(t(9 * 60 + 12, date = day.minusDays(1))), zone))
        assertTrue(!w.writtenAlready(p, listOf(t(10 * 60 + 30)), zone))
        assertTrue(!w.writtenAlready(p, listOf(t(null)), zone))
    }
}
