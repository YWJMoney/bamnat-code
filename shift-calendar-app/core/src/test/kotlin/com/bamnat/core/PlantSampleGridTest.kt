package com.bamnat.core

import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [PHOTO-27] 1.30 AV (사용자 2026-10-27 "명세서를 전보다는 잘 읽지만 사진마다 읽는 부분이 다르다"): the same payslip ([PlantSample])
 * photographed every way a phone does, and read as the app reads it (three made-over readings put together). 1.29 shook the
 * letters and boxes only; here the page is also turned, taken from the side (keystone), near or far, cut at the top or the
 * bottom, its words cut and joined differently, its 지급내역·공제내역 heads lost. Every way must come out the same:
 * nothing silently wrong, 97% of the lines or more, more than three yellow lines on one photo in twenty at most.
 */
class PlantSampleGridTest {
    private fun key(s: String) = PayslipReader.ocrFix(s.filter { it.isLetter() }).take(2)

    private fun hit(l: SlipLine, item: PlantSample.Item): Boolean {
        if (l.amount != item.amount || l.role !in item.role) return false
        if (item in PlantSample.TOTALS) return true
        val name = SlipLexicon.of(l.name)?.takeIf { it.distance > 0 }?.name ?: l.name
        return key(name) == key(item.name)
    }

    class Score {
        var items = 0; var hit = 0; var hours = 0; var hoursHit = 0; var silent = 0; var pages = 0; var yellow = 0; var worstYellow = 0; var many = 0
        val misses = mutableListOf<String>()
    }

    private fun score(s: Score, res: SlipVote.Result, noise: PlantSample.Noise, tag: String) {
        val lines = res.read.lines
        val want = PlantSample.expected(noise)
        s.pages++
        val miss = want.filter { item -> lines.none { hit(it, item) } }
        s.items += want.size
        s.hit += want.size - miss.size
        for ((name, minutes) in PlantSample.expectedHours(noise)) {
            s.hours++
            val item = PlantSample.ALL.first { it.name == name }
            if (lines.any { hit(it, item) && it.minutes == minutes }) s.hoursHit++
            else if (s.misses.size < 40) s.misses += "HOURS $tag $name want $minutes got ${lines.filter { hit(it, item) }.map { it.minutes }}"
        }
        lines.forEachIndexed { i, l ->
            if (l.amount == null || i in res.unsure) return@forEachIndexed
            if (PlantSample.ALL.none { hit(l, it) }) { s.silent++; s.misses += "SILENT $tag ${l.role} ${l.name} ${l.amount}" }
        }
        s.yellow += res.unsure.size
        s.worstYellow = maxOf(s.worstYellow, res.unsure.size)
        if (res.unsure.size > 3) s.many++
        if (miss.isNotEmpty() && s.misses.size < 40) s.misses += "MISS $tag ${miss.map { it.name }} yellow=${res.unsure.size}"
    }

    /** The letters' and boxes' noise of 1.29's test, on every reading. */
    private val NOISY = PlantSample.Noise(digit = 0.06, comma = 0.15, roman = 0.5, mark = 0.7, jitter = 6)

    private data class Way(val name: String, val noise: PlantSample.Noise)

    private val perspectives = listOf(0.0, 0.06, 0.12)
    private val tilts = listOf(0.0, -2.5, 3.0)
    private val scales = listOf(0.6, 1.0, 1.4)
    private val splits = listOf(0.0, 0.25)
    private val crops = listOf("", "top", "bottom")
    private val heads = listOf("", "lost", "garbled")

    private fun ways(): List<Way> = buildList {
        for (p in perspectives) for (t in tilts) for (sc in scales) for (sp in splits) for (c in crops) for (h in heads) {
            val n = NOISY.copy(perspective = p, tilt = t, scale = sc, split = sp, cropTop = c == "top", cropBottom = c == "bottom",
                headLost = h == "lost", headGarbled = h == "garbled")
            add(Way("p=$p t=$t s=$sc split=$sp crop=$c head=$h", n))
        }
    }

