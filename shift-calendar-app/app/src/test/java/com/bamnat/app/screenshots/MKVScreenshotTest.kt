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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
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
                official = true, publicData = false, parent = null, at = Instant.parse("2026-11-01T00:00:00Z"), tabs = tabs, scope = scope,
                // 1.39 [MKC-04] 규칙·단추 작품 가운데 하나 걸러 하나는 1.39 새 기능 장치(값 칸·단추 위젯)를 써요.
                fx = if (scope == "logic" && i % 2 == 0) listOf("values", "outside") else emptyList())
        }

        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(
            recipes.indices.map(::item).filter { style == null || style in it.styles }.filter { !RecipeClient.fxOnly(kind) || it.fx.isNotEmpty() }.filter { !RecipeClient.freeOnly(kind) || it.fx.isEmpty() }.filter {
                val k = RecipeClient.plain(kind)
                when {
                    k == RecipeClient.SCOPE_THEME -> it.scope == "theme"
                    k == RecipeClient.SCOPE_LOGIC -> it.scope == "logic"
                    // 1.39 [MKC-01] 도구 (옛 규칙·단추로 열어도 도구로 가요).
                    k != null && k.startsWith(RecipeClient.SCOPE_TOOL) -> it.scope == "logic" || it.scope == "part"
                    k != null && k.startsWith(RecipeClient.SCOPE_TAB) -> it.scope == "tab" &&
                        RecipeClient.tabOf(k).let { t -> t.isEmpty() || t in it.tabs }
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
    /** PC 확인 7b219467 MED-1: a work from a newer app (a kind of piece this app does not know) is still drawn with what it knows. */
    @Test fun aNewerWorkIsDrawnWithThePiecesThisAppKnows() {
        val body = org.json.JSONObject(RecipeCodec.write(Market().recipes.first { r -> r.pieces.any { it.kind == PieceKind.SCREEN_LOOK } }))
        body.getJSONArray("pieces").put(org.json.JSONObject().put("kind", "ledgerFromTheFuture").put("costs", org.json.JSONArray().put("식비")))
        val r = com.bamnat.app.ui.recipe.MarketShots.known(body.toString())
        assertTrue("아는 조각만으로 그려요", r != null && r.pieces.isNotEmpty() && r.pieces.none { it.kind.key == "ledgerFromTheFuture" })
    }

    @Test fun rulesShowTheirNotifications() = market(RecipeClient.SCOPE_LOGIC, "1377_mkv_rules")

    /** 1.39 [MKC-01] 도구: 옛 규칙·단추로 열어도 도구 구간, 종류 칩(부품·자동 규칙·단추)과 화면 칩. */
    @Test fun toolsHaveTheirKinds() = shoot("1520_mkc_tools", PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.kind(RecipeClient.SCOPE_LOGIC)
        settle()
        assertTrue("규칙·단추는 도구로", model.state.value.kind == RecipeClient.SCOPE_TOOL)
        assertTrue(appears("🧱 부품", exact = true))
        assertTrue(appears("⚡ 자동 규칙", exact = true))
        assertTrue("쓰는 화면 칩은 둘째 줄에 펼쳐져 있어요", appears("모든 화면", exact = true))
    }

    /** 1.39 [MKC-01] 탭별 작품: 탭 칩 아래 꾸미기·기능 칩. */
    @Test fun tabWorksHaveLookAndFunction() = shoot("1521_mkc_tab_made", PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.kind(RecipeClient.SCOPE_TAB)
        settle()
        tapText("🎨 꾸미기", exact = true)
        settle()
        assertTrue(model.state.value.kind == RecipeClient.tabKind("", "look"))
        assertTrue(appears("탭별 작품", exact = true))
    }

    /** 1.39 [MKC-02][MKC-03] 도구 전후 해 보기: 새 단추를 눌러 보면 무엇을 하는지, 규칙은 상황을 만들어 지금 ↔ 넣으면. */
    @Test fun aToolIsTriedBeforeItGoesIn() = shoot("1522_mkc_tool_tryout", PHONE) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val logic = com.bamnat.core.formula.Logic(
            buttons = listOf(com.bamnat.core.formula.LogicButton("커피", "☕", com.bamnat.core.formula.ButtonPlace.MONEY,
                com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.ADD_MONEY, "커피", 4_500))),
            rules = listOf(com.bamnat.core.formula.LogicRule("밤 정리", com.bamnat.core.formula.RuleTrigger.DAILY, at = "21:00",
                act = com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.NOTIFY, "오늘 하루 정리해요"))),
        )
        val r = Recipe("커피 단추와 밤 정리", styles = WorkStyle.entries.toSet(),
            pieces = listOf(com.bamnat.core.recipe.Piece(PieceKind.LOGIC, logic.toValues())))
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp)) { com.bamnat.app.ui.recipe.ToolTryout(r, WorkStyle.SHIFT) }
        }
        settle()
        assertTrue(appears("전후 해 보기", exact = true))
        tapText("✨ ☕ 커피", exact = true)
        settle()
        assertTrue(appears("‘커피’를 누르면 가계부에 지출 ‘커피 4,500원’ 한 줄이 생겨요", exact = false))
        tapText("21:00 만들어 보기", exact = false)
        settle()
        assertTrue(appears("이때 아무 일도 없어요", exact = true))
        // The result under it, reached by scrolling (a tap on the words does nothing).
        tapText("‘오늘 하루 정리해요’ 알림이 떠요", exact = false)
        assertTrue(appears("‘오늘 하루 정리해요’ 알림이 떠요", exact = false))
    }

    /** 1.39 [MKD-04] 값 칸 해 보기: 단추 위젯 '물 한 잔'을 누를 때마다 숫자가 오르고, 목표에 처음 닿으면 그 규칙이 따라 돌아요. 저장 안 됨. */
    @Test fun aValueButtonIsTriedWithItsCount() = shoot("1528_mkd_value_tryout", PHONE) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val f = com.bamnat.core.formula.LogicVerb.VAL_ADD
        val logic = com.bamnat.core.formula.Logic(
            buttons = listOf(com.bamnat.core.formula.LogicButton("물 한 잔", "💧", com.bamnat.core.formula.ButtonPlace.WIDGET,
                com.bamnat.core.formula.LogicAct(f, "물 잔", by = 1.0))),
            rules = listOf(com.bamnat.core.formula.LogicRule("다 마셨어요", com.bamnat.core.formula.RuleTrigger.VALUE, of = "물 잔", goal = true,
                act = com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.NOTIFY, "오늘 물 다 마셨어요"))),
            values = listOf(com.bamnat.core.formula.LogicValue("물 잔", com.bamnat.core.formula.ValueReset.DAY, goal = 2.0, unit = "잔")),
        )
        val r = Recipe("물 마시기", styles = WorkStyle.entries.toSet(), pieces = listOf(com.bamnat.core.recipe.Piece(PieceKind.LOGIC, logic.toValues())))
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp)) { com.bamnat.app.ui.recipe.ToolTryout(r, WorkStyle.SHIFT) }
        }
        settle()
        assertTrue(appears("✦ 값 칸 (해 보기)", exact = true))
        assertTrue(appears("0/2잔", exact = true))
        tapText("✨ 💧 물 한 잔", exact = true)
        settle()
        assertTrue(appears("1/2잔", exact = true))
        tapText("✨ 💧 물 한 잔", exact = true)
        settle()
        assertTrue(appears("2/2잔", exact = true))
        assertTrue("목표에 닿으면 규칙이 따라 돌아요", appears("그러면 ‘다 마셨어요’ 규칙이 돌아요", exact = false))
        tapText("처음으로", exact = true)
        settle()
        assertTrue(appears("0/2잔", exact = true))
    }

    /** 1.39 [MKC-04] '✦ 프로 기능' 칩: 도구 줄 전체 바로 다음, 고르면 새 기능 장치를 쓴 작품만, 카드에 무엇이 들었는지. */
    @Test fun proWorksHaveTheirChip() = shoot("1523_mkc_fx_chip", PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.kind(RecipeClient.SCOPE_TOOL)
        settle()
        tapText("✦ 프로 기능", exact = true)
        settle()
        assertTrue(model.state.value.kind == RecipeClient.toolKind("", null, fx = true))
        assertTrue("프로 기능 작품만", model.state.value.items.isNotEmpty() && model.state.value.items.all { it.fx.isNotEmpty() })
        assertTrue("무엇이 들었는지 한 줄", appears("✦ 값 칸 · 단추 위젯·빠른 설정·바로가기", exact = true))
        // 다른 칩을 고르면 '✦ 프로 기능'은 풀려요.
        tapText("🧱 부품", exact = true)
        settle()
        assertTrue(!RecipeClient.fxOnly(model.state.value.kind))
    }

    /** 1.39 [MKC-04] 무료 폰의 상세: 프로 기능 카드에 하루 써 보기·프로 보기, 시작하면 넣는 길로. */
    @Test fun aFreePhoneTastesAProWorkForADay() = shoot("1524_mkc_fx_card", PHONE) {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT, previewTier = com.bamnat.app.tier.Tier.FREE) }
        val parts = setOf(com.bamnat.core.formula.FxPro.Part.VALUES, com.bamnat.core.formula.FxPro.Part.OUTSIDE)
        var opened = 0
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp)) {
                com.bamnat.app.ui.recipe.FxHeadLine(parts)
                com.bamnat.app.ui.recipe.FxCard(parts, "FXTASTE23", applied = false) { opened++ }
                com.bamnat.app.ui.recipe.FxHeadLine(emptySet())
            }
        }
        settle()
        assertTrue(appears("프로 기능이 든 작품", exact = true))
        assertTrue(appears("✦ 들어 있는 것: 값 칸 · 단추 위젯·빠른 설정·바로가기", exact = true))
        assertTrue("무료 작품은 무료라고", appears("무료 · 모두 무료로 넣고 쓸 수 있어요", exact = true))
        assertTrue(appears("프로 보기", exact = true))
        tapText("하루 써 보기", exact = true)
        settle()
        assertTrue(appears("하루 써 보기를 시작할까요?", exact = true))
        tapText("시작하고 넣기", exact = true)
        settle()
        assertTrue("시작하면 넣는 길로", opened == 1)
        assertTrue(com.bamnat.app.logic.FxAccess.status(c, "FXTASTE23") is com.bamnat.core.formula.FxTaste.Status.Using)
        assertTrue(appears("하루 써 보기 중이에요", exact = false))
    }

    /** 1.39 [MKF-01][MKF-02] 크게 넘겨 보기: 좌우로 다음 작품, 위아래로 그 작품이 바꾸는 다른 화면(칩), 아래에 입혀 보기·자세히. */
    @Test fun aWorkIsFlippedBigBeforeItIsPicked() = shoot("1525_mkf_flip", PHONE) {
        val m = Market()
        c.recipeApi = m
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val themes = m.recipes.indices.map(m::item).filter { it.scope == "theme" }.take(4)
        assertTrue("테마가 있어요", themes.size >= 2)
        var opened: String? = null
        content {
            com.bamnat.app.ui.recipe.MarketFlip(themes, 0, WorkStyle.SHIFT, onDismiss = {}, onOpen = { opened = it }, onTry = {})
        }
        settle()
        assertTrue(appears("크게 넘겨 보기", exact = true))
        assertTrue(appears("1 / ${themes.size}", exact = true))
        assertTrue("테마는 달력만이 아니라 다른 화면도", appears("가계부", exact = true, ms = 5_000))
        assertTrue(appears("내 앱에 입혀 보기", exact = true))
        tapText("다음 ▶", exact = true)
        settle()
        assertTrue(appears("2 / ${themes.size}", exact = true))
        tapText("자세히", exact = true)
        settle()
        assertTrue("자세히는 지금 보는 작품으로", opened == themes[1].code)
    }

    /** 1.39 [MKF-03] 작게 보기: 한 줄에 셋, 이 폰에 기억해요. */
    @Test fun smallCardsShowMoreAtOnce() = shoot("1526_mkf_small", PHONE) {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.kind(RecipeClient.SCOPE_THEME)
        settle()
        tapText("작게 보기", exact = true)
        settle()
        assertTrue("이 폰에 기억해요", app.getSharedPreferences("market_view", 0).getBoolean("small", false))
        scrollUp(300)
        assertTrue(appears("작품 모습", exact = true, ms = 5_000))
        settle()
    }

    /**
     * 1.39 [MKD-41][MKD-42][MKD-43] 상세 (사용자 2026-11-07 "설명들이 너무 난잡 … 간결하게"): 맨 위에 작품 실제 모습(좌우로 다른 화면),
     * 이름 · 만든 분(표식 · 팔로워, 누르면 프로필) · 한두 줄, 가운데 '넣기 전에 해 보기' 사다리, 조각은 '자세히 보기'에 접혀요.
     */
    @Test fun theDetailShowsTheWorkFirst() = shoot("1527_mkd_detail_top", PHONE) {
        val m = Market()
        val i = m.recipes.indices.first { m.item(it).scope == "theme" }
        c.recipeApi = object : RecipeApi by m {
            override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = m.show(code, device, version).let {
                it.copy(item = it.item.copy(nick = "밤샘간호사", official = false, maker = "mk1", mark = "month", followers = 1_234))
            }
        }
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        var maker: String? = null
        val vm = com.bamnat.app.ui.recipe.RecipeDetailViewModel(c, m.code(i), com.bamnat.app.data.PersonalCalendar.MAIN)
        content { com.bamnat.app.ui.recipe.RecipeDetailScreen(vm, onBack = {}, onRemake = {}, onMaker = { maker = it }) }
        settle()
        assertTrue("작품 모습이 맨 위에", appears("작품 모습", exact = true, ms = 5_000))
        assertTrue("테마는 다른 화면도 넘겨 봐요", appears("가계부", exact = true))
        assertTrue(appears("🏅 이달의 제작자", exact = true))
        assertTrue(appears("팔로워 1.2천 · 프로필 · 다른 작품", exact = true))
        assertTrue(scrolledTo("넣기 전에 해 보기", exact = true))
        assertTrue(appears("예시로 보기", exact = true))
        assertTrue(appears("내 앱에 입혀 보기", exact = true))
        assertTrue("조각 고르기는 접혀 있어요", gone("켜 둔 조각만 들어가요. 근무 기록·가계부·메모는 그대로예요", exact = true))
        repeat(6) { scrollDown(400) }
        settle()
        tapText("밤샘간호사", exact = true)
        settle()
        assertTrue("만든 분을 누르면 프로필로", maker == "mk1")
    }
}
