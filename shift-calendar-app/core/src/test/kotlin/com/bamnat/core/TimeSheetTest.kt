package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.TimeSheet
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVL-72] (검토 T07b-01·T07b-02·I07bb-02) 알바 시간표 and 현장 공수표 read from a file, pasted text or a photo's words: before, every
 * cell was "not a code" and the screen said it read nothing.
 */
class TimeSheetTest {
    private val near = LocalDate.of(2026, 11, 12)
    private val nov = YearMonth.of(2026, 11)

    @Test fun `a cell is a time, a length, 공수 or a day off`() {
        assertEquals(DutyCell.Hours(9 * 60, 6 * 60), TimeSheet.cell("09:00-15:00"))
        assertEquals("written in hours only, 9-3 is 9시 to 오후 3시", DutyCell.Hours(9 * 60, 6 * 60), TimeSheet.cell("9-3"))
        assertEquals(DutyCell.Hours(9 * 60 + 30, 8 * 60 + 30), TimeSheet.cell("9:30~18"))
        assertEquals("past midnight", DutyCell.Hours(22 * 60, 8 * 60), TimeSheet.cell("22-6"))
        assertEquals(DutyCell.Hours(18 * 60, 5 * 60), TimeSheet.cell("18:00 - 23:00"))
        assertEquals(DutyCell.Hours(9 * 60, 6 * 60), TimeSheet.cell("0900-1500"))
        assertEquals(DutyCell.Hours(9 * 60, 6 * 60), TimeSheet.cell("9시-15시"))
        assertEquals(DutyCell.Hours(null, 6 * 60), TimeSheet.cell("6h"))
        assertEquals(DutyCell.Hours(null, 6 * 60 + 30), TimeSheet.cell("6.5시간"))
        assertEquals(DutyCell.Off, TimeSheet.cell("휴무"))
        assertEquals(DutyCell.Units(1.5), TimeSheet.cell("1.5", units = true))
        assertNull("a bare number is 공수 only on a 공수표", TimeSheet.cell("1.5"))
        assertEquals(DutyCell.Units(1.0), TimeSheet.cell("1공"))
        assertEquals(DutyCell.Units(0.5), TimeSheet.cell("반공", units = true))
        assertNull(TimeSheet.cell("D"))
        assertNull(TimeSheet.cell("김알바"))
        assertNull("too long a day", TimeSheet.cell("20h"))
        assertNull(TimeSheet.cell("5", units = true))
    }

    @Test fun `an 알바 sheet of days and times`() {
        val rows = listOf(
            listOf("2026년 11월 알바 시간표"),
            listOf("이름", "1", "2", "3", "4", "5", "6", "7"),
            listOf("김알바", "09:00-15:00", "", "9-3", "", "18:00-23:00", "휴무", ""),
            listOf("박알바", "", "10-16", "", "10-16", "", "", "6h"),
        )
        val r = TimeSheet.read(rows, near = near)!!
        assertEquals(nov, r.month)
        assertEquals(listOf("김알바", "박알바"), r.rows.map { it.label })
        val kim = r.rows[0]
        assertEquals(DutyCell.Hours(9 * 60, 360), kim.cells[1])
        assertEquals(DutyCell.Hours(18 * 60, 300), kim.cells[5])
        assertEquals(DutyCell.Off, kim.cells[6])
        assertTrue(kim.timed)
        assertTrue("days not worked are not cells the photo lost", kim.doubtful(30).isEmpty())
        assertEquals(DutyCell.Hours(null, 360), r.rows[1].cells[7])
    }

    @Test fun `a 현장 공수표`() {
        val rows = listOf(
            listOf("11월 공수"),
            listOf("성명") + (1..10).map { "$it" },
            listOf("홍길동", "1", "1", "0.5", "", "1.5", "1", "", "1", "1", "0.5"),
        )
        val r = TimeSheet.read(rows, near = near)!!
        val line = r.rows.single()
        assertEquals("홍길동", line.label)
        assertEquals(DutyCell.Units(0.5), line.cells[3])
        assertEquals(DutyCell.Units(1.5), line.cells[5])
        assertEquals(8, line.cells.size)
    }

    @Test fun `a 출근 line and a 퇴근 line are one person`() {
        val rows = listOf(
            listOf("", "1", "2", "3", "4", "5", "6"),
            listOf("김알바 출근", "09:00", "", "10:00", "", "", "13:00"),
            listOf("퇴근", "15:00", "", "16:30", "", "", "18:00"),
        )
        val line = TimeSheet.read(rows, nov, near)!!.rows.single()
        assertEquals("김알바", line.label)
        assertEquals(DutyCell.Hours(10 * 60, 390), line.cells[3])
        assertEquals(3, line.cells.size)
    }

