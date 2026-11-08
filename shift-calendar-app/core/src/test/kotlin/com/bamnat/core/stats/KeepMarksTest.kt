package com.bamnat.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.39 [OBS2-08] 기능과 머묾: what a coming back says about the last open day's features. */
class KeepMarksTest {
    private val used = mapOf(100L to setOf("open", "pay_view", "life_new", "nope"))
    private fun of(last: Long?, today: Long) = KeepMarks.of(last, today) { used[it].orEmpty() }

    @Test fun withinAWeekIsSevenThenThirtyThenNothing() {
        assertEquals(KeepMarks.Mark(100, 7, listOf("open", "pay_view")), of(100, 101))
        assertEquals(7L, of(100, 107)?.window)
        assertEquals(30L, of(100, 108)?.window)
        assertEquals(30L, of(100, 130)?.window)
        assertNull(of(100, 131))
    }

    @Test fun noEarlierOpenTheSameDayOrNothingCountedIsNothing() {
        assertNull(of(null, 101))
        assertNull(of(100, 100))
        assertNull(KeepMarks.of(100, 101) { setOf("life_d1") })
    }

    @Test fun waitingMarksRoundTripAndOldOrForeignLinesDrop() {
        val marks = listOf(KeepMarks.Mark(100, 7, listOf("open", "pay_view")), KeepMarks.Mark(120, 30, listOf("backup")))
        assertEquals(marks, KeepMarks.decode(KeepMarks.encode(marks), keepFrom = 90))
        assertEquals(marks.drop(1), KeepMarks.decode(KeepMarks.encode(marks), keepFrom = 110))
        assertEquals(emptyList<KeepMarks.Mark>(), KeepMarks.decode("100\t9\topen\nx\n101\t7\tlife_new,zz", keepFrom = 0))
    }
}
