package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.InquiryApi
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipePost
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.recipe.MakerScreen
import com.bamnat.app.ui.recipe.MakerViewModel
import com.bamnat.app.ui.recipe.RecipeDetailScreen
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.app.ui.recipe.RequestsScreen
import com.bamnat.app.ui.recipe.RequestsViewModel
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * 2.0.0 갈래 Q1 PR 3c (검토 U22-04·U22-05·U22-06·U22-08·U22b-01·U22b-05·U22b-07·U22b-08·I22bb-06): the seven screens the app part
 * touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after sheets. What differs between before and after is only
 * in [Rvq3c].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvq3cScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val starter: Recipe by lazy {
        JSONArray(File("../docs/recipes/starter-recipes.json").readText()).let { a ->
            (0 until a.length()).map { RecipeCodec.read(a.getJSONObject(it).getJSONObject("recipe").toString()) }
                .first { WorkStyle.SHIFT in it.styles }
        }
    }

    private fun item(code: String, title: String, at: Instant = Today.instant()) = RecipeItem(code, title, "야간이 잘 보여요", setOf(WorkStyle.SHIFT), Cover(),
        "밤샘간호사", 1, likes = 41, dislikes = 1, users = 214, kept = 80, official = false, publicData = false, parent = null, at = at)

    private open inner class Market : RecipeApi {
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(emptyList(), null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc =
            RecipeDoc(item(code, starter.title), RecipeCodec.hash(starter), RecipeCodec.write(starter), "public", mine = false)
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, "h", "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, "h", "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(41, 1, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    private fun shift() = c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }

    // ---- 01 내 문의: 다시 물었어요 ----
    private fun asks(): Screen.() -> Unit = {
        val base = FakeInquiryApi()
        val mine = Rvq3c.inquiries(Today.instant())
        c.inquiryApi = object : InquiryApi by base { override suspend fun mine(device: String) = mine }
        c.inquiryStore.asked = true
        // The one written to again was opened to write (its answer is read).
        c.inquiryStore.read(mine.first(), mine)
        askFromScreen(com.bamnat.app.ui.settings.InquiryStart(help = true))
        tapText("내 문의", exact = true)
        settle()
    }

    // ---- 02 문의 하나: 고친 판 한 줄, 이어 쓰기 사진 ----
    private fun fixedThread(): Screen.() -> Unit = {
        asks().invoke(this)
        tapText("[달력] 달력 칸을 누르면", prefix = true)
        settle()
    }

    // ---- 03 새 판에서 만든 작품 ----
    private fun newer(): Screen.() -> Unit = {
        c.recipeApi = object : Market() {
            override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = throw Rvq3c.newer()
        }
        shift()
        val vm = RecipeDetailViewModel(c, "NEWR2345", PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
    }

    // ---- 04 요청 판: 만드는 중 D+5, 이 작품이 맞나요? ----
    private fun requests(): Screen.() -> Unit = {
        val now = Today.instant()
        c.recipeApi = object : Market() {
            override suspend fun requests(device: String?, style: WorkStyle?) = Rvq3c.requests(now)
        }
        shift()
        val vm = RequestsViewModel(c)
        content { RequestsScreen(vm, onBack = {}, onOpen = {}, onMake = {}) }
        settle()
        reveal("4조 2교대 야간이", exact = false)
    }

    // ---- 05 만든 분 페이지: 작품 더 보기 ----
    private fun maker(): Screen.() -> Unit = {
        val works = (1..8).map { item("MKR${it}A2B3", "야간 테마 ${it}호") }
        c.recipeApi = object : Market() {
            override suspend fun maker(device: String?, id: String) = Rvq3c.maker(MakerPage(id, "밤샘간호사", "🦉", 0xFF3949ABL, mine = false,
                recipes = works, likes = 410, users = 2140, followers = 180, following = false, badges = emptyList(),
                bio = "야간 근무자를 위한 테마를 만들어요"))
        }
        shift()
        val vm = MakerViewModel(c, "a1b2c3d4e5f6")
        content { MakerScreen(vm, onBack = {}, onOpen = {}) }
        settle()
        reveal("야간 테마 8호", exact = true)
    }

    // ---- 06 작품 이야기: 최신부터, 더 보기 ----
    // 이야기는 정식 출시일부터 열려요(처리방침 3.8): the shot turns it on as an admin phone's 미리 보기 does.
    private fun talk(): Screen.() -> Unit = {
        c.proStore.setPasses(com.bamnat.app.tier.Passes(null, 0, emptyList(), 0, com.bamnat.app.tier.AdminState("approved", "dd", owner = true,
            previews = listOf(com.bamnat.core.flags.FlagCatalog.RECIPE_TALK)), false, Today.instant()))
        c.featureGate.refresh()
        repeat(50) { if (!c.flags.on(com.bamnat.core.flags.FlagCatalog.RECIPE_TALK)) settle() }
        val start = Instant.parse("2026-10-01T00:00:00Z")
        val words = listOf("야간 색이 눈에 편해요", "주말에도 잘 보이나요?", "3교대에도 맞아요", "글자가 조금 작아요", "위젯에도 되나요?",
            "알람이랑 같이 쓰니 좋아요", "휴무 색을 바꿀 수 있나요?", "한 달째 쓰는 중이에요", "폴드에서도 예뻐요", "어두운 모드도 좋아요",
            "근무 이름이 길면 잘려요", "새 판 고마워요", "야간 다음 날이 잘 보여요", "오늘 처음 넣어 봤어요")
        val posts = words.mapIndexed { i, w ->
            RecipePost("t$i", i + 1, null, w, start.plus(Duration.ofHours(i * 20L)), if (w.endsWith("?")) "question" else "opinion", null,
                mine = false, maker = false, usingDays = null, likes = i % 4, dislikes = 0)
        } + RecipePost("t1a", 99, null, "네, 주말 색도 따로 있어요", start.plus(Duration.ofHours(14 * 20L)), "answer", "t1", mine = false,
            maker = true, usingDays = null, likes = 2, dislikes = 0)
        c.recipeApi = object : Market() {
            override suspend fun talk(device: String?, code: String) = Rvq3c.talk(posts)
        }
        shift()
        val vm = RecipeDetailViewModel(c, "TALK2345", PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        reveal("이야기", exact = true)
    }

    // ---- 07 장터 목록: 불러오는 동안 ----
    private fun loading(): Screen.() -> Unit = {
        c.recipeApi = object : Market() {
            override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?): RecipePage =
                kotlinx.coroutines.awaitCancellation()
        }
        shift()
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
    }

    @Test fun asksLight() = shoot("rvq3c_01_asks_light_100_fold", FOLDED, asks())
    @Test fun asksLightBig() = shoot("rvq3c_01_asks_light_200_fold", FOLDED, 2f, asks())
    @Test fun asksDark() = shoot("rvq3c_01_asks_dark_100_fold", DARK, asks())
    @Test fun asksDarkBig() = shoot("rvq3c_01_asks_dark_200_fold", DARK, 2f, asks())
    @Test fun asksWide() = shoot("rvq3c_01_asks_light_100_open", UNFOLDED, asks())
    @Test fun asksWideBig() = shoot("rvq3c_01_asks_light_200_open", UNFOLDED, 2f, asks())
    @Test fun asksWideDark() = shoot("rvq3c_01_asks_dark_100_open", DARK_WIDE, asks())
    @Test fun asksWideDarkBig() = shoot("rvq3c_01_asks_dark_200_open", DARK_WIDE, 2f, asks())

    @Test fun fixedLight() = shoot("rvq3c_02_fixed_light_100_fold", FOLDED, fixedThread())
    @Test fun fixedLightBig() = shoot("rvq3c_02_fixed_light_200_fold", FOLDED, 2f, fixedThread())
    @Test fun fixedDark() = shoot("rvq3c_02_fixed_dark_100_fold", DARK, fixedThread())
    @Test fun fixedDarkBig() = shoot("rvq3c_02_fixed_dark_200_fold", DARK, 2f, fixedThread())
    @Test fun fixedWide() = shoot("rvq3c_02_fixed_light_100_open", UNFOLDED, fixedThread())
    @Test fun fixedWideBig() = shoot("rvq3c_02_fixed_light_200_open", UNFOLDED, 2f, fixedThread())
    @Test fun fixedWideDark() = shoot("rvq3c_02_fixed_dark_100_open", DARK_WIDE, fixedThread())
    @Test fun fixedWideDarkBig() = shoot("rvq3c_02_fixed_dark_200_open", DARK_WIDE, 2f, fixedThread())

    @Test fun newerLight() = shoot("rvq3c_03_newer_light_100_fold", FOLDED, newer())
    @Test fun newerLightBig() = shoot("rvq3c_03_newer_light_200_fold", FOLDED, 2f, newer())
    @Test fun newerDark() = shoot("rvq3c_03_newer_dark_100_fold", DARK, newer())
    @Test fun newerDarkBig() = shoot("rvq3c_03_newer_dark_200_fold", DARK, 2f, newer())
    @Test fun newerWide() = shoot("rvq3c_03_newer_light_100_open", UNFOLDED, newer())
    @Test fun newerWideBig() = shoot("rvq3c_03_newer_light_200_open", UNFOLDED, 2f, newer())
    @Test fun newerWideDark() = shoot("rvq3c_03_newer_dark_100_open", DARK_WIDE, newer())
    @Test fun newerWideDarkBig() = shoot("rvq3c_03_newer_dark_200_open", DARK_WIDE, 2f, newer())

    @Test fun requestsLight() = shoot("rvq3c_04_requests_light_100_fold", FOLDED, requests())
    @Test fun requestsLightBig() = shoot("rvq3c_04_requests_light_200_fold", FOLDED, 2f, requests())
    @Test fun requestsDark() = shoot("rvq3c_04_requests_dark_100_fold", DARK, requests())
    @Test fun requestsDarkBig() = shoot("rvq3c_04_requests_dark_200_fold", DARK, 2f, requests())
    @Test fun requestsWide() = shoot("rvq3c_04_requests_light_100_open", UNFOLDED, requests())
    @Test fun requestsWideBig() = shoot("rvq3c_04_requests_light_200_open", UNFOLDED, 2f, requests())
    @Test fun requestsWideDark() = shoot("rvq3c_04_requests_dark_100_open", DARK_WIDE, requests())
    @Test fun requestsWideDarkBig() = shoot("rvq3c_04_requests_dark_200_open", DARK_WIDE, 2f, requests())

    @Test fun makerLight() = shoot("rvq3c_05_maker_light_100_fold", FOLDED, maker())
    @Test fun makerLightBig() = shoot("rvq3c_05_maker_light_200_fold", FOLDED, 2f, maker())
    @Test fun makerDark() = shoot("rvq3c_05_maker_dark_100_fold", DARK, maker())
    @Test fun makerDarkBig() = shoot("rvq3c_05_maker_dark_200_fold", DARK, 2f, maker())
    @Test fun makerWide() = shoot("rvq3c_05_maker_light_100_open", UNFOLDED, maker())
    @Test fun makerWideBig() = shoot("rvq3c_05_maker_light_200_open", UNFOLDED, 2f, maker())
    @Test fun makerWideDark() = shoot("rvq3c_05_maker_dark_100_open", DARK_WIDE, maker())
    @Test fun makerWideDarkBig() = shoot("rvq3c_05_maker_dark_200_open", DARK_WIDE, 2f, maker())

    @Test fun talkLight() = shoot("rvq3c_06_talk_light_100_fold", FOLDED, talk())
    @Test fun talkLightBig() = shoot("rvq3c_06_talk_light_200_fold", FOLDED, 2f, talk())
    @Test fun talkDark() = shoot("rvq3c_06_talk_dark_100_fold", DARK, talk())
    @Test fun talkDarkBig() = shoot("rvq3c_06_talk_dark_200_fold", DARK, 2f, talk())
    @Test fun talkWide() = shoot("rvq3c_06_talk_light_100_open", UNFOLDED, talk())
    @Test fun talkWideBig() = shoot("rvq3c_06_talk_light_200_open", UNFOLDED, 2f, talk())
    @Test fun talkWideDark() = shoot("rvq3c_06_talk_dark_100_open", DARK_WIDE, talk())
    @Test fun talkWideDarkBig() = shoot("rvq3c_06_talk_dark_200_open", DARK_WIDE, 2f, talk())

    @Test fun loadingLight() = shoot("rvq3c_07_loading_light_100_fold", FOLDED, loading())
    @Test fun loadingLightBig() = shoot("rvq3c_07_loading_light_200_fold", FOLDED, 2f, loading())
    @Test fun loadingDark() = shoot("rvq3c_07_loading_dark_100_fold", DARK, loading())
    @Test fun loadingDarkBig() = shoot("rvq3c_07_loading_dark_200_fold", DARK, 2f, loading())
    @Test fun loadingWide() = shoot("rvq3c_07_loading_light_100_open", UNFOLDED, loading())
    @Test fun loadingWideBig() = shoot("rvq3c_07_loading_light_200_open", UNFOLDED, 2f, loading())
    @Test fun loadingWideDark() = shoot("rvq3c_07_loading_dark_100_open", DARK_WIDE, loading())
    @Test fun loadingWideDarkBig() = shoot("rvq3c_07_loading_dark_200_open", DARK_WIDE, 2f, loading())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DARK_WIDE = "w707dp-h823dp-port-night-420dpi"
    }
}
