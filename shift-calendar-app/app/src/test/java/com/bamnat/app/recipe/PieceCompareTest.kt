package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.recipe.PieceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * 1.39 [MKP-05] 기능 조각 전후: what a 장터 piece would change on this phone, 지금 ↔ 넣으면, row by row, read before anything is put in
 * (nothing on the phone moves while it is worked out).
 */
@RunWith(AndroidJUnit4::class)
class PieceCompareTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    @Test fun alarmsSayEachShiftNowAndAfterAndTheSettings() = runBlocking {
        c.scheduleRepository.saveShiftType(ShiftType(0, "밤 근무", "야", 0xFF424242L, LocalTime.of(22, 0), LocalTime.of(7, 0), alarms = listOf(AlarmSpec(60))))
        val snooze = c.settingsRepository.current.snoozeMinutes
        val sides = c.recipes.pieces.compare(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "night", "before" to listOf(30L, 60L))),
            "snooze" to 10L, "mission" to "none"), PersonalCalendar.MAIN)
        val night = sides.first { it.what == "밤 근무 알람" }
        assertTrue(night.now, night.now.contains("1시간 전"))
        assertEquals("1시간 전·30분 전", night.then)
        assertTrue(night.changed)
        val again = sides.first { it.what == "다시 울림" }
        assertEquals("${snooze}분", again.now)
        assertEquals("10분", again.then)
        assertTrue("미션 없음은 줄로 나오지 않아요", sides.none { it.what == "끄기 미션" })
        // Worked out only: the phone is as it was.
        assertEquals(snooze, c.settingsRepository.current.snoozeMinutes)
    }

    @Test fun memoTemplatesCountAndNameWhatComes() = runBlocking {
        val before = c.memoTemplates.items.value.size
        val sides = c.recipes.pieces.compare(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품")))),
            PersonalCalendar.MAIN)
        assertEquals("${before}개", sides.first().now)
        assertEquals("${before + 1}개", sides.first().then)
        assertEquals("새로 생겨요", sides.first { it.what == "‘인수인계’" }.then)
    }

    @Test fun calendarColoursCarryTheirSwatches() = runBlocking {
        val sides = c.recipes.pieces.compare(PieceKind.CAL_THEME, mapOf("sat" to 0xFF1565C0L, "cell" to "fill"), PersonalCalendar.MAIN)
        val sat = sides.first { it.what == "토요일 글자색" }
        assertEquals(0xFF1565C0L, sat.thenColor)
        assertTrue(sat.changed)
        val cell = sides.first { it.what == "날짜 칸 칠하기" }
        assertEquals("옅게", cell.now)
        assertEquals("진하게", cell.then)
        // The same as now: kept, but not counted as a change.
        assertFalse(c.recipes.pieces.compare(PieceKind.CAL_THEME, mapOf("cell" to "tint"), PersonalCalendar.MAIN).single().changed)
    }
}
