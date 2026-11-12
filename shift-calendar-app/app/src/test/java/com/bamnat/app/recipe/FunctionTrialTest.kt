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
}
