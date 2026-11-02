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
        assertTrue(ruleName(daily.copy(name = "열두글자를넘는아주긴규칙이름이에요"), setOf("열두글자를넘는아주긴규칙이름이에요")).length <= 20)
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
