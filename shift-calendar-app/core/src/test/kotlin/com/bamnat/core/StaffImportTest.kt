package com.bamnat.core

import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.WageForm
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.StaffRow
import com.bamnat.core.imports.StaffSheets
import com.bamnat.core.imports.WordTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 직원 명단 가져오기 (사용자 2026-11-05 "어떤 형식이든"). */
class StaffImportTest {
    private val on = LocalDate.of(2026, 11, 5)

    @Test fun aListBecomesStaffAndSameNamesAreNotAddedTwice() {
        val rows = listOf(
            StaffRow("김 알바", WageForm.HOUR, 11_000, mapOf(DayOfWeek.MONDAY to (LocalTime.of(9, 0) to LocalTime.of(15, 0))), job = "홀"),
            StaffRow("이주방", null, null),
            StaffRow("박매니저", WageForm.MONTH, 2_500_000, doubtful = setOf("base")),
        )
        val l = StaffImport.lines(rows, listOf(StaffMember("s0", "김알바")), on)
        assertTrue("같은 이름은 이미 있음", l[0].already)
        assertEquals(LocalTime.of(15, 0), l[0].member.weekly[DayOfWeek.MONDAY]?.end)
        assertEquals("홀", l[0].member.job)
        assertEquals("빈 금액은 그해 최저시급", 10_320L, l[1].member.base)
        assertEquals(WageForm.HOUR, l[1].member.form)
        assertEquals(3, l[1].check.size)
        assertEquals(listOf("금액", "근무 요일·시간이 없어요"), l[2].check)
    }

    private fun w(t: String, x: Int, y: Int) = RosterWord(t, x, y, x + t.length * 20, y + 24)

    @Test fun aPhotoOfAListIsReadAsItsColumns() {
        val words = listOf(
            w("이름", 10, 10), w("시급", 200, 10), w("근무요일", 400, 12), w("출근", 600, 10), w("퇴근", 760, 9),
            w("김민지", 10, 60), w("10,500", 200, 62), w("월~금", 400, 60), w("09:00", 600, 61), w("18:00", 760, 60),
            // 시급 칸이 비어 있어도 다음 칸이 밀리지 않아요.
            w("박준", 10, 110), w("토", 400, 110), w("10:00", 600, 111), w("16:00", 760, 110),
        )
        val rows = WordTable.rows(words)
        assertEquals(listOf("박준", "", "토", "10:00", "16:00"), rows[2])
        val sheet = StaffSheets.read(rows, on)!!
        assertEquals(10_500L, sheet.rows[0].base)
        assertEquals(5, sheet.rows[0].weekly.size)
        assertEquals(setOf(DayOfWeek.SATURDAY), sheet.rows[1].weekly.keys)
    }
}
