package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * [RVF-06] 개발 전 검토 RCP-01: 뒤에 넣은 작품이 조각 하나만 겹쳐도 앞 작품의 되돌리기·받는 칸 답·새 판 알림이 통째로 사라졌어요.
 * 더하기 조각(메모 틀·알람·규칙·가계부·투자)은 앞 작품 자리를 빼앗지 않고, 바꾸는 조각은 그 조각만 넘겨받아요.
 */
@RunWith(AndroidJUnit4::class)
class LaterRecipeKeepsEarlierTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val main = PersonalCalendar.MAIN

    private val first = Recipe(
        title = "야간 묶음", styles = setOf(WorkStyle.SHIFT),
        inputs = listOf(Input("i1", "야간 알람", InputKind.NUMBER, default = 90L, min = 0.0, max = 720.0)),
        pieces = listOf(
            Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFF1565C0L)),
            Piece(PieceKind.ALARMS, mapOf("byRole" to listOf(mapOf("role" to "night", "before" to listOf(Ref.Input("i1")))))),
            Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품"))))),
        ),
    )
    /** A work whose 부품 reads its 받는 칸 (칸("목표 급여")), with a 메모 틀 too. */
    private val goal = Recipe(title = "목표 급여 진행", styles = setOf(WorkStyle.SHIFT),
        inputs = listOf(Input("i1", "목표 급여", InputKind.MONEY, default = 2_500_000L)),
        pieces = listOf(Piece(PieceKind.PARTS, mapOf("parts" to listOf(mapOf("id" to "pay.total", "fill" to 4280825235L),
            mapOf("id" to "pay.total", "when" to "이달급여 >= 칸(\"목표 급여\", 2500000)", "paint" to "lg.120.FFFFD54F,FFFFA000")))),
            Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품")))))))
    private val memoOnly = Recipe(title = "메모 틀", styles = setOf(WorkStyle.SHIFT),
        pieces = listOf(Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "회의록", "check" to listOf("안건")))))))
    private val colourOnly = Recipe(title = "빨간 토요일", styles = setOf(WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFFD32F2FL))))

    private fun all(r: Recipe) = r.pieces.map { RecipeRepository.Pick(it.kind, if (it.kind == PieceKind.MEMO_TEMPLATES) RecipePieces.Mode.BOTH else RecipePieces.Mode.REPLACE) }
    private suspend fun row(id: Long) = c.database.recipeDao().appliedById(id)!!

    @Test fun anAddedPieceLeavesTheEarlierWorkOn() = runBlocking {
        val a = c.recipes.apply(goal, "AAAA2345", 1, main, all(goal), mapOf("i1" to 1_500_000L))!!
        assertNotNull(c.recipes.apply(memoOnly, "BBBB2345", 1, main, all(memoOnly), emptyMap()))
        assertTrue("앞 작품은 7일 안이면 되돌릴 수 있어요", c.recipes.canUndo(row(a)))
        assertEquals("앞 작품의 받는 칸 답이 남아요", 1_500_000L, (c.recipes.formulaInputs(main).first()["목표 급여"] as Number).toLong())
        assertTrue("새 판 알림도", c.recipes.inUse().any { it.code == "AAAA2345" })
    }

    @Test fun aReplacedPieceGoesToTheLaterWorkAndTheRestUndoes() = runBlocking {
        val id = c.scheduleRepository.saveShiftType(ShiftType(0, "밤 근무", "야", 0xFF424242L, LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val a = c.recipes.apply(first, "AAAA2345", 1, main, all(first), emptyMap())!!
        c.recipes.apply(colourOnly, "CCCC2345", 1, main, all(colourOnly), emptyMap())
        val after = row(a)
        assertTrue(c.recipes.canUndo(after))
        assertFalse("토요일 색은 뒤 작품 것", "calTheme" in after.pieces.split(','))
        assertTrue(c.recipes.undo(a))
        assertEquals("뒤 작품의 색은 그대로", 0xFFD32F2FL, CalendarTheme.decode(c.settingsRepository.current.calTheme).sat)
        assertTrue("앞 작품의 알람은 빠져요", c.scheduleRepository.snapshot.first().shiftTypes.first { it.id == id }.alarms.none { it.offsetMinutes == 90 })
        assertTrue("메모 틀도", c.memoTemplates.items.value.none { it.from == "AAAA2345" })
    }

    @Test fun theSameWorkAgainStillTakesItsOwnPlace() = runBlocking {
        val a = c.recipes.apply(first, "AAAA2345", 1, main, all(first), emptyMap())!!
        c.recipes.apply(first, "AAAA2345", 2, main, all(first), emptyMap())
        assertFalse("같은 작품을 다시 넣으면 앞 기록은 '바뀜'", c.recipes.canUndo(row(a)))
    }
}
