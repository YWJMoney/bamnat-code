package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.glance.appwidget.compose
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    private inner class Market : RecipeApi {
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
    }

    private fun style(s: WorkStyle) = c.settingsRepository.update { it.copy(workStyle = s) }

    /** 작품 상세 of [title] as it opens (PC-66: the 넣기 step on the bottom edge; PC-53 '넣어 본 분'; PC-59 the ✦ line in the user's words). */
    private fun detail(title: String, put: Boolean = false): Screen.() -> Unit = {
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

    private fun counting(base: String, body: (Asked) -> Unit) {
        val asked = Asked()
        val open0 = com.bamnat.app.ui.theme.OpenFonts.open
        val get0 = com.bamnat.app.ui.recipe.MarketFonts.get
        val base0 = com.bamnat.app.ui.recipe.MarketFonts.base
        com.bamnat.app.ui.theme.OpenFonts.open = { asked.urls += it; throw java.io.IOException("시험: 인터넷 없음") }
        com.bamnat.app.ui.recipe.MarketFonts.get = { asked.urls += it; null }
        com.bamnat.app.ui.recipe.MarketFonts.base = base
        com.bamnat.app.ui.recipe.MarketFonts.forget()
        try {
            body(asked)
        } finally {
            com.bamnat.app.ui.theme.OpenFonts.open = open0
            com.bamnat.app.ui.recipe.MarketFonts.get = get0
            com.bamnat.app.ui.recipe.MarketFonts.base = base0
            com.bamnat.app.ui.recipe.MarketFonts.forget()
            com.bamnat.app.ui.theme.AppFonts.loads.clear()
        }
    }

    /** 장터 목록 as it opens (테마 cards drawn in their card fonts). */
    private fun market(): Screen.() -> Unit = {
        Market().also { c.recipeApi = it }
        style(WorkStyle.SHIFT)
        content { RecipeListScreen(RecipeListViewModel(c), onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
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
        /** The release build's public files (gradle.properties `bamnat.remoteBaseUrl`). */
        const val PAGES = "https://ywjmoney.github.io/-/"
    }
}
