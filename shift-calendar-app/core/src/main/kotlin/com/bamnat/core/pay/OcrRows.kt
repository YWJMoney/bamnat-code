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
    /**
     * [PHOTO-12] 1.29 AQ: words that stand at another slant than the page's are a watermark across it (화학 공장 명세서 사진,
     * 사용자 2026-10-27: 사번·날짜·IP가 20° 기울어 몇 번씩 찍힘), never part of the table: their dates and numbers were read as
     * amounts. The page's slant is the middle one of its long lines; a word more than 8° off it is left out.
     */
    fun unmarked(lines: List<OcrLine>): List<OcrLine> {
        val long = lines.filter { it.right - it.left > 3 * it.height }.map { it.angle.toDouble() }.sorted()
        if (long.size < 3) return lines
        val page = long[long.size / 2]
        return lines.filter { kotlin.math.abs(it.angle - page) <= 8.0 }
    }

    fun join(lines: List<OcrLine>): String {
        val words = unmarked(lines).filter { it.text.isNotBlank() }.map { com.bamnat.core.imports.RosterWord(it.text, it.left, it.top, it.right, it.bottom, angle = it.angle) }
        if (words.isEmpty()) return ""
        val geo = com.bamnat.core.imports.RosterGeometry
        // Long lines' own slopes, when the text recognition gave them; else how the lines stack best.
        val slopes = words.filter { it.angle != 0f && it.right - it.left > 3 * it.height }.map { it.angle.toDouble() }
        val tilt = if (slopes.size >= 3) geo.median(slopes) else geo.tilt(words)
        return sideBySide(geo.lines(geo.level(words, tilt))).joinToString("\n") { row -> row.joinToString("  ") { it.text.trim() } }
    }

    /**
     * [PHOTO-12] 1.29 AQ: a table whose columns sit at different heights (화학 공장 계산 방법 사진: 산출방법 칸이 구분 칸보다 반 줄
     * 위, 명세서는 금액 칸이 반 줄 아래) came out as two rows, the name on one and what goes with it on the next. Two rows next
     * to each other, less than two letters high apart, whose words stand wholly left and wholly right of each other, are one
     * row, when each is the other's nearest such row.
     */
    internal fun sideBySide(rows: List<List<com.bamnat.core.imports.Placed>>): List<List<com.bamnat.core.imports.Placed>> {
        if (rows.size < 2) return rows
        val h = com.bamnat.core.imports.RosterGeometry.median(rows.flatten().map { it.h })
        fun y(r: List<com.bamnat.core.imports.Placed>) = r.sumOf { it.y } / r.size
        fun apart(a: List<com.bamnat.core.imports.Placed>, b: List<com.bamnat.core.imports.Placed>) =
            a.maxOf { it.right } < b.minOf { it.left } - 0.5 * h || b.maxOf { it.right } < a.minOf { it.left } - 0.5 * h
        fun partner(i: Int): Int? = listOf(i - 1, i + 1).filter { it in rows.indices }
            .filter { j -> kotlin.math.abs(y(rows[i]) - y(rows[j])) < 2.0 * h && apart(rows[i], rows[j]) }
            .minByOrNull { j -> kotlin.math.abs(y(rows[i]) - y(rows[j])) }
        val out = mutableListOf<List<com.bamnat.core.imports.Placed>>()
        var i = 0
        while (i < rows.size) {
            val j = partner(i)
            if (j == i + 1 && partner(j) == i) { out += (rows[i] + rows[j]).sortedBy { it.x }; i += 2 } else { out += rows[i]; i++ }
        }
        return out
    }
}
