package com.bamnat.core

import com.bamnat.core.invest.FillReconcile
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [INVA-03] 1.29 AR: 체결 내역 캡처 읽기 정확도를 숫자로. 증권사 여덟·코인 거래소 셋·해외 주식 하나의 지어낸 화면([FillCorpus]) 앱마다
 * 120장(그중 3분의 2는 글자 인식 흔들림)을 글자 상자에서 줄로 묶어 읽고, 체결마다
 *  - 맞게 읽음: 매수/매도·종목·수량·단가·(화면에 있으면) 날짜·시각까지 모두 맞음
 *  - 바로 넣음: 묻지 않고 넣을 만큼 확실함([FillReconcile.confident]) — 그리고 맞음
 *  - 조용히 틀림: 확실하다고 했는데 틀림 (0이어야 해요)
 * 을 세요. 1.28(바꾸기 전) 숫자는 계획 문서 `docs/plans/1.29-AR-개발계획.md` 3장.
 */
class FillCorpusTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 27)

    @Before fun fix() = Today.fix(today.atTime(15, 40).atZone(zone))
    @After fun reset() = Today.reset()

    private fun near(x: Double, y: Double) = abs(x - y) <= maxOf(0.0001, abs(y) * 1e-9)

    data class Score(var fills: Int = 0, var right: Int = 0, var auto: Int = 0, var silentWrong: Int = 0, var cleanWrong: Int = 0)

    private fun right(f: FillSheetReader.Fill, t: FillCorpus.Truth): Boolean =
        f.side == t.side && (t.name.isNotEmpty() && f.name == t.name || t.code.isNotEmpty() && f.symbol.equals(t.code, true)) &&
            near(f.quantity, t.q) && near(f.price, t.p) && (f.amount == 0.0 || near(f.amount, t.a)) &&
            (t.date == null || f.date == t.date) && f.time?.let { it.hour == t.time.hour && it.minute == t.time.minute } == true

    /** Reads every app's captures; returns the score by app and the first misreads to look at. */
    fun run(perApp: Int = 120): Pair<Map<String, Score>, List<String>> {
        val scores = LinkedHashMap<String, Score>()
        val bad = mutableListOf<String>()
        for ((ai, app) in FillCorpus.APPS.withIndex()) {
            val s = Score()
            repeat(perApp) { n ->
                val r = Random(1290 + ai * 1000 + n)
                val shot = FillCorpus.shot(app, r, today, noisy = n % 3 != 0)
                val text = OcrRows.join(shot.lines)
                val got = FillSheetReader.read(text, captured = today)
                var j = 0
                for (t in shot.truths) {
                    s.fills++
                    val idx = (j until got.size).firstOrNull { k -> got[k].side == t.side &&
                        (t.name.isNotEmpty() && got[k].name == t.name || t.code.isNotEmpty() && got[k].symbol.equals(t.code, true)) }
                    val f = idx?.let { got[it] }
                    if (idx != null) j = idx + 1
                    val ok = f != null && right(f, t)
                    if (ok) s.right++
                    val confident = f != null && FillReconcile.confident(f)
                    if (confident && ok) s.auto++
                    if (f != null && confident && !ok) { s.silentWrong++; bad.add(0, "[$app #$n] SILENT $t\n  → $f\n$text") }
                    if (!shot.noisy && !ok && bad.size < 30) { s.cleanWrong++; bad += "[$app #$n] CLEAN $t\n  → $f\n$text" }
                    else if (!shot.noisy && !ok) s.cleanWrong++
                }
            }
            scores[app] = s
        }
        return scores to bad
    }

    /**
     * [INVA-02] The phone reads a capture several ways (READ-13); here three, two of them with their own digit misreads (3↔8,
     * 1↔7, 0↔6, 5↔6, a digit lost). Put together ([com.bamnat.core.invest.FillVote]), a fill sure enough to go in is never wrong.
     */
    @Test fun threeReadingsOfEveryAppNeverPutAWrongFillIn() {
        val swap = mapOf('3' to '8', '8' to '3', '1' to '7', '7' to '1', '0' to '6', '6' to '0', '5' to '6')
        fun misread(r: Random, text: String): String = text.lines().joinToString("\n") { line ->
            var t = line
            if (r.nextInt(100) < 15) {
                val digits = t.indices.filter { t[it] in swap }
                if (digits.isNotEmpty()) { val i = digits[r.nextInt(digits.size)]; t = t.substring(0, i) + swap.getValue(t[i]) + t.substring(i + 1) }
            }
            if (r.nextInt(100) < 5) { val digits = t.indices.filter { t[it].isDigit() }; if (digits.isNotEmpty()) { val i = digits[r.nextInt(digits.size)]; t = t.removeRange(i, i + 1) } }
            t
        }
        var fills = 0; var auto = 0; var silent = 0
        val bad = mutableListOf<String>()
        for ((ai, app) in FillCorpus.APPS.withIndex()) repeat(80) { n ->
            val shot = FillCorpus.shot(app, Random(7290 + ai * 1000 + n), today, noisy = false)
            val text = OcrRows.join(shot.lines)
            val reads = listOf(text, misread(Random(n * 31 + ai), text), misread(Random(n * 37 + ai + 5), text)).map { FillSheetReader.read(it, today) }
            val voted = com.bamnat.core.invest.FillVote.merge(reads)
            fills += shot.truths.size
            for (f in voted.filter { FillReconcile.confident(it) }) {
                if (shot.truths.any { right(f, it) }) auto++ else { silent++; if (bad.size < 10) bad += "[$app #$n] $f\n$text" }
            }
        }
        println("corpus 3-way: fills=$fills auto=$auto silentWrong=$silent")
        bad.forEach(::println)
        assertEquals("틀린 체결이 묻지 않고 들어감", 0, silent)
        assertTrue("세 번 읽어 바로 넣음 $auto/$fills", auto * 10 >= fills * 7)
    }

    @Test fun everyAppsCaptureReadsAndNothingWrongGoesInQuietly() {
        val (scores, bad) = run()
        println("app\tfills\tright\tauto\tsilentWrong\tcleanWrong")
        scores.forEach { (app, s) -> println("$app\t${s.fills}\t${s.right}\t${s.auto}\t${s.silentWrong}\t${s.cleanWrong}") }
        val all = scores.values
        val fills = all.sumOf { it.fills }
        println("total\t$fills\t${all.sumOf { it.right }}\t${all.sumOf { it.auto }}\t${all.sumOf { it.silentWrong }}\t${all.sumOf { it.cleanWrong }}")
        bad.take(12).forEach(::println)
        assertEquals("확실하다고 한 틀린 체결", 0, all.sumOf { it.silentWrong })
        assertEquals("깨끗한 화면에서 틀림", 0, all.sumOf { it.cleanWrong })
        // Every app: at least 9 in 10 fills read right, 8 in 10 sure enough to go in without a question.
        scores.forEach { (app, s) ->
            assertTrue("$app 맞게 읽음 ${s.right}/${s.fills}", s.right * 10 >= s.fills * 9)
            assertTrue("$app 바로 넣음 ${s.auto}/${s.fills}", s.auto * 10 >= s.fills * 8)
        }
    }
}
