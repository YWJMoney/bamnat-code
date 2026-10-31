package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.ShiftIcon
import com.bamnat.core.model.ShiftType
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.Input
import com.bamnat.core.recipe.InputKind
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.Ref
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * [RCP-25][RCP-26] A recipe put on the main calendar piece by piece, then taken back: what it set comes back to the user's own,
 * what the user changed in between stays, and records are never touched.
 */
@RunWith(AndroidJUnit4::class)
class RecipeApplyTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    private fun recipe() = Recipe(
        title = "야간 파랑", styles = setOf(WorkStyle.SHIFT),
        inputs = listOf(Input("i1", "야간 알람", InputKind.NUMBER, default = 90L, min = 0.0, max = 720.0)),
        pieces = listOf(
            Piece(PieceKind.SHIFT_LOOK, mapOf("items" to listOf(mapOf("name" to "밤 근무", "role" to "night", "label" to "N", "color" to 0xFF1A237EL, "emoji" to "🌙")))),
            Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFF1565C0L, "cell" to "fill")),
            Piece(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "night", "before" to listOf(Ref.Input("i1")))), "snooze" to 7L, "mission" to "none")),
            Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품", "환자 수"))))),
        ),
    )

    @Test fun applyThenUndoPutsBackOnlyWhatTheRecipeSet() = runBlocking {
        val repo = c.scheduleRepository
        val id = repo.saveShiftType(ShiftType(0, "밤 근무", "야", 0xFF424242L, LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val missionBefore = c.settingsRepository.current.alarmMission
        val r = recipe()
        val all = r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        val applied = c.recipes.apply(r, "ABCD2345", 1, PersonalCalendar.MAIN, all, emptyMap())
        assertNotNull(applied)

        val night = repo.snapshot.first().shiftTypes.first { it.id == id }
        assertEquals("N", night.label)
        assertEquals("🌙", ShiftIcon.emoji(night.icon))
        assertTrue("알람은 더하기만", night.alarms.any { it.offsetMinutes == 90 } && night.alarmEnabled)
        assertEquals(0xFF1565C0L, CalendarTheme.decode(c.settingsRepository.current.calTheme).sat)
        assertEquals(7, c.settingsRepository.current.snoozeMinutes)
        assertEquals("미션 없음은 지금 미션을 끄지 않아요", missionBefore, c.settingsRepository.current.alarmMission)
        assertTrue(c.memoTemplates.items.value.any { it.title == "인수인계" && it.from == "ABCD2345" })

        // The user changes the colour after; 되돌리기 keeps that change and puts the rest back.
        repo.saveShiftType(night.copy(colorArgb = 0xFF00897BL))
        assertTrue(c.recipes.undo(applied!!))
        val back = repo.snapshot.first().shiftTypes.first { it.id == id }
        assertEquals("야", back.label)
        assertEquals(0xFF00897BL, back.colorArgb)
        assertTrue(back.alarms.none { it.offsetMinutes == 90 })
        assertEquals("", c.settingsRepository.current.calTheme)
        assertEquals(5, c.settingsRepository.current.snoozeMinutes)
        assertTrue(c.memoTemplates.items.value.none { it.from == "ABCD2345" })
        assertTrue("두 번 되돌리지 않아요", !c.recipes.undo(applied))
    }

    @Test fun inputsAnswerAndEmptyLeavesMine() = runBlocking {
        val repo = c.scheduleRepository
        val id = repo.saveShiftType(ShiftType(0, "나이트", "N", 0xFF424242L, LocalTime.of(22, 0), LocalTime.of(7, 0), alarms = emptyList()))
        val r = recipe().copy(pieces = recipe().pieces.filter { it.kind == PieceKind.ALARMS })
        c.recipes.apply(r, "", 0, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.ALARMS, RecipePieces.Mode.REPLACE)), mapOf("i1" to 120L))
        assertTrue(repo.snapshot.first().shiftTypes.first { it.id == id }.alarms.any { it.offsetMinutes == 120 })
    }
}
