package com.bamnat.core.imports

/**
 * 2.0.0 [RVL-80] (검토 I07bc-02 '근무표 회사 틀 학습, 내 폰 안에서') What a calendar's roster looks like, learned from the one the user said
 * was right ('네, 같아요' or a line put in): where their line stands among the others ([rank] of [lines], top to bottom), and where on the
 * table they tapped the row of days and their own line when the photo needed it ([dayAt], [myAt], per mille of the table's height). The
 * name on the line stays where it was ([DutyCodeBook]'s 내 줄), and what each code means in the code book; this holds no name, no cell
 * and no picture, so it is the shape of the ward's sheet and nothing of anyone on it.
 *
 * Next month's sheet from the same ward then finds the user's line by itself even when the photo misread a letter of the name
 * ("김민지" → "김민시"), or lost it, when the sheet has as many lines and theirs stands in the same place ([find]); and a sheet whose
 * row of days the reader could not find is read along last month's taps without asking ([hint]).
 */
data class RosterForm(
    val rank: Int,
    val lines: Int,
    val dayAt: Int? = null,
    val myAt: Int? = null,
) {
    fun encode(): String = listOf(rank, lines, dayAt ?: -1, myAt ?: -1).joinToString(",")

    companion object {
        /** Name and 조 letters read wrong by at most this many, on a name of at least [CLOSE_MIN] letters. */
        private const val CLOSE = 1
        private const val CLOSE_MIN = 3

        fun decode(s: String?): RosterForm? = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 4 }?.let { (r, n, d, m) ->
            if (r < 0 || n <= 0 || r >= n) null else RosterForm(r, n, d.takeIf { it in 0..1000 }, m.takeIf { it in 0..1000 })
        }

        /** The form of a sheet whose line [picked] of [rows] (in the table's order) the user took, with where they tapped ([taps]). */
        fun of(rows: List<RosterRow>, picked: Int, taps: Pair<Int?, Int?>? = null): RosterForm? {
            if (picked !in rows.indices) return null
            val ordered = rows.sortedBy { it.line }
            val rank = ordered.indexOf(rows[picked]).takeIf { it >= 0 } ?: return null
            return RosterForm(rank, rows.size, taps?.first, taps?.second)
        }

        /**
         * The user's line on a new sheet [rows] (in any order; the index is of [rows]), or null to ask '어느 줄이 내 것인가요?'. Their
         * name as it was ([mine]) is found first, never when two people share it ([DutyRoster.twins]); then a name one letter off,
         * when only one line is so close; then the line in the same place on a sheet of as many lines whose name the photo lost.
         */
        fun find(rows: List<RosterRow>, mine: String?, form: RosterForm?): Int? {
            if (rows.isEmpty()) return null
            val name = mine?.trim().orEmpty()
            if (name.isNotEmpty()) {
                if (DutyRoster.twins(rows, name)) return null
                rows.indexOfFirst { it.label == name }.takeIf { it >= 0 }?.let { return it }
                val close = rows.indices.filter { close(rows[it].label, name) }
                if (close.size == 1) return close.single()
            }
            if (form == null || rows.size != form.lines) return null
            val ordered = rows.sortedBy { it.line }
            val there = ordered.getOrNull(form.rank) ?: return null
            // A name the photo read in that place that is someone else's is theirs, not the user's.
            if (there.label.isNotBlank() && name.isNotEmpty()) return null
            return rows.indexOf(there).takeIf { it >= 0 }
        }

        /**
         * Two names one letter apart, the same length, at least [CLOSE_MIN] letters, with the same digits ("1조" and "2조" are never close,
         * nor "김민지" and "김민지 (2)").
         */
        fun close(a: String, b: String): Boolean {
            val x = DutyCodes.fold(a).filterNot { it.isWhitespace() }
            val y = DutyCodes.fold(b).filterNot { it.isWhitespace() }
            if (x == y) return x.isNotEmpty()
            if (x.length != y.length || x.length < CLOSE_MIN) return false
            if (x.filter { it.isDigit() } != y.filter { it.isDigit() }) return false
            return x.indices.count { x[it] != y[it] } <= CLOSE
        }

        /** Per mille of the table's height at [y], the table being the words from [top] to [bottom]. */
        fun at(y: Int, top: Int, bottom: Int): Int? = (bottom - top).takeIf { it > 0 }?.let { h -> ((y - top) * 1000L / h).toInt().coerceIn(0, 1000) }

        /** The pixel row [at] per mille of a table from [top] to [bottom]. */
        fun y(at: Int, top: Int, bottom: Int): Int = top + ((bottom - top) * at.toLong() / 1000).toInt()

        /**
         * Where to tap on a new photo whose words span [left]..[right] × [top]..[bottom], by last month's taps: the row of days (when there
         * was one) and the user's line. Null when last month needed no tap.
         */
        fun hint(form: RosterForm?, left: Int, right: Int, top: Int, bottom: Int): RosterHint? {
            val my = form?.myAt ?: return null
            if (bottom <= top) return null
            val x = (left + right) / 2
            return RosterHint(form.dayAt?.let { x to y(it, top, bottom) }, x to y(my, top, bottom))
        }

        /**
         * The table of [words] laid level: its tilt taken out ([RosterGeometry.tilt]), so a tap on a name at the left edge and one in the
         * middle of the same line are the same place, and a sheet photographed a little turned next month still stands as this one did.
         */
        private class Level(words: List<RosterWord>) {
            private val r = Math.toRadians(RosterGeometry.tilt(words))
            private val sin = kotlin.math.sin(r)
            private val cos = kotlin.math.cos(r)
            fun ly(x: Double, y: Double) = -x * sin + y * cos
            fun lx(x: Double, y: Double) = x * cos + y * sin
            val top = words.minOf { ly(it.centerX.toDouble(), it.centerY.toDouble()) - it.height / 2.0 }
            val bottom = words.maxOf { ly(it.centerX.toDouble(), it.centerY.toDouble()) + it.height / 2.0 }
            val middle = (words.minOf { lx(it.left.toDouble(), it.centerY.toDouble()) } + words.maxOf { lx(it.right.toDouble(), it.centerY.toDouble()) }) / 2
            /** The photo's pixel for the level table's ([x], [y]). */
            fun back(x: Double, y: Double) = (x * cos - y * sin).toInt() to (x * sin + y * cos).toInt()
        }

        /** 2.0.0 [RVL-80] Per mille of the level table's height at the tap ([x], [y]) on the photo of [words]. */
        fun at(x: Int, y: Int, words: List<RosterWord>): Int? {
            if (words.isEmpty()) return null
            val l = Level(words)
            val h = l.bottom - l.top
            if (h <= 0) return null
            return ((l.ly(x.toDouble(), y.toDouble()) - l.top) * 1000 / h).toInt().coerceIn(0, 1000)
        }

        /**
         * 2.0.0 [RVL-80] Where to tap on a new photo of [words] by last month's taps, measured on the level table (as [at] keeps them):
         * the middle of the row of days (when there was one) and of the user's line. Null when last month needed no tap.
         */
        fun hint(form: RosterForm?, words: List<RosterWord>): RosterHint? {
            val my = form?.myAt ?: return null
            if (words.isEmpty()) return null
            val l = Level(words)
            if (l.bottom <= l.top) return null
            fun place(at: Int) = l.back(l.middle, l.top + (l.bottom - l.top) * at / 1000.0)
            return RosterHint(form.dayAt?.let(::place), place(my))
        }
    }
}
