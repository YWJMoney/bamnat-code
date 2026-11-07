package com.bamnat.app.ui.calendar

import com.bamnat.core.recipe.CalendarView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CELL-03] 1.39 (PC a483ca15) 알아서 lists a calendar's memos too; only a month with lines, where lists were asked for, gets tall cells. */
class CellListOnTest {
    private val plain = CalendarView()

    @Test fun autoListsWhenTheCalendarHasMemos() {
        assertFalse(cellListOn("auto", jobs = false, memoInCells = false, view = plain, sideways = false))
        assertTrue(cellListOn("auto", jobs = false, memoInCells = false, view = plain, sideways = false, hasMemos = true))
        assertFalse(cellListOn("off", jobs = true, memoInCells = true, view = plain, sideways = false, hasMemos = true))
        assertFalse(cellListOn("on", jobs = true, memoInCells = true, view = plain, sideways = true, hasMemos = true))
    }

    @Test fun tallOnlyForAMonthWithLinesWhereListsWereAskedFor() {
        assertTrue(cellListTall("auto", jobs = true, memoInCells = false, view = plain, monthHasLines = true))
        // 현장 달력의 빈 달: 낮은 칸.
        assertFalse(cellListTall("auto", jobs = true, memoInCells = false, view = plain, monthHasLines = false))
        // 메모가 있어서 켜진 목록(사무직 기본): 보통 높이.
        assertFalse(cellListTall("auto", jobs = false, memoInCells = false, view = plain, monthHasLines = true))
        assertTrue(cellListTall("on", jobs = false, memoInCells = false, view = plain, monthHasLines = true))
        assertTrue(cellListTall("auto", jobs = false, memoInCells = false, view = CalendarView(memo = 2), monthHasLines = true))
    }
}