    private fun run(ways: List<Way>, seeds: Int, readings: Int): Map<String, Score> {
        val byAxis = LinkedHashMap<String, Score>()
        val all = Score()
        byAxis["전체"] = all
        for ((wi, way) in ways.withIndex()) for (seed in 0 until seeds) {
            val lines = (wi + seed) % 4 == 3
            val reads = (0 until readings).map { w -> PlantSample.payslip(way.noise, Random(wi * 1009 + seed * 31 + w), lines) }
            val res = SlipReading.read(reads)
            score(all, res, way.noise, "${way.name} #$seed lines=$lines")
            for (axis in axes(way.noise)) score(byAxis.getOrPut(axis) { Score() }, res, way.noise, way.name)
        }
        return byAxis
    }

    private fun axes(n: PlantSample.Noise): List<String> = listOf(
        "원근 ${n.perspective}", "기울기 ${n.tilt}°", "크기 ×${n.scale}", "쪼갬 ${n.split}",
        "잘림 " + when { n.cropTop -> "위"; n.cropBottom -> "아래"; else -> "없음" },
        "머리글 " + when { n.headLost -> "없음"; n.headGarbled -> "틀림"; else -> "있음" },
    )

    private fun pct(a: Int, b: Int) = "%.1f%%".format(100.0 * a / b.coerceAtLeast(1))

    private fun report(title: String, scores: Map<String, Score>) {
        println("$title")
        println("| 축 | 장 | 항목 | 시간 | 조용히 틀림 | 노란 줄 평균 | 노란 줄 최대 | 노란 줄 4개 넘는 장 |")
        println("|---|---|---|---|---|---|---|---|")
        for ((axis, s) in scores) println("| $axis | ${s.pages} | ${s.hit}/${s.items} (${pct(s.hit, s.items)}) | ${s.hoursHit}/${s.hours} | ${s.silent} | " +
            "%.2f".format(s.yellow.toDouble() / s.pages.coerceAtLeast(1)) + " | ${s.worstYellow} | ${s.many} |")
        scores["전체"]?.misses?.take(40)?.forEach { println("  $it") }
    }

    /** Every combination of the six axes (486 ways), each read three ways as the app does, two photos each. */
    @Test fun everyWayAPhoneTakesIt() {
        val scores = run(ways(), seeds = 2, readings = 3)
        report("변형 격자 (486가지 × 2장 × 3번 읽기)", scores)
        val all = scores.getValue("전체")
        assertEquals("조용히 틀린 줄", 0, all.silent)
        assertTrue("항목 ${pct(all.hit, all.items)}", all.hit >= all.items * 0.97)
        assertTrue("시간 ${pct(all.hoursHit, all.hours)}", all.hoursHit >= all.hours * 0.97)
        // Yellow lines are what the user looks at: a few on most photos; more than three on one photo in twenty at most. When
        // one 공제 line is misread or lost the 공제 sum no longer vouches for the rest, and the lines the reader placed by their
        // column only (볼링회(남부공장), 축구회(AB/CD): names no list holds) turn yellow with it (1.30 AV: 39 of 972, 4.0%).
        assertTrue("노란 줄 4개 넘는 장 ${all.many}/${all.pages}", all.many * 20 <= all.pages)
    }

    /** The hardest corner, twenty photos: strong keystone, turned, small letters, words cut, no heads. */
    @Test fun theHardestCorner() {
        val n = NOISY.copy(perspective = 0.12, tilt = 3.0, scale = 0.6, split = 0.25, headLost = true)
        val scores = run(listOf(Way("hardest", n)), seeds = 20, readings = 3)
        report("가장 어려운 조합 20장", scores)
        val all = scores.getValue("전체")
        assertEquals(0, all.silent)
        assertTrue("항목 ${pct(all.hit, all.items)}", all.hit >= all.items * 0.97)
    }

    /** One reading alone (a phone that runs short of memory reads the photo once): never silently wrong. */
    @Test fun oneReadingIsNeverSilentlyWrong() {
        val ways = ways().filterIndexed { i, _ -> i % 3 == 0 }
        val scores = run(ways, seeds = 1, readings = 1)
        report("한 번 읽기 (162가지)", scores)
        assertEquals(0, scores.getValue("전체").silent)
    }
}
