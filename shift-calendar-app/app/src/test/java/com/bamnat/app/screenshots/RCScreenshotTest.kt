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
        assertTrue(appears("레시피 만들기 1/5 · 꾸미기", exact = true))
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
        content { RecipeMineScreen(RecipeMineViewModel(c), onBack = {}, onOpen = {}, onDraft = {}, onList = {}) }
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
        content { MakerScreen(MakerViewModel(c, "a1b2c3d4e5f6"), onBack = {}, onOpen = {}) }
        settle()
        assertTrue(appears("밤샘간호사", exact = false))
        assertTrue(appears("🏅 쓰는 분 100명", exact = true))
        assertTrue(appears("만든 레시피", exact = true))
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
        content { RequestsScreen(RequestsViewModel(c), onBack = {}, onOpen = {}, onMake = {}) }
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
            override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int) =
                if (sort == "showcase") RecipePage(listOf(item(0).copy(showcase = true)), null) else super.list(style, sort, q, cursor)
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