    @Test fun `a weekly timetable goes on every such weekday of the month`() {
        val rows = listOf(
            listOf("", "월", "화", "수", "목", "금", "토", "일"),
            listOf("김알바", "09:00-15:00", "", "9-3", "", "18-22", "휴무", ""),
        )
        val line = TimeSheet.read(rows, nov, near)!!.rows.single()
        assertEquals(DutyCell.Hours(18 * 60, 240), line.weekly[DayOfWeek.FRIDAY])
        val days = line.cellsIn(nov)
        // 2026-11-02 is a Monday.
        assertEquals(DutyCell.Hours(9 * 60, 360), days[2])
        assertEquals(DutyCell.Hours(9 * 60, 360), days[30])
        assertEquals(DutyCell.Off, days[7])
        assertEquals(listOf(2, 9, 16, 23, 30), days.filterValues { it == DutyCell.Hours(9 * 60, 360) }.keys.filter { nov.atDay(it).dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test fun `one day a row, with 출근 and 퇴근 columns or a range`() {
        val sheet = listOf(
            listOf("날짜", "요일", "출근", "퇴근"),
            listOf("2026-11-03", "화", "09:00", "15:00"),
            listOf("2026-11-05", "목", "오후 6:00", "오후 11:00"),
            listOf("2026-11-07", "토", "10:00", "14:30"),
        )
        val a = TimeSheet.read(sheet, near = near)!!.rows.single()
        assertEquals(DutyCell.Hours(18 * 60, 300), a.cells[5])
        assertEquals(DutyCell.Hours(10 * 60, 270), a.cells[7])
        val pasted = TimeSheet.readText("11/3 09:00 - 15:00\n11/4 9-3\n11/6 6h\n11/8 휴무", near = near)!!.rows.single()
        assertEquals(DutyCell.Hours(9 * 60, 360), pasted.cells[3])
        assertEquals(DutyCell.Hours(9 * 60, 360), pasted.cells[4])
        assertEquals(DutyCell.Hours(null, 360), pasted.cells[6])
        assertEquals(DutyCell.Off, pasted.cells[8])
        val weekly = TimeSheet.readText("월 9-15\n수 9-15\n금 18-22", near = near)!!.rows.single()
        assertEquals(3, weekly.weekly.size)
    }

    @Test fun `a roster of codes is not a time sheet`() {
        val rows = listOf(
            listOf("이름") + (1..7).map { "$it" },
            listOf("김민지", "D", "D", "E", "E", "N", "N", "O"),
        )
        assertNull(TimeSheet.read(rows, nov, near))
    }

    @Test fun `a photo's words, a range in pieces joined and placed under its day`() {
        fun w(t: String, x: Int, y: Int, width: Int = 40) = RosterWord(t, x, y, x + width, y + 30)
        val words = (1..7).map { w("$it", it * 100, 0, 20) } + listOf(
            w("김알바", -60, 100, 50),
            w("09:00", 85, 100, 25), w("-", 112, 100, 4), w("15:00", 118, 100, 25),
            w("9-3", 290, 100),
            w("18:00-23:00", 470, 100, 60),
        ) + listOf(w("홍길동", -60, 200, 50)) + (1..7).map { w(if (it == 3) "0.5" else "1", it * 100, 200, 20) }
        val r = TimeSheet.readWords(words, nov, near)!!
        val kim = r.rows.first { it.label == "김알바" }
        assertEquals(DutyCell.Hours(9 * 60, 360), kim.cells[1])
        assertEquals(DutyCell.Hours(9 * 60, 360), kim.cells[3])
        assertEquals(DutyCell.Hours(18 * 60, 300), kim.cells[5])
        val hong = r.rows.first { it.label == "홍길동" }
        assertEquals(DutyCell.Units(0.5), hong.cells[3])
        assertEquals(7, hong.cells.size)
    }

    @Test fun `a day of the 시간표 goes on the calendar as work at its times`() {
        val e = DayEntry(nov.atDay(3))
        val after = DutyRoster.apply(e, DutyCell.Hours(9 * 60, 360), work = 7)
        assertEquals(ShiftOverride.Shift(7), after.override)
        assertEquals(LocalTime.of(9, 0), after.customStart)
        assertEquals(LocalTime.of(15, 0), after.customEnd)
        val night = DutyRoster.hours(e, DutyCell.Hours(22 * 60, 480), work = null)
        assertEquals(LocalTime.of(6, 0), night.customEnd)
        assertEquals("a length only starts when its kind does", LocalTime.of(10, 0),
            DutyRoster.hours(e, DutyCell.Hours(null, 300), work = 7, kindStart = 10 * 60).customStart)
        assertEquals("공수 is the day's 일감, not the day", e, DutyRoster.apply(e, DutyCell.Units(1.0)))
        assertEquals("1.5공", TimeSheet.short(DutyCell.Units(1.5)))
        assertEquals("9–15", TimeSheet.short(DutyCell.Hours(9 * 60, 360)))
        assertEquals("9:30–18", TimeSheet.short(DutyCell.Hours(9 * 60 + 30, 510)))
        assertEquals("6시간", TimeSheet.short(DutyCell.Hours(null, 360)))
        // The code reader still reads its own rosters as before.
        assertTrue(DutyRoster.readText("1 2 3 4 5 6 7\n김민지 D D E E N N O", DutyCodes(emptyList())).isNotEmpty())
    }
}
