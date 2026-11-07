package com.bamnat.core

import com.bamnat.core.imports.CalendarShot
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * [GROW-05] 1.38 다른 앱에서 옮기기: a month calendar's screenshot (7 columns, each day's number with its code under it), the
 * greyed days of the months around it left out. Made-up pictures drawn here; no other app's screen is copied.
 */
class CalendarShotTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "주간", "주", false), DutyType(2, "야간", "야", false, night = true),
        DutyType(3, "비번", "비", true), DutyType(4, "휴무", "휴", true)))
    private val order = listOf("주간", "야간", "비번", "휴무")

    /** November 2026 (it starts on a Sunday) drawn as a phone's month view: 6 weeks, days before and after greyed. */
    private fun month(code: (Int) -> String? = { order[(it - 1) % 4] }, stuck: Boolean = false, cell: Int = 150, x0: Int = 20): List<RosterWord> {
        val ym = YearMonth.of(2026, 11)
        val lead = ym.atDay(1).dayOfWeek.value % 7 // Sunday first
        val words = mutableListOf<RosterWord>()
        // The title and weekdays above the grid.
        words += RosterWord("2026년 11월", 300, 20, 700, 70)
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d -> words += RosterWord(d, x0 + i * cell + 60, 110, x0 + i * cell + 90, 140) }
        for (i in 0 until 42) {
            val row = i / 7; val col = i % 7
            val x = x0 + col * cell; val y = 170 + row * 180
            val day = i - lead + 1
            val shown = when {
                day < 1 -> 31 + day // October's last days
                day > ym.lengthOfMonth() -> day - ym.lengthOfMonth()
                else -> day
            }
            val c = if (day in 1..ym.lengthOfMonth()) code(day) else null
            if (stuck && c != null) words += RosterWord("$shown${c.take(1)}", x + 10, y, x + 80, y + 36)
            else {
                words += RosterWord("$shown", x + 10, y, x + 50, y + 36)
                if (c != null) words += RosterWord(c, x + 20, y + 70, x + 110, y + 110)
            }
        }
        return words
    }

    @Test fun aMonthViewIsOneLineOfTheMonth() {
        val row = CalendarShot.read(month(), codes)!!
        assertEquals(30, row.cells.size)
        assertEquals(DutyCell.Shift(1), row.cells[1])
        assertEquals(DutyCell.Shift(2), row.cells[2])
        assertEquals(DutyCell.Shift(4), row.cells[4])
        assertEquals(DutyCell.Shift(2), row.cells[30])
        assertEquals(CalendarShot.LABEL, row.label)
    }

    @Test fun theRosterReaderTakesItAsOneLine() {
        val read = DutyRoster.analyse(month(), codes)
        assertEquals(1, read.rows.size)
        assertEquals(30, read.rows[0].cells.size)
        assertNull(read.problem)
    }

    @Test fun aNumberStuckToItsCode() {
        val row = CalendarShot.read(month(stuck = true), codes)!!
        assertEquals(30, row.cells.size)
        assertEquals(DutyCell.Shift(3), row.cells[3])
    }

    @Test fun emptyDaysStayOut() {
        val row = CalendarShot.read(month(code = { if (it % 5 == 0) null else order[(it - 1) % 4] }), codes)!!
        assertEquals(24, row.cells.size)
        assertTrue(15 !in row.cells)
        // A day lost between read ones is one to look at.
        assertTrue(15 in row.doubtful(30))
    }

    @Test fun aRosterTableIsNotACalendar() {
        val words = mutableListOf<RosterWord>()
        (1..30).forEach { d -> words += RosterWord("$d", 200 + d * 60, 100, 240 + d * 60, 130) }
        (1..30).forEach { d -> words += RosterWord(order[(d - 1) % 4].take(1), 200 + d * 60, 160, 240 + d * 60, 190) }
        assertNull(CalendarShot.read(words, codes))
    }

    @Test fun aCalendarWithoutWorkIsNothing() {
        assertNull(CalendarShot.read(month(code = { null }), codes))
    }

    @Test fun theOrderOfAMonth() {
        // 주 야 비 휴, one 대근 on the 10th.
        val days = (1..30).associateWith { d -> if (d == 10) 1L else listOf(1L, 2L, null, null)[(d - 1) % 4] }
        val o = CalendarShot.order(days)!!
        assertEquals(listOf(1L, 2L, null, null), o.steps)
        // A month painted by hand has no order.
        val painted = (1..30).associateWith { d -> listOf(1L, 2L, null, 1L, 1L, 2L, null)[(d * 7 + d / 3) % 7] }
        assertNull(CalendarShot.order(painted))
    }
}
