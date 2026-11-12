package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.glance.appwidget.compose
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
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
import com.bamnat.app.ui.recipe.RecipeDetailScreen
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.core.formula.FxPro
import com.bamnat.core.formula.Logic
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * 2.0.0 갈래 M (장터·스튜디오·레시피): the screens its first PR touches, light·dark × 글자 100%·200% × 접은·편 폭, for the PR's before/after
 * sheet (디자인 품질 조건 16가지). The works are the 1.39 장터 ones as they are in the repository (docs/recipes/market-139.json). Written
 * against what both the old and new screens show, so the same test draws the 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvmScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val works: List<Recipe> by lazy {
        val a = JSONArray(File("../docs/recipes/market-139.json").readText())
        (0 until a.length()).map { RecipeCodec.read(a.getJSONObject(it).getJSONObject("recipe").toString()) }
    }
    private fun work(title: String) = works.first { it.title == title }

    /** The 1.39 장터 as the server lists and shows it, nothing found for words no work has. */
    private inner class Market(private val makers: List<com.bamnat.app.recipe.MakerFaceItem> = emptyList()) : RecipeApi {
        private val codes = works.indices.associateBy { "MKT" + (10000 + it) }
        fun code(title: String) = codes.entries.first { works[it.value].title == title }.key
        private fun item(i: Int): RecipeItem = works[i].let { r ->
            val logic = r.pieces.firstOrNull { it.kind == PieceKind.LOGIC }?.let { Logic.from(it.values) }
            val fx = logic?.let { l -> (l.buttons.flatMap { FxPro.parts(l, it) } + l.rules.flatMap { FxPro.parts(l, it) }).map { it.key }.distinct() }.orEmpty()
            RecipeItem(codes.entries.first { it.value == i }.key, r.title, r.summary, r.styles, r.cover, "밤낮", 1, likes = 38, dislikes = 1, users = 214,
                kept = 81, official = true, publicData = false, parent = null, at = Instant.parse("2026-11-09T00:00:00Z"), showcase = true, scope = "theme", fx = fx)
        }
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) =
            RecipePage(works.indices.filter { q.isBlank() || q in works[it].title }.take(12).map(::item), null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc {
            val i = codes.getValue(code)
            return RecipeDoc(item(i), RecipeCodec.hash(works[i]), RecipeCodec.write(works[i]), "public", mine = false)
        }
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, RecipeCodec.hash(recipe), "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, RecipeCodec.hash(recipe), "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(38, 1, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String) = MyRecipes(0, 3, emptyList())
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
        override suspend fun monthMakers(style: WorkStyle?) = makers
    }

    private fun style(s: WorkStyle) = c.settingsRepository.update { it.copy(workStyle = s) }

    /** 작품 상세 of [title] as it opens (PC-66: the 넣기 step on the bottom edge; PC-53 '넣어 본 분'; PC-59 the ✦ line in the user's words). */
    private fun detail(title: String, put: Boolean = false, sees: String? = null): Screen.() -> Unit = {
        val market = Market().also { c.recipeApi = it }
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, market.code(title), PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        appears(title, exact = true)
        if (put) {
            // PC-59·60·34·33: the 넣기 창 says what changes in a few lines (and that the cells show colour alone, with '글자 남기기').
            tapText("이 테마 입기", exact = true)
            settle()
            appears("이렇게 바뀌어요", exact = true)
            sees?.let { check(appears(it)) { "넣기 창에 '$it'이 없어요" } }
        }
    }

    /** 장터 찾기 with nothing found (PC-64: one 빈 화면 with 칸이, a line and one way on). */
    private fun emptySearch(): Screen.() -> Unit = {
        Market().also { c.recipeApi = it }
        style(WorkStyle.SHIFT)
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        model.query("없는이름가나"); model.load()
        settle()
        appears("없는이름가나", exact = false)
    }

    /** 설정 › 수식·규칙·버튼 with 딥워크 데스크's 규칙·단추 (PC-61: the name, then what it does in a few words). */
    private fun rules(): Screen.() -> Unit = {
        val logic = Logic.from(work("딥워크 데스크").pieces.first { it.kind == PieceKind.LOGIC }.values)
        content { Surface { Column(Modifier.padding(16.dp)) { com.bamnat.app.logic.LogicEditor(logic, {}, phone = true, readOnly = true, onOpen = {}) } } }
        settle()
    }

    private fun wear(title: String): Screen.() -> Unit = {
        runBlocking { c.wardrobe.wear(work(title), "TESTWORK", own = false) }
        settle()
    }

    @Test fun detailLight() = shoot("rvm_01_detail_folded", FOLDED, detail("야간 회복 스테이션"))
    @Test fun detailDark() = shoot("rvm_02_detail_folded_dark", DARK, detail("야간 회복 스테이션"))
    @Test fun detailBig() = shoot("rvm_03_detail_folded_200", FOLDED, 2f, detail("야간 회복 스테이션"))
    @Test fun detailWide() = shoot("rvm_04_detail_unfolded", UNFOLDED, detail("야간 회복 스테이션"))
    @Test fun putLight() = shoot("rvm_05_put_folded", FOLDED, detail("야간 회복 스테이션", put = true))
    @Test fun putDarkBig() = shoot("rvm_06_put_folded_dark_200", DARK, 2f, detail("야간 회복 스테이션", put = true))
    @Test fun putWide() = shoot("rvm_07_put_unfolded", UNFOLDED, detail("야간 회복 스테이션", put = true))
    @Test fun putDeepWork() = shoot("rvm_08_put_deepwork_folded", FOLDED, detail("딥워크 데스크", put = true))
    @Test fun emptyLight() = shoot("rvm_09_market_empty_folded", FOLDED, emptySearch())
    @Test fun emptyDark() = shoot("rvm_10_market_empty_folded_dark", DARK, emptySearch())
    @Test fun emptyBig() = shoot("rvm_11_market_empty_folded_200", FOLDED, 2f, emptySearch())
    @Test fun rulesLight() = shoot("rvm_12_rules_folded", FOLDED, rules())
    @Test fun rulesDark() = shoot("rvm_13_rules_folded_dark", DARK, rules())
    @Test fun rulesBig() = shoot("rvm_14_rules_folded_200", FOLDED, 2f, rules())
    // 총괄 #462 검토(조건 2·12): 넣기 창은 채운 '넣기' 하나, 규칙 줄은 줄 전체를 누르고 오른쪽 화살표, 장터 위 막대는 200%에서 더 보기.
    @Test fun putBig() = shoot("rvm_24_put_folded_200", FOLDED, 2f, detail("야간 회복 스테이션", put = true))
    @Test fun putDark() = shoot("rvm_25_put_folded_dark", DARK, detail("야간 회복 스테이션", put = true))
    @Test fun rulesWide() = shoot("rvm_26_rules_unfolded", UNFOLDED, rules())
    @Test fun rulesDarkBig() = shoot("rvm_27_rules_folded_dark_200", DARK, 2f, rules())
    @Test fun marketTopBar() = counting(PAGES) {
        shoot("rvm_28_market_folded_200", FOLDED, 2f, market())
        shoot("rvm_29_market_folded_dark_200", DARK, 2f, market())
        shoot("rvm_30_market_unfolded", UNFOLDED, market())
        shoot("rvm_31_market_folded_dark", DARK, market())
    }

    /**
     * 2.0.0 [RVM-31] 총괄 HQ-42 (조건 1·12): 장터's first card begins in the top quarter of the window on a folded phone (35% before, 53% at
     * 200%), the 장터 opened on its own with its top bar and inside 마을 under 마을's name and tabs; above it, the box and one row of chips.
     * At 200% the 구간 are one-line chips of the same height (before, tiles where '탭별 작품' was four lines and the others two), each name
     * whole, and the box asks in two words.
     */
    @Test fun theWorksStartInTheTopQuarter() {
        shoot("rvm_48_market_top_folded", FOLDED) { market()(this); worksStartHigh("장터 접은 폭") }
        shoot("rvm_49_market_village_folded", FOLDED) { village()(this); worksStartHigh("마을 › 장터 접은 폭") }
        shoot("rvm_50_market_village_folded_dark", DARK) { village()(this); worksStartHigh("마을 › 장터 어두운") }
        shoot("rvm_51_market_halves_folded_200", FOLDED, 2f) {
            market()(this)
            worksStartHigh("장터 접은 폭 200%")
            val halves = listOf("테마", "탭별 작품").map { t ->
                checkNotNull(nodes().firstOrNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any { it.text == t } && n.boundsInWindow.width > 1f }) { "200%에서 ‘$t’가 안 보여요" }
            }
            val tall = halves.map { it.boundsInWindow.height }
            check(tall.max() - tall.min() < 1f) { "구간 칩의 높이가 서로 달라요: $tall" }
            check(appears("작품 찾기", exact = true)) { "200%에서 찾기 칸 안내가 짧지 않아요" }
        }
        // In 마을 at 200% 마을's own head (name row + tabs, 124dp, the same over 광장·테마·공방) is above; the part under it is 장터's.
        shoot("rvm_57_market_village_folded_dark_200", DARK, 2f) { village()(this); worksStartHigh("마을 › 장터 어두운 200%", share = 1 / 3f) }
        // With 이달의 제작자 (or people followed) the row of faces comes under the first row of works, still on the first screen.
        shoot("rvm_59_market_village_makers_folded", FOLDED) {
            village(makers = listOf(MakerFace("m1", "새벽 장인", 0xFF5B8DEF), MakerFace("m2", "야간 화가", 0xFFE0866E), MakerFace("m3", "달빛 공방", 0xFF6BBF8A)))(this)
            worksStartHigh("마을 › 장터, 이달의 제작자가 있을 때")
            check(appears("이달의 제작자", exact = true)) { "이달의 제작자 줄이 첫 화면에 없어요" }
            val faces = nodes().first { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any { it.text == "이달의 제작자" } }
            check(faces.boundsInWindow.top > cards().minOf { it.boundsInWindow.top }) { "이달의 제작자 줄이 첫 작품보다 위에 있어요" }
        }
    }

    /**
     * [RVM-31] HQ-42: picking 탭별 작품 slides the row so its 탭 are in sight (모든 탭 · 근무·급여 …), the 구간 picked first; picking 도구 brings
     * its own chips (전체 · ✦ 프로 기능 · 무료 · 종류). The 정렬 chip opens its menu of six; the open row (▾) lays the six out.
     */
    @Test fun aHalfPickedBringsItsChips() {
        shoot("rvm_52_market_tabs_folded", FOLDED) {
            market()(this)
            tapText("🗂 탭별 작품", exact = true)
            settle()
            check(appears("모든 탭", exact = true)) { "탭별 작품을 골라도 탭 칩이 화면 밖이에요" }
        }
        shoot("rvm_53_market_tools_folded_dark", DARK) {
            market()(this)
            tapText("🧰 도구", exact = true)
            settle()
            // 도구's own chips start in sight (전체 · ✦ 프로 기능 first, [MKC-04]); its 종류 follow them.
            check(appears("전체", exact = true) && appears("✦ 프로 기능", exact = true)) { "도구를 골라도 도구 칩이 화면 밖이에요" }
        }
        // 정렬 is one chip with a menu in the closed row; opened with ▾ the six orders lie in the row, so another order is two taps either way.
        shoot("rvm_58_market_open_row_phone", PHONE) {
            market()(this)
            check(!appears("좋은 순", exact = true)) { "닫힌 줄에 정렬 여섯 칩이 보여요" }
            tapText("칩 모두 펼쳐 보기", exact = true)
            settle()
            check(appears("좋은 순", exact = true) && appears("요즘 뜨는", exact = true)) { "펼친 줄에 정렬 여섯 칩이 없어요" }
        }
    }

    /** 2.0.0 [RVM-32] 총괄 HQ-43 (조건 1·12 넓은 화면): an unfolded phone shows four cards a row (two before), a folded one still two. */
    @Test fun anUnfoldedPhoneShowsFourARow() {
        shoot("rvm_54_market_grid_unfolded", UNFOLDED) { market()(this); check(firstRow() == 4) { "편 폭 첫 줄 카드 ${firstRow()}장" }; worksStartHigh("장터 편 폭") }
        shoot("rvm_55_market_grid_unfolded_dark_200", UNFOLDED_DARK, 2f) {
            market()(this); check(firstRow() == 4) { "편 폭 200% 첫 줄 카드 ${firstRow()}장" }; worksStartHigh("장터 편 폭 200%")
        }
        shoot("rvm_56_market_grid_folded", FOLDED) { market()(this); check(firstRow() == 2) { "접은 폭 첫 줄 카드 ${firstRow()}장" } }
        org.junit.Assert.assertEquals(listOf(2, 3, 4, 6), listOf(com.bamnat.app.ui.recipe.marketCols(337, false), com.bamnat.app.ui.recipe.marketCols(337, true),
            com.bamnat.app.ui.recipe.marketCols(675, false), com.bamnat.app.ui.recipe.marketCols(675, true)))
    }

    /**
     * 2.0.0 [RVM-34] 총괄 HQ-72 (조건 4·12): at 200% a 장터 card's numbers line ('♥ 38 · 넣어 본 분 214 · 오래 써요 81%') went over three lines
     * in a folded phone's tile. Now it keeps the two numbers that matter ('♥ 38 · 214명') and is two lines at most, on a light and a dark page.
     */
    @Test fun theNumbersLineStaysWithinTwoLines() {
        fun Screen.numbers(): List<Pair<String, Int>> = nodes(merged = false).mapNotNull { n ->
            val action = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action ?: return@mapNotNull null
            val out = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            if (action(out) != true) return@mapNotNull null
            out.firstOrNull { it.layoutInput.text.text.startsWith("♥") }?.let { it.layoutInput.text.text.replace('\u00A0', ' ') to it.lineCount }
        }
        for ((name, qualifiers) in listOf("rvm_60_market_numbers_folded_200" to FOLDED, "rvm_61_market_numbers_folded_dark_200" to DARK)) {
            shoot(name, qualifiers, 2f) {
                market()(this)
                val lines = numbers()
                check(lines.isNotEmpty()) { "숫자 줄이 안 보여요" }
                check(lines.all { it.first == "♥ 38 · 214명" }) { "200%의 숫자 줄은 두 숫자만: $lines" }
                check(lines.all { it.second <= 2 }) { "200%에서 두 줄을 넘어요: $lines" }
                // Read aloud whole, in words, at any size.
                val said = nodes(merged = false).mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString() }
                check("좋아요 38 · 넣어 본 분 214명 · 7일 뒤에도 쓰는 분 81%" in said) { "읽어 주는 숫자 줄: ${said.filter { "좋아요" in it }}" }
            }
        }
        shoot("rvm_62_market_numbers_folded", FOLDED) {
            market()(this)
            check(numbers().any { it.first == "♥ 38 · 넣어 본 분 214 · 오래 써요 81%" }) { "100%에서는 그대로: ${numbers()}" }
            // A wrapped line breaks between the numbers, not inside one ('오래 / 써요 81%' before).
            check(numbers().all { it.second <= 2 }) { "100%에서 두 줄을 넘어요: ${numbers()}" }
        }
    }

    private val cardWords = setOf("테마 보기", "작품 보기", "도구 보기")
    private fun Screen.cards() = nodes().filter { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label in cardWords && n.boundsInWindow.height > 1f }
    private fun Screen.firstRow(): Int { val top = cards().minOf { it.boundsInWindow.top }; return cards().count { kotlin.math.abs(it.boundsInWindow.top - top) < 2f } }

    /** 조건 1: the first card begins within the top quarter of the window ([share]). */
    private fun Screen.worksStartHigh(name: String, share: Float = 0.25f) {
        val all = nodes()
        val height = all.maxOf { it.boundsInWindow.bottom }
        val top = cards().minOf { it.boundsInWindow.top }
        println("[RVM-31] $name: ${top.toInt()} / ${height.toInt()} = ${"%.1f".format(top / height * 100)}%")
        check(top <= height * share) { "$name: 첫 작품이 화면 위 ${(share * 100).toInt()}% 안에서 시작해야 해요 (${top.toInt()} / ${height.toInt()} = ${(top / height * 100).toInt()}%)" }
    }

    @Suppress("FunctionName")
    private fun MakerFace(id: String, nick: String, color: Long) = com.bamnat.app.recipe.MakerFaceItem(id, nick, null, color)

    /** 마을 › 장터, as the bottom bar's 마을 opens it on 장터: 마을's name and tabs above the list. */
    private fun village(makers: List<com.bamnat.app.recipe.MakerFaceItem> = emptyList()): Screen.() -> Unit = {
        Market(makers).also { c.recipeApi = it }
        style(WorkStyle.SHIFT)
        app.getSharedPreferences("village_ui", 0).edit().putString("section", "SHARE").commit()
        val model = RecipeListViewModel(c)
        content {
            com.bamnat.app.ui.hub.HubScreen(c, WorkStyle.SHIFT, talk = {}, share = {
                RecipeListScreen(model, onBack = null, onOpen = {}, onMake = {}, onMine = {}, title = "장터", onSwipe = {})
            }, onMake = {}, onMine = {}, onRequests = {}, onCode = {}, onStudio = {})
        }
        settle()
        settle()
    }
    // 2.0.0 [RVM-22] RCP-05: 큰 시계 기상 (울림 5분) on a phone that rings 10분: the 넣기 창 names what would make the alarm weaker and
    // leaves it out unless '약하게 하는 설정도 넣기' is on.
    private val weakAlarm = detail("큰 시계 기상", put = true, sees = "알람을 약하게 하는 설정은 빼고 넣어요")
    @Test fun putWeakAlarm() = shoot("rvm_40_put_weak_alarm_folded", FOLDED, weakAlarm)
    @Test fun putWeakAlarmDarkBig() = shoot("rvm_41_put_weak_alarm_folded_dark_200", DARK, 2f, weakAlarm)
    @Test fun putWeakAlarmWide() = shoot("rvm_42_put_weak_alarm_unfolded", UNFOLDED, weakAlarm)
    @Test fun putWeakAlarmBig() = shoot("rvm_43_put_weak_alarm_folded_200", FOLDED, 2f, weakAlarm)

    /**
     * 2.0.0 [RVM-26] RCP-08: 내 서랍 with a theme the 입었던 테마 limit let go — the line '자동으로 정리한 것 1건 보기' under the drawer and,
     * [open], its sheet in the drawer's words (not the 가계부's) with 되살리기.
     */
    private fun drawerTidy(open: Boolean): Screen.() -> Unit = {
        val today = com.bamnat.core.time.Today.date().toEpochDay()
        fun wear(title: String, code: String) = com.bamnat.core.recipe.ThemeLease.Wear(title, code, RecipeCodec.write(work(title)), today - 3, today + 27)
        val lease = com.bamnat.core.recipe.ThemeLease(history = listOf(wear("야간 회복 스테이션", "MKT10001"), wear("딥워크 데스크", "MKT10002")))
        app.getSharedPreferences("theme_wardrobe", 0).edit().putString("lease", com.bamnat.core.recipe.ThemeLease.encode(lease)).commit()
        c.wardrobe.reload()
        val gone = com.bamnat.core.recipe.ThemeLease(history = listOf(wear("큰 시계 기상", "MKT10003")))
        runBlocking {
            c.autoTidy.add(com.bamnat.app.data.AutoTidy.AREA_RECIPE, PersonalCalendar.MAIN, "큰 시계 기상",
                "입었던 테마가 30개를 넘어 가장 오래 전에 입은 것을 서랍에서 뺐어요", com.bamnat.app.recipe.ThemeWardrobe.KIND_PUSHED,
                com.bamnat.core.recipe.ThemeLease.encode(gone))
        }
        content {
            Surface {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.bamnat.app.ui.recipe.ThemeDrawer(WorkStyle.SHIFT, onFind = {}, onFix = {}, onStudio = {}, onRecipe = {}, onUpload = {})
                }
            }
        }
        settle()
        check(appears("자동으로 정리한 것 1건 보기", exact = true)) { "서랍 아래 정리 줄이 없어요" }
        if (open) {
            tapText("자동으로 정리한 것 1건 보기", exact = true)
            settle()
            check(appears("입었던 테마가 30개를 넘어 서랍에서 뺀 것이에요")) { "정리함 글이 테마의 말이 아니에요" }
        }
    }
    @Test fun drawerTidyLine() = shoot("rvm_44_drawer_tidy_folded", FOLDED, drawerTidy(open = false))
    @Test fun drawerTidySheet() = shoot("rvm_45_drawer_tidy_sheet_folded", FOLDED, drawerTidy(open = true))
    @Test fun drawerTidySheetDarkBig() = shoot("rvm_46_drawer_tidy_sheet_folded_dark_200", DARK, 2f, drawerTidy(open = true))
    @Test fun drawerTidySheetWide() = shoot("rvm_47_drawer_tidy_sheet_unfolded", UNFOLDED, drawerTidy(open = true))

    @Test fun nightDark() = shoot("rvm_15_night_recovery_folded_dark", DARK, wear("야간 회복 스테이션"))
    @Test fun nightBig() = shoot("rvm_16_night_recovery_folded_200", FOLDED, 2f, wear("야간 회복 스테이션"))
    @Test fun nightWide() = shoot("rvm_17_night_recovery_unfolded", UNFOLDED, wear("야간 회복 스테이션"))
    @Test fun deepWorkWide() = shoot("rvm_18_deepwork_tabs_unfolded", UNFOLDED, wear("딥워크 데스크"))
    @Test fun deepWorkBig() = shoot("rvm_19_deepwork_tabs_folded_200", FOLDED, 2f, wear("딥워크 데스크"))

    /** PC-45: 딥워크 데스크's 단추 위젯 small, tall and wide-and-tall (요일 under the 7-day bars, buttons as tall as the space). */
    @Test fun buttonWidget() = runBlocking {
        // The 규칙·단추 as 넣기 puts them in (입기 alone carries the looks).
        val logic = Logic.from(work("딥워크 데스크").pieces.first { it.kind == PieceKind.LOGIC }.values)
        c.settingsRepository.update { it.copy(logic = Logic.encode(logic)) }
        logic.buttons.filter { it.place == com.bamnat.core.formula.ButtonPlace.WIDGET }.take(1).forEach { b ->
            repeat(2) { com.bamnat.app.logic.FxOutside.press(c, b.id) }
        }
        for ((name, dims) in listOf("small" to (180 to 110), "tall" to (180 to 330), "wide_tall" to (340 to 330))) {
            val (wDp, hDp) = dims
            @OptIn(androidx.glance.ExperimentalGlanceApi::class)
            val rv = com.bamnat.app.widget.FxButtonWidget().compose(app, size = androidx.compose.ui.unit.DpSize(wDp.dp, hDp.dp))
            val density = app.resources.displayMetrics.density
            val w = (wDp * density).toInt()
            val h = (hDp * density).toInt()
            val parent = android.widget.FrameLayout(app)
            parent.addView(rv.apply(app, parent), android.widget.FrameLayout.LayoutParams(w, h))
            parent.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { cv: Canvas -> cv.drawColor(0xFF7A8BA8.toInt()); parent.draw(cv) }
            File("build/screenshots/rvm_20_fx_widget_$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) }
        }
    }

    /** 2.0.0 [RVM-21] MKT-32 Every font address the 장터 asks for while [body] runs, card fonts on [base] (blank: a PC build without public files). */
    private class Asked {
        val urls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        val google get() = urls.filter { "fonts.googleapis.com" in it || "fonts.gstatic.com" in it }
        val ours get() = urls.filter { it.startsWith(PAGES + "remote/card-fonts/") }
    }

    /** Counts where the fonts are asked for; [after313] stands for 처리방침 3.13's effective day having come (the `market.cardfonts` row on). */
    private fun counting(base: String, after313: Boolean = true, body: (Asked) -> Unit) {
        val asked = Asked()
        val open0 = com.bamnat.app.ui.theme.OpenFonts.open
        val get0 = com.bamnat.app.ui.recipe.MarketFonts.get
        val base0 = com.bamnat.app.ui.recipe.MarketFonts.base
        val allowed0 = com.bamnat.app.ui.recipe.MarketFonts.allowed
        com.bamnat.app.ui.theme.OpenFonts.open = { asked.urls += it; throw java.io.IOException("시험: 인터넷 없음") }
        com.bamnat.app.ui.recipe.MarketFonts.get = { asked.urls += it; null }
        com.bamnat.app.ui.recipe.MarketFonts.base = base
        if (after313) com.bamnat.app.ui.recipe.MarketFonts.allowed = { true }
        com.bamnat.app.ui.recipe.MarketFonts.forget()
        try {
            body(asked)
        } finally {
            com.bamnat.app.ui.theme.OpenFonts.open = open0
            com.bamnat.app.ui.recipe.MarketFonts.get = get0
            com.bamnat.app.ui.recipe.MarketFonts.base = base0
            com.bamnat.app.ui.recipe.MarketFonts.allowed = allowed0
            com.bamnat.app.ui.recipe.MarketFonts.forget()
            com.bamnat.app.ui.theme.AppFonts.loads.clear()
        }
    }

    /** 장터 목록 as it opens (테마 cards drawn in their card fonts). */
    private fun market(): Screen.() -> Unit = {
        Market().also { c.recipeApi = it }
        style(WorkStyle.SHIFT)
        // Made outside the composable (lint ViewModelConstructorInComposable), as the other screenshot tests do.
        val vm = RecipeListViewModel(c)
        content { RecipeListScreen(vm, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        settle()
    }

    /**
     * MKT-32 (사용자 2026-11-12 카드 '우리 쪽에서 글꼴 내주기': 장터 모습은 그대로, Google 요청은 글꼴을 고를 때만): 장터 목록, 작품 열기와
     * 예시로 보기(입어 보기) never ask Google Fonts; the card fonts come from our public files.
     */
    @Test fun theMarketAsksGoogleNothing() = counting(PAGES) { asked ->
        shoot("rvm_21_fonts_market", FOLDED, market())
        shoot("rvm_22_fonts_try_on", FOLDED) {
            detail("야간 회복 스테이션")(this)
            tapText("예시로 보기", exact = true)
            settle()
        }
        org.junit.Assert.assertEquals(asked.google.toString(), 0, asked.google.size)
        org.junit.Assert.assertTrue(asked.urls.toString(), asked.ours.isNotEmpty())
        // Only the fonts this app was built with, each at its place for these letters.
        org.junit.Assert.assertTrue(asked.urls.toString(), asked.urls.all { it.startsWith(PAGES + "remote/card-fonts/" + com.bamnat.app.ui.recipe.CardFontPins.VERSION + "/") })
    }

    /** MKT-32 내 앱에 입혀 보기 (the real app wearing the theme in memory): its fonts as card fonts too, the whole font only with 입기. */
    @Test fun tryingOnTheRealAppAsksGoogleNothing() = counting(PAGES) { asked ->
        style(WorkStyle.SHIFT)
        val r = work("야간 회복 스테이션")
        val all = com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet()
        org.junit.Assert.assertTrue(runBlocking {
            com.bamnat.app.ui.recipe.LiveTryOn.start(c, r, own = false, picked = all, tab = com.bamnat.core.layout.TabOrder.CALENDAR,
                from = com.bamnat.app.ui.recipe.LiveTryOn.From.Detail("MKT10000"))
        })
        try {
            shoot("rvm_23_fonts_live_try", FOLDED) { settle(); settle() }
        } finally {
            com.bamnat.app.ui.recipe.LiveTryOn.drop(c)
        }
        org.junit.Assert.assertEquals(asked.google.toString(), 0, asked.google.size)
        org.junit.Assert.assertTrue(asked.urls.toString(), asked.ours.isNotEmpty())
    }

    /** MKT-32 Picking a font still brings it whole from Google Fonts (what the policy says); a PC build (no public files) asks nothing. */
    @Test fun pickingAFontAsksGoogleAndAPcBuildAsksNothing() {
        counting(PAGES) { asked ->
            runBlocking { com.bamnat.app.ui.theme.AppFonts.fetch(app, "jua") }
            org.junit.Assert.assertTrue(asked.urls.toString(), asked.google.isNotEmpty())
        }
        counting("") { asked ->
            val got = runBlocking { com.bamnat.app.ui.recipe.MarketFonts.ready(app, setOf("jua", "orbit", "notosanskr")) }
            org.junit.Assert.assertTrue(got.keys.toString(), got.isEmpty())
            org.junit.Assert.assertEquals(asked.urls.toString(), 0, asked.urls.size)
        }
    }

    /**
     * MKT-32 (총괄 11-12): before 처리방침 3.13's 7-day line (PolicyConsent.REVISION_EFFECTIVE, by the server's clock) the phone asks our
     * public files nothing — the app's own wiring, with no server time known here, keeps `market.cardfonts` off — and the cards draw in
     * the phone's fonts; Google is not asked either. (The day itself is FlagEvalTest.cardFontsWaitForTheSevenDayLineOf313.)
     */
    @Test fun beforeThe313DayTheMarketAsksNothing() = counting(PAGES, after313 = false) { asked ->
        org.junit.Assert.assertFalse(c.flags.on(com.bamnat.core.flags.FlagCatalog.CARD_FONTS))
        shoot("rvm_32_fonts_market_before_313", FOLDED, market())
        val got = runBlocking { com.bamnat.app.ui.recipe.MarketFonts.ready(app, setOf("jua", "orbit")) }
        org.junit.Assert.assertTrue(got.keys.toString(), got.isEmpty())
        org.junit.Assert.assertEquals(asked.urls.toString(), 0, asked.urls.size)
    }

    /** MKT-32 A card font is used only when its bytes are the ones made for this app: a changed file on the public site is never opened. */
    @Test fun aCardFontIsUsedOnlyWhenItIsTheOneMade() = counting(PAGES) { asked ->
        val font = File("src/test/resources/font-files/sold-test.ttf").readBytes()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(font).joinToString("") { "%02x".format(it) }
        val pins0 = com.bamnat.app.ui.recipe.MarketFonts.pins
        com.bamnat.app.ui.recipe.MarketFonts.get = { asked.urls += it; font }
        try {
            com.bamnat.app.ui.recipe.MarketFonts.pins = mapOf("jua-r" to "0".repeat(64))
            org.junit.Assert.assertTrue(runBlocking { com.bamnat.app.ui.recipe.MarketFonts.ready(app, setOf("jua")) }.isEmpty())
            com.bamnat.app.ui.recipe.MarketFonts.forget()
            com.bamnat.app.ui.recipe.MarketFonts.pins = mapOf("jua-r" to sha)
            org.junit.Assert.assertEquals(setOf("jua"), runBlocking { com.bamnat.app.ui.recipe.MarketFonts.ready(app, setOf("jua")) }.keys)
            org.junit.Assert.assertEquals(listOf(PAGES + "remote/card-fonts/" + com.bamnat.app.ui.recipe.CardFontPins.VERSION + "/jua-r.ttf"), asked.urls.distinct().take(1))
        } finally {
            com.bamnat.app.ui.recipe.MarketFonts.pins = pins0
        }
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_DARK = "w707dp-h823dp-port-night-420dpi"
        /** The release build's public files (gradle.properties `bamnat.remoteBaseUrl`). */
        const val PAGES = "https://ywjmoney.github.io/-/"
    }
}
