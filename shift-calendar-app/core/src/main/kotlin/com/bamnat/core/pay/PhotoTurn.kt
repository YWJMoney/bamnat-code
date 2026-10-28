package com.bamnat.core.pay

import kotlin.math.abs

/**
 * A 1.32 [FIX132-01] (사용자 2026-10-28 "명세서 사진이 위아래 뒤집혀"): which way up a photo reads. 1.30 turned only a photo
 * read on its side, kept the first quarter turn with twice the words, and never looked at a photo upside down — the words
 * of an upside-down payslip still come out (as nonsense), so it was read as it stood. Now each way a photo is read is
 * scored by how much it looks like a Korean payslip ([legibility]), and the best kept.
 */
object PhotoTurn {
    /** What a payslip says; one found weighs as much as a short line of letters. */
    private val WORDS = listOf(
        "기본급", "지급", "공제", "합계", "수당", "소득세", "지방소득세", "국민연금", "건강보험", "고용보험", "장기요양", "실수령",
        "차인지급", "급여", "명세서", "연장", "야간", "휴일", "식대", "성명", "부서", "근무", "시간", "일수", "총액",
    )
    private val AMOUNT = Regex("""\d{1,3}(,\d{3})+""")

    /** How much [lines] look like a payslip read the right way up: 한글 syllables, a payslip's words, amounts with commas. */
    fun legibility(lines: List<OcrLine>): Int {
        val text = lines.joinToString(" ") { it.text }
        val hangul = text.count { it in '가'..'힣' }
        val words = WORDS.count { it in text }
        val amounts = AMOUNT.findAll(text).count()
        return hangul + words * 20 + amounts * 4
    }

    /** Rows whose slope says the reader saw them upside down (ML Kit gives about ±180°). */
    fun upsideDown(lines: List<OcrLine>): Boolean {
        val words = lines.filter { it.text.count { c -> !c.isWhitespace() } >= 2 }
        if (words.isEmpty()) return false
        return words.count { abs(it.angle) >= 135f } * 2 > words.size
    }

    /**
     * Whether the photo as taken is worth reading turned too: on its side ([OcrRows.sideways]), upside down by the slopes,
     * or reading as too little of a payslip for its words (the upside-down case the slopes miss).
     */
    fun worthTurning(lines: List<OcrLine>): Boolean {
        if (lines.isEmpty()) return false
        if (OcrRows.sideways(lines) || upsideDown(lines)) return true
        val letters = lines.sumOf { l -> l.text.count { !it.isWhitespace() } }
        return letters >= 20 && legibility(lines) * 3 < letters
    }

    /** The turns to try for [lines], best guess first: [remembered] (the last turn that read well on this phone) leads. */
    fun candidates(lines: List<OcrLine>, remembered: Int? = null): List<Int> {
        val base = if (OcrRows.sideways(lines)) listOf(90, 270, 180) else listOf(180, 90, 270)
        return (listOfNotNull(remembered?.takeIf { it in base }) + base).distinct()
    }

    /**
     * The turn to keep: 0 unless one reads clearly better (a fifth more and at least 15 points), so a straight photo is
     * never turned on a whim. [readings] maps a turn (90, 180, 270) to what it read.
     */
    fun pick(asTaken: List<OcrLine>, readings: Map<Int, List<OcrLine>>): Int {
        val base = legibility(asTaken)
        val best = readings.mapValues { legibility(it.value) }.maxByOrNull { it.value } ?: return 0
        return if (best.value >= base + 15 && best.value * 5 >= base * 6) best.key else 0
    }

    /** A turn added to another, kept in 0, 90, 180, 270. */
    fun add(a: Int, b: Int): Int = ((a + b) % 360 + 360) % 360
}
