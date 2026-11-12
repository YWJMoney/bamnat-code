package com.bamnat.app.screenshots

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.bamnat.app.ui.company.PlazaComposer
import com.bamnat.app.ui.company.PlazaHead
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.getOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * 1.39 [PLZC-60][MKD-60] 마을 광장·장터 윗부분 (사용자 2026-11-08 "위에 공간을 불필요하게 차지"), numbers 1540~1543:
 * 광장's head as one line (이름 · 오늘 글 n · 문 하나 · 메뉴) and one 글 쓰기 box; 장터 opening on its search box.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HeadRoomScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** The head of 교대 광장 as 마을 draws it with 사람 먼저 on, over two posts (the posts here are stand-ins for the feed). */
    private fun plazaHead(plan: StylePlaza.Plan, prompt: String, topic: String?, picks: Int = 0, onTag: (() -> Unit)? = {}) =
        @androidx.compose.runtime.Composable {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlazaHead(title = plan.title, today = 4, model = com.bamnat.app.ui.company.plazaHeadModel(plan, picks = picks, onPicks = {},
                        onCompany = {}, onShare = {}, onRates = {},
                        onAll = {}.takeIf { plan.room == StylePlaza.SHIFT || plan.top == StylePlaza.Top.COMPANY_ROOM }, onFind = {}.takeIf { plan.room == StylePlaza.SHIFT },
                        onTag = onTag))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlazaComposer(prompt, topic, onWrite = {})
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("전체", "지금 근무 중 3명", "답 기다리는 질문 2", "질문", "팁", "자랑").forEachIndexed { i, l ->
                                com.bamnat.app.ui.components.FilterChip(selected = i == 0, onClick = {}, label = { Text(l) })
                            }
                        }
                        listOf("지호 첫 글이에요 야간 처음이라 떨려요", "둘째 글 당겨서 받기").forEach { body ->
                            com.bamnat.app.ui.company.SoftCard { Text(body, Modifier.padding(14.dp)) }
                        }
                    }
                }
            }
        }

    @Test fun theShiftPlazaHeadIsOneLine() = shoot("1540_plzc60_shift_head", PHONE) {
        val plan = StylePlaza.own(WorkStyle.SHIFT)
        content(plazaHead(plan, "쉬는 날이에요 · 이번 주 이야기를 한 줄 남겨요", topic = "야간 끝나고 잠드는 법"))
        settle()
        assertTrue(appears("교대 광장", exact = true))
        assertTrue(appears(" · 오늘 글 4", exact = true))
        assertTrue("회사 규칙 광장·우리 회사 찾기는 메뉴에", gone("📏 회사 규칙 광장", exact = true))
        assertTrue(appears("광장 메뉴: 회사 규칙 광장, 내 근무 태그 등", exact = true))
        // The menu is a popup window the shot does not read: what it holds is checked as the head builds it.
        val menu = com.bamnat.app.ui.company.plazaMenu(plan, 0, {}, {}, {}, {}, onAll = {}, onFind = {}, onTag = {}).map { it.first }
        assertEquals(listOf("📏 회사 규칙 광장", "🔎 우리 회사 찾기", "🏷 내 근무 태그·광장 알림"), menu)
        val company = com.bamnat.app.ui.company.plazaMenu(StylePlaza.plan(WorkStyle.SHIFT, "c1" to "예시"), 2, {}, {}, {}, {}, onAll = {}, onFind = null, onTag = null)
            .map { it.first }
        assertEquals(listOf("📚 선배 답 모음 2", "📋 근무·급여 정보·고친 점", "📤 내 설정 보내기", "🏢 다른 회사 규칙"), company)
    }

    /** 알바 광장: 시세 is the plaza's own business, so it stays beside the name. */
    @Test fun theRatesDoorStaysBesideTheName() = shoot("1541_plzc60_hourly_head", PHONE) {
        val plan = StylePlaza.plan(WorkStyle.HOURLY)
        content(plazaHead(plan, plan.hint, topic = null, picks = 3))
        settle()
        assertTrue(appears("💰 시세", exact = true))
        assertTrue("다른 회사 규칙은 알바에 없어요", gone("📏 회사 규칙 광장", exact = true))
    }

    /** A company's 정보 광장 with a long name: the name is cut, 회사 정보 and the menu stay. */
    @Test fun aCompanyRoomKeepsItsInfoDoor() = shoot("1542_plzc60_company_head", FOLDED) {
        val plan = StylePlaza.plan(WorkStyle.SHIFT, "c1" to "(주)예시정밀공업주식회사 제2공장")
        content(plazaHead(plan, plan.hint, topic = null, onTag = null))
        settle()
        assertTrue(appears("📋 회사 정보", exact = true))
    }

    private class Market : RecipeApi {
        val recipes: List<Recipe> = JSONArray(File("../docs/recipes/market-136.json").readText()).let { a ->
            (0 until a.length()).map { i -> RecipeCodec.read(a.getJSONObject(i).getJSONObject("recipe").toString()) }
        }
        fun code(i: Int) = "HRM" + "ABCDEFGHJK"[i / 100] + "ABCDEFGHJK"[(i / 10) % 10] + "ABCDEFGHJK"[i % 10] + "23"
        fun item(i: Int): RecipeItem = recipes[i].let { r ->
            RecipeItem(code(i), r.title, r.summary, r.styles, r.cover, null, 1, likes = 30 - i % 20, dislikes = 0, users = 90 - i % 50, kept = null,
                official = true, publicData = false, parent = null, at = Instant.parse("2026-11-01T00:00:00Z"),
                tabs = listOf("calendar", "money", "memo"), scope = "theme")
        }
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) =
            RecipePage(recipes.indices.filter { recipes[it].pieces.size >= 6 }.map(::item).take(8), null)
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

    /** 마을 › 장터: the search box is the first row, 만들기 beside it; the three 구간 lead the row of chips under it (2.0.0 RVM-31); 넘겨 보기 a chip. */
    @Test fun theMarketOpensOnItsSearch() = shoot("1543_mkd60_market_top", PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = null, onOpen = {}, onMake = {}, onMine = {}, title = "장터", onSwipe = {}) }
        settle()
        model.kind(RecipeClient.SCOPE_THEME)
        settle()
        assertTrue(appears("이름·설명·초성으로 찾기", exact = true))
        assertTrue(appears("스튜디오에서 만들기", exact = true))
        // 2.0.0 [RVM-31]: 테마's own chips follow the three 구간 in the one row; on a 360dp phone 넘겨 보기 is one slide (or ▾) away.
        assertTrue(nodes().any { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any { it.text == "🃏 넘겨 보기" } })
        assertTrue("긴 설명 줄은 화면 글자로 없어요 (TalkBack 설명에만)", nodes(merged = false).none { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any { "모든 화면을 한 벌로 꾸민 테마예요" in it.text } })
        // The search box comes before the 구간 on the screen.
        val top = nodes().filter { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText) != null }
            .minOf { it.boundsInWindow.top }
        val half = nodes().first { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            .orEmpty().any { it.text == "🎨 테마" } }.boundsInWindow.top
        assertTrue("찾기 칸이 구간보다 위", top < half)
    }
}
