package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.recipe.RecipeDetailScreen
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.recipe.AppFeel
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * 2.0.0 갈래 S 3묶음의 사진 (디자인 품질 조건 10·12·14, `build/screenshots/s3/`): RVS-15 앱 동작 섞은 안 (사용자 2026-11-12 'A + B 혼합').
 * Each screen in the four looks that see every pair of 밝은·어두운 × 글자 100·200% × 접은·편 폭 once; what it must show is checked after
 * every shot is taken, so the branch before gives the 앞 사진 too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class S3ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val looks = listOf(
        Triple(false, 1f, FOLDED), Triple(true, 2f, FOLDED), Triple(false, 2f, UNFOLDED), Triple(true, 1f, UNFOLDED),
    )

    private fun night(q: String) = q.replace("-port-", "-port-night-")
    private fun lookName(dark: Boolean, font: Float, q: String) =
        "${if (dark) "dark" else "light"}_${(font * 100).toInt()}_${if (q == FOLDED) "folded" else "unfolded"}"

    private fun fourLooks(name: String, what: ScreenshotBase.Screen.() -> String?) {
        val misses = ArrayList<String>()
        for ((dark, font, q) in looks) {
            val n = "s3/${name}_${lookName(dark, font, q)}"
            shoot(n, if (dark) night(q) else q, font) { what()?.let { misses += "$n: $it" } }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    /** Someone else's work whose 앱 동작 changes 꾸밈, 밝기 and three of my own settings. */
    private val feel = mapOf<String, Any?>("accent" to 0xFF00897BL, "theme" to "dark", "textStep" to 1L, "payPeriod" to true, "hold" to "memo")
    private val work = Recipe("밤바다 테마", summary = "어두운 화면에서 가장 예쁜 청록", styles = WorkStyle.entries.toSet(),
        pieces = listOf(Piece(PieceKind.APP_FEEL, feel)))

    private inner class OneWork : RecipeApi {
        val code = "FEEL2345"
        private val item = RecipeItem(code, work.title, work.summary, work.styles, work.cover, null, 1, likes = 12, dislikes = 0, users = 40,
            kept = null, official = false, publicData = false, parent = null, at = Instant.parse("2026-11-01T00:00:00Z"))
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(listOf(item), null)
        override suspend fun show(code: String, device: String?, version: Int?) = RecipeDoc(item, RecipeCodec.hash(work), RecipeCodec.write(work), "public", mine = false)
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, RecipeCodec.hash(recipe), "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, RecipeCodec.hash(recipe), "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(12, 0, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    private fun mySettings() = runBlocking {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, textStep = 3, themeMode = ThemeMode.SYSTEM, accentColor = 0L, longPress = "menu") }
    }

    /**
     * RVS-15 (SN-04·SI-03): 넣기 창 shows the work's 앱 동작 choice by choice, 지금 → 넣으면, 꾸밈 on, 밝기 off with why, and my own settings
     * folded in one line until opened.
     */
    @Test fun thePutDialogShowsEachFeelChoice() = fourLooks("rvs15_put_feel") {
        mySettings()
        val api = OneWork().also { c.recipeApi = it }
        val vm = RecipeDetailViewModel(c, api.code, PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        // The choices are read off the main thread; the 창 is opened once they are there, as a person would after a look at the page.
        waitUntil { vm.state.value.feels.isNotEmpty() }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        when {
            !appears("앱 느낌 · 켠 것만 바뀌어요", exact = true) -> "넣기 창에 앱 느낌 줄이 없어요"
            !appears("이 작품은 어두운 화면에서 가장 예뻐요", exact = true) -> "밝기 줄에 권하는 말이 없어요"
            !appears("휴대폰 따라", exact = true) -> "밝기 줄에 지금 값이 없어요"
            !appears("내 설정 3개는 그대로 둬요", exact = true) -> "내 설정 한 줄이 없어요"
            else -> null
        }
    }

    /** The same 창 with 내 설정 opened: each of my own settings with 지금 → 넣으면, all off. */
    @Test fun myOwnSettingsOpenOff() = fourLooks("rvs15_put_feel_mine") {
        mySettings()
        val api = OneWork().also { c.recipeApi = it }
        val vm = RecipeDetailViewModel(c, api.code, PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        // The choices are read off the main thread; the 창 is opened once they are there, as a person would after a look at the page.
        waitUntil { vm.state.value.feels.isNotEmpty() }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        tapText("내 설정 3개는 그대로 둬요", exact = true)
        settle()
        when {
            !scrolledTo("글자가 지금보다 작아져요", exact = true) -> "글자 크기 줄에 '작아져요'가 없어요"
            vm.state.value.feelOn["textStep"] == true -> "내 설정이 처음부터 켜져 있어요"
            else -> null
        }
    }

    /** RVS-15 (SD-05): what a work changed of my own settings is put back from 설정 › 화면과 글자, asked once. */
    @Test fun settingsPutsBackWhatAWorkChanged() = fourLooks("rvs15_settings_back") {
        mySettings()
        runBlocking {
            val on = work.copy(pieces = listOf(Piece(PieceKind.APP_FEEL, AppFeel.only(feel, setOf("accent", "textStep", "hold")))))
            c.recipes.apply(on, "FEEL2345", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.APP_FEEL, RecipePieces.Mode.REPLACE)), emptyMap())
        }
        settle()
        // Wide, the list is the left part of the screen.
        runCatching { settingsPage(SettingsPage.CALENDAR.title) }.getOrElse { settingsPage(SettingsPage.CALENDAR.title, wide = true) }
        settle()
        // Wide, the page is the right part of the screen: it scrolls there.
        if (runCatching { tapText("작품이 바꾼 내 설정 2개", exact = true, across = 0.8f) }.isFailure) "설정 › 화면과 글자에 되돌리기 줄이 없어요"
        else {
            settle()
            if (!appears("넣기 전 설정으로 되돌릴까요?", exact = true)) "되돌리기 전에 묻지 않아요" else null
        }
    }
}
