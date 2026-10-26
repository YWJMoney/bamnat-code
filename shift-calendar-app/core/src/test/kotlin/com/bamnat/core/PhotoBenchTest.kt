package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.FillVote
import com.bamnat.core.invest.Side
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime

/**
 * [READ-12][READ-13] 1.27 순번 446 어려운 사진 표본 (`tools/photo_bench.py`): 저조도·화면 찍은 사진(모아레·반사)·작은 글씨·강한 원근·큰
 * 그림자·칸 위 펜 표시·병합 칸·심한 흐림·다크 모드 캡처를 실제 OCR(RapidOCR)로 원본과 네 가지로 다듬은 사진에서 읽은 것.
 *
 * 한 번 읽기(1.26까지: 원본)와 여러 번 읽고 합의(1.27: [RosterVote]·[SlipVote]·[FillVote])를 같은 표본으로 세어 전·후 숫자를
 * 찍고(계획 4장), 두 가지를 지켜요: 합의가 한 번 읽기보다 맞는 칸이 적지 않고, **틀린 값이 노란 표시 없이 들어가는 일 0**.
 */
class PhotoBenchTest {
    private val types = listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true), DutyType(4, "오프", "O", true),
    )
    private val codes = DutyCodes(types)

    private fun dir(kind: String): File? = javaClass.classLoader.getResource("photo-bench/$kind")?.toURI()?.let(::File)

    /** Case → variant → rows of the file. */
    private fun cases(kind: String): Map<String, Map<String, List<List<String>>>> =
        dir(kind)?.listFiles()?.filter { it.name.endsWith(".tsv") }?.groupBy { it.name.substringBefore('.') }
            ?.mapValues { (_, fs) -> fs.associate { f -> f.name.split('.')[1] to f.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') } } }
            ?.toSortedMap().orEmpty()

    // ---------------------------------------------------------------- roster

    data class RosterScore(val cells: Int, val right: Int, val silent: Int, val yellow: Int)

    private fun words(rows: List<List<String>>) = rows.filter { it[0] == "word" }.map { f ->
        RosterWord(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
    }

    private fun scoreRoster(truth: Map<String, List<String>>, read: RosterPhotoRead, debug: Boolean = false): RosterScore {
        var cells = 0; var right = 0; var silent = 0; var yellow = 0
        for ((label, want) in truth) {
            val row = read.rows.maxWithOrNull(compareBy({ it.label == label }, { r -> want.withIndex().count { (i, c) -> r.cells[i + 1] == codes.read(c)?.first } }))
            val doubt = row?.doubtful(want.size).orEmpty()
            want.forEachIndexed { i, c ->
                val d = i + 1
                cells++
                val got = row?.cells?.get(d)
                val ok = got == codes.read(c)?.first
                if (ok) right++
                if (d in doubt || got is DutyCell.Unknown) yellow++
                if (!ok && got != null && got !is DutyCell.Unknown && d !in doubt) { silent++; if (debug) println("  silent ${row?.label}/$label day $d want $c got $got") }
            }
        }
        return RosterScore(cells, right, silent, yellow)
    }

    @Test fun rosterReadSeveralWaysBeatsOnceAndNeverQuietlyWrong() {
        val all = cases("roster")
        if (all.isEmpty()) return
        var once = RosterScore(0, 0, 0, 0); var voted = RosterScore(0, 0, 0, 0)
        fun RosterScore.plus(o: RosterScore) = RosterScore(cells + o.cells, right + o.right, silent + o.silent, yellow + o.yellow)
        for ((name, variants) in all) {
            val truth = variants.values.first().filter { it[0] == "truth" }.associate { it[1] to it[2].split(',') }
            val reads = variants.mapValues { (_, rows) -> DutyRoster.analyse(words(rows), codes) }
            val single = scoreRoster(truth, reads.getValue("orig"))
            val vote = scoreRoster(truth, RosterVote.merge(listOf(reads.getValue("orig")) + reads.filterKeys { it != "orig" }.values), debug = true)
            println("READ-13 roster ${name.padEnd(20)} once ${single.right}/${single.cells} (조용히 틀림 ${single.silent})  " +
                "합의 ${vote.right}/${vote.cells} (조용히 틀림 ${vote.silent}, 노란 ${vote.yellow})  " +
                variants.keys.filter { it != "orig" }.joinToString(" ") { k -> "$k ${scoreRoster(truth, reads.getValue(k)).right}" })
            once = once.plus(single); voted = voted.plus(vote)
        }
        println("READ-13 roster total: once ${once.right}/${once.cells} silent ${once.silent} → vote ${voted.right}/${voted.cells} silent ${voted.silent} yellow ${voted.yellow}")
        assertTrue("합의가 한 번 읽기보다 못함", voted.right >= once.right)
        assertEquals("합의: 틀린 칸이 노란 표시 없이", 0, voted.silent)
    }

    // ---------------------------------------------------------------- payslip

    private fun text(rows: List<List<String>>) = OcrRows.join(rows.filter { it[0] == "line" }.map { f ->
        OcrLine(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
    })

    private fun slipHits(truth: Map<String, Long>, read: PayslipRead): Int = truth.count { (name, amount) ->
        when (name) {
            "지급합계" -> read.gross == amount
            "실수령액" -> read.net == amount
            else -> read.lines.any { it.amount == amount && (it.name.take(2) == name.take(2) || it.role == PayslipReader.roleOf(name)) }
        }
    }

    @Test fun payslipReadSeveralWaysBeatsOnceAndNeverQuietlyWrong() {
        val all = cases("payslip")
        if (all.isEmpty()) return
        var total = 0; var once = 0; var voted = 0; var silentOnce = 0; var silentVote = 0
        for ((name, variants) in all) {
            val truth = variants.values.first().filter { it[0] == "truth" }.associate { it[1] to it[2].toLong() }
            val texts = listOf(text(variants.getValue("orig"))) + variants.filterKeys { it != "orig" }.values.map(::text)
            val single = PayslipReader.read(texts[0])
            val vote = SlipVote.merge(texts)
            val a = slipHits(truth, single); val b = slipHits(truth, vote.read)
            // Quietly wrong: the reading says its lines add up (nothing to ask) but the total or the pay is not the payslip's.
            val wrongOnce = single.consistent && (single.gross != truth["지급합계"])
            val wrongVote = vote.sure && (vote.read.gross != truth["지급합계"] || vote.read.net?.let { it != truth["실수령액"] } == true)
            if (wrongOnce) silentOnce++
            if (wrongVote) silentVote++
            println("READ-13 payslip ${name.padEnd(18)} once $a/${truth.size}  합의 $b/${truth.size} (확인할 줄 ${vote.unsure.size}, 확실 ${vote.sure}) ${vote.unsure.map { vote.read.lines[it].let { l -> "${l.name}:${l.amount}" } }}")
            total += truth.size; once += a; voted += b
        }
        println("READ-13 payslip total: once $once/$total silent $silentOnce → vote $voted/$total silent $silentVote")
        assertTrue(voted >= once)
        assertEquals(0, silentVote)
    }

    // ---------------------------------------------------------------- fill captures

    @Test fun fillCapturesReadSeveralWaysNeverFillAWrongNumberQuietly() {
        val all = cases("fill")
        if (all.isEmpty()) return
        var total = 0; var once = 0; var voted = 0; var silent = 0
        for ((name, variants) in all) {
            val truth = variants.values.first().filter { it[0] == "truth" }.map { it[1].split('|') + it[2] }
            val reads = listOf(FillSheetReader.read(text(variants.getValue("orig")))) + variants.filterKeys { it != "orig" }.values.map { FillSheetReader.read(text(it)) }
            val vote = FillVote.merge(reads)
            fun hits(fills: List<FillSheetReader.Fill>) = truth.count { (n, s, q, p) ->
                fills.any { it.name == n && it.side == (if (s == "매수") Side.BUY else Side.SELL) && it.quantity == q.toDouble() && it.price == p.toDouble() }
            }
            for (f in vote) if (f.sure && truth.none { (n, s, q, p) -> f.name == n && f.side == (if (s == "매수") Side.BUY else Side.SELL) && f.quantity == q.toDouble() && f.price == p.toDouble() }) {
                silent++; println("  silent fill $f  reads ${reads.map { r -> r.map { "${it.name} ${it.quantity}×${it.price} ${it.sure}" } }}")
            }
            println("READ-13 fill ${name.padEnd(22)} once ${hits(reads[0])}/${truth.size}  합의 ${hits(vote)}/${truth.size} 확실 ${vote.count { it.sure }}")
            total += truth.size; once += hits(reads[0]); voted += hits(vote)
        }
        println("READ-13 fill total: once $once/$total → vote $voted/$total, silent $silent")
        assertTrue(voted >= once)
        assertEquals(0, silent)
    }
}
