package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ThemeMode
import com.bamnat.core.recipe.AppFeel
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVS-15] 검토 SN-04·SD-05·SI-03 (사용자 2026-11-12 'A + B 혼합'): someone else's 앱 동작 shows each choice 지금 → 넣으면 with
 * whose it is; my own settings it changed can be put back from 설정 (one I changed since stays mine), and taking the theme off puts back
 * what its 앱 동작 changed.
 */
@RunWith(AndroidJUnit4::class)
class AppFeelMixTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    private val feel = mapOf<String, Any?>("accent" to 0xFF00897BL, "theme" to "dark", "textStep" to 1L, "payPeriod" to true, "hold" to "memo")

    @Test fun theChoicesSayWhatChangesAndWhoseItIs() = runBlocking {
        c.settingsRepository.update { it.copy(textStep = 3, themeMode = ThemeMode.SYSTEM, accentColor = 0L) }
        val feels = c.recipes.pieces.feels(feel)
        assertEquals("꾸밈, 밝기, 내 설정 차례", listOf("accent", "theme", "textStep", "hold", "payPeriod"), feels.map { it.key })
        assertEquals(listOf(AppFeel.Whose.LOOK, AppFeel.Whose.BRIGHT, AppFeel.Whose.MINE, AppFeel.Whose.MINE, AppFeel.Whose.MINE), feels.map { it.whose })
        val text = feels.first { it.key == "textStep" }
        assertEquals("글자가 지금보다 작아져요", text.note)
        assertEquals("이 작품은 어두운 화면에서 가장 예뻐요", feels.first { it.key == "theme" }.note)
        assertEquals("휴대폰 따라", feels.first { it.key == "theme" }.now)
        assertEquals("어둡게", feels.first { it.key == "theme" }.then)
        assertEquals("처음에는 꾸밈만 켜져요", listOf("accent"), feels.filter { AppFeel.onAtFirst(it.key) }.map { it.key })
        // The same as now is not a choice.
        c.settingsRepository.update { it.copy(themeMode = ThemeMode.DARK) }
        assertTrue(c.recipes.pieces.feels(feel).none { it.key == "theme" })
    }

    @Test fun myOwnSettingsAWorkChangedGoBackFromSettings() = runBlocking {
        c.settingsRepository.update { it.copy(textStep = 3, themeMode = ThemeMode.SYSTEM, longPress = "menu") }
        val r = Recipe("다른 분 앱 동작", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.APP_FEEL, feel)))
        // As if 글자 크기·밝기·누르기 were turned on in the 넣기 창.
        val on = r.copy(pieces = listOf(Piece(PieceKind.APP_FEEL, AppFeel.only(feel, setOf("accent", "textStep", "theme", "hold")))))
        assertNotNull(c.recipes.apply(on, "FEEL2345", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.APP_FEEL, RecipePieces.Mode.REPLACE)), emptyMap()))
        assertEquals(1, c.settingsRepository.current.textStep)
        // I change 밝기 myself afterwards: it is mine now and is not offered.
        c.settingsRepository.update { it.copy(themeMode = ThemeMode.LIGHT) }
        val back = c.recipes.feelChanged()
        assertTrue("글자 크기와 누르기는 되돌릴 수 있어요: ${back.map { it.key }}", back.map { it.key }.containsAll(listOf("textStep", "longPress")))
        assertTrue("내가 바꾼 밝기는 빼요", back.none { it.key == "themeMode" })
        assertTrue("꾸밈(강조색)은 설정 되돌리기에 없어요", back.none { it.key == "accentColor" })
        assertEquals("3", back.first { it.key == "textStep" }.before)
        assertEquals(2, c.recipes.putFeelBack(back))
        val s = c.settingsRepository.current
        assertEquals(3, s.textStep)
        assertEquals("menu", s.longPress)
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertTrue("다 되돌린 뒤에는 없어요", c.recipes.feelChanged().isEmpty())
    }

    @Test fun takingTheThemeOffPutsBackWhatItsFeelChanged() = runBlocking {
        c.settingsRepository.update { it.copy(accentColor = 0L) }
        val r = Recipe("테마와 강조색", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFFC62828L)),
            Piece(PieceKind.APP_FEEL, mapOf("accent" to 0xFF00897BL)),
        ))
        c.wardrobe.beforeWear()
        val before = c.recipes.pieces.feelSnapshot()
        assertNotNull(c.recipes.apply(r, "WEAR2345", 1, PersonalCalendar.MAIN, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap()))
        c.wardrobe.afterWear(r.copy(pieces = r.pieces.filter { it.kind == PieceKind.CAL_THEME }), "WEAR2345", own = false, ver = 1)
        val after = c.recipes.pieces.feelSnapshot()
        c.wardrobe.feelChanged(before.filter { (k, v) -> after[k] != v }.mapValues { (k, v) -> v to after.getValue(k) })
        assertEquals(0xFF00897BL, c.settingsRepository.current.accentColor)
        c.wardrobe.takeOff()
        assertEquals("테마를 벗으면 강조색도 원래대로", 0L, c.settingsRepository.current.accentColor)
    }
}
