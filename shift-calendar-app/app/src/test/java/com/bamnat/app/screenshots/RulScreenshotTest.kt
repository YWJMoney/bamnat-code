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
