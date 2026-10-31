package com.bamnat.core.recipe

import com.bamnat.core.Fixtures
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** [STU-37][STU-38] 1.36 근무에 따라 바뀌는 모양, 위젯 색·모서리·글자 크기. */
class ShiftLooksTest {
    private val nightLook = ShiftLooks.Look(ScreenLooks().with(ScreenLooks.ALL, ScreenLook(bg = 0xFF101010, ink = 0xFFEEEEEE)).encode(),
        CalendarTheme(sat = 0xFF90CAF9).encode())

    @Test fun theModeLaysItsColoursOverTheUsual() {
        val usual = ScreenLooks().with(ScreenLooks.ALL, ScreenLook(bg = 0xFFFFFFFF, photo = "0123456789abcdef", accent = 0xFF2F5BEA))
            .with("money", ScreenLook(card = 0xFFFFF8E1)).encode()
        val cal = CalendarTheme(today = 0xFFE91E63, sat = 0xFF1565C0, cell = "fill").encode()
        val looks = ShiftLooks(night = nightLook)
        assertEquals("평소엔 그대로", usual to cal, looks.apply(ShiftLooks.Mode.NORMAL, usual, cal))
        assertEquals("쉬는 날 모양이 없으면 그대로", usual to cal, looks.apply(ShiftLooks.Mode.OFF, usual, cal))
        val (s, c) = looks.apply(ShiftLooks.Mode.NIGHT, usual, cal)
        val all = ScreenLooks.decode(s).looks[ScreenLooks.ALL]!!
        assertEquals(0xFF101010L, all.bg); assertEquals(0xFFEEEEEEL, all.ink)
        assertEquals("그림은 남아요", "0123456789abcdef", all.photo)
        assertEquals("고르지 않은 색은 평소 것", 0xFF2F5BEAL, all.accent)
        assertEquals(0xFFFFF8E1L, ScreenLooks.decode(s).looks["money"]?.card)
        val ct = CalendarTheme.decode(c)
        assertEquals(0xFF90CAF9L, ct.sat); assertEquals(0xFFE91E63L, ct.today); assertEquals("fill", ct.cell)
    }

    @Test fun keptAsLinesAndBadLinesDropped() {
        val looks = ShiftLooks(night = nightLook, off = ShiftLooks.Look(cal = CalendarTheme(sun = 0xFFC62828).encode()))
        assertEquals(looks, ShiftLooks.decode(looks.encode()))
        assertEquals(2, looks.encode().lines().size)
        assertTrue(ShiftLooks.decode("").isDefault)
        assertTrue(ShiftLooks.decode("night\tonly two").isDefault)
        assertEquals(nightLook, ShiftLooks.decode("junk\nnight\t${nightLook.screens}\t${nightLook.cal}\nday\tx\ty").night)
        assertNull("빈 모양은 지워져요", looks.with(ShiftLooks.Mode.OFF, ShiftLooks.Look()).off)
        assertEquals("", ShiftLooks().encode())
    }

    @Test fun theDayDecidesTheMode() {
        val oct1 = LocalDate.of(2026, 10, 1)
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(oct1, listOf(Fixtures.night.id, Fixtures.day.id, Fixtures.off.id)), emptyList())
        assertEquals(ShiftLooks.Mode.NIGHT, com.bamnat.core.schedule.ShiftLookMode.at(LocalDateTime.of(2026, 10, 2, 3, 0), r::resolve))
        assertEquals(ShiftLooks.Mode.NORMAL, com.bamnat.core.schedule.ShiftLookMode.at(LocalDateTime.of(2026, 10, 2, 12, 0), r::resolve))
        assertEquals(ShiftLooks.Mode.OFF, com.bamnat.core.schedule.ShiftLookMode.at(LocalDateTime.of(2026, 10, 3, 12, 0), r::resolve))
    }

    @Test fun widgetLookRoundTrips() {
        val w = WidgetLook(80, "dark", bg = 0xFF223344, ink = 0xFFFFFFFF, corner = 12, text = "l")
        assertEquals(w, WidgetLook.decode(w.encode()))
        assertEquals(1.15f, w.textScale)
        assertEquals("예전 줄도 읽혀요", WidgetLook(70, "light"), WidgetLook.decode("alpha=70;theme=light"))
        val bad = WidgetLook.decode("alpha=80;theme=dark;bg=zz;corner=99;text=xl")
        assertNull(bad.bg); assertNull(bad.corner); assertNull(bad.text)
        assertEquals("", WidgetLook().encode())
    }
}
