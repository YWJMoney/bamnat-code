package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.logic.BlockEditor
import com.bamnat.app.logic.ConditionEditor
import com.bamnat.app.logic.FormulaField
import com.bamnat.app.logic.LogicEditor
import com.bamnat.app.logic.RuleDialog
import com.bamnat.app.logic.followWords
import com.bamnat.app.logic.minutesWord
import com.bamnat.app.logic.notifyWords
import com.bamnat.app.logic.whenFor
import com.bamnat.app.logic.timeOf
import com.bamnat.app.logic.ruleProblem
import com.bamnat.app.logic.ruleName
import com.bamnat.app.logic.sameSay
import com.bamnat.app.logic.bigSay
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.FormulaNames
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [RUL-06] 1.36 886 수식·규칙 편집기: the list in 설정 (switch, 지난 7일), a rule's window with the condition as a table
 * (근무종류 이(가) 야간 그리고 꼬리표 포함 인수인계), and a formula typed by hand with its mistake said in words.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RulScreenshotTest : ScreenshotBase() {
    private companion object {
        const val COND = "근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\")"
        val RESET = LogicRule("퇴근 뒤 체크 비우기", RuleTrigger.END, min = 30, act = LogicAct(LogicVerb.CHECK_RESET))
        val NIGHT = LogicRule("야간 인수인계", RuleTrigger.START, min = -60, role = "night", cond = COND,
            act = LogicAct(LogicVerb.NOTIFY, "{근무시작} 야간이에요. 인수인계 메모를 봐요"))
        val MEMO = LogicButton("오늘 기록", "📝", ButtonPlace.DAY, LogicAct(LogicVerb.MEMO_TEMPLATE, "하루 기록"))
    }

    @Test fun minutesWords() {
        assertEquals("1시간 전", minutesWord(-60))
        assertEquals("30분 뒤", minutesWord(30))
        assertEquals("바로", minutesWord(0))
    }

    @Test fun notifyWordsFollowWhenAndShift() {
        // PC 3차: 주간·근무 끝 규칙에 '야간 근무예요'가 왔어요.
        val start = LogicRule("", RuleTrigger.START, min = -60, role = "night", act = LogicAct(LogicVerb.NOTIFY, notifyWords(RuleTrigger.START, "night")))
        val end = followWords(start, start.copy(on = RuleTrigger.END, role = "day"))
        assertEquals("{근무끝}에 주간 근무가 끝나요", end.act.arg)
        val mine = start.copy(act = start.act.copy(arg = "내가 쓴 글"))
        assertEquals("내가 쓴 글", followWords(mine, mine.copy(on = RuleTrigger.END)).act.arg)
        val old = start.copy(act = start.act.copy(arg = "{근무시작}에 야간 근무예요"))
        assertEquals("{근무시작}에 주간 근무가 시작돼요", followWords(old, old.copy(role = "day")).act.arg)
    }

    @Test fun endStartsAtTheEnd() {
        // PC D7 (사용성 중): '근무 끝'을 고르면 시작 규칙의 '1시간 전'이 남았어요.
        val start = LogicRule("", RuleTrigger.START, min = -60, act = LogicAct(LogicVerb.NOTIFY, "x"))
        val end = whenFor(start, RuleTrigger.END)
        assertEquals(0, end.min)
        assertEquals(-60, whenFor(end, RuleTrigger.START).min)
        assertEquals(30, whenFor(end.copy(min = 30), RuleTrigger.START).min)
        assertEquals(-30, whenFor(start.copy(min = -30), RuleTrigger.START).min)
    }

    @Test fun dailyShowsItsRealTime() {
        // PC D9 (높): '매일'을 고르면 흐린 21:00인데 요약은 09:00이고, 저장은 말없이 안 됐어요.
        val start = LogicRule("", RuleTrigger.START, min = -60, act = LogicAct(LogicVerb.NOTIFY, "x"))
        val daily = whenFor(start, RuleTrigger.DAILY)
        assertEquals("21:00", daily.at)
        assertEquals(0, daily.min)
        assertEquals("매일 21:00", daily.sayWhen())
        assertEquals(null, ruleProblem(daily))
        assertEquals("시각을 07:00처럼 넣어 주세요", ruleProblem(daily.copy(at = "")))
        assertEquals("07:00", timeOf("7:00")); assertEquals("07:00", timeOf("700")); assertEquals("21:30", timeOf("2130"))
        assertEquals("07:00", timeOf("7시")); assertEquals(null, timeOf("25:00")); assertEquals(null, timeOf("아침"))
        assertEquals(0, whenFor(start, RuleTrigger.TAP).min)
    }

    @Test fun namesSaidFromWhenAndWhatAndKeptApart() {
        // PC D10 (낮): 이름을 비우면 '‘오늘 {근무} · 확인할 시간이에요'처럼 따옴표 한쪽이 남은 글이 이름이 됐고, 같은 이름도 막지 않았어요.
        val daily = LogicRule("", RuleTrigger.DAILY, at = "07:00", act = LogicAct(LogicVerb.NOTIFY, "오늘 {근무} · 확인할 시간이에요"))
        assertEquals("매일 07:00 알림", ruleName(daily, emptySet()))
        assertEquals("매일 07:00 알림 (2)", ruleName(daily, setOf("매일 07:00 알림")))
        assertEquals("매일 07:00 알림 (3)", ruleName(daily, setOf("매일 07:00 알림", "매일 07:00 알림 (2)")))
        assertEquals("내 규칙", ruleName(daily.copy(name = " 내 규칙 "), emptySet()))
        // PC D11: 같은 일을 하는 규칙은 한 번 물어요 (이름과 상관없이 언제·조건·무엇을이 같으면).
        assertEquals(daily.key, daily.copy(name = "다른 이름").key)
        assertTrue(sameSay(daily.copy(name = "매일 07:00 알림")).startsWith("같은 일을 하는 ‘매일 07:00 알림’ 규칙이 이미 있어요"))
        // PC D14: 내 규칙도 한 번에 100만 원이 넘으면 한 번 물어요.
        val money = daily.copy(act = LogicAct(LogicVerb.ADD_MONEY, "저축", 2_000_000))
        assertEquals("돌 때마다 가계부에 2,000,000원을 넣는 규칙이에요. 맞으면 한 번 더 저장을 눌러요", bigSay(money))
        assertEquals(null, bigSay(money.copy(act = money.act.copy(amount = 1_000_000))))
        assertEquals(null, bigSay(daily))
        assertTrue(ruleName(daily.copy(name = "열두글자를넘는아주긴규칙이름이에요"), setOf("열두글자를넘는아주긴규칙이름이에요")).length <= 20)
    }

    @Test fun messageShowsEvenWhenSaidTwice() = shoot("887_rul_message", FOLDED) {
        // PC D16 (낮): 안전 모드에서 단추를 세 번 눌러도 '안전 모드라 … 쉬어요'가 안 나왔어요 (받자마자 비우면 스낵바가 취소됐어요).
        val sb = androidx.compose.material3.SnackbarHostState()
        content {
            Surface { Column(Modifier.padding(16.dp)) {
                com.bamnat.app.logic.LogicMessageHost(app.container, sb)
                androidx.compose.material3.SnackbarHost(sb)
            } }
        }
        // The app's own screen (also a host) is gone before the line is said.
        settle()
        repeat(2) { n ->
            com.bamnat.app.logic.LogicRunner.say("단추가 쉬어요")
            repeat(5) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeBy(16) }
            assertEquals("${n + 1}번째에도 줄이 떠요", "단추가 쉬어요", sb.currentSnackbarData?.visuals?.message)
            assertEquals("받으면 비워요", null, com.bamnat.app.logic.LogicRunner.message.value)
            sb.currentSnackbarData?.dismiss()
            repeat(5) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.mainClock.advanceTimeBy(16) }
        }
    }

    @Test fun buttonConditionShowsRightBelow() = shoot("887_rul_button_condition", FOLDED) {
        // PC D17 (중): '조건이 맞는 날에만 보이기'를 켜도 조건 고르는 칸이 안 보여 조건 있는 단추를 못 만들었어요.
        content { com.bamnat.app.logic.ButtonDialog(MEMO, FormulaNames.SAMPLE, {}, {}) }
        assertTrue(appears("조건이 맞는 날에만 보이기"))
        tapText("조건이 맞는 날에만 보이기", scrolls = 0)
        assertTrue("easy view right away", appears("쉬운 보기"))
        assertTrue("one row to pick", appears("야간", exact = true))
        assertTrue("more rows", appears("+ 조건 더하기"))
        // PC D19 (낮): 칩을 고르기 전에는 '다음에 보이는 날' 대신 고르라고 해요.
        assertTrue("asks to pick first", appears("값을 골라 주세요", exact = false))
        tapText("야간", exact = true, scrolls = 0)
        assertTrue("picked", gone("값을 골라 주세요"))
    }

    @Test fun unpickedConditionWaits() {
        assertTrue(com.bamnat.app.logic.unpicked("근무종류 = \"\""))
        assertEquals(false, com.bamnat.app.logic.unpicked("근무종류 = \"야간\""))
        assertEquals(false, com.bamnat.app.logic.unpicked(""))
    }

    @Test fun settingsLineOpensTheStudio() = shoot("887_rul_settings_line", FOLDED) {
        // PC D19 (중): 설정 › 수식·규칙·버튼의 줄을 눌러도 아무 일이 없었어요.
        var opened = 0
        content {
            Surface { Column(Modifier.padding(16.dp)) {
                LogicEditor(Logic(listOf(MEMO), listOf(NIGHT)), {}, phone = true, readOnly = true, onOpen = { opened++ })
            } }
        }
        tapText("오늘 기록", scrolls = 0)
        settle()
        assertEquals(1, opened)
        tapText("야간 인수인계", scrolls = 0)
        settle()
        assertEquals(2, opened)
    }

    @Test fun hiddenButtonSaysWhenItShows() {
        // PC D18 (낮): 조건 때문에 숨은 단추는 왜 안 보이는지, 다음에 언제 보이는지 몰랐어요.
        val mon = java.time.LocalDate.of(2026, 11, 2)
        val facts = com.bamnat.core.formula.DayFactsBook(mon, "교대", { d ->
            if (d == mon.plusDays(3)) com.bamnat.core.formula.DayFactsBook.DayShift("야간", "night", "22:00", "07:00") else
                com.bamnat.core.formula.DayFactsBook.DayShift("주간", "day", "07:00", "15:00")
        })
        val night = MEMO.copy(show = "근무종류 = \"야간\"")
        assertEquals("오늘은 숨김(조건) · 다음 11/5(목)", com.bamnat.app.logic.showLine(night, facts, mon))
        assertEquals("오늘 보여요", com.bamnat.app.logic.showLine(night, facts, mon.plusDays(3)))
        assertEquals(null, com.bamnat.app.logic.showLine(MEMO, facts, mon))
        assertEquals("오늘은 숨김(조건) · 한 달 안에 보이는 날이 없어요", com.bamnat.app.logic.showLine(MEMO.copy(show = "근무종류 = \"당직\""), facts, mon))
    }

    @Test fun editorList() = shoot("886_rul_editor_list", FOLDED) {
        content {
            Surface { Column(Modifier.padding(16.dp)) { LogicEditor(Logic(listOf(MEMO), listOf(NIGHT, RESET)), {}, phone = true) } }
        }
        assertTrue("rule line", appears("야간 인수인계"))
        assertTrue("button line", appears("오늘 기록"))
        assertTrue("7-day count", appears("지난 7일"))
    }

    @Test fun ruleWindow() = shoot("886_rul_rule_window", FOLDED) {
        content { RuleDialog(NIGHT, FormulaNames.SAMPLE, {}, {}) }
        assertTrue("easy view", appears("쉬운 보기"))
        assertTrue("joined with 그리고", appears("그리고", exact = true))
        assertTrue("value chip", appears("야간", exact = true))
    }

    /** 누락 점검 N7 블록: 만약 안에 그리고, 빈칸 하나가 남은 모습. */
    @Test fun blocks() = shoot("887_rul_blocks", FOLDED) {
        content {
            Surface {
                Column(Modifier.padding(16.dp)) {
                    BlockEditor("만약(근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\"), \"빨강\", 지출 > 50000, \"주황\", \"없음\")", {}, FormulaNames.SAMPLE)
                }
            }
        }
        assertTrue("if block", appears("만약", exact = true))
        assertTrue("else words", appears("아니고 만약", exact = true))
        assertTrue("join block", appears("모두 맞으면 (그리고)"))
    }

    @Test fun formulaMistake() = shoot("886_rul_formula_mistake", FOLDED) {
        content {
            Surface {
                Column(Modifier.padding(16.dp)) {
                    FormulaField("근무종류 = \"야간\" 그리고", {}, FormulaNames.SAMPLE)
                    ConditionEditor(COND, {}, FormulaNames.SAMPLE)
                }
            }
        }
        assertTrue("names offered", appears("만약("))
        assertTrue("answer on the day", appears("이 날에는"))
    }
}
