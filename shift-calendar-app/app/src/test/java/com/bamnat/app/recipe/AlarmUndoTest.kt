package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * 2.0.0 [RVM-50] 검토 RCP-04 (조건 10, 알람 믿음): 알람 묶음 되돌리기가 넣기 전 알람이 꺼져 있던 근무는 무조건 껐고(그 사이 내가 더한
 * 알람까지), 두 작품이 같은 '90분 전'을 담았을 때 한 작품을 되돌리면 다른 작품이 원하는 알람도 뺐어요. 꺼지면 그렇다고 말해요.
 */
@RunWith(AndroidJUnit4::class)
class AlarmUndoTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val main = PersonalCalendar.MAIN

    private fun night(title: String, extra: List<Piece> = emptyList()) = Recipe(title, styles = setOf(WorkStyle.SHIFT), pieces = listOf(
        Piece(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "night", "before" to listOf(90L)))))) + extra)
    private suspend fun putOn(r: Recipe, code: String) = c.recipes.apply(r, code, 1, main,
        r.pieces.map { RecipeRepository.Pick(it.kind, if (it.kind == PieceKind.MEMO_TEMPLATES) RecipePieces.Mode.BOTH else RecipePieces.Mode.REPLACE) }, emptyMap())!!
    private suspend fun shift(id: Long) = c.scheduleRepository.snapshot.first().shiftTypes.first { it.id == id }
    private suspend fun night(): Long = c.scheduleRepository.saveShiftType(ShiftType(0, "밤 근무", "야", 0xFF424242L, LocalTime.of(22, 0), LocalTime.of(7, 0)))

    @Test fun anAlarmIAddedKeepsTheShiftsAlarmsOn() = runBlocking {
        val id = night()
        val had = shift(id).alarms.map { it.offsetMinutes }
        assertFalse(90 in had)
        val a = putOn(night("야간 알람"), "AAAA2345")
        assertTrue(shift(id).alarmEnabled)
        c.scheduleRepository.saveShiftType(shift(id).let { it.copy(alarms = it.alarms + AlarmSpec(30)) })
        assertTrue(c.recipes.undo(a))
        assertEquals("작품의 90분 전만 빠지고 내가 더한 30분 전은 남아요", (had + 30).toSet(), shift(id).alarms.map { it.offsetMinutes }.toSet())
        assertTrue("알람은 켜진 채", shift(id).alarmEnabled)
        assertFalse(c.recipes.undoSay(true).contains("꺼졌어요"))
    }

    @Test fun anAlarmAnotherWorkWantsStays() = runBlocking {
        val id = night()
        val had = shift(id).alarms.map { it.offsetMinutes }
        assertFalse(90 in had)
        val a = putOn(night("야간 알람"), "AAAA2345")
        putOn(night("다른 야간 묶음", listOf(Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품"))))))), "BBBB2345")
        assertTrue(c.recipes.undo(a))
        assertEquals("다른 작품도 원하는 90분 전은 남아요", (had + 90).toSet(), shift(id).alarms.map { it.offsetMinutes }.toSet())
        assertTrue(shift(id).alarmEnabled)
    }

    @Test fun anAlarmSwitchedOffIsSaid() = runBlocking {
        val id = night()
        val had = shift(id).alarms.map { it.offsetMinutes }
        assertFalse(90 in had)
        val a = putOn(night("야간 알람"), "AAAA2345")
        assertTrue(c.recipes.undo(a))
        assertEquals("넣기 전 그대로", had.toSet(), shift(id).alarms.map { it.offsetMinutes }.toSet())
        assertFalse(shift(id).alarmEnabled)
        val said = c.recipes.undoSay(true)
        assertTrue(said, said.startsWith("되돌렸어요.") && said.contains("‘밤 근무’") && said.endsWith(" 알람이 꺼졌어요"))
    }
}
