package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeRequest
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.app.ui.recipe.MakerScreen
import com.bamnat.app.ui.recipe.MakerViewModel
import com.bamnat.app.ui.recipe.RecipeDetailScreen
import com.bamnat.app.ui.recipe.RequestsScreen
import com.bamnat.app.ui.recipe.RequestsViewModel
import com.bamnat.app.ui.recipe.RecipeDetailViewModel
import com.bamnat.app.ui.recipe.RecipeListScreen
import com.bamnat.app.ui.recipe.RecipeListViewModel
import com.bamnat.app.ui.recipe.RecipeMakeScreen
import com.bamnat.app.ui.recipe.RecipeMakeViewModel
import com.bamnat.app.ui.recipe.RecipeMineScreen
import com.bamnat.app.ui.recipe.RecipeMineViewModel
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.recipe.RecipePieces
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

    private open class Starters : RecipeApi {
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
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(
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
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
        settle()
        assertTrue(appears("요즘 뜨는", exact = true))
        assertTrue(appears("야간 파랑", exact = true))
        // A 알바 recipe is not in the 교대 list.
        assertTrue(gone("주휴", exact = false))
    }

    @Test fun theHourlyListHasItsOwn() = shoot("1341_rcp_list_hourly", PHONE) {
        val api = starters()
        style(WorkStyle.HOURLY)
        val model = RecipeListViewModel(c)
        content { RecipeListScreen(model, onBack = {}, onOpen = {}, onMake = {}, onMine = {}) }
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
        // 보안 점검 M9 (PC 확인 7525ccff): put on again after 되돌리기, this phone is not counted a second time in 쓰는 분.
        val r = api.recipes[0].first
        val again = runBlocking { c.recipes.apply(r, api.code(0), 1, PersonalCalendar.MAIN, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap()) }
        assertTrue("다시 들어갔어요", again != null)
        assertEquals("한 휴대폰은 한 번만 세요", 1, api.applied)
    }

    /**
     * PC 확인 20ab0254 (require에서 신고하면 창만 닫혀요): the answer to a report stays on screen, whether the server turned it
     * away for Play Integrity or took it. The line was cleared before it was shown, which took the snackbar down at once.
     */
    @Test fun aReportSaysWhatHappened() = shoot("1342b_rcp_detail_flag", PHONE) {
        var refuse = true
        var flags = 0
        val api = object : Starters() {
            override suspend fun flag(device: String, code: String, reason: String) {
                if (refuse) throw com.bamnat.app.company.CompanyException("integrity")
                flags++
            }
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, api.code(0), PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        vm.flag("spam")
        settle()
        settle()
        assertTrue("막히면 올리기와 같은 안내", appears("Play 스토어에서 받은 밤낮으로만", exact = false))
        assertEquals("막힌 신고는 신고한 것이 아니에요", false, vm.state.value.flagged)
        // PC 확인 7525ccff: the line has its own 닫기 (✕), so it need not cover what is under it.
        assertTrue(appears("Dismiss", exact = true) || appears("닫기", exact = true))
        refuse = false
        compose.mainClock.advanceTimeBy(10_000)
        settle()
        vm.flag("spam")
        settle()
        settle()
        assertEquals(1, flags)
        assertTrue("되면 신고했어요", appears("신고했어요", exact = false))
        // The ⋮ menu says it was reported after the line has gone (a popup window these shots do not reach, so its state).
        assertTrue(vm.state.value.flagged)
    }

    /** PC 확인 8773e3ae (되돌리기 뒤에도 '내 달력에 넣어 둔 레시피예요'가 남아요): taking it back takes the line away too. */
    @Test fun takingItBackTakesTheLineAway() = shoot("1342c_rcp_detail_undone", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, api.code(0), PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        tapText("넣기", exact = true)
        settle()
        assertTrue(vm.state.value.appliedAt != null)
        // PC 확인 보안 7차 (넣었는데 쓰는 분 0명): this phone is one of them now.
        assertEquals(1, vm.state.value.usersPlus)
        // The 적용했어요 line with its own 되돌리기 goes after its ten seconds; then back to the top, where the line sits.
        compose.mainClock.advanceTimeBy(12_000)
        settle()
        repeat(6) { scrollDown(400) }
        settle()
        assertTrue(appears("내 달력에 넣어 둔 레시피예요", exact = true))
        // 되돌리기 on the line, then on its question.
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        assertTrue(appears("넣기 전으로 되돌릴까요?", exact = true))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        settle()
        assertEquals(null, vm.state.value.appliedAt)
        assertTrue("되돌린 뒤 줄이 없어요", gone("내 달력에 넣어 둔 레시피예요", exact = true))
        assertTrue(runBlocking { c.recipes.inUse() }.none { it.code == api.code(0) })
    }

    /** PC 확인 8773e3ae: the same from the 적용했어요 line's own 되돌리기. */
    @Test fun takingItBackFromTheSnackbar() = shoot("1342d_rcp_detail_undone_snack", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, api.code(0), PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        tapText("넣기", exact = true)
        settle()
        assertTrue(appears("적용했어요", exact = false))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        settle()
        assertTrue("되돌린 뒤: ${vm.state.value.appliedAt} ${vm.state.value.message}", vm.state.value.appliedAt == null)
        assertTrue(runBlocking { c.recipes.inUse() }.none { it.code == api.code(0) })
        repeat(6) { scrollDown(400) }
        settle()
        assertTrue("되돌린 뒤 줄이 없어요", gone("내 달력에 넣어 둔 레시피예요", exact = true))
    }

    /** PC 확인 8773e3ae: put on twice with other pieces, 되돌리기 on the page takes the whole recipe back, not only the newer half. */
    @Test fun takingItBackTakesBothPutOns() = shoot("1342e_rcp_detail_undone_twice", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        // 야간 파랑 put on now, and the same recipe still on from a day ago with another piece (a row as an older 넣기 leaves it).
        val code = api.code(0)
        val r = api.recipes[0].first
        val first = runBlocking {
            c.recipes.apply(r, code, 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(r.pieces[0].kind, RecipePieces.Mode.REPLACE)), emptyMap())
        }
        assertTrue(first != null)
        runBlocking {
            val dao = c.database.recipeDao()
            val e = dao.appliedById(first!!)!!
            dao.insertApplied(e.copy(id = 0, pieces = "calTheme", before = "{}", appliedAt = e.appliedAt - 86_400_000L))
        }
        val vm = RecipeDetailViewModel(c, code, PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        assertTrue(appears("내 달력에 넣어 둔 레시피예요", exact = true))
        assertEquals("두 번 넣은 것이 모두 남아 있어요", 2, runBlocking { c.database.recipeDao().applied() }.count { it.code == code && it.undoneAt == null && it.replacedAt == null })
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        // PC 확인 7525ccff: the question lists what goes back.
        assertTrue(appears("이 레시피를 2번 넣었어요. 모두 넣기 전으로 돌아가요", exact = true))
        assertTrue(appears("근무 꾸미기", exact = false))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        settle()
        assertEquals(null, vm.state.value.appliedAt)
        assertTrue("되돌린 뒤 줄이 없어요", gone("내 달력에 넣어 둔 레시피예요", exact = true))
        assertTrue(runBlocking { c.recipes.inUse() }.none { it.code == code })
        // Opened again, the page has no line to come back with.
        val again = RecipeDetailViewModel(c, code, PersonalCalendar.MAIN)
        settle()
        assertTrue(again.state.value.doc != null)
        assertEquals(null, again.state.value.appliedAt)
    }

    /**
     * PC 확인 보안 6차 d00db8a4 (조각을 바꿔 두세 번 넣어도 되돌리기 창에 넣은 것별 줄이 없어요): 파스텔 근무색's 달력 테마 is laid on
     * the theme worn ([MKX-02] 얹기) and leaves no record, so the question listed nothing and 되돌리기 left it on. Now it is a line of
     * the question and comes off with the rest.
     */
    @Test fun takingItBackTakesTheTabLaidOnToo() = shoot("1342f_rcp_detail_undo_laid", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val i = api.recipes.indexOfFirst { it.first.title == "파스텔 근무색" }
        val code = api.code(i)
        val vm = RecipeDetailViewModel(c, code, PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        vm.apply { }
        settle()
        settle()
        assertTrue("달력 탭이 테마 위에 얹혔어요", c.wardrobe.overlays.value.any { it.code == code })
        assertEquals(2, vm.state.value.undoable.size)
        // Its line (with 되돌리기 now, as the tab comes off here too) goes after ten seconds; then the card's.
        compose.mainClock.advanceTimeBy(12_000)
        settle()
        repeat(6) { scrollDown(400) }
        settle()
        assertTrue(appears("내 달력에 넣어 둔 레시피예요", exact = true))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        assertTrue(appears("이 레시피를 2번 넣었어요. 모두 넣기 전으로 돌아가요", exact = true))
        assertTrue(appears("테마 위에 얹음 · ‘달력’ 탭", exact = false))
        assertTrue(appears("넣음 · 근무 꾸미기", exact = false))
        tapText("되돌리기", exact = true, scrolls = 0)
        settle()
        settle()
        assertTrue("얹은 탭도 빠졌어요", c.wardrobe.overlays.value.none { it.code == code })
        assertTrue(runBlocking { c.recipes.inUse() }.none { it.code == code })
        assertEquals(null, vm.state.value.laidAt)
        assertTrue(gone("내 달력에 넣어 둔 레시피예요", exact = true))
    }

    /** PC 확인 보안 8차 (넣어도 쓰는 분 0명): a recipe of one tab is laid on the theme with no record, and is a use all the same. */
    @Test fun aTabLaidOnCountsItsUser() = shoot("1342g_rcp_detail_laid_counted", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val i = api.recipes.indexOfFirst { it.first.title == "토·일 강조 달력" }
        val code = api.code(i)
        val vm = RecipeDetailViewModel(c, code, PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        vm.apply { }
        settle()
        settle()
        assertTrue(c.wardrobe.overlays.value.any { it.code == code })
        assertEquals(1, api.applied)
        assertEquals(1, vm.state.value.usersPlus)
        // Once a phone: laid on again, not counted again.
        vm.apply { }
        settle()
        assertEquals(1, api.applied)
    }

    @Test fun theConfirmShowsWhatChanges() = shoot("1343_rcp_detail_confirm", "w360dp-h780dp-port-night-450dpi") {
        val api = starters()
        style(WorkStyle.SHIFT)
        val model = RecipeDetailViewModel(c, api.code(0))
        content { RecipeDetailScreen(model, onBack = {}, onRemake = {}) }
        settle()
        tapText("내 달력에 넣기", exact = false)
        settle()
        // [MKX-02] Where it is undone: 내 것 › 받은 레시피, or 마을 › 테마 for a tab laid on the theme.
        assertTrue(appears("마을 › 공방 › 내 것 › 받은 레시피에서 되돌", exact = false) || appears("마을 › 테마에서", exact = false))
    }

    /** PC 확인 보안 7차 ("글자 주→주, 색 바뀜"): the put-on question says only what changes. */
    @Test fun theConfirmSaysOnlyWhatChanges() = shoot("1343b_rcp_detail_confirm_color", PHONE) {
        val api = starters()
        style(WorkStyle.SHIFT)
        val i = api.recipes.indexOfFirst { it.first.title == "파스텔 근무색" }
        val model = RecipeDetailViewModel(c, api.code(i))
        content { RecipeDetailScreen(model, onBack = {}, onRemake = {}) }
        settle()
        repeat(3) { scrollDown(400) }
        settle()
        // The lines of what it changes, on the page (the same ones the question repeats).
        assertTrue(appears("휴무: 색만 바뀜", exact = false))
        assertTrue(gone("글자 휴→휴", exact = false))
        assertTrue(gone("글자 주→주", exact = false))
    }

    @Test fun makingStartsWithWhatToShare() = shoot("1344_rcp_make_step1", PHONE) {
        starters()
        style(WorkStyle.SHIFT)
        val model = RecipeMakeViewModel(c)
        content { RecipeMakeScreen(model, onBack = {}, onOpen = {}) }
        settle()
        assertTrue(appears("레시피 만들기 1/5 · 꾸미기", exact = true))
    }

    /** PC 확인 d263030e: a draft opened at 마치기 goes back to 이름·표지 by its chip; a step ahead waits for the name, as 다음 does. */
    @Test fun aStepChipGoesBackAndWaitsLikeNext() = shoot("1344b_rcp_make_chips", PHONE) {
        starters()
        style(WorkStyle.SHIFT)
        val model = RecipeMakeViewModel(c)
        content { RecipeMakeScreen(model, onBack = {}, onOpen = {}) }
        settle()
        // After the model has read the phone, as a hand on 꾸미기 would.
        model.setValue(PieceKind.CAL_VIEW, "size", "tall")
        model.go(5)
        settle()
        assertTrue(model.state.value.recipe.pieces.isNotEmpty())
        assertTrue(appears("레시피 만들기 5/5 · 마치기", exact = true))
        tapText("이름·표지", exact = true)
        settle()
        assertEquals(3, model.state.value.step)
        tapText("넣을 값", exact = true)
        settle()
        assertEquals(2, model.state.value.step)
        tapText("마치기", exact = true)
        settle()
        assertEquals("이름이 없으면 3단계를 넘지 않아요", 2, model.state.value.step)
        tapText("이름·표지", exact = true)
        settle()
        assertEquals("앞 단계는 다음처럼 갈 수 있어요", 3, model.state.value.step)
    }

    @Test fun settingsOfTheBoard() = shoot("1345_rcp_mine", PHONE) {
        starters()
        val model = RecipeMineViewModel(c)
        content { RecipeMineScreen(model, onBack = {}, onOpen = {}, onDraft = {}, onList = {}) }
        settle()
        assertTrue(appears("장터 쓰기", exact = true))
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

    // ---- 1.35 [RCX-21] ----

    /** [RCX-02][RCX-03] A recipe put on, a new version with 야간's colour changed: offered, picked, put in. */
    @Test fun aNewVersionIsOfferedAndPutIn() = shoot("1347_rcx_update", PHONE) {
        val base = Starters()
        val i = base.recipes.indexOfFirst { (r, data) -> !data && r.pieces.any { it.kind == PieceKind.SHIFT_LOOK } }
        val old = base.recipes[i].first
        val look = old.pieces.first { it.kind == PieceKind.SHIFT_LOOK }
        @Suppress("UNCHECKED_CAST") val items = look.values["items"] as List<Map<String, Any?>>
        val newer = old.copy(pieces = old.pieces.map { p ->
            if (p.kind != PieceKind.SHIFT_LOOK) p else p.copy(values = p.values + ("items" to items.mapIndexed { n, m -> if (n == 0) m + ("color" to 0xFF123456L) else m }))
        })
        val api = object : Starters() {
            override suspend fun versions(codes: List<String>) = codes.associateWith { 2 }
            override suspend fun show(code: String, device: String?, version: Int?) =
                if (version == 2) super.show(code, device, null).copy(body = RecipeCodec.write(newer), hash = RecipeCodec.hash(newer)) else super.show(code, device, version)
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        runBlocking { c.recipes.apply(old, api.code(i), 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.SHIFT_LOOK, com.bamnat.app.recipe.RecipePieces.Mode.REPLACE)), emptyMap()) }
        val model = RecipeMineViewModel(c)
        content { RecipeMineScreen(model, onBack = {}, onOpen = {}, onDraft = {}, onList = {}) }
        settle()
        assertTrue(appears("새 판 v2 있어요", exact = false))
        tapText("새 판 v2 있어요", exact = false)
        settle()
        assertTrue(appears("v2에서 바뀐 점", exact = false))
        tapText("고른 것 반영", exact = true)
        settle()
        val now = runBlocking { c.recipes.applied.first() }
        assertEquals("새 판이 한 번 더 들어가요", 2, now.size)
    }

    /** [RCX-05][RCX-06] 만든 분 페이지: face, numbers, badges, recipes; following waits for 3.8. */
    @Test fun aMakersPage() = shoot("1348_rcx_maker", PHONE) {
        val api = object : Starters() {
            override suspend fun maker(device: String?, id: String) = MakerPage(id, "밤샘간호사", "🦉", 0xFF3949ABL, mine = false,
                recipes = (0 until 3).map(::item), likes = 112, users = 340, followers = 18, following = false,
                badges = listOf("first" to "첫 레시피", "users100" to "쓰는 분 100명"))
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        val model = MakerViewModel(c, "a1b2c3d4e5f6")
        content { MakerScreen(model, onBack = {}, onOpen = {}) }
        settle()
        assertTrue(appears("밤샘간호사", exact = false))
        // [PRF-01] 1.36: 테마 | 기능별 탭 | 모음, then the badges at the foot.
        assertTrue(appears("모음 0", exact = true))
        scrollUp(900)
        settle()
        assertTrue(appears("🏅 쓰는 분 100명", exact = true))
    }

    /** [RCX-04] 요청 판: wishes with their likes and where they stand. */
    @Test fun theRequestBoard() = shoot("1349_rcx_requests", PHONE) {
        val at = Instant.parse("2026-10-29T00:00:00Z")
        val api = object : Starters() {
            override suspend fun requests(device: String?, style: WorkStyle?) = listOf(
                RecipeRequest("q000000000001", WorkStyle.SHIFT, "간호사 3교대 근무별 알람 세트 있었으면 좋겠어요", 12, "making", null, at, mine = false, taking = false, liked = true),
                RecipeRequest("q000000000002", WorkStyle.SHIFT, "4조 2교대 야간 많은 달 색 구분", 5, "done", code(0), at, mine = false, taking = false, liked = false),
            )
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        val model = RequestsViewModel(c)
        content { RequestsScreen(model, onBack = {}, onOpen = {}, onMake = {}) }
        settle()
        assertTrue(appears("간호사 3교대 근무별 알람 세트", exact = false))
        assertTrue(appears("완성된 레시피 보기", exact = true))
    }
    // ---- 1.35 꾸미기 자유도 [CVW-01]..[CVW-10] [STU-01]..[STU-06] [LIN-01]..[LIN-05] ----

    private fun memosThisMonth() = runBlocking {
        listOf(0L to "치과 3시", 0L to "보험증 챙기기", 1L to "인수인계 정리", 3L to "엄마 생신 저녁", 5L to "헬스 PT 7시").forEachIndexed { n, (d, t) ->
            c.memoRepository.save(com.bamnat.core.memo.Memo(date = today.plusDays(d), title = t, createdAtMillis = n + 1L))
        }
    }

    private fun preset(name: String) = com.bamnat.core.recipe.CalendarView.PRESETS.first { it.first == name }.second

    /** [CVW-01][CVW-02][CVW-03] 메모 중심: tall cells, a colour bar for the shift, the date at the top left and 3 memo lines. */
    @Test fun aMemoFirstCalendar() = shoot("1350_cvw_memo_calendar", PHONE) {
        memosThisMonth()
        c.settingsRepository.update { with(com.bamnat.app.ui.calendar.CalendarViewSettings) { it.withView(preset("메모 중심")) } }
        settle()
        assertTrue(c.settingsRepository.current.memoInCells)
        assertTrue(appears("보험증", exact = false))
    }

    /** [CVW-01][CVW-04] 다이어리: the tallest cells with lines, like a paper diary, on a Fold. */
    @Test fun aDiaryCalendar() = shoot("1351_cvw_diary_calendar", FOLDED) {
        memosThisMonth()
        c.settingsRepository.update { with(com.bamnat.app.ui.calendar.CalendarViewSettings) { it.withView(preset("다이어리")) } }
        settle()
        assertTrue(appears("인수인계", exact = false))
    }

    /** [STU-01][STU-02][STU-05][LIN-01] The studio starts from a 보여주기 판 as its 수정판, and 내 앱에만 넣기 puts it on without sending. */
    @Test fun theStudioStartsFromAShowcaseAndPutsItOnMine() = shoot("1352_stu_studio_start", PHONE) {
        val api = object : Starters() {
            var sent = 0
            override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) =
                if (sort == "showcase") RecipePage(listOf(item(0).copy(showcase = true)), null) else super.list(style, sort, q, cursor, kind)
            override suspend fun publish(device: String, recipe: Recipe, scope: String): Sent { sent++; return super.publish(device, recipe, scope) }
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        val vm = RecipeMakeViewModel(c)
        content { RecipeMakeScreen(vm, onBack = {}, onOpen = {}) }
        settle()
        assertTrue(appears("레시피 만들기 1/5 · 꾸미기", exact = true))
        assertTrue(appears("어디서 시작할까요?", exact = true))
        tapText(api.item(0).title, exact = true)
        settle()
        assertEquals("보여주기 판의 수정판", api.code(0), vm.state.value.recipe.parent)
        assertEquals(api.item(0).title, vm.state.value.parentTitle)
        assertTrue(appears("’에서 시작했어요. 올리면 그 판의 수정판으로 붙어요", exact = false))
        vm.setValue(PieceKind.CAL_VIEW, "size", "tall")
        vm.putOnMine()
        settle()
        val put = vm.state.value.putMine
        assertTrue("내 앱에 들어갔어요", put != null && put > 0)
        assertEquals("올리지 않아요", 0, api.sent)
        assertEquals("tall", com.bamnat.core.recipe.CalendarView.decode(c.settingsRepository.current.calView).size)
        assertTrue(runBlocking { c.recipes.undo(put!!) })
        assertEquals("되돌리면 처음대로", "", c.settingsRepository.current.calView)
    }

    /** [LIN-02][LIN-03] The maker opens a 수정판 of their recipe: what changed, field by field, and 내 판에 가져오기 takes the picked ones. */
    @Test fun theMakerSeesWhatARemixChangedAndTakesIt() = shoot("1353_lin_diff", PHONE) {
        val base = Starters()
        val i = base.recipes.indexOfFirst { (r, data) -> !data && WorkStyle.SHIFT in r.styles && r.pieces.any { it.kind == PieceKind.SHIFT_LOOK } }
        val mine = base.recipes[i].first
        val look = mine.pieces.first { it.kind == PieceKind.SHIFT_LOOK }
        @Suppress("UNCHECKED_CAST") val items = look.values["items"] as List<Map<String, Any?>>
        val remix = mine.copy(title = "야간 더 진하게", parent = base.code(i), pieces = (mine.pieces.map { p ->
            if (p.kind != PieceKind.SHIFT_LOOK) p else p.copy(values = p.values + ("items" to items.mapIndexed { n, m -> if (n == 0) m + ("color" to 0xFF0D1B4CL) else m }))
        } + com.bamnat.core.recipe.Piece(PieceKind.CAL_VIEW, mapOf("size" to "tall", "memo" to 3L))).sortedBy { it.kind.ordinal })
        val api = object : Starters() {
            override suspend fun show(code: String, device: String?, version: Int?) = when (code) {
                "RMIX2345" -> RecipeDoc(item(i).copy(code = code, title = remix.title, parent = code(i)), RecipeCodec.hash(remix), RecipeCodec.write(remix), "public", mine = false)
                code(i) -> super.show(code, device, version).copy(mine = true)
                else -> super.show(code, device, version)
            }
        }
        c.recipeApi = api
        style(WorkStyle.SHIFT)
        val vm = RecipeDetailViewModel(c, "RMIX2345", PersonalCalendar.MAIN)
        content { RecipeDetailScreen(vm, onBack = {}, onRemake = {}) }
        settle()
        assertTrue(appears("내 판과 달라진 점", exact = true))
        val diff = vm.state.value.diff
        assertEquals(setOf(PieceKind.SHIFT_LOOK, PieceKind.CAL_VIEW), diff.map { it.kind }.toSet())
        // Only the colour is taken; the 달력 모양 the 수정판 added is left out.
        val colour = diff.first { it.kind == PieceKind.SHIFT_LOOK }.lines.single()
        val taken = RecipeCodec.read(vm.takeInto(setOf(PieceKind.SHIFT_LOOK to colour.path))!!)
        @Suppress("UNCHECKED_CAST") val first = (taken.pieces.first { it.kind == PieceKind.SHIFT_LOOK }.values["items"] as List<Map<String, Any?>>)[0]
        assertEquals(0xFF0D1B4CL, first["color"])
        assertTrue(taken.pieces.none { it.kind == PieceKind.CAL_VIEW })
        assertTrue(taken.desc.contains("도움 준 수정판: ‘야간 더 진하게’"))
        // Every row starts ticked: a piece added whole counts once.
        assertTrue(appears("고른 2개 내 판에 가져오기", exact = true))
        assertEquals("내 판 그대로의 이름", mine.title, taken.title)
    }
}
