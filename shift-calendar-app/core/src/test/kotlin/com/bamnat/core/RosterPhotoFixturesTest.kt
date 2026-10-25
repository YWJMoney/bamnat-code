package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * [READ-01] Rosters photographed the way a phone does (tilted 0~5°, skewed, soft, shaded) and read by a real OCR
 * (`tools/roster_fixtures.py`, 1.25 순번 367): what the reader makes of each, day by day, against what was drawn.
 *
 * `*.boxes.tsv` are the drawn words where the photo put them, with an OCR's usual slips: each person's line must come
 * out with every day the photo kept right (but one). `*.ocr.tsv` are what RapidOCR read, which loses and misreads more lone letters than
 * ML Kit (야 as "OF"): with each word's text set to what was drawn there, every line must be found and nearly every
 * cell must land on its own day (a missing cell is yellow for the user, a cell on the wrong day is a wrong calendar);
 * as read, nearly every line must still be found.
 */
class RosterPhotoFixturesTest {
    private val types = listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)),
        DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true),
        DutyType(4, "오프", "O", true),
    )
    private val codes = DutyCodes(types)

    /** [lost]: by person, the days whose cell the photo lost (drawn boxes only). */
    class Fixture(val name: String, val truth: Map<String, List<String>>, val words: List<RosterWord>, val lost: Map<String, Int> = emptyMap())

    /** How a read line scored against a drawn one. */
    data class Score(val label: String, val days: Int, val right: Int, val wrong: Int, val found: Boolean, val lost: Int = 0)

    private fun load(file: String, drawn: Boolean = false): Fixture {
        val text = javaClass.classLoader.getResourceAsStream("roster-photos/$file")!!.bufferedReader(Charsets.UTF_8).readText()
        val truth = LinkedHashMap<String, List<String>>()
        val words = mutableListOf<RosterWord>()
        val lost = HashMap<String, Int>()
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t')
            when (f[0]) {
                "truth" -> truth[f[1]] = f[2].split(',')
                "lost" -> lost.merge(f[1], 1, Int::plus)
                "word" -> {
                    val text = if (drawn && f.size > 7 && f[7].isNotEmpty()) f[7] else f[6]
                    words += RosterWord(text, f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
                }
            }
        }
        return Fixture(file, truth, words, lost)
    }

    private fun files(kind: String): List<String> =
        javaClass.classLoader.getResource("roster-photos")!!.toURI().let { java.io.File(it) }.list()!!
            .filter { it.endsWith(".$kind.tsv") }.sorted()

    private fun expected(code: String): DutyCell? = codes.read(code)?.first

    /** Each drawn line against the read line of its name, else the one that agrees with it most. */
    private fun score(f: Fixture, rows: List<RosterRow>): List<Score> = f.truth.map { (label, want) ->
        val best = rows.maxWithOrNull(compareBy<RosterRow> { label.isNotEmpty() && it.label == label }
            .thenBy { r -> want.withIndex().count { (i, c) -> r.cells[i + 1] == expected(c) } })
        val right = best?.let { r -> want.withIndex().count { (i, c) -> r.cells[i + 1] == expected(c) } } ?: 0
        val wrong = best?.let { r -> r.cells.count { (d, c) -> c !is DutyCell.Unknown && want.getOrNull(d - 1)?.let(::expected)?.let { it != c } ?: true } } ?: 0
        // Found: a line of this person's days, at least a week of them right and three times more right than wrong.
        Score(label, want.size, right, wrong, best != null && right >= 7 && right > wrong * 3, f.lost[label] ?: 0)
    }

    private fun report(kind: String, drawn: Boolean = false): List<Pair<String, List<Score>>> = files(kind).map { file ->
        val f = load(file, drawn)
        val scores = score(f, DutyRoster.read(f.words, codes))
        println("READ-01 ${if (drawn) "drawn " else ""}${file.padEnd(28)} " + scores.joinToString(" ") { "${it.right}/${it.days}" + if (it.wrong > 0) "(x${it.wrong})" else "" })
        file to scores
    }

    @Test fun drawnBoxesReadEveryDayThePhotoKept() {
        val all = report("boxes")
        assertTrue(all.size >= 10)
        // Every day the photo kept (all but one); a lost cell is a yellow day for the user, not the reader's miss.
        val bad = all.flatMap { (file, scores) -> scores.filter { it.right < it.days - it.lost - 1 || it.wrong > 1 }.map { "$file ${it.label} ${it.right}/${it.days} lost ${it.lost} x${it.wrong}" } }
        assertTrue("lines short of all but two days:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun realOcrPutsEveryCellOnItsDay() {
        val all = report("ocr", drawn = true)
        assertTrue(all.size >= 10)
        val lost = all.flatMap { (file, scores) -> scores.filter { !it.found }.map { "$file ${it.label} ${it.right}/${it.days}" } }
        val read = all.sumOf { (_, s) -> s.sumOf { it.right + it.wrong } }
        val wrong = all.sumOf { (_, s) -> s.sumOf { it.wrong } }
        println("READ-01 ocr boxes: lines lost ${lost.size}, cells read $read, on a wrong day $wrong")
        assertTrue("lines not found:\n" + lost.joinToString("\n"), lost.isEmpty())
        assertTrue("cells on a wrong day: $wrong of $read", wrong * 100 <= read)
    }

    @Test fun realOcrAsReadFindsTheLines() {
        val all = report("ocr")
        val lines = all.sumOf { it.second.size }
        val lost = all.flatMap { (file, scores) -> scores.filter { !it.found }.map { "$file ${it.label} ${it.right}/${it.days}" } }
        val right = all.sumOf { (_, s) -> s.sumOf { it.right } }
        val days = all.sumOf { (_, s) -> s.sumOf { it.days } }
        println("READ-01 ocr as read: lines ${lines - lost.size}/$lines, days right $right/$days")
        assertTrue("lines not found:\n" + lost.joinToString("\n"), lost.size * 10 <= lines)
    }
}
