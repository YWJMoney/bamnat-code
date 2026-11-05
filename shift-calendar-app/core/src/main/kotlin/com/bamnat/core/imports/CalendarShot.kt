package com.bamnat.core.imports

import kotlin.math.abs

/**
 * [GROW-05][EASE-15] 1.38 다른 앱에서 옮기기: a screenshot of a month calendar (any 근무 앱, a 캘린더, a 사진 of a wall
 * calendar), read on the phone. Unlike a 듀티표 (one row of days, a line per person) the days stand in a grid of 7
 * columns, each day's number with the day's code under or beside it ("6 주", "7 야간", "8 비"). Only what is on the
 * picture is read — no other app's files or servers (계획 GROW-05 "남의 파일 형식·서버는 쓰지 않음").
 *
 * The month's days are the longest run 1, 2, 3 … in reading order, so the greyed days of the months before and after
 * ("27 28 29 30 | 1 2 …") are left out. Each day's cell reaches to the next day's number to its right and the next row's
 * numbers below; the first word in it that is a code is the day's.
 */
object CalendarShot {
    /** Fewest days for a picture to be a month calendar: four weeks. */
    const val MIN_DAYS = 20

    private val NUMBER = Regex("""^(\d{1,2})(?:일)?$""")
    /** A number stuck to its code in one word: "6주", "12야간", "3D". */
    private val STUCK = Regex("""^(\d{1,2})([^\d\s].*)$""")

    private data class Day(val day: Int, val word: RosterWord)

    /** The calendar on the picture as one line ("달력 캡처"), or null when it is no month calendar. */
    fun read(words: List<RosterWord>, codes: DutyCodes): RosterRow? {
        val clean = words.filter { it.text.isNotBlank() }
        val numbers = ArrayList<Day>()
        val rest = ArrayList<RosterWord>()
        for (w in clean) {
            val t = DutyCodes.fold(w.text).trim()
            val n = NUMBER.matchEntire(t)?.groupValues?.get(1)?.toIntOrNull()
            val stuck = if (n == null) STUCK.matchEntire(t) else null
            when {
                n != null && n in 1..31 -> numbers += Day(n, w)
                stuck != null && stuck.groupValues[1].toInt() in 1..31 && codes.read(stuck.groupValues[2]) != null -> {
                    // Split the word across its box, the number at the left.
                    val split = w.left + (w.right - w.left) * stuck.groupValues[1].length / t.length.coerceAtLeast(1)
                    numbers += Day(stuck.groupValues[1].toInt(), w.copy(text = stuck.groupValues[1], right = split))
                    rest += w.copy(text = stuck.groupValues[2], left = split)
                }
                else -> rest += w
            }
        }
        if (numbers.size < MIN_DAYS) return null
        val rows = rowsOf(numbers)
        if (rows.size < 4) return null
        // Reading order: rows top to bottom, each left to right.
        val ordered = rows.flatMap { r -> r.sortedBy { it.word.centerX } }
        val run = longestRun(ordered)
        if (run.size < MIN_DAYS) return null
        // A month calendar has at most 7 days a row, and each row's days in order across.
        val runRows = rows.map { r -> r.filter { it in run } }.filter { it.isNotEmpty() }
        if (runRows.any { it.size > 7 }) return null
        val pitchX = medianPitchX(runRows) ?: return null
        val pitchY = medianPitchY(runRows) ?: return null
        val cells = HashMap<Int, DutyCell>()
        val raw = HashMap<Int, String>()
        val guessed = HashSet<Int>()
        val notes = HashMap<Int, String>()
        val used = HashSet<RosterWord>()
        for (d in run) {
            val w = d.word
            val left = w.left - pitchX * 0.15
            val right = w.left + pitchX * 0.85
            val top = w.top - w.height * 0.3
            val bottom = w.top + pitchY * 0.92
            val inside = rest.filter { it !in used && it.centerX >= left && it.centerX < right && it.centerY >= top && it.centerY < bottom }
                .sortedWith(compareBy<RosterWord> { it.top }.thenBy { it.left })
            for (c in inside) {
                val read = codes.read(c.text) ?: continue
                cells[d.day] = read.first
                raw[d.day] = c.text
                used += c
                if (codes.guessedWork(c.text, read.first)) guessed += d.day
                read.second?.let { notes[d.day] = it }
                break
            }
        }
        if (cells.size < MIN_CELLS) return null
        return RosterRow(LABEL, cells, guessed = guessed, notes = notes, raw = raw)
    }

