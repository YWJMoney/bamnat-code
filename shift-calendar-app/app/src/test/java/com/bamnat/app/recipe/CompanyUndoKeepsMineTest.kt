package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVM-44] 검토 RCP-03·T09b-05 (조건 10 '그것이 바꾼 것만 되돌려요'): 회사 작품(근무 주기·급여 방식)을 되돌리면 그 사이 내가 바꾼
 * 급여 설정·근무 순서·근무 시간까지 덮어쓰고, 작품이 더한 근무(교육)로 직접 넣은 날을 지웠어요. 이제 작품이 바꾼 것만 돌아가요.
 */
@RunWith(AndroidJUnit4::class)
class CompanyUndoKeepsMineTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val s get() = c.scheduleRepository
    private val today: LocalDate get() = Today.date()

    private class Mine(val am: Long, val pm: Long, val off: Long)

    /** A 교대 worker's own phone: 오전·오후·쉼 in turn, a 월급 setup at a 5인 미만 workplace. */
    private suspend fun mine(): Mine {
        val am = s.saveShiftType(ShiftType(0, "오전", "오", 0xFF1E88E5L, LocalTime.of(7, 0), LocalTime.of(15, 0)))
        val pm = s.saveShiftType(ShiftType(0, "오후", "후", 0xFFFB8C00L, LocalTime.of(15, 0), LocalTime.of(23, 0)))
        val off = s.saveShiftType(ShiftType(0, "쉼", "휴", 0xFF9E9E9EL, null, null, isOff = true))
        s.setFirstPattern(WorkPattern.Rotation(today, listOf(am, pm, off), anchorDate = today), today)
        c.payStore.save(PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_000_000L))),
            fiveOrMoreEmployees = false))
        return Mine(am, pm, off)
    }

    /** A company's way: 오전 from 06:00, a new 교육 shift, the order 오전·오전·오후·교육·쉼, and a 5인 이상 workplace. */
    private suspend fun company(): Recipe {
        val o = JSONObject(c.companyShare.export("우리 회사", true))
        val types = o.getJSONArray("shiftTypes")
        for (i in 0 until types.length()) types.getJSONObject(i).takeIf { it.getString("name") == "오전" }?.put("start", "06:00")
        types.put(JSONObject().put("name", "교육").put("label", "교").put("color", 0xFF43A047L).put("start", "09:00").put("end", "18:00")
            .put("break", 60).put("off", false))
        o.put("rotation", JSONObject().put("anchor", today.toString()).put("steps", JSONArray(listOf("오전", "오전", "오후", "교육", "쉼"))))
        o.getJSONObject("pay").put("fiveOrMore", true)
        return Recipe(title = "우리 회사 방식", styles = setOf(WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.COMPANY, mapOf("setup" to o.toString()))))
    }

    private suspend fun putOn(r: Recipe): Long = c.recipes.apply(r, "COMP2345", 1, PersonalCalendar.MAIN,
        listOf(RecipeRepository.Pick(PieceKind.COMPANY, RecipePieces.Mode.REPLACE)), emptyMap())!!

    private suspend fun type(name: String) = s.snapshot.first().shiftTypes.firstOrNull { it.name == name }
    private suspend fun stepsOn(day: LocalDate) = (PatternTimeline.current(s.snapshot.first().patterns, day) as WorkPattern.Rotation).steps

    @Test fun untouchedItAllGoesBack() = runBlocking {
        val m = mine()
        val id = putOn(company())
        assertEquals(LocalTime.of(6, 0), type("오전")!!.start)
        assertNotNull(type("교육"))
        assertTrue(c.payStore.profile.value!!.fiveOrMoreEmployees)
        assertTrue(c.recipes.undo(id))
        assertEquals("오전 시각은 원래대로", LocalTime.of(7, 0), type("오전")!!.start)
        assertEquals("작품이 더한 근무는 빠져요", null, type("교육"))
        assertEquals("근무 순서는 원래대로", listOf(m.am, m.pm, m.off), stepsOn(today.plusDays(10)))
        assertFalse("5인 미만으로", c.payStore.profile.value!!.fiveOrMoreEmployees)
    }

    @Test fun aDayIPutTheNewShiftOnStaysWithIt() = runBlocking {
        val m = mine()
        val id = putOn(company())
        val edu = type("교육")!!.id
        val day = today.plusDays(2)
        s.setOverride(day, ShiftOverride.Shift(edu))
        assertTrue(c.recipes.undo(id))
        assertNotNull("직접 넣은 날이 쓰는 근무는 남아요", type("교육"))
        assertEquals("그 날도 그대로", ShiftOverride.Shift(edu), s.entryNow(day)?.override)
        assertEquals("나머지는 원래대로", listOf(m.am, m.pm, m.off), stepsOn(today.plusDays(10)))
        assertEquals(LocalTime.of(7, 0), type("오전")!!.start)
    }

    @Test fun whatIChangedSinceStays() = runBlocking {
        val m = mine()
        val id = putOn(company())
        // After putting it on: my own 오전 time, my own 기본급, and a new order from next week.
        s.saveShiftType(type("오전")!!.copy(start = LocalTime.of(5, 30)))
        c.payStore.save(c.payStore.profile.value!!.copy(base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_500_000L)))))
        s.changePatternFrom(WorkPattern.Rotation(today.plusDays(7), listOf(m.am, m.off), anchorDate = today.plusDays(7)), today.plusDays(7))
        assertTrue(c.recipes.undo(id))
        assertEquals("내가 바꾼 시각은 남아요", LocalTime.of(5, 30), type("오전")!!.start)
        val p = c.payStore.profile.value!!
        assertEquals("내가 바꾼 기본급은 남아요", 2_500_000L, p.base.entries.last().value)
        assertFalse("작품이 바꾼 5인 이상만 돌아가요", p.fiveOrMoreEmployees)
        assertEquals("내가 바꾼 순서는 남아요", listOf(m.am, m.off), stepsOn(today.plusDays(10)))
        assertNotNull("남은 순서(이번 주)가 쓰는 교육도 남아요", type("교육"))
    }
}
