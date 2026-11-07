package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.ui.recipe.LiveTryOn
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TRY-05] 1.39 실제 화면으로 입어 보기 (사용자 2026-11-07 "입혀보기에서 … 실 모습과 100% 동일하게"): the look tried on the real app is the
 * look 입기 leaves, part for part, and nothing of it is ever saved.
 */
@RunWith(AndroidJUnit4::class)
class LiveTryOnTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    @After fun letGo() = LiveTryOn.drop(c)

    /** What 입기 would leave, worked out without putting it on, against what it does leave. */
    private suspend fun sameAsWorn(r: Recipe, wear: suspend () -> Unit) {
        val look = c.wardrobe.trialLook(r, own = false)
        assertTrue("the theme changes something", look.isNotEmpty())
        val tried = c.recipes.pieces.lookOf(c.recipes.pieces.withLook(c.settingsRepository.current, look))
        wear()
        assertEquals(c.recipes.pieces.lookOf(c.settingsRepository.current), tried)
    }

    @Test fun aWholeThemeTriedOnIsTheLookItLeavesWhenWorn() = runBlocking {
        BuiltInThemes.of(WorkStyle.SHIFT).take(3).forEach { theme ->
            sameAsWorn(theme) { assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false)) }
        }
    }

    @Test fun someOfItsPartsTriedOnAreThePartsWorn() = runBlocking {
        val theme = BuiltInThemes.of(WorkStyle.DAY)[1]
        val ids = LookParts.of(theme).map { it.id }
        val some = ids.take((ids.size / 2).coerceAtLeast(1)).toSet()
        val worn = LookParts.pick(theme, some)
        sameAsWorn(worn) { assertTrue(c.wardrobe.wear(worn.copy(title = "${theme.title} 일부"), "builtin:${theme.title}", own = false, whole = theme)) }
    }

    /** A tab laid on stays over the next theme ([ThemeWardrobe.keepOverlays]), and the trial shows it so too. */
    @Test fun theTabsLaidOnStayOverTheThemeTriedOn() = runBlocking {
        val money = Recipe("돈 탭", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to ScreenLooks().with("money", ScreenLook(bg = 0xFF102030)).encode()))))
        assertTrue(c.wardrobe.overlay(money, "ABCD2345"))
        val theme = BuiltInThemes.of(WorkStyle.SHIFT)[2]
        sameAsWorn(theme) { assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false)) }
    }

    /** One tab's looks are laid on what is worn, as 장터's 넣기 does with them. */
    @Test fun oneTabsLookTriedOnIsLaidOnAsWhenPutIn() = runBlocking {
        val memo = Recipe("메모 탭", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to ScreenLooks().with("memo", ScreenLook(bg = 0xFF203040)).encode()))))
        sameAsWorn(memo) { assertTrue(c.wardrobe.overlay(memo, "MEMO2345")) }
    }

    @Test fun theTrialIsOnScreenOnlyAndNeverSaved() = runBlocking {
        val repo = c.settingsRepository
        val saved = repo.current
        val theme = BuiltInThemes.of(WorkStyle.SHIFT)[1]
        val picked = LookParts.of(theme).map { it.id }.toSet()
        assertTrue(LiveTryOn.start(c, theme, own = false, picked, "money", LiveTryOn.From.Detail("ABCD2345")))
        assertTrue(repo.trying)
        // The screens draw the theme; what is saved (and backed up, and put back) is mine.
        assertNotEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.settings.value))
        assertEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.current))
        assertEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(SettingsRepository(c.appContext).current))
        // A setting changed while trying it on is saved; the theme still is not.
        repo.update { it.copy(showLunar = !saved.showLunar) }
        assertEquals(!saved.showLunar, repo.settings.value.showLunar)
        assertEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(SettingsRepository(c.appContext).current))
        assertNotEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.settings.value))
        // 지금 모습 and back.
        LiveTryOn.show(c, false)
        assertEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.settings.value))
        LiveTryOn.show(c, true)
        assertNotEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.settings.value))
        // 끝내기: my look again, and 입어 보기 to open again with the same parts and tab.
        LiveTryOn.end(c)
        assertFalse(repo.trying)
        assertNull(LiveTryOn.trial.value)
        assertEquals(c.recipes.pieces.lookOf(saved), c.recipes.pieces.lookOf(repo.settings.value))
        val again = LiveTryOn.take { it == LiveTryOn.From.Detail("ABCD2345") }
        assertEquals(picked, again?.picked)
        assertEquals("money", again?.tab)
        assertNull(LiveTryOn.reopen.value)
        repo.update { it.copy(showLunar = saved.showLunar) }
    }

    /** A look put on for real while trying one on ends the trial, so what was put on is what shows. */
    @Test fun wearingForRealEndsTheTrial() = runBlocking {
        val tried = BuiltInThemes.of(WorkStyle.SHIFT)[1]
        assertTrue(LiveTryOn.start(c, tried, own = false, LookParts.of(tried).map { it.id }.toSet(), "calendar", LiveTryOn.From.Detail("X")))
        val worn = BuiltInThemes.of(WorkStyle.SHIFT)[2]
        assertTrue(c.wardrobe.wear(worn, "builtin:${worn.title}", own = false))
        assertFalse(c.settingsRepository.trying)
        assertNull(LiveTryOn.trial.value)
        assertEquals(c.recipes.pieces.lookOf(c.settingsRepository.current), c.recipes.pieces.lookOf(c.settingsRepository.settings.value))
    }

    /**
     * PC a483ca15 (사무직이 벚꽃 파스텔을 입어 보면 메모 줄이 보이는데, 입은 달력은 메모 점): the example's 입으면 draws the 달력 모양 입기
     * leaves — someone else's theme keeps my 메모 줄 수 and 줄 순서 — for every theme of every kind of work.
     */
    @Test fun theExampleShowsTheCellsWearingLeaves() = runBlocking {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY, memoInCells = false, calView = com.bamnat.core.recipe.CalendarView(memo = 0).encode()) }
        val start = c.recipes.pieces.lookSnapshot()
        WorkStyle.entries.flatMap { BuiltInThemes.of(it) }.distinctBy { it.title }.forEach { theme ->
            c.recipes.pieces.restoreLook(start)
            val mine = Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
            val shown = com.bamnat.app.ui.recipe.tryOnAfter(mine, theme, LookParts.of(theme).map { it.id }.toSet(), own = false)
            fun content(r: Recipe) = r.pieces.firstOrNull { it.kind == PieceKind.CAL_VIEW }?.values.orEmpty()
                .filterKeys { it in com.bamnat.core.recipe.CalendarView.CONTENT_KEYS }.mapValues { (_, v) -> (v as? Number)?.toLong() ?: v }
            assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
            val worn = Recipe("입음", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
            assertEquals(theme.title, content(worn), content(shown))
            val view = com.bamnat.core.recipe.CalendarView.decode(c.settingsRepository.current.calView)
            assertEquals(theme.title, 0, view.memo ?: 0)
        }
    }

    @Test fun nothingToTryWhenTheLookIsAlreadyMine() = runBlocking {
        val theme = BuiltInThemes.of(WorkStyle.SHIFT)[0]
        assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
        assertFalse(LiveTryOn.start(c, theme, own = false, LookParts.of(theme).map { it.id }.toSet(), "calendar", LiveTryOn.From.Detail("X")))
        assertFalse(c.settingsRepository.trying)
    }
}
