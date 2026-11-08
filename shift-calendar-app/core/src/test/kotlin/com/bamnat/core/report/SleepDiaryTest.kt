package com.bamnat.core.report

import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepMood
import com.bamnat.core.sleep.SleepSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.39 G5-N1 2주 수면 일지. */
class SleepDiaryTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test fun twoWeeksByTheDayOfWaking() {
        val y = today.minusDays(1)
        val night = SleepEntry(1, y.atTime(23, 10), today.atTime(6, 40))
        val nap = SleepEntry(2, today.atTime(13, 0), today.atTime(13, 40))
        val after = SleepEntry(3, y.atTime(8, 30), y.atTime(15, 0))
        val work = mapOf(y.minusDays(1) to listOf(SleepSchedule.Work(y.minusDays(1), y.minusDays(1).atTime(19, 0), y.atTime(7, 0), "야간")))
        val checks = listOf(SleepCheck(start = night.start, end = night.end!!, mood = SleepMood.entries.first()))
        val doc = SleepDiary.doc(today, listOf(night, nap, after), checks, work, 420)
        val table = doc.blocks.filterIsInstance<ReportDoc.Table>().single()
        assertEquals(14, table.rows.size)
        val last = table.rows.last()
        assertEquals("11/9 (월)", last[0])
        assertEquals("쉼", last[1])
        assertEquals("23:10~06:40", last[2])
        assertEquals("7시간 30분", last[3])
        assertEquals("13:00~13:40", last[4])
        assertEquals(SleepMood.entries.first().label, last[5])
        assertEquals("08:30~15:00", table.rows[12][2])
        assertEquals("야간 19:00~07:00", table.rows[11][1])
        val text = doc.text()
        assertTrue(text.contains("야간 근무: 1번"))
        assertTrue(text.contains("잠을 적은 날: 2일"))
        assertTrue(text.contains("진단이 아니며"))
    }
}
