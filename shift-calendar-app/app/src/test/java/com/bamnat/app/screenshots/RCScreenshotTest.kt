package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.app.ui.recipe.RecipeDetailScreen
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.app.ui.recipe.RecipeMakeScreen
import com.bamnat.app.ui.recipe.RecipeMakeViewModel
import com.bamnat.app.ui.recipe.RecipeMineScreen
import com.bamnat.app.ui.recipe.RecipeMineViewModel
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * [RCP-41] 1.34 나눔 판's screens, numbers 1340~1349, on a made-up server that serves the 시작 레시피 core wrote
 * (`docs/recipes/starter-recipes.json`): the list of a kind of work, a recipe with its 미리 보기 and 넣기 → 되돌리기, 만들기 step 1,
 * 설정 › 나눔 판, and the new 교대 광장.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RCScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private class Starters : RecipeApi {
        val recipes: List<Pair<Recipe, Boolean>> = JSONArray(File("../docs/recipes/starter-recipes.json").readText()).let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { RecipeCodec.read(it.getJSONObject("recipe").toString()) to it.getBoolean("publicData") } }
        }
        private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        fun code(i: Int) = "STRT" + (0 until 4).map { alphabet[(i shr (it * 5)) and 31] }.joinToString("")
        fun item(i: Int): RecipeItem = recipes[i].let { (r, data) ->
            RecipeItem(code(i), r.title, r.summary, r.styles, r.cover, null, 1, likes = 40 - i % 30, dislikes = i % 3, users = 120 - i,
                kept = if (i % 2 == 0) 82 else null, official = !data, publicData = data, parent = null, at = Instant.parse("2026-10-29T00:00:00Z"))
        }
        var applied = 0
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int) = RecipePage(
            recipes.indices.filter { i -> (style == null || style in recipes[i].first.styles) && (sort == "data") == recipes[i].second }
                .map(::item).filter { q.isBlank() || q in it.title }, null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc {
            val i = recipes.indices.first { code(it) == code }
            val r = recipes[i].first
            return RecipeDoc(item(i), RecipeCodec.hash(r), RecipeCodec.write(r), "public", mine = false)
        }
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, RecipeCodec.hash(recipe), "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, RecipeCodec.hash(recipe), "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(41, 1, value)
        override suspend fun applied(code: String, kept: Boolean) { applied++ }
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    private fun starters() = Starters().also { c.recipeApi = it }
    private fun style(s: WorkStyle) = c.settingsRepository.update { it.copy(workStyle = s) }

    @Test fun theListOfAKindOfWork() = shoot("1340_rcp_list_shift", PHONE) {
        starters()
        style(WorkStyle.SHIFT)
        content { RecipeListScreen(RecipeListViewModel(c), onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        assertTrue(appears("요즘 뜨는", exact = true))
        assertTrue(appears("야간 파랑", exact = true))
        // A 알바 recipe is not in the 교대 list.
        assertTrue(gone("주휴", exact = false))
    }

    @Test fun theHourlyListHasItsOwn() = shoot("1341_rcp_list_hourly", PHONE) {
        val api = starters()
        style(WorkStyle.HOURLY)
        content { RecipeListScreen(RecipeListViewModel(c), onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        val first = api.recipes.first { WorkStyle.HOURLY in it.first.styles && !it.second }.first.title
        assertTrue(appears(first, exact = true))
    }

    @Test fun aRecipeIsPreviewedThenPutInAndTakenBack() = shoot("1342_rcp_detail_applied", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, api.code(0), PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        assertTrue(appears("내 달력에 넣을 것 고르기", exact = true))
        tapText("내 달력에 넣기", exact = false)
        settle()
        assertTrue("넣기 전에 바뀌는 것을 보여 줘요", appears("이렇게 바뀌어요", exact = true))
        tapText("넣기", exact = true)
        settle()
        val applied = runBlocking { c.recipes.applied.first() }
        assertEquals(1, applied.size)
        assertEquals(1, api.applied)
        assertTrue(c.recipes.canUndo(applied.first()))
        assertTrue(runBlocking { c.recipes.undo(applied.first().id) })
    }

    @Test fun theConfirmShowsWhatChanges() = shoot("1343_rcp_detail_confirm", "w360dp-h780dp-port-night-450dpi") {
        val api = starters()
        style(WorkStyle.SHIFT)
        content { RecipeDetailScreen(RecipeDetailViewModel(c, api.code(0)), onBack = {}, onRemake = {}) }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        assertTrue(appears("7일 안에는 설정 › 나눔 판에서 되돌릴 수 있어요", exact = true))
    }

    @Test fun makingStartsWithWhatToShare() = shoot("1344_rcp_make_step1", PHONE) {
        starters()
        style(WorkStyle.SHIFT)
        content { RecipeMakeScreen(RecipeMakeViewModel(c), onBack = {}, onOpen = {}) }
        settle()
        assertTrue(appears("레시피 만들기 1/5 · 고르기", exact = true))
    }

    @Test fun settingsOfTheBoard() = shoot("1345_rcp_mine", PHONE) {
        starters()
        content { RecipeMineScreen(RecipeMineViewModel(c), onBack = {}, onOpen = {}, onDraft = {}, onList = {}) }
        settle()
        assertTrue(appears("나눔 판 쓰기", exact = true))
        assertTrue(appears("받은 레시피", exact = true))
        assertTrue(appears("앞으로 3개는 운영자가 보고 올려요", exact = false))
    }

    @Test fun theShiftPlazaOfItsOwn() = shoot("1346_rcp_shift_plaza", PHONE) {
        companyOn()
        style(WorkStyle.SHIFT)
        val vm = PlazaHomeViewModel(c, own = true)
        content { PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        assertTrue(appears("교대 광장", exact = true))
        assertTrue(appears("교대 이야기", exact = true))
        assertTrue("회사 이야기는 회사 광장에", gone("근무·급여 정보", exact = true))
    }
}
