package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.logic.LogicRunner
import com.bamnat.app.logic.LogicStore
import com.bamnat.app.ui.recipe.LiveTryOn
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TRY-06] 1.39 기능까지 써 보기 (조사 18장 C-1): a work's 기능 run on the real app while tried — its buttons write, its rules are on,
 * its 메모 틀 are there — and 끝내기 leaves the app as it was: the pieces put back, what they wrote taken back, and what I wrote as I
 * chose. Nothing of it is a 넣기, and one the app stopped in is put back at the next start.
 */
@RunWith(AndroidJUnit4::class)
class FunctionTrialTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val day get() = Today.date()
    private val add = LogicButton("야식 기록", "🍜", ButtonPlace.MONEY, LogicAct(LogicVerb.ADD_MONEY, "야식", 5000))
    private val pay = LogicRule("야간 끝 수당", RuleTrigger.END, act = LogicAct(LogicVerb.ADD_INCOME, "야간 수당", 30000))
    private val work = Recipe("야식 관리판", styles = WorkStyle.entries.toSet(), pieces = listOf(
        Piece(PieceKind.LOGIC, Logic(listOf(add), listOf(pay)).toValues()),
        Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "야식 메모", "body" to "무엇을 먹었나")))),
    ))
    private val all = mapOf(PieceKind.LOGIC to RecipePieces.Mode.REPLACE, PieceKind.MEMO_TEMPLATES to RecipePieces.Mode.BOTH)
    private val money get() = c.scopeOf(PersonalCalendar.MAIN).money

    @After fun letGo() = runBlocking { LiveTryOn.settle(c); LiveTryOn.forget() }

    private suspend fun tryIt(r: Recipe = work, picked: Set<String> = emptySet(), fns: Map<PieceKind, RecipePieces.Mode> = all) =
        LiveTryOn.start(c, r, own = false, picked, FunctionTrial.tab(r, fns.keys), LiveTryOn.From.Detail("FN234567"), functions = fns)

    private suspend fun press() = LogicRunner.run(c, add.act, day, LogicRunner.facts(c).scope(day), add.label)

    @Test fun theWorkRunsForRealAndIsTakenBackWhole() = runBlocking {
        val logicBefore = c.settingsRepository.current.logic
        val templatesBefore = c.memoTemplates.items.value
        assertTrue(tryIt())
        assertEquals("its button sits in 가계부, so the trial opens there", "money", LiveTryOn.trial.value?.tab)
        assertTrue(FunctionTrial.busy(app))
        assertFalse("no look laid over", c.settingsRepository.trying)
        assertTrue(Logic.decode(c.settingsRepository.current.logic).buttons.any { it.label == add.label })
        assertFalse("its rule that writes runs while tried (one put in comes in off)", LogicStore(app).isOff(pay.key))
        assertTrue(c.memoTemplates.items.value.any { it.title == "야식 메모" })
        // The button writes for real; I write a memo and a 가계부 line of my own.
        press()
        assertTrue(money.allEntries().any { it.category == "야식" && it.amount == 5000L })
        c.memoRepository.save(Memo(date = day, title = "써 본 메모"))
        money.save(MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = 1200, category = "간식"))
        assertEquals(FunctionTrial.Made(1, 1), FunctionTrial(c).made())

        LiveTryOn.finish(c, keepMine = false)
        assertFalse(FunctionTrial.busy(app))
        assertNull(LiveTryOn.trial.value)
        assertEquals(logicBefore, c.settingsRepository.current.logic)
        assertEquals(templatesBefore, c.memoTemplates.items.value)
        assertTrue(money.allEntries().none { it.category == "야식" || it.category == "간식" })
        assertTrue(c.memoRepository.memos.first().none { it.title == "써 본 메모" })
        assertTrue("taken back, so not in '규칙이 쓴 것'", LogicStore(app).written().none { it.optString("who") == add.label })
        val e = LiveTryOn.ended.value
        assertNotNull(e)
        assertEquals("FN234567", e!!.code)
        assertEquals(1, e.ran)
        assertEquals(FunctionTrial.Made(1, 1), e.made)
        assertFalse(e.kept)
        assertNull("no record of a put-on: 되돌리기 has nothing of it", c.recipes.undoable("FN234567").firstOrNull())
    }

    @Test fun whatIWroteStaysWhenIKeepIt() = runBlocking {
        assertTrue(tryIt())
        press()
        c.memoRepository.save(Memo(date = day, title = "진짜 메모"))
        LiveTryOn.finish(c, keepMine = true)
        assertTrue(c.memoRepository.memos.first().any { it.title == "진짜 메모" })
        assertTrue("what the button wrote goes either way", money.allEntries().none { it.category == "야식" })
        assertTrue(LiveTryOn.ended.value!!.kept)
        assertTrue(LiveTryOn.say(LiveTryOn.ended.value!!).contains("남겼어요"))
    }

    @Test fun aTrialTheAppStoppedInIsPutBackAtTheNextStart() = runBlocking {
        val logicBefore = c.settingsRepository.current.logic
        assertTrue(tryIt())
        press()
        // The app stopped: nothing in memory, only the file, read at the next start (AppContainer.start).
        val e = FunctionTrial(c).recover()
        assertNotNull(e)
        assertFalse(FunctionTrial.busy(app))
        assertEquals(logicBefore, c.settingsRepository.current.logic)
        assertTrue(money.allEntries().none { it.category == "야식" })
        assertNull("once only", FunctionTrial(c).recover())
    }

    @Test fun puttingItInForRealEndsTheTrialFirst() = runBlocking {
        val logicBefore = c.settingsRepository.current.logic
        assertTrue(tryIt())
        val id = c.recipes.apply(work, "FN234567", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.LOGIC, RecipePieces.Mode.REPLACE)), emptyMap())
        assertNotNull("put in for real, over my own look of the app", id)
        assertFalse(FunctionTrial.busy(app))
        assertNull(LiveTryOn.trial.value)
        assertTrue(Logic.decode(c.settingsRepository.current.logic).buttons.any { it.label == add.label })
        // Its 되돌리기 goes back to what was mine before, not to the trial.
        assertTrue(c.recipes.undo(id!!))
        assertEquals(logicBefore, c.settingsRepository.current.logic)
    }

    @Test fun nothingToTryWhenItIsAlreadyMine() = runBlocking {
        val logicOnly = work.copy(pieces = work.pieces.filter { it.kind == PieceKind.LOGIC })
        c.recipes.apply(logicOnly, "FN234567", 1, PersonalCalendar.MAIN, listOf(RecipeRepository.Pick(PieceKind.LOGIC, RecipePieces.Mode.REPLACE)), emptyMap())
        assertFalse(tryIt(logicOnly, fns = mapOf(PieceKind.LOGIC to RecipePieces.Mode.REPLACE)))
        assertFalse(FunctionTrial.busy(app))
        assertNull(LiveTryOn.trial.value)
    }

    @Test fun looksAndFunctionsTogether() = runBlocking {
        val theme = BuiltInThemes.of(WorkStyle.SHIFT)[1]
        val both = theme.copy(pieces = theme.pieces + work.pieces.first { it.kind == PieceKind.LOGIC })
        val saved = c.recipes.pieces.lookOf(c.settingsRepository.current)
        assertTrue(tryIt(both, LookParts.of(both).map { it.id }.toSet(), mapOf(PieceKind.LOGIC to RecipePieces.Mode.REPLACE)))
        assertTrue(c.settingsRepository.trying)
        assertTrue(FunctionTrial.busy(app))
        assertEquals(listOf(PieceKind.LOGIC.label), LiveTryOn.trial.value?.functions)
        assertEquals("the look is still only on screen", saved, c.recipes.pieces.lookOf(c.settingsRepository.current))
        LiveTryOn.finish(c, keepMine = false)
        assertFalse(c.settingsRepository.trying)
        assertFalse(FunctionTrial.busy(app))
        assertEquals(saved, c.recipes.pieces.lookOf(c.settingsRepository.settings.value))
        assertNotNull("입어 보기 opens again with the parts ticked", LiveTryOn.take { it == LiveTryOn.From.Detail("FN234567") })
    }

    /** PC 03111522: 넣기 asked while trying is worked out against my own app (the button tried is not 'already there'). */
    @Test fun whatPuttingInChangesIsSaidAgainstMyOwnApp() = runBlocking {
        val logic = work.pieces.first { it.kind == PieceKind.LOGIC }.values
        assertTrue(tryIt())
        assertTrue("while tried, it counts as there", c.recipes.pieces.preview(PieceKind.LOGIC, logic, PersonalCalendar.MAIN).any { it.text.contains("하는 일이 같아") })
        LiveTryOn.settle(c, snack = false)
        assertTrue(c.recipes.pieces.preview(PieceKind.LOGIC, logic, PersonalCalendar.MAIN).none { it.text.contains("하는 일이 같아") })
        assertNotNull("the page's card says it ended", LiveTryOn.ended.value)
    }

    /** 2.0.0 [RVM-46] 검토 RCP-20: ending the trial takes back what the tried work wrote, not what my own buttons wrote meanwhile. */
    @Test fun endingTheTrialLeavesWhatMyOwnButtonWrote() = runBlocking {
        val mine = LogicButton("커피값", "☕", ButtonPlace.MONEY, LogicAct(LogicVerb.ADD_MONEY, "커피", 4500))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(listOf(mine)))) }
        assertTrue(tryIt())
        press()
        LogicRunner.run(c, mine.act, day, LogicRunner.facts(c).scope(day), mine.label)
        assertEquals(2, money.book.first().entries.size)
        LiveTryOn.finish(c, keepMine = true)
        LiveTryOn.settle(c, snack = false)
        val left = money.book.first().entries
        assertEquals("내 단추가 적은 커피값은 남아요", listOf(4500L), left.map { it.amount })
        assertTrue("써 본 단추의 야식은 빠져요", left.none { it.amount == 5000L })
        assertEquals("내 단추도 그대로", listOf(mine.label), Logic.decode(c.settingsRepository.current.logic).buttons.map { it.label })
    }

    /**
     * 2.0.0 [RVM-53] 검토 RCP-21 ① (조건 10): 써 보는 중에 내 규칙을 하나 만들면 `logic` 글이 달라져, 끝낼 때 써 본 단추·규칙이 빠지지 않고
     * 남았어요. 이제 써 본 작품이 더한 것만 빠지고 그 사이 만든 내 규칙은 남아요.
     */
    @Test fun aRuleIMadeWhileTryingStaysAndTheTriedOnesGo() = runBlocking {
        assertTrue(tryIt())
        val mine = LogicRule("내 퇴근 메모", RuleTrigger.END, act = LogicAct(LogicVerb.NOTIFY, "수고했어요"))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic.decode(it.logic).let { l -> l.copy(rules = l.rules + mine) })) }
        LiveTryOn.finish(c, keepMine = true)
        val l = Logic.decode(c.settingsRepository.current.logic)
        assertEquals("내가 만든 규칙은 남아요", listOf(mine.name), l.rules.map { it.name })
        assertTrue("써 본 단추는 빠져요", l.buttons.none { it.label == add.label })
        // [RVM-54] U09b-04: 끝낸 카드의 '되돌린 것 보기'가 이것을 말해요.
        val e = LiveTryOn.ended.value!!
        assertEquals(listOf("수식·규칙·버튼", "메모 틀"), e.back)
        assertEquals(listOf(add.label, pay.name), e.removed)
        assertTrue(e.mineRules)
        assertEquals(listOf("원래대로 돌린 것: 수식·규칙·버튼, 메모 틀", "뺀 단추·규칙: 야식 기록, 야간 끝 수당", "내 단추·규칙이 적은 것은 건드리지 않았어요"),
            LiveTryOn.details(e))
    }

    /**
     * [RVM-53] RCP-21 ②: 써 보는 중에 설정 › 백업을 손으로 저장하면 써 보던 단추·규칙·메모 틀이 든 채 저장돼, 그 백업을 불러오면 넣은 것처럼
     * 남았어요. 이제 저장 전에 써 보기를 끝내고(내가 적은 것은 남아요) 그렇다고 말해요.
     */
    @Test fun aBackupSavedWhileTryingHasNoneOfTheTriedWork() = runBlocking {
        assertTrue(tryIt())
        val vm = com.bamnat.app.ui.settings.SettingsViewModel(c)
        val f = java.io.File(app.cacheDir, "trial-backup.bamnat").apply { delete() }
        vm.exportTo(app, android.net.Uri.fromFile(f))
        repeat(100) { if (vm.message.value == null) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) } }
        val said = vm.message.value?.text.orEmpty()
        assertTrue(said, said.startsWith("‘야식 관리판’ 써 보기는 끝냈어요 · 백업 파일을 저장하고"))
        assertFalse("써 보기는 끝났어요", FunctionTrial.busy(app))
        val json = f.inputStream().use { c.backupManager.read(it) }
        assertFalse("백업에 써 본 단추가 없어요", add.label in json)
        assertFalse("써 본 메모 틀도 없어요", "야식 메모" in json)
    }

    /**
     * [RVM-53] RCP-21 ③: 기능까지 써 보는 동안은 휴대폰의 모든 프로 단추·규칙이 돌았어요(무료인 내 다른 ✦ 단추도). 이제 하루 써 보기처럼
     * 써 보는 작품 것만 돌아요.
     */
    @Test fun tryingAWorkRunsOnlyThatWorksProParts() = runBlocking {
        c.settingsRepository.update { it.copy(previewTier = com.bamnat.app.tier.Tier.FREE) }
        c.featureGate.state.first { !it.allows(com.bamnat.app.tier.Feature.FX_WORKS) }
        try {
            val cup = com.bamnat.core.formula.LogicValue("물 잔", com.bamnat.core.formula.ValueReset.DAY)
            // 지금 시각 적기는 무료 맛보기(값 칸 하나 더하기)가 아닌 ✦ 장치예요.
            val mine = LogicButton("내 물 한 잔", "💧", ButtonPlace.MONEY, LogicAct(LogicVerb.VAL_STAMP, "물 잔"))
            c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(listOf(mine), values = listOf(cup)))) }
            val step = com.bamnat.core.formula.LogicValue("걸음", com.bamnat.core.formula.ValueReset.DAY)
            val walk = LogicButton("만 보 걷기", "🚶", ButtonPlace.MONEY, LogicAct(LogicVerb.VAL_ADD, "걸음", 1000))
            val pro = Recipe("걷기판", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.LOGIC, Logic(listOf(walk), values = listOf(step)).toValues())))
            assertFalse("무료라 내 ✦ 단추는 쉬어요", com.bamnat.app.logic.FxAccess.runnable(c, Logic.decode(c.settingsRepository.current.logic)).buttons.any { it.label == mine.label })
            assertTrue(tryIt(pro, fns = mapOf(PieceKind.LOGIC to RecipePieces.Mode.BOTH)))
            val runs = com.bamnat.app.logic.FxAccess.runnable(c, Logic.decode(c.settingsRepository.current.logic)).buttons.map { it.label }
            assertTrue("써 보는 작품의 ✦ 단추는 돌아요", walk.label in runs)
            assertFalse("내 다른 ✦ 단추는 그대로 쉬어요", mine.label in runs)
        } finally {
            c.settingsRepository.update { it.copy(previewTier = null) }
        }
    }
}
