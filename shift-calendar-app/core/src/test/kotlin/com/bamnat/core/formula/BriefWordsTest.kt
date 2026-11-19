package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RVM-20] PC-61 (설정의 규칙 글이 기계 말투): a button's and a rule's line says what it does in a few words.
 * [RVM-12] PC-59 (작품의 프로 줄이 만든 사람 말투): what a work's ✦ parts do, in the words of the one who uses it.
 */
class BriefWordsTest {
    private val count = LogicAct(LogicVerb.VAL_ADD, "집중")
    private val notify = LogicAct(LogicVerb.NOTIFY, "쉬어요", after = 50)
    private val cafe = LogicAct(LogicVerb.ADD_MONEY, "카페", amount = 4_500)

    @Test fun anActIsAFewWords() {
        assertEquals("‘집중’ +1", count.brief())
        assertEquals("‘물’ −2", LogicAct(LogicVerb.VAL_ADD, "물", by = -2.0).brief())
        assertEquals("‘잔 수’ 0으로", LogicAct(LogicVerb.VAL_SET, "잔 수", by = 0.0).brief())
        assertEquals("50분 뒤 알림", notify.brief())
        assertEquals("알림(단추 2개)", LogicAct(LogicVerb.NOTIFY, "약", then = listOf("b1", "again10")).brief())
        assertEquals("가계부 ‘카페 4,500원’", cafe.brief())
        assertEquals("수입 ‘기타’", LogicAct(LogicVerb.ADD_INCOME).brief())
        assertEquals("1시간 30분 뒤 메모에 한 줄", LogicAct(LogicVerb.MEMO_LINE, "약 먹음", after = 90).brief())
        // A verb with no short words keeps its whole sentence.
        assertEquals(LogicAct(LogicVerb.NEW_MEMO).say(), LogicAct(LogicVerb.NEW_MEMO).brief())
    }

    @Test fun aButtonAndARuleSayTheirSteps() {
        assertEquals("누르면 ‘집중’ +1, 50분 뒤 알림", LogicButton("집중", act = count, more = listOf(notify)).brief())
        assertEquals("누르면 조건에 따라 가계부 ‘카페 4,500원’", LogicButton("카페", act = cafe, cond = "근무 = \"야간\"").brief())
        assertEquals("근무 중 2시간마다 · ‘집중’ +1",
            LogicRule("쉬기", RuleTrigger.EVERY, min = 120, inWork = true, act = count).brief())
        assertEquals("야간 근무 시작 3시간 전 · 조건이 맞으면 · 50분 뒤 알림",
            LogicRule("야간 전", RuleTrigger.START, min = -180, role = "night", cond = "근무 = \"야간\"", act = notify).brief())
    }

    @Test fun moreThanThreeStepsAreCounted() {
        val five = listOf(count, notify, cafe, LogicAct(LogicVerb.MEMO_LINE), LogicAct(LogicVerb.VAL_STAMP, "기상"))
        assertEquals("‘집중’ +1, 50분 뒤 알림, 가계부 ‘카페 4,500원’ 외 2가지", briefSteps(five))
        assertEquals("‘집중’ +1", briefSteps(listOf(count)))
    }

    @Test fun proPartsAreSaidInTheUsersWords() {
        // 값 칸·값 더하기·값 기록 are all one thing for the one who uses them.
        assertEquals("숫자 세기", FxPro.plain(listOf(FxPro.Part.VALUES, FxPro.Part.VALUE_ACT, FxPro.Part.HISTORY)))
        assertEquals("홈 화면 단추 · 알림 단추 · 자동 기록",
            FxPro.plain(listOf(FxPro.Part.TIME, FxPro.Part.NOTIFY_BUTTONS, FxPro.Part.OUTSIDE, FxPro.Part.CHAIN)))
        assertEquals("홈 화면 단추 · 알림 단추 · 자동 기록 등",
            FxPro.plain(listOf(FxPro.Part.OUTSIDE, FxPro.Part.NOTIFY_BUTTONS, FxPro.Part.CHAIN, FxPro.Part.VALUES)))
        assertEquals("홈 화면 단추 · 알림 단추 등", FxPro.plain(listOf(FxPro.Part.OUTSIDE, FxPro.Part.NOTIFY_BUTTONS, FxPro.Part.CHAIN), max = 2))
        assertEquals("", FxPro.plain(emptyList()))
        // Every part is told by one of the plain words.
        FxPro.Part.entries.forEach { p -> org.junit.Assert.assertTrue("${p.key} has no plain word", FxPro.Plain.of(listOf(p)).isNotEmpty()) }
    }
}