    /**
     * [GROW-05] The repeating order a month of days shows ([days] by day of the month, null a day off): the shortest period
     * from 2 to [MAX_PERIOD] days that nearly every pair of days that far apart agrees with (a cell read wrong or a 대근
     * may break one), and its days from the 1st. Null when no order repeats in it (a 매달 근무표 that is painted instead).
     */
    fun order(days: Map<Int, Long?>, minAgree: Double = 0.9): Order? {
        val known = days.keys.sorted()
        if (known.size < MIN_DAYS) return null
        for (p in 2..MAX_PERIOD) {
            val pairs = known.filter { it + p in days }
            if (pairs.size < p * 2) continue
            val agree = pairs.count { days[it] == days[it + p] }.toDouble() / pairs.size
            if (agree < minAgree) continue
            // Each step is the value most of its days have.
            val steps = (0 until p).map { i ->
                val values = known.filter { (it - 1) % p == i }.map { days[it] }
                if (values.isEmpty()) return@map null
                values.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            }
            if (steps.all { it == null }) return null
            return Order(steps, agree)
        }
        return null
    }

    /** The order found ([steps], the 1st of the month being step 0) and how many days agreed with it. */
    data class Order(val steps: List<Long?>, val agree: Double)

    /** The longest order looked for: two weeks. */
    const val MAX_PERIOD = 14

    /** The line's name for a calendar picture. */
    const val LABEL = "달력 캡처"

    /** Fewest days with a code for the picture to be read as a calendar of work. */
    const val MIN_CELLS = 7

    /** Numbers on one row of the grid: their middles within about half a number's height of each other. */
    private fun rowsOf(days: List<Day>): List<List<Day>> {
        val sorted = days.sortedBy { it.word.centerY }
        val rows = ArrayList<MutableList<Day>>()
        for (d in sorted) {
            val row = rows.lastOrNull()
            val mid = row?.let { r -> r.sumOf { it.word.centerY } / r.size }
            if (row != null && mid != null && abs(d.word.centerY - mid) <= d.word.height * 0.6) row += d else rows += mutableListOf(d)
        }
        return rows
    }

    /** The longest run 1, 2, 3 … in reading order: the month itself, not the days around it. */
    private fun longestRun(ordered: List<Day>): List<Day> {
        var best = emptyList<Day>()
        for (i in ordered.indices) {
            if (ordered[i].day != 1) continue
            val run = arrayListOf(ordered[i])
            for (j in i + 1 until ordered.size) {
                if (ordered[j].day == run.last().day + 1) run += ordered[j]
                else if (ordered[j].day == 1) break
            }
            if (run.size > best.size) best = run
        }
        return best
    }

    private fun medianPitchX(rows: List<List<Day>>): Double? {
        val gaps = rows.flatMap { r -> r.sortedBy { it.word.left }.zipWithNext { a, b -> (b.word.left - a.word.left).toDouble() } }.filter { it > 0 }
        return gaps.sorted().getOrNull(gaps.size / 2)
    }

    private fun medianPitchY(rows: List<List<Day>>): Double? {
        val tops = rows.map { r -> r.map { it.word.top }.sorted()[r.size / 2].toDouble() }
        val gaps = tops.zipWithNext { a, b -> b - a }.filter { it > 0 }
        return gaps.sorted().getOrNull(gaps.size / 2)
    }
}
