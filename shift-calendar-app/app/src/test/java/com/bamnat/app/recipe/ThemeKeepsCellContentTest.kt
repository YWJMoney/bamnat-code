package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [CELL-02] 1.39 (사용자 2026-11-07 "1.37에서 테마를 입으면 기존 달력에 적어 놓은 것들이 안 보여요"): someone else's theme changes how the
 * cells look, never what they show; a theme of my own puts on everything it has.
 */
@RunWith(AndroidJUnit4::class)
class ThemeKeepsCellContentTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    /** A theme made by someone who kept memos out of the cells (메모 줄 0) and the hours too. */
    private val noMemos = Recipe("메모 없는 테마", styles = WorkStyle.entries.toSet(), pieces = listOf(
        Piece(PieceKind.CAL_VIEW, mapOf("size" to "tall", "badge" to "line", "memo" to 0L, "order" to "hours,holiday", "hours" to false))))

    private fun mine() = c.settingsRepository.update {
        it.copy(memoInCells = true, showHoursInCells = true, calView = CalendarView(memo = 2, order = listOf("memo", "holiday")).encode())
    }

    @Test fun someoneElsesThemeKeepsMyMemosInTheCells() = runBlocking {
        mine()
        assertTrue(c.wardrobe.wear(noMemos, "ABCD2345", own = false))
        val s = c.settingsRepository.current
        val v = CalendarView.decode(s.calView)
        assertEquals("tall", v.size)
        assertEquals("line", v.badge)
        assertEquals(2, v.memo)
        assertEquals(listOf("memo", "holiday"), v.order)
        assertTrue(s.memoInCells)
        assertTrue(s.showHoursInCells)
    }

    @Test fun myOwnThemePutsOnWhatItSays() = runBlocking {
        mine()
        assertTrue(c.wardrobe.wear(noMemos, "", own = true))
        val s = c.settingsRepository.current
        assertEquals(0, CalendarView.decode(s.calView).memo)
        assertFalse(s.memoInCells)
        assertFalse(s.showHoursInCells)
    }
}
