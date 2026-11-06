package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * [READ-37] 1.38 PC (7차 빌드, duty-clean.png): a ward's roster with each date over its weekday in one cell and "이름"
 * between the two lines, read with a few weekdays misread ("으", "의" for 일). The two head lines are not people.
 */
class RosterHeadRowsTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true), DutyType(4, "오프", "O", true),
    ))
    private val names = listOf("김민지", "이서연", "박지훈", "최수빈", "정하늘", "강다은", "윤서준", "임유진", "한지민", "오세훈", "서예린", "권도윤")
    private val order = listOf("D", "D", "E", "E", "N", "N", "O", "O")
    // 2026-11-01 is a Sunday; the 1st's 일 read as "의", some others as "으" and "윌".
    private val week = "일월화수목금토"
    private val misread = mapOf(1 to "으", 8 to "으", 15 to "으", 22 to "으", 29 to "의", 2 to "윌", 9 to "윌", 16 to "윌",
        23 to "윌", 30 to "윌", 4 to "스", 11 to "스", 6 to "급", 13 to "급")

    private fun words(): List<RosterWord> {
        val w = mutableListOf<RosterWord>()
        w += RosterWord("7병동", 40, 20, 140, 54)
        w += RosterWord("2026년", 150, 16, 260, 50)
        w += RosterWord("11월", 270, 14, 330, 48)
        val x0 = 170; val cw = 54
        w += RosterWord("이름", 70, 108, 130, 132)
        for (d in 1..30) {
            val x = x0 + (d - 1) * cw
            w += RosterWord("$d", x + 6, 92, x + 30, 116)
            w += RosterWord(misread[d] ?: week[(d - 1) % 7].toString(), x + 8, 124, x + 32, 148)
        }
        names.forEachIndexed { r, n ->
            val y = 160 + r * 66
            w += RosterWord(n, 60, y + 18, 140, y + 42)
            for (d in 1..30) {
                val x = x0 + (d - 1) * cw
                w += RosterWord(order[(d - 1 + r * 2) % 8], x + 14, y + 18, x + 30, y + 42)
            }
        }
        return w
    }

    @Test fun theDatesAndWeekdaysAreNotPeople() {
        val read = DutyRoster.analyse(words(), codes)
        assertEquals(read.rows.joinToString { "${it.label}·${it.cells.size}" }, names.toSet(), read.rows.map { it.label }.toSet())
        assertEquals(12, read.rows.size)
        val kim = read.rows.first { it.label == "김민지" }
        assertEquals(30, kim.cells.size)
        assertEquals(DutyCell.Shift(1), kim.cells[1])
    }

    @Test fun aLineOfKoreanCodesStays() {
        val row = com.bamnat.core.imports.RosterRow("1조", (1..30).associateWith { DutyCell.Shift(1L) }, raw = (1..30).associateWith { "주" })
        assertTrue(!DutyRoster.headerLike(row))
        val head = com.bamnat.core.imports.RosterRow("으", (1..16).associateWith { DutyCell.Unknown("월") })
        assertTrue(DutyRoster.headerLike(head))
        val named = com.bamnat.core.imports.RosterRow("이름 의", (1..22).associateWith { d -> if (d % 5 == 0) DutyCell.Shift(1L) else DutyCell.Unknown("$d") })
        assertTrue(DutyRoster.headerLike(named))
    }
}
