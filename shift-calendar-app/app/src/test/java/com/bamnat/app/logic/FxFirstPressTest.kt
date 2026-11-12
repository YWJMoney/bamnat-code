package com.bamnat.app.logic

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicValue
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.ValueKind
import com.bamnat.core.formula.ValueReset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [RVM-01] PC-41 (통합 1차, 딥워크 데스크): the 단추 위젯 '☕ 커피' counted 커피 1잔 on its first press but wrote no 카페 line in the 가계부
 * (3잔, 2줄) and no run. The first press of a 값 칸 kept "nothing before" as NaN, which JSON refuses, so the press stopped half-way.
 * Every 값 동작 is pressed first here, and what follows it in the 동작 줄 must be done too.
 */
@RunWith(AndroidJUnit4::class)
class FxFirstPressTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val money get() = c.scopeOf(PersonalCalendar.MAIN).money

    private val coffee = LogicButton("커피", "☕", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "커피", by = 1.0),
        more = listOf(LogicAct(LogicVerb.ADD_MONEY, "카페", 4500)))

    private suspend fun use(vararg b: LogicButton, values: List<LogicValue> = listOf(LogicValue("커피", ValueReset.DAY, unit = "잔"))) {
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(buttons = b.toList(), values = values))) }
    }

    @Before fun clean() = runBlocking {
        FxValues.clear(c)
        val store = LogicStore(app)
        store.clearWritten(); store.clearLog(); store.undo = null
        Unit
    }

    @After fun forget() = runBlocking {
        c.settingsRepository.update { it.copy(logic = "") }
        FxValues.clear(c)
        Unit
    }

    @Test fun theFirstPressWritesTheWholeLine() = runBlocking {
        use(coffee)
        val before = money.allEntries().count { it.category == "카페" }
        FxOutside.press(c, coffee.id)
        assertEquals("커피 1잔", 1.0, FxValues.kept(c, "커피")?.v)
        assertEquals("the 카페 line on the very first press", before + 1, money.allEntries().count { it.category == "카페" && it.amount == 4500L })
        assertEquals("counted in 지난 7일", 1, LogicStore(app).runs(coffee.label))
        assertTrue(LogicStore(app).log().none { it.what.contains("멈췄") })
        // [RVM-02] PC-42: the line reads '카페', not '규칙·단추: 커피', and says a 단추 wrote it.
        val line = money.allEntries().last { it.category == "카페" && it.amount == 4500L }
        assertEquals("카페", line.title)
        assertTrue(line.tags.toString(), LogicRunner.TAG_BUTTON in line.tags)
        FxOutside.press(c, coffee.id)
        FxOutside.press(c, coffee.id)
        assertEquals("3잔, 3줄", before + 3, money.allEntries().count { it.category == "카페" && it.amount == 4500L })
        assertEquals(3.0, FxValues.kept(c, "커피")?.v)
    }

    @Test fun takingTheFirstPressBackGoesToTheStart() = runBlocking {
        use(coffee)
        val before = money.allEntries().count { it.category == "카페" }
        FxOutside.press(c, coffee.id)
        assertEquals(2, LogicRunner.undoAll(c))
        assertEquals("the 카페 line out", before, money.allEntries().count { it.category == "카페" })
        assertEquals("back to its start", 0.0, FxValues.kept(c, "커피")?.v)
    }

    @Test fun everyValueVerbPressedFirstGoesOnToTheNextStep() = runBlocking {
        val values = listOf(
            LogicValue("물", ValueReset.DAY, unit = "잔"),
            LogicValue("체중", ValueReset.NEVER, unit = "kg"),
            LogicValue("잔 시각", ValueReset.DAY, kind = ValueKind.TIME),
            LogicValue("한 줄", ValueReset.DAY, kind = ValueKind.TEXT),
        )
        val then = LogicAct(LogicVerb.ADD_MONEY, "확인", 100)
        val buttons = listOf(
            LogicButton("물", null, ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "물", by = 1.0), more = listOf(then)),
            LogicButton("체중", null, ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_SET, "체중", by = 70.0), more = listOf(then)),
            LogicButton("잠", null, ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_STAMP, "잔 시각"), more = listOf(then)),
            LogicButton("한 줄", null, ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_TEXT, "한 줄", text = "좋아요"), more = listOf(then)),
        )
        use(*buttons.toTypedArray(), values = values)
        val before = money.allEntries().count { it.category == "확인" }
        buttons.forEach { FxOutside.press(c, it.id) }
        assertEquals("each first press went on to its 가계부 step", before + 4, money.allEntries().count { it.category == "확인" })
        buttons.forEach { assertEquals(it.label, 1, LogicStore(app).runs(it.label)) }
    }
    @Test fun oneUndoTakesTheWholePressBack() = runBlocking {
        use(coffee)
        FxOutside.press(c, coffee.id)
        FxOutside.press(c, coffee.id)
        val before = money.allEntries().count { it.category == "카페" }
        assertTrue(LogicRunner.undo(c))
        // PC-41: 되돌리기 took the 가계부 line back and left the 1잔; the last press goes back whole.
        assertEquals("the last press's 카페 line out", before - 1, money.allEntries().count { it.category == "카페" })
        assertEquals("and its 1잔", 1.0, FxValues.kept(c, "커피")?.v)
        assertEquals("the first press stays", 1, LogicStore(app).written().count { it.optString("who") == coffee.label && it.has("money") })
    }

    /**
     * PC-98 (통합 2차, 작품 '공수 카운터'): the home widget's '1공수' then '0.5공수' left 1.5공수 but one memo line ('0.5공수 · 이달
     * 1.5공수'); the first press's line was lost the same way as PC-41's 카페 line (the first 값 동작 stopped the press half-way).
     * Both presses write their line, each with the count as it is after that press.
     */
    @Test fun aCounterWritesTheFirstPressLineToo() = runBlocking {
        val one = LogicButton("1공수", "🦺", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "공수", by = 1.0),
            more = listOf(LogicAct(LogicVerb.MEMO_LINE, "1공수 · 이달 {값(\"공수\")}공수")))
        val half = LogicButton("0.5공수", "⛑️", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "공수", by = 0.5),
            more = listOf(LogicAct(LogicVerb.MEMO_LINE, "0.5공수 · 이달 {값(\"공수\")}공수")))
        use(one, half, values = listOf(LogicValue("공수", ValueReset.MONTH, goal = 22.0, unit = "공수")))
        val today = com.bamnat.core.time.Today.date()
        c.memoRepository.deleteForever(c.memoRepository.memos.first().filter { it.date == today }.map { it.id })
        FxOutside.press(c, one.id)
        FxOutside.press(c, half.id)
        assertEquals(1.5, FxValues.kept(c, "공수")?.v)
        val memo = c.memoRepository.memos.first().single { it.date == today && !it.inTrash }
        val lines = (listOf(memo.title) + memo.body.lines()).filter { it.isNotBlank() }
        assertEquals("both presses wrote their line", listOf("1공수 · 이달 1공수", "0.5공수 · 이달 1.5공수"), lines)
        assertEquals(1, LogicStore(app).runs(one.label))
        assertEquals(1, LogicStore(app).runs(half.label))
        // PC-99: the 날짜 창's '🌧️ 우천 쉼' read '… 이달 3공수' (장터 예시의 숫자) at 1.5공수; it says the count as it is now.
        val rain = LogicAct(LogicVerb.MEMO_LINE, "#우천 비로 쉼 · 이달 {값(\"공수\")}공수")
        assertEquals("그날 메모에 ‘#우천 비로 쉼 · 이달 1.5공수’ 더하기", LogicRunner.sayNow(rain, LogicRunner.facts(c), today))
        // With nothing to work it out from (a launcher's label), the place is left out, never a made-up number.
        assertEquals("그날 메모에 ‘#우천 비로 쉼 · 이달 공수’ 더하기", LogicRunner.sayNow(rain, null, today))
    }

    @Test fun aWorkPutInDoesNotRunItsOpeningRuleThere() = runBlocking {
        // [RVM-19] PC-48: 레트로 데스크톱 '넣기' opened its '오늘 계획' 메모 틀 over the work at once.
        val plan = com.bamnat.core.formula.LogicRule("오늘 계획", com.bamnat.core.formula.RuleTrigger.OPEN, act = LogicAct(LogicVerb.MEMO_TEMPLATE, "오늘 계획"))
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic(rules = listOf(plan)))) }
        val store = LogicStore(app)
        store.lastOpen = null; store.clearLog()
        LogicRunner.onOpen(c, opened = false)
        assertEquals(com.bamnat.core.time.Today.date().toString(), store.lastOpen)
        assertTrue(store.log().toString(), store.log().any { it.who == plan.name && it.what.contains("내일 앱을 처음 열 때") })
        assertEquals("no 메모 틀 opened", null, LogicLinks.template)
    }

    @Test fun aStampAndTheSleepScreenIsFallingAsleep() {
        // [RVM-14] PC-35: '잠들어요' (시각 적기 + 수면 화면) is the app's '잠들었어요' too.
        val stamp = LogicAct(LogicVerb.VAL_STAMP, "잔 시각")
        assertTrue(LogicRunner.sleeps(listOf(stamp, LogicAct(LogicVerb.SCREEN, "sleep"))))
        assertTrue(LogicRunner.sleeps(listOf(LogicAct(LogicVerb.SCREEN, "night"), stamp)))
        org.junit.Assert.assertFalse(LogicRunner.sleeps(listOf(stamp, LogicAct(LogicVerb.SCREEN, "memo"))))
        org.junit.Assert.assertFalse("a stamp that waits is not now", LogicRunner.sleeps(listOf(stamp.copy(after = 30), LogicAct(LogicVerb.SCREEN, "sleep"))))
        assertEquals(com.bamnat.app.NoticeLinks.Target.SLEEP, LogicRunner.screenOf("sleep"))
        assertEquals(com.bamnat.app.NoticeLinks.Target.PAY, LogicRunner.screenOf("stats"))
        assertEquals(null, LogicRunner.screenOf("nowhere"))
        // Every screen a 화면 열기 can name opens one.
        LogicVerb.SCREENS.keys.forEach { org.junit.Assert.assertNotNull(it, LogicRunner.screenOf(it)) }
    }
    @Test fun aRuleThatComesNowReadsItsValuesNow() = runBlocking {
        // [RVM-04] PC-38 ('물 잔째예요'): a night's rule, its day begun yesterday, read the 값 칸 of that day (nothing kept).
        use(coffee)
        FxOutside.press(c, coffee.id)
        FxOutside.press(c, coffee.id)
        val f = LogicRunner.facts(c)
        val yesterday = com.bamnat.core.time.Today.date().minusDays(1)
        assertEquals(com.bamnat.core.formula.Value.Num(2.0), LogicRunner.scopeNow(f, yesterday).value("커피"))
        assertTrue("that day itself had none", f.scope(yesterday).value("커피") != com.bamnat.core.formula.Value.Num(2.0))
        assertEquals("today is today", f.scope(com.bamnat.core.time.Today.date()).value("커피"), LogicRunner.scopeNow(f, com.bamnat.core.time.Today.date()).value("커피"))
    }
}
