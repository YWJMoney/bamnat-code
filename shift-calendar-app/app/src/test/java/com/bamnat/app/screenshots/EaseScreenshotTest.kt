package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * [EASE-10][EASE-11][EASE-12][EASE-13][EASE-26] 1.38 갈래5's screenshots, numbers 2600~2619: the day sheet's 근무 칩
 * (one tap changes the day), the rest day's 대근 question, "원래 → 지금 · 되돌리기", the 잔업 ± chips, and the keypad's
 * ✕·». Each shot also checks what the tap did to the data, not only that the screen shows it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EaseScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private fun snap() = runBlocking { c.scheduleRepository.snapshot.first() }
    private fun typeId(name: String) = snap().shiftTypes.first { it.name == name }.id
    private fun entry(date: LocalDate) = snap().entries.firstOrNull { it.date == date }
    private fun overrideOn(date: LocalDate) = entry(date)?.override?.takeIf { it != ShiftOverride.None }

    /** A day off with nothing on it, before today (the seed's 특근 day off is the next one). */
    private val plainRestDay get() = today.minusDays(6)

    // ---- EASE-10: 근무 칩, one tap ----
    @Test fun dayChipsChangeTheDay() = shoot("2600_ease_day_chips_one_tap", FOLDED, style(WorkStyle.SHIFT) {
        openDay(dayCell)
        assertTrue(appears("주간으로 바꾸기"))
        tapText("주간으로 바꾸기")
        settle()
        assertEquals(ShiftOverride.Shift(typeId("주간")), overrideOn(dayCell))
        // [EASE-12] The change says what it was, with the way back.
        assertTrue(appears("원래 야간, 지금 주간"))
    })

    @Test fun changedLineGoesBack() = shoot("2601_ease_changed_line_back", FOLDED, style(WorkStyle.SHIFT) {
        openDay(dayCell)
        tapText("주간으로 바꾸기")
        settle()
        tapText("되돌리기", exact = true)
        settle()
        assertNull(overrideOn(dayCell))
        assertTrue(gone("원래 야간, 지금 주간"))
    })

    // ---- EASE-11: a rest day's shift asks once about 대근 ----
    @Test fun restDayAsksCover() = shoot("2602_ease_rest_day_cover", FOLDED, style(WorkStyle.SHIFT) {
        openDay(plainRestDay)
        tapText("주간으로 바꾸기")
        settle()
        assertEquals(ShiftOverride.Shift(typeId("주간")), overrideOn(plainRestDay))
        assertTrue(appears("대근으로 칠까요"))
        assertTrue(appears("대근으로", exact = true))
    })

    /** PC 3db30e1e: after the bar is gone, the day's own line still makes it a 대근 (근무표는 쉬는 날 그대로, 대근은 추가 근무). */
    @Test fun changedLineMakesItACover() = shoot("2606_ease_changed_line_cover", FOLDED, style(WorkStyle.SHIFT) {
        openDay(plainRestDay)
        tapText("주간으로 바꾸기")
        settle()
        tapText("근무표는 쉬는 날 그대로")
        settle()
        assertNull(overrideOn(plainRestDay))
        assertTrue(entry(plainRestDay)!!.extras.any { it.kindId != ExtraKind.OVERTIME_ID })
        assertTrue(gone("근무표는 쉬는 날 그대로"))
    })

    /** PC 3db30e1e: 한 달 근무 넣기's keys are the keypad sheet's: ✕ takes the day away, » leaves it as it is. */
    @Test fun rosterKeysEraseAndSkip() = shoot("2607_ease_roster_keys", FOLDED, style(WorkStyle.SHIFT) {
        val vm = com.bamnat.app.ui.imports.DutyRosterViewModel(c, com.bamnat.app.data.PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        val day = com.bamnat.core.imports.DutyCell.Shift(typeId("주간"))
        vm.tap(1); vm.put(day); vm.put(day)
        vm.tap(1); vm.skip()
        assertEquals(2, vm.ui.value.cursor)
        assertEquals(day, vm.ui.value.cells[1])
        vm.erase()
        assertEquals(3, vm.ui.value.cursor)
        assertFalse(2 in vm.ui.value.cells)
        settle()
        assertTrue(appears("» 건너뛰기"))
        assertTrue(appears("✕ 지우기"))
    })

    // ---- EASE-26: 잔업 ± chips in the opened 추가 근무 piece (the day under the calendar has them in its head) ----
    @Test fun extraNudgeAddsMinutes() = shoot("2603_ease_extra_nudge", FOLDED, style(WorkStyle.SHIFT) {
        openDay(dayCell)
        // The piece opens with its ± under it.
        tapText("근무 뒤 2시간 · 08")
        settle()
        assertTrue(appears("+30분", exact = true))
        tapText("+30분", exact = true, scrolls = 0)
        settle()
        val e = entry(dayCell)!!.extras.last { it.kindId == ExtraKind.OVERTIME_ID }
        assertEquals(150, e.minutes)
    })

    @Test fun extraNudgeTakesOff() = shoot("2604_ease_extra_nudge_less", FOLDED, style(WorkStyle.SHIFT) {
        openDay(dayCell)
        // The piece opens with its ± under it.
        tapText("근무 뒤 2시간 · 08")
        settle()
        assertTrue(appears("−30분", exact = true))
        tapText("−30분", exact = true, scrolls = 0)
        settle()
        val e = entry(dayCell)!!.extras.last { it.kindId == ExtraKind.OVERTIME_ID }
        assertEquals(90, e.minutes)
    })

    // ---- EASE-13: the keypad's ✕ and » ----
    @Test fun keypadEraseAndSkip() {
        var saved: Map<LocalDate, ShiftOverride>? = null
        shoot("2605_ease_keypad_erase_skip", FOLDED, style(WorkStyle.SHIFT) {
            val s = snap()
            val t = s.shiftTypes.associateBy { it.name }
            val month = java.time.YearMonth.of(2026, 11)
            val codes = listOf("주간", "주간", "오후", "주간", "야간", "야간", "주간", "휴무", "휴무", "오후", "오후", "야간")
            val initial = codes.mapIndexed { i, n -> month.atDay(i + 1) to ShiftOverride.Shift(t.getValue(n).id) as ShiftOverride }.toMap()
            content { com.bamnat.app.ui.calendar.DutyTypingSheet(s, month, onDismiss = {}, onSave = { saved = it }, initial = initial) }
            settle()
            // » leaves the 13th, the key fills the 14th.
            tapText("건너뛰기", prefix = false)
            settle()
            tapText("주간 넣기")
            settle()
            // ✕ on the 1st takes it away.
            tapText("11월 1일 주간")
            settle()
            tapText("✕ 지우기")
            settle()
            tapText("12일 저장")
            settle()
            val m = saved!!
            assertFalse(month.atDay(1) in m)
            assertFalse(month.atDay(13) in m)
            assertEquals(ShiftOverride.Shift(t.getValue("주간").id), m[month.atDay(14)])
            assertEquals(12, m.size)
        })
    }
}
