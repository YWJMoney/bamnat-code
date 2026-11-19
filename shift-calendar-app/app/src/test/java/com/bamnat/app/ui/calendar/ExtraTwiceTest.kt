package com.bamnat.app.ui.calendar

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVH-58] 검토 PC-109 (통합 2차 PC 20:51 KST판): 주간 07~19에 특근 09~18을 넣으면 '근무 시간 안이라 시간은 안 더해요'와 빨간
 * '겹친 시간도 따로따로 세니 시각을 확인해 주세요'가 함께 나왔어요. 급여와 증빙은 0시간으로 세는데 빨간 글이 그 반대를 말했어요. 이제
 * 겹침을 말하는 것은 두 번 센 때뿐이고(추가 근무끼리), 근무 안의 추가 근무는 그 줄 한 문장으로만.
 */
class ExtraTwiceTest {
    private val date = LocalDate.of(2026, 11, 18)
    private fun t(h: Int) = LocalTime.of(h, 0)
    private val day = ShiftType(1, "주간", "주", 0xFF1E88E5, t(7), t(19))
    private val night = ShiftType(2, "야간", "야", 0xFF3949AB, t(19), t(7))
    private val shop = ShiftType(3, "가게", "가", 0xFF2E7D32, t(11), t(21), gapStart = t(15), gapMinutes = 120)

    private fun on(type: ShiftType, vararg e: ExtraEntry): ResolvedDay =
        ScheduleResolver(listOf(day, night, shop), listOf(WorkPattern.Rotation(date, listOf(type.id))), listOf(DayEntry(date, extras = e.toList()))).resolve(date)

    @Test fun workInsideTheShiftIsNotSaidToCountTwice() {
        // PC-109 as found: 특근 09~18 on a 주간 07~19.
        val d = on(day, ExtraEntry(ExtraKind.SPECIAL_ID, t(9), t(18)))
        assertEquals(0, d.extras.single().minutes)
        assertNull(ExtraText.twice(d))
        assertEquals("특근 · 09:00 – 18:00 · 근무 시간 안이라 시간은 안 더해요", extraLine(d.extras.single()))
        // 잔업 and 대근 over the shift in the same way: the part inside is taken out, nothing counts twice.
        assertNull(ExtraText.twice(on(day, ExtraEntry(ExtraKind.OVERTIME_ID, t(17), t(21)))))
        assertNull(ExtraText.twice(on(day, ExtraEntry(ExtraKind.COVER_ID, coveredShiftId = night.id))))
        assertNull(ExtraText.twice(on(day, ExtraEntry(ExtraKind.COVER_ID, t(5), t(21)))))
        // Two pieces both inside the shift: neither counts, so neither twice.
        assertNull(ExtraText.twice(on(day, ExtraEntry(ExtraKind.SPECIAL_ID, t(9), t(12)), ExtraEntry(ExtraKind.OVERTIME_ID, t(10), t(11)))))
    }

    @Test fun twoPiecesOverTheSameTimeAreSaidWithTheHoursCountedTwice() {
        // 잔업 19~21 and 특근 20~22 after a 주간: both counted, as pay counts them.
        val d = on(day, ExtraEntry(ExtraKind.OVERTIME_ID, t(19), t(21)), ExtraEntry(ExtraKind.SPECIAL_ID, t(20), t(22)))
        val twice = ExtraText.twice(d)!!
        assertEquals(60, twice.minutes)
        assertEquals("잔업과 특근이 1시간 겹쳐 두 번 셌어요. 한 번만 일했다면 시각을 고쳐 주세요.", ExtraText.twiceLine(twice, WorkStyle.SHIFT))
        // 일근 says 야근 for the built-in 잔업 ([J133-27]).
        assertEquals("야근과 특근이 1시간 겹쳐 두 번 셌어요. 한 번만 일했다면 시각을 고쳐 주세요.", ExtraText.twiceLine(twice, WorkStyle.DAY))
        // Only the part each counts: 잔업 17~21 counts 19~21 after the shift, so with 특근 18~20 that is 19~20.
        val cut = on(day, ExtraEntry(ExtraKind.OVERTIME_ID, t(17), t(21)), ExtraEntry(ExtraKind.SPECIAL_ID, t(18), t(20)))
        assertEquals(60, ExtraText.twice(cut)!!.minutes)
    }

    @Test fun onAShopDayTheGapIsNotTheShift() {
        // 대타 15~17 in the 브레이크 타임 counts (RVH-58) and, beside 특근 16~18 (17~18 in the shift), 16~17 twice.
        val d = on(shop, ExtraEntry(ExtraKind.COVER_ID, t(15), t(17)), ExtraEntry(ExtraKind.SPECIAL_ID, t(16), t(18)))
        assertEquals(120, d.extras[0].minutes)
        assertEquals(60, d.extras[1].minutes)
        assertEquals(60, ExtraText.twice(d)!!.minutes)
        assertNull(ExtraText.twice(on(shop, ExtraEntry(ExtraKind.COVER_ID, t(15), t(17)))))
    }
}
