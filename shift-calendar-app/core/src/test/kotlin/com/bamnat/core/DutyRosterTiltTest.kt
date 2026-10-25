package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterHint
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * [READ-02][READ-03][READ-04] The cause of "사진으로 읽기가 실행 자체를 못 함" (사용자 2026-10-25, 1.25 순번 367), kept as
 * tests: 1.24 read a 5-person, 31-day table level and at 0.5°, mixed names and codes from 1° and found no line at 2~3°
 * (통합 재현 notes/1.25/사진읽기-기울기-재현-시험). A phone's photo is tilted that much more often than not.
 */
class DutyRosterTiltTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false), DutyType(2, "이브닝", "E", false), DutyType(3, "나이트", "N", false, night = true), DutyType(4, "휴무", "O", true)))
    private val names = listOf("김민지", "이수진", "박하늘", "최유리", "정다은")
    private val pattern = listOf("D", "D", "E", "E", "N", "N", "O", "O")
    private fun code(person: Int, day: Int) = pattern[(day + person * 2) % pattern.size]

    /** The table drawn at [deg], with the camera's angle making it [keystone] wider at the bottom per row. */
    private fun sheet(deg: Double, cell: Int = 90, h: Int = 40, keystone: Double = 0.0, days: (Int) -> String? = { "$it" },
                      weekdays: Boolean = false): List<RosterWord> {
        val words = mutableListOf<RosterWord>()
        val a = Math.toRadians(deg)
        fun add(t: String, x: Int, y: Int, w: Int) {
            val cx = x + w / 2.0; val cy = y + h / 2.0
            val rx = cx * cos(a) - cy * sin(a); val ry = cx * sin(a) + cy * cos(a)
            words += RosterWord(t, (rx - w / 2).toInt(), (ry - h / 2).toInt(), (rx + w / 2).toInt(), (ry + h / 2).toInt())
        }
        // Wider at the bottom: each row stretched about the table's middle.
        fun at(x: Int, row: Int) = (1650 + (x - 1650) * (1 + keystone * row)).toInt()
        fun col(d: Int, row: Int) = at(300 + (d - 1) * cell, row)
        add("10월 근무표", 400, 0, 300)
        for (d in 1..31) days(d)?.let { t ->
            // Numbers read together ("1213") have one box over all their cells.
            val n = generateSequence(d) { it + 1 }.takeWhile { it <= 31 }.map { "$it" }.runningReduce { a, b -> a + b }.indexOf(t).coerceAtLeast(0)
            add(t, col(d, 0), 100, if (t.length < 2) 20 else n * cell + 18 * t.length / (n + 1))
        }
        if (weekdays) for (d in 1..31) add("월화수목금토일"[(d + 2) % 7].toString(), col(d, 0), 150, 20)
        names.forEachIndexed { i, n ->
            add(n, at(100, i + 1), 200 + i * 70, 120)
            for (d in 1..31) add(code(i, d), col(d, i + 1), 200 + i * 70, 22)
        }
        return words
    }

    private fun assertAllRead(words: List<RosterWord>, why: String) {
        val rows = DutyRoster.read(words, codes)
        names.forEachIndexed { i, n ->
            val row = rows.firstOrNull { it.label == n }
            assertTrue("$why: $n not found in ${rows.map { it.label }}", row != null)
            val wrong = (1..31).filter { d -> row!!.cells[d] != codes.match(code(i, d)) }
            assertTrue("$why: $n wrong on $wrong", wrong.isEmpty())
        }
    }

    @Test fun tiltedEitherWayUpToFiveDegrees() {
        listOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, -1.0, -2.5, -5.0).forEach { assertAllRead(sheet(it), "$it°") }
    }

    @Test fun theTiltIsMeasured() {
        val r = DutyRoster.analyse(sheet(2.0), codes)
        assertEquals(2.0, r.tilt, 0.15)
        assertEquals(0.0, DutyRoster.analyse(sheet(0.0), codes).tilt, 0.0)
    }

    @Test fun tableWiderAtTheBottom() {
        // The camera held below the table: each row's columns 2% wider, on top of a 1.5° tilt.
        assertAllRead(sheet(1.5, keystone = 0.02), "keystone")
    }

    @Test fun dayNumbersRunTogetherOrLost() {
        // "1213" for 12 13, "293031" for 29 30 31, and 7 and 8 lost: every column is still where the line through the rest puts it.
        val days: (Int) -> String? = { d ->
            when (d) { 12 -> "1213"; 13 -> null; 29 -> "293031"; 30, 31 -> null; 7, 8 -> null; else -> "$d" }
        }
        assertAllRead(sheet(1.0, days = days), "joined")
    }

    @Test fun lastDayCutAtTheEdge() {
        // 31 is out of the photo but its cells are there.
        assertAllRead(sheet(0.0, days = { d -> if (d == 31) null else "$d" }), "no 31")
    }

    @Test fun weekdaysUnderTheDays() = assertAllRead(sheet(2.0, weekdays = true), "weekdays")

    @Test fun nothingReadSaysWhy() {
        assertEquals(RosterPhotoRead.Problem.NO_TEXT, DutyRoster.analyse(emptyList(), codes).problem)
        val noDays = listOf(RosterWord("안녕하세요", 0, 0, 100, 40), RosterWord("반가워요", 0, 60, 100, 100))
        assertEquals(RosterPhotoRead.Problem.NO_DAYS, DutyRoster.analyse(noDays, codes).problem)
        val onlyDays = (1..10).map { RosterWord("$it", it * 50, 0, it * 50 + 20, 30) }
        assertEquals(RosterPhotoRead.Problem.NO_DUTIES, DutyRoster.analyse(onlyDays, codes).problem)
        assertNull(DutyRoster.analyse(sheet(1.0), codes).problem)
    }

    @Test fun linesAreListedForTheDiagnosis() {
        val r = DutyRoster.analyse(sheet(1.0, weekdays = true), codes)
        assertEquals(RosterPhotoRead.Kind.DAYS, r.lines[1].kind)
        assertEquals(RosterPhotoRead.Kind.WEEKDAYS, r.lines[2].kind)
        assertEquals(5, r.lines.count { it.kind == RosterPhotoRead.Kind.DUTIES })
    }

    @Test fun twoTapsReadATableWithNoDayNumbers() {
        // [READ-04] A roster with a row of dates the photo could not read (here: blurred into nothing but dots): the user
        // taps that row, then their own line.
        val words = sheet(1.5, days = { "·" })
        assertTrue(DutyRoster.read(words, codes).none { it.label == "박하늘" && it.cells.size >= 31 })
        val dayRow = words.filter { it.text == "·" }.let { w -> w[w.size / 2] }
        val mine = words.first { it.text == "박하늘" }
        val rows = DutyRoster.read(words, codes, RosterHint(dayRow.centerX to dayRow.centerY, mine.centerX to mine.centerY))
        assertEquals(1, rows.size)
        assertEquals("박하늘", rows[0].label)
        (1..31).forEach { d -> assertEquals("day $d", codes.match(code(2, d)), rows[0].cells[d]) }
    }

    @Test fun myLineAloneWithNoRowOfDays() {
        // [READ-04] No row of days at all: the tapped line's codes go in from the 1st, every day yellow to look at.
        val words = sheet(0.0, days = { null })
        val mine = words.first { it.text == "최유리" }
        val row = DutyRoster.read(words, codes, RosterHint(myRow = mine.centerX to mine.centerY)).single()
        assertEquals(31, row.cells.size)
        assertEquals(codes.match(code(3, 1)), row.cells[1])
        assertEquals(31, row.doubtful(31).size)
    }

    @Test fun tappingMyLinePicksItAmongMany() {
        val words = sheet(2.0)
        val mine = words.first { it.text == "정다은" }
        val rows = DutyRoster.read(words, codes, RosterHint(myRow = mine.centerX to mine.centerY))
        assertEquals(listOf("정다은"), rows.map { it.label })
    }

    @Test fun linesKeepTheTablesOrder() {
        val rows = DutyRoster.read(sheet(1.0), codes)
        assertEquals(names, rows.sortedBy { it.line }.map { it.label })
    }

    @Test fun cellsOffTheirColumnAreLookedAt() {
        // One code pushed 40% of a column to the right: it stays on its day, yellow.
        val words = sheet(0.0).map { w -> if (w.text == code(0, 10) && w.top == 200 && w.left == 300 + 9 * 90) w.copy(left = w.left + 36, right = w.right + 36) else w }
        val row = DutyRoster.read(words, codes).first { it.label == "김민지" }
        assertEquals(codes.match(code(0, 10)), row.cells[10])
        assertTrue(10 in row.doubtful(31))
        assertTrue(DutyCell.Unknown("?") !in row.cells.values)
    }
}
