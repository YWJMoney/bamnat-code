package com.bamnat.core

import com.bamnat.core.invest.Side
import com.bamnat.core.pay.OcrLine
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * [INVA-03] 1.29 AR: 지어낸 체결 내역 화면(실제 앱 화면을 옮긴 것이 아니라 그 앱들이 쓰는 배치를 흉내 낸 것) — 국내 증권사 여덟
 * (키움·미래에셋·삼성·NH·KB·한국투자·토스·카카오페이)과 코인 거래소 셋(업비트·빗썸·코인원)·해외 주식 하나의 체결 내역을 글자 상자
 * ([OcrLine], 픽셀 자리)로 그리고, 글자 인식이 흔히 틀리는 곳(쉼표가 점, 쉼표 뒤 띄어쓰기, O↔0, l↔1, 상자 흔들림·기울기, 맨 위 상태 줄,
 * 주문번호·계좌 줄)을 섞어요. 시험은 이 상자를 [com.bamnat.core.pay.OcrRows]로 줄로 묶어 휴대폰과 같은 길로 읽어요.
 */
object FillCorpus {
    data class Truth(
        val side: Side, val name: String, val code: String, val q: Double, val p: Double, val a: Double,
        /** Null when the screen does not show the day (오늘 목록). */
        val date: LocalDate?, val time: LocalTime,
    )

    class Shot(val app: String, val lines: List<OcrLine>, val truths: List<Truth>, val noisy: Boolean)

    val APPS = listOf("키움", "미래에셋", "삼성", "NH", "KB", "한국투자", "토스", "카카오페이", "업비트", "빗썸", "코인원", "해외")

    private val kr = listOf("삼성전자" to "005930", "카카오" to "035720", "현대차" to "005380", "SK하이닉스" to "000660",
        "LG에너지솔루션" to "373220", "NAVER" to "035420", "기아" to "000270", "셀트리온" to "068270", "KODEX 200" to "069500",
        "TIGER 미국S&P500" to "360750", "삼성SDI" to "006400", "한화에어로스페이스" to "012450")
    private val us = listOf("애플" to "AAPL", "엔비디아" to "NVDA", "테슬라" to "TSLA", "마이크로소프트" to "MSFT")
    private val coins = listOf("비트코인" to "BTC", "이더리움" to "ETH", "리플" to "XRP", "솔라나" to "SOL", "도지코인" to "DOGE")

    private fun won(v: Double) = "%,d".format(v.toLong())
    private fun usd(v: Double) = "%,.2f".format(v)
    private fun coinQty(v: Double) = v.toBigDecimal().stripTrailingZeros().toPlainString()

    private class Canvas(val r: Random, val noisy: Boolean) {
        val lines = mutableListOf<OcrLine>()
        var y = 60
        /** A slight tilt of the whole capture (a screenshot is straight; a photo of a screen is not). */
        val tilt = if (noisy && r.nextInt(4) == 0) r.nextDouble(-0.02, 0.02) else 0.0

        fun shake(t: String): String {
            if (!noisy || r.nextInt(100) >= 22) return t
            return when (r.nextInt(5)) {
                0 -> if (t.contains('$') || t.contains("BTC") || t.contains("ETH") || Regex("\\d\\.\\d").containsMatchIn(t)) t
                    else t.replaceFirst(Regex("(?<=\\d),(?=\\d{3})"), ".")
                1 -> t.replaceFirst(Regex("(?<=\\d),(?=\\d{3})"), ", ")
                2 -> t.replaceFirst(Regex("(?<=\\d)0(?=[\\d,])"), "O")
                3 -> t.replaceFirst(Regex("(?<=\\d)1(?=[\\d,])"), "l")
                else -> t
            }
        }

        /** One screen row: (x, text) boxes left to right. */
        fun row(vararg parts: Pair<Int, String>) {
            var right = 0
            for ((at, t) in parts) {
                if (t.isBlank()) continue
                // A long name pushes the next column on, as a screen's own layout does.
                val x = maxOf(at, right + 24)
                val w = t.length * 26 + 10
                right = x + w
                val dy = (x * tilt).toInt() + if (noisy) r.nextInt(-3, 4) else 0
                lines += OcrLine(shake(t), x, y + dy, x + w, y + dy + 34)
            }
            y += 54 + if (noisy) r.nextInt(0, 6) else 0
        }

        fun gap() { y += 30 }

        fun chrome() {
            row(30 to "9:41", 900 to "LTE 87%")
            gap()
        }
    }

    private fun side(r: Random) = if (r.nextBoolean()) Side.BUY else Side.SELL

    private fun krFill(r: Random, day: LocalDate, shown: Boolean): Truth {
        val (n, c) = kr[r.nextInt(kr.size)]
        val q = (1 + r.nextInt(120)).toDouble()
        val p = ((20 + r.nextInt(7000)) * 50).toDouble()
        return Truth(side(r), n, c, q, p, q * p, if (shown) day else null, LocalTime.of(9 + r.nextInt(6), r.nextInt(60), r.nextInt(60)))
    }

