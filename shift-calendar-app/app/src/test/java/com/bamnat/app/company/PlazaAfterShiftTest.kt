package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.WorkStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVN-24] 검토 I10ac-03 근무에 맞춰 말을 거는 광장: '오늘 야간 어땠어요?' 20 minutes after a shift ends, in my kind of work's words,
 * once a day, off by default, and after three left alone it waits two weeks.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlazaAfterShiftTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun off() = PlazaAfterShift.setOn(context, false)

    @Test fun theQuestionIsInMyKindOfWorksWords() {
        assertEquals("오늘 야간 어땠어요?", PlazaAfterShift.question(WorkStyle.SHIFT, "야간", night = true))
        assertEquals("오늘 주간 어땠어요?", PlazaAfterShift.question(WorkStyle.SHIFT, "주간", night = false))
        assertEquals("오늘 하루 어땠어요?", PlazaAfterShift.question(WorkStyle.DAY, "근무", night = false))
        assertEquals("오늘 현장 어땠어요?", PlazaAfterShift.question(WorkStyle.FIELD, null, night = false))
        assertEquals("오늘 알바 어땠어요?", PlazaAfterShift.question(WorkStyle.HOURLY, "오픈", night = false))
    }

    @Test fun onceADayAndAfterThreeLeftAloneItWaitsTwoWeeks() {
        var t = PlazaAfterShift.turn(today = 100, last = Long.MIN_VALUE, left = 0, pausedUntil = Long.MIN_VALUE)
        assertTrue(t.ask)
        assertFalse("not twice a day", PlazaAfterShift.turn(100, 100, t.left, t.pausedUntil).ask)
        t = PlazaAfterShift.turn(101, 100, t.left, t.pausedUntil)
        t = PlazaAfterShift.turn(102, 101, t.left, t.pausedUntil)
        assertEquals(3, t.left)
        t = PlazaAfterShift.turn(103, 102, t.left, t.pausedUntil)
        assertFalse(t.ask)
        assertEquals(103 + PlazaAfterShift.PAUSE_DAYS, t.pausedUntil)
        assertFalse(PlazaAfterShift.turn(110, 102, t.left, t.pausedUntil).ask)
        assertTrue(PlazaAfterShift.turn(103 + PlazaAfterShift.PAUSE_DAYS, 102, t.left, t.pausedUntil).ask)
    }

    @Test fun itAsksTwentyMinutesAfterTheNextEndAndANightEndsInTheMorning() {
        val day = ShiftType(1, "주간", "주", 0xFF000000, LocalTime.of(7, 0), LocalTime.of(19, 0))
        val night = ShiftType(2, "야간", "야", 0xFF000000, LocalTime.of(19, 0), LocalTime.of(7, 0))
        val start = LocalDate.of(2026, 11, 16)
        val resolver = ScheduleResolver(listOf(day, night), listOf(WorkPattern.Rotation(start, listOf(1L, 2L, null, null))), emptyList())
        // 11-16 주간 ends 19:00 → 19:20; then 11-17 야간 ends 11-18 07:00 → 07:20; 11-18·19 off.
        assertEquals(LocalDateTime.of(2026, 11, 16, 19, 20), PlazaAfterShift.next(resolver, LocalDateTime.of(2026, 11, 16, 12, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 18, 7, 20), PlazaAfterShift.next(resolver, LocalDateTime.of(2026, 11, 16, 19, 21)))
        assertEquals(LocalDateTime.of(2026, 11, 20, 19, 20), PlazaAfterShift.next(resolver, LocalDateTime.of(2026, 11, 18, 7, 21)))
    }

    @Test fun offUntilTurnedOnAndTurningOnAsksAsNew() {
        assertFalse(PlazaAfterShift.on(context))
        PlazaAfterShift.setOn(context, true)
        assertTrue(PlazaAfterShift.on(context))
    }
}
