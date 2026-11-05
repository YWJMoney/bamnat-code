package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * [MKV-01] 1.37 장터 카드 실제 모습, numbers 1375~1379, on a made-up server serving the 1.36 출시 작품 (`docs/recipes/market-136.json`):
 * each card draws the work itself — 테마 on its calendar, 기능별 탭 on its tab's page, 규칙·단추 as notifications.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MKVScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private class Market : RecipeApi {
        val recipes: List<Recipe> = JSONArray(File("../docs/recipes/market-136.json").readText()).let { a ->
            (0 until a.length()).map { i -> RecipeCodec.read(a.getJSONObject(i).getJSONObject("recipe").toString()) }
        }
        fun code(i: Int) = "MKV" + "ABCDEFGHJK"[i / 100] + "ABCDEFGHJK"[(i / 10) % 10] + "ABCDEFGHJK"[i % 10] + "23"

        /** What the server says of each: 테마, one 탭, or 규칙·단추 (its scope and tabs). */
        private fun sort(r: Recipe): Pair<String, List<String>> {
            val k = r.pieces.map { it.kind }.toSet()
            return when {
                k == setOf(PieceKind.LOGIC) -> "logic" to emptyList()
                k.size >= 6 -> "theme" to listOf("calendar", "money", "memo")
                PieceKind.ALARMS in k -> "tab" to listOf("alarm")
                PieceKind.WIDGET in k -> "tab" to listOf("widget")
                PieceKind.MONEY_LOOK in k -> "tab" to listOf("money")
                PieceKind.MEMO_LOOK in k -> "tab" to listOf("memo")
                PieceKind.CARDS in k -> "tab" to listOf("pay")
                else -> "tab" to listOf("calendar")
            }
        }

        fun item(i: Int): RecipeItem = recipes[i].let { r ->
            val (scope, tabs) = sort(r)
            RecipeItem(code(i), r.title, r.summary, r.styles, r.cover, null, 1, likes = 30 - i % 20, dislikes = 0, users = 90 - i % 50, kept = null,
                official = true, publicData = false, parent = null, at = Instant.parse("2026-11-01T00:00:00Z"), tabs = tabs, scope = scope)
        }

        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(
            recipes.indices.map(::item).filter { style == null || style in it.styles }.filter {
                when {
                    kind == RecipeClient.SCOPE_THEME -> it.scope == "theme"
                    kind == RecipeClient.SCOPE_LOGIC -> it.scope == "logic"
                    kind != null && kind.startsWith(RecipeClient.SCOPE_TAB) -> it.scope == "tab" &&
                        kind.removePrefix(RecipeClient.SCOPE_TAB).let { t -> t.isEmpty() || t in it.tabs }
                    else -> true
                }
            }.let { l ->
                // One of each tab first, so a page shows every kind of picture.
                if (kind == RecipeClient.SCOPE_TAB) l.groupBy { it.tabs.first() }.values.map { it.first() } + l else l
            }.distinctBy { it.code }.take(12), null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc {
            val i = recipes.indices.first { code(it) == code }
            return RecipeDoc(item(i), RecipeCodec.hash(recipes[i]), RecipeCodec.write(recipes[i]), "public", mine = false)
        }
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, RecipeCodec.hash(recipe), "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, RecipeCodec.hash(recipe), "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(1, 0, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    private fun market(kind: String, name: String) = shoot(name, PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.kind(kind)
        settle()
        scrollUp(300)
        assertTrue("카드가 작품을 받아 실제 모습으로 그려요", appears("작품 모습", exact = true, ms = 5_000))
        settle()
    }

    @Test fun themesShowTheirCalendar() = market(RecipeClient.SCOPE_THEME, "1375_mkv_themes")
    @Test fun tabsShowTheirPage() = market(RecipeClient.SCOPE_TAB, "1376_mkv_tabs")
    @Test fun rulesShowTheirNotifications() = market(RecipeClient.SCOPE_LOGIC, "1377_mkv_rules")
}