    private fun usFill(r: Random, day: LocalDate): Truth {
        val (n, c) = us[r.nextInt(us.size)]
        val q = (1 + r.nextInt(30)).toDouble()
        val p = (2000 + r.nextInt(60000)) / 100.0
        return Truth(side(r), n, c, q, p, Math.round(q * p * 100) / 100.0, day, LocalTime.of(22 + r.nextInt(2), r.nextInt(60)))
    }

    private fun coinFill(r: Random, day: LocalDate): Truth {
        val (n, c) = coins[r.nextInt(coins.size)]
        val big = c == "BTC" || c == "ETH"
        val q = if (big) (1 + r.nextInt(99999)) / 100000.0 else (1 + r.nextInt(900)).toDouble()
        val p = when (c) { "BTC" -> (90 + r.nextInt(20)) * 1_000_000.0; "ETH" -> (3 + r.nextInt(3)) * 1_000_000.0 + r.nextInt(10) * 1000; else -> (100 + r.nextInt(3000)).toDouble() }
        return Truth(side(r), n, c, q, p, Math.round(q * p).toDouble(), day, LocalTime.of(r.nextInt(24), r.nextInt(60), r.nextInt(60)))
    }

    private fun hms(t: LocalTime) = "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
    private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
    private fun ampm(t: LocalTime) = (if (t.hour < 12) "오전 " else "오후 ") + "%d:%02d".format(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute)
    private fun ymd(d: LocalDate, sep: String) = "%d%s%02d%s%02d".format(d.year, sep, d.monthValue, sep, d.dayOfMonth)
    private fun md(d: LocalDate) = "%02d.%02d".format(d.monthValue, d.dayOfMonth)
    private fun kw(s: Side, buy: String, sell: String) = if (s == Side.BUY) buy else sell

