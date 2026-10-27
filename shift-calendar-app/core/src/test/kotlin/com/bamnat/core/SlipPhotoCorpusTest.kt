package com.bamnat.core

import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ 순번 47(명세서 정답 시험, C 몫): 24 꼴 × 10장 = 240장의 명세서를 ML Kit가 돌려주는 모양(글자 상자, 기울기 ±4°,
 * 이름 오타, 숫자 오독, 쉼표→점, '원' 빠짐, 붙은 단어·떨어진 단어, 잃은 단어)으로 세 번씩 읽고(여러 방식 읽기) 정답과 대조해요.
 *
 * 1.28 (줄을 글로 이어 [PayslipReader]로 읽고 [SlipVote]로 합의)과 1.29 ([SlipReading]: 글자 상자 위치로 표를 다시 짜고, 표 읽기와
 * 글 읽기 중 나은 쪽, 합의, 합계 검산)을 같은 사진으로 견줘요. 지켜야 하는 것:
 * - 깨끗한 사진(잡음 없음)은 모든 꼴에서 모든 줄이 맞고 묻지 않아요(확실).
 * - 노란 칸이 아닌 줄은 틀리지 않아요(조용히 틀린 값 0).
 * - 1.29가 1.28보다 많이 맞혀요.
 */
class SlipPhotoCorpusTest {
    private class Score { var items = 0; var hit = 0; var silent = 0; var pages = 0; var allRight = 0; var sure = 0 }

    private fun matches(line: com.bamnat.core.pay.SlipLine, item: SlipCorpus.Item): Boolean {
        val roleOk = line.role == item.role || item.role == SlipRole.GROSS && line.role == SlipRole.TOTAL
        if (!roleOk || line.amount != item.amount) return false
        if (item.role in setOf(SlipRole.ALLOWANCE, SlipRole.BONUS, SlipRole.DEDUCTION)) {
            val a = SlipLexicon.of(line.name)?.name ?: line.name
            val b = item.name
            return PayslipReader.ocrFix(a.filter { it.isLetter() }).take(2) == b.take(2)
        }
        return true
    }

    private fun score(s: Score, truth: SlipCorpus.Truth, res: SlipVote.Result, debug: String? = null) {
        val read = res.read
        s.pages++
        val want = truth.all
        val hits = want.count { item -> read.lines.any { matches(it, item) } }
        s.items += want.size
        s.hit += hits
        if (hits == want.size) s.allRight++
        if (res.sure && hits == want.size) s.sure++
        // A line not yellow must be right (or an hours-only line with no amount).
        read.lines.forEachIndexed { i, l ->
            if (i in res.unsure || l.amount == null) return@forEachIndexed
            if (want.none { matches(l, it) }) {
                s.silent++
                if (debug != null) println("  SILENT $debug: ${l.role} ${l.name} ${l.amount}")
            }
        }
    }

    private fun noise(seed: Int, way: Int): SlipCorpus.Noise {
        if (seed % 3 == 0) return SlipCorpus.Noise.CLEAN.copy(tilt = listOf(0.0, 1.2, -2.0, 3.0)[seed % 4])
        val r = Random(seed * 13 + 7)
        val tilt = r.nextDouble(-4.0, 4.0)
        val k = if (way == 0) 0.6 else 1.0
        return SlipCorpus.Noise(tilt, digit = 0.04 * k, comma = 0.12 * k, name = 0.10 * k, glue = 0.05 * k, lose = 0.02 * k, won = 0.2 * k)
    }

    @Test fun payslipCorpusBeforeAndAfter() {
        val before = sortedMapOf<String, Score>()
        val after = sortedMapOf<String, Score>()
        val afterLines = sortedMapOf<String, Score>()
        val onceAfter = Score()
        val cleanAfter = Score()
        for (layout in SlipCorpus.LAYOUTS) for (seed in 0 until 10) {
            val page = SlipCorpus.page(layout, seed)
            val ways = (0 until 3).map { w -> noise(seed, w) to Random(seed * 101 + w * 7 + layout.hashCode()) }
            val lineReads = ways.map { (n, r) -> SlipCorpus.read(page, n, Random(r.nextInt()), lines = true) }
            val wordReads = ways.map { (n, r) -> SlipCorpus.read(page, n, Random(r.nextInt()), lines = false) }
            val old = SlipVote.merge(lineReads.map { OcrRows.join(it) })
            val new = SlipReading.read(wordReads)
            val newLines = SlipReading.read(lineReads)
            score(before.getOrPut(layout) { Score() }, page.truth, old)
            score(after.getOrPut(layout) { Score() }, page.truth, new, debug = "$layout#$seed")
            score(afterLines.getOrPut(layout) { Score() }, page.truth, newLines)
            score(onceAfter, page.truth, SlipReading.read(wordReads.take(1)))
            if (seed % 3 == 0) {
                score(cleanAfter, page.truth, new)
                if (!(new.sure && page.truth.all.all { item -> new.read.lines.any { matches(it, item) } })) {
                    println("CLEAN NOT SURE $layout#$seed unsure=${new.unsure} adds=${SlipVote.adds(new.read)} lines=${new.read.lines.map { "${it.role}:${it.name}:${it.amount}" }}")
                    println(new.text)
                }
            }
        }
        fun sum(m: Map<String, Score>) = Score().apply { m.values.forEach { items += it.items; hit += it.hit; silent += it.silent; pages += it.pages; allRight += it.allRight; sure += it.sure } }
        fun pct(a: Int, b: Int) = "%.1f%%".format(100.0 * a / b.coerceAtLeast(1))
        println("PHOTO-07 명세서 24꼴 × 10장, 꼴마다 항목 맞힘 (1.28 → 1.29 단어 상자 / 1.29 줄 상자), 조용히 틀린 줄")
        for (l in SlipCorpus.LAYOUTS) {
            val b = before.getValue(l); val a = after.getValue(l); val al = afterLines.getValue(l)
            println("  ${l.padEnd(14)} ${pct(b.hit, b.items).padStart(6)} → ${pct(a.hit, a.items).padStart(6)} / ${pct(al.hit, al.items).padStart(6)}   " +
                "다 맞은 장 ${b.allRight}→${a.allRight}/10  조용히 틀림 ${b.silent}→${a.silent}")
        }
        val b = sum(before); val a = sum(after); val al = sum(afterLines)
        println("PHOTO-07 합계 항목 ${b.items}: 1.28 ${b.hit} (${pct(b.hit, b.items)}) → 1.29 ${a.hit} (${pct(a.hit, a.items)}), 줄 상자로도 ${al.hit} (${pct(al.hit, al.items)}); " +
            "다 맞은 장 ${b.allRight} → ${a.allRight} / ${a.pages}; 조용히 틀린 줄 ${b.silent} → ${a.silent}; 한 번만 읽기 ${pct(onceAfter.hit, onceAfter.items)}; " +
            "깨끗한 사진 확실 ${cleanAfter.sure}/${cleanAfter.pages}")
        assertEquals("깨끗한 사진은 모든 꼴에서 다 맞고 확실", cleanAfter.pages, cleanAfter.sure)
        assertEquals("노란 칸이 아닌 줄은 틀리지 않음", 0, a.silent)
        assertTrue("1.29가 1.28보다 많이 맞힘", a.hit > b.hit)
        assertTrue("1.29 항목 맞힘 97% 이상: ${pct(a.hit, a.items)}", a.hit * 100 >= a.items * 97)
    }
}
