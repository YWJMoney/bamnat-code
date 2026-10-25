package com.bamnat.core.pay

/** [PAYS-05] One line of text recognised in a photo, with its box in pixels. */
data class OcrLine(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    /** [READ-07] The line's slope in degrees (ML Kit `Text.Line.angle`), 0 when unknown. */
    val angle: Float = 0f,
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * [PAYS-05] Text recognition returns a payslip table in blocks, often a column at a time, so a name and its
 * amount end up far apart. This puts lines that sit on one row back together, left to right, which is how the
 * payslip reads: "기본급 2,508,000 연장근로수당 288,000".
 * [READ-07] A phone's photo is tilted: across a payslip 2° moves the amount at the right end of a row by more than a
 * line's height, so "same height" put 기본급 beside the next row's amount (사용자 2026-10-25 "명세서를 보내도 제대로
 * 인식하지 못하고"). The tilt is taken out first and each row followed from its left, as a roster's are
 * ([com.bamnat.core.imports.RosterGeometry]).
 */
object OcrRows {
    fun join(lines: List<OcrLine>): String {
        val words = lines.filter { it.text.isNotBlank() }.map { com.bamnat.core.imports.RosterWord(it.text, it.left, it.top, it.right, it.bottom, angle = it.angle) }
        if (words.isEmpty()) return ""
        val geo = com.bamnat.core.imports.RosterGeometry
        // Long lines' own slopes, when the text recognition gave them; else how the lines stack best.
        val slopes = words.filter { it.angle != 0f && it.right - it.left > 3 * it.height }.map { it.angle.toDouble() }
        val tilt = if (slopes.size >= 3) geo.median(slopes) else geo.tilt(words)
        return geo.lines(geo.level(words, tilt)).joinToString("\n") { row -> row.joinToString("  ") { it.text.trim() } }
    }
}