    /** One capture of [app] with one to four fills on [today] or the days before. */
    fun shot(app: String, r: Random, today: LocalDate, noisy: Boolean): Shot {
        val c = Canvas(r, noisy)
        val truths = mutableListOf<Truth>()
        val n = 1 + r.nextInt(4)
        val day = today.minusDays(r.nextInt(0, 6).toLong())
        c.chrome()
        when (app) {
            // A '오늘 체결' list: two screen rows a fill, no date on the screen, 주문수량 beside 체결수량.
            "키움" -> {
                c.row(30 to "주문체결", 400 to "체결내역")
                c.row(30 to "종목명", 330 to "구분", 520 to "체결수량", 760 to "체결단가")
                c.row(30 to "시간", 330 to "상태", 520 to "주문수량", 760 to "체결금액")
                repeat(n) {
                    val t = krFill(r, day, false).also(truths::add)
                    c.row(30 to t.name, 330 to kw(t.side, "현금매수", "현금매도"), 560 to won(t.q), 760 to won(t.p))
                    c.row(30 to hms(t.time), 330 to "체결", 560 to won(t.q), 760 to won(t.a))
                }
            }
            // Cards: label on the left, value on the right; the day and time under the name.
            "미래에셋" -> {
                c.row(30 to "거래내역", 600 to "주문번호 " + (10_000_000 + r.nextInt(80_000_000)))
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to t.name, 700 to kw(t.side, "매수", "매도"))
                    c.row(30 to "${ymd(day, ".")} ${hm(t.time)}", 700 to "체결완료")
                    c.row(30 to "체결수량", 700 to "${won(t.q)}주")
                    c.row(30 to "체결단가", 700 to "${won(t.p)}원")
                    c.row(30 to "체결금액", 700 to "${won(t.a)}원")
                    c.gap()
                }
            }
            // One table row a fill with the day as MM/DD.
            "삼성" -> {
                c.row(30 to "체결내역", 500 to "계좌 ***-**-" + (1000 + r.nextInt(9000)))
                c.row(30 to "일자", 160 to "시간", 300 to "종목명", 560 to "구분", 650 to "수량", 760 to "단가", 900 to "금액")
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to "%02d/%02d".format(day.monthValue, day.dayOfMonth), 160 to hm(t.time), 300 to t.name, 560 to kw(t.side, "매수", "매도"),
                        650 to won(t.q), 760 to won(t.p), 900 to won(t.a))
                }
            }
            // A day header, then cards whose labels are 체결가·체결량.
            "NH" -> {
                c.row(30 to "체결/미체결")
                c.row(30 to ymd(day, "."))
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to t.name, 640 to kw(t.side, "매수", "매도"), 820 to hms(t.time))
                    c.row(30 to "체결가", 300 to won(t.p), 520 to "체결량", 760 to won(t.q))
                    c.row(30 to "체결금액", 300 to won(t.a))
                    c.gap()
                }
            }
            // 주문 n주 · 체결 n주, 평균체결가.
            "KB" -> {
                c.row(30 to "주문내역")
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to kw(t.side, "매수체결", "매도체결"), 300 to t.name)
                    c.row(30 to "주문 ${won(t.q)}주 · 체결 ${won(t.q)}주")
                    c.row(30 to "평균체결가", 600 to "${won(t.p)}원")
                    c.row(30 to "체결금액", 600 to "${won(t.a)}원")
                    c.row(30 to "${md(day)} ${hms(t.time)}")
                    c.gap()
                }
            }
            // A header table led by 체결시각, the code in its own column.
            "한국투자" -> {
                c.row(30 to "일별 체결내역", 600 to ymd(day, "-"))
                c.row(30 to "체결시각", 200 to "종목명", 460 to "종목코드", 600 to "구분", 690 to "체결수량", 820 to "체결단가", 960 to "체결금액")
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to hms(t.time), 200 to t.name, 460 to t.code, 600 to kw(t.side, "매수", "매도"), 700 to won(t.q), 820 to won(t.p), 960 to won(t.a))
                }
            }
            // A day header (10월 27일), the amount on the right, 오전·오후 times, 1주당 price.
            "토스" -> {
                c.row(30 to "주문내역")
                c.row(30 to "${day.monthValue}월 ${day.dayOfMonth}일")
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to t.name, 700 to "${won(t.a)}원")
                    c.row(30 to "${won(t.q)}주 ${kw(t.side, "구매", "판매")} · ${ampm(t.time)}")
                    c.row(30 to "1주당 ${won(t.p)}원")
                    c.gap()
                }
            }
            // 샀어요·팔았어요, 총 금액.
            "카카오페이" -> {
                c.row(30 to "거래내역")
                repeat(n) {
                    val t = krFill(r, day, true).also(truths::add)
                    c.row(30 to "${t.name} ${won(t.q)}주 ${kw(t.side, "샀어요", "팔았어요")}")
                    c.row(30 to "1주 ${won(t.p)}원 · 총 ${won(t.a)}원")
                    c.row(30 to "${md(day)} ${hm(t.time)}")
                    c.gap()
                }
            }
            // The day and time above each fill, 거래수량·거래단가·거래금액, 수수료 and 정산금액 under it.
            "업비트" -> {
                c.row(30 to "거래내역", 500 to "체결", 700 to "미체결")
                repeat(n) {
                    val t = coinFill(r, day).also(truths::add)
                    val fee = Math.round(t.a * 0.0005 * 100) / 100.0
                    c.row(30 to "${ymd(day, ".")} ${hm(t.time)}")
                    c.row(30 to "${t.name} ${t.code}/KRW", 760 to kw(t.side, "매수", "매도"))
                    c.row(30 to "거래수량", 600 to "${coinQty(t.q)} ${t.code}")
                    c.row(30 to "거래단가", 600 to "${won(t.p)} KRW")
                    c.row(30 to "거래금액", 600 to "${won(t.a)} KRW")
                    c.row(30 to "수수료", 600 to "%.2f KRW".format(fee))
                    c.row(30 to "정산금액", 600 to "${won(if (t.side == Side.BUY) t.a + fee else t.a - fee)} KRW")
                    c.gap()
                }
            }
            "빗썸" -> {
                c.row(30 to "체결내역")
                repeat(n) {
                    val t = coinFill(r, day).also(truths::add)
                    c.row(30 to kw(t.side, "매수", "매도"), 200 to t.name, 700 to "${ymd(day, "-")} ${hms(t.time)}")
                    c.row(30 to "체결가", 600 to "${won(t.p)}원")
                    c.row(30 to "체결량", 600 to "${coinQty(t.q)} ${t.code}")
                    c.row(30 to "체결금액", 600 to "${won(t.a)}원")
                    c.gap()
                }
            }
            // Only the code (BTC) names the coin.
            "코인원" -> {
                c.row(30 to "체결 내역")
                repeat(n) {
                    val t = coinFill(r, day).also(truths::add)
                    c.row(30 to "${t.code} ${kw(t.side, "매수", "매도")}", 600 to "${md(day)} ${hm(t.time)}")
                    c.row(30 to "체결 수량", 600 to "${coinQty(t.q)} ${t.code}")
                    c.row(30 to "체결 가격", 600 to "${won(t.p)} KRW")
                    c.row(30 to "체결 금액", 600 to "${won(t.a)} KRW")
                    c.gap()
                }
            }
            else -> {
                c.row(30 to "해외주식 주문내역", 600 to ymd(day, "."))
                repeat(n) {
                    val t = usFill(r, day).also(truths::add)
                    c.row(30 to "${t.name} ${t.code}", 700 to "\$${usd(t.a)}")
                    c.row(30 to "${kw(t.side, "구매", "판매")} ${won(t.q)}주 · ${hm(t.time)}")
                    c.row(30 to "주당 \$${usd(t.p)}")
                    c.gap()
                }
            }
        }
        // Coin names: the screen names by code only on 코인원.
        val shown = if (app == "코인원") truths.map { it.copy(name = "") } else truths
        return Shot(app, c.lines, shown, noisy)
    }
}
