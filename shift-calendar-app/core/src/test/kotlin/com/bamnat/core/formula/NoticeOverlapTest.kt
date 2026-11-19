package com.bamnat.core.formula

import com.bamnat.core.formula.NoticeOverlap.BuiltIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [NTC-01] 겹치는 알림: 장터 '오늘 요약 한 줄' and the lock-screen 오늘 근무 (사용자 2026-11-08 화면 사진). */
class NoticeOverlapTest {
    private fun notify(name: String, on: RuleTrigger, arg: String, at: String? = null, cond: String = "", min: Int = 0) =
        LogicRule(name, on, min = min, at = at, cond = cond, act = LogicAct(LogicVerb.NOTIFY, arg))

    private val summary = notify("오늘 요약", RuleTrigger.OPEN, "오늘 {근무} {근무시작}~{근무끝} · 이달 쓴 돈 {원(이달지출)}")
    private val dated = notify("앱 열면 오늘 요약", RuleTrigger.OPEN, "{월}월 {일}일 {요일}요일 · 메모 {메모수}개 · 이달 쓴 돈 {원(이달지출)}")
    private val restDay = notify("쉬는 날 알림", RuleTrigger.OPEN, "쉬는 날이에요. 이번 주 {이번주근무시간}시간 일했어요", cond = "쉬는날")
    private val ledger = notify("밤 9시 가계부 알림", RuleTrigger.DAILY, "오늘 쓴 돈을 아직 안 적었어요", at = "21:00", cond = "지출 = 0 그리고 아님 지난날")
    private val monthEnd = notify("월말 정리", RuleTrigger.DAILY, "이달 쓴 돈 {원(이달지출)}", at = "20:00", cond = "일 = 28")
    private val beforeShift = notify("시작 전 알림", RuleTrigger.START, "{근무} 한 시간 전이에요", min = -60)

    @Test fun theSummaryRepeatsTheLockScreenNotice() {
        assertEquals(BuiltIn.TODAY, NoticeOverlap.builtIn(summary))
        assertTrue(NoticeOverlap.foldsIntoToday(summary))
        // A reminder an hour before a shift says the shift too, but it is for that time, not today's list.
        assertNull(NoticeOverlap.builtIn(beforeShift))
        assertNull(NoticeOverlap.builtIn(restDay))
        assertNull(NoticeOverlap.builtIn(dated))
    }

    @Test fun anEveningLedgerReminderRepeatsTheDayClose() {
        assertEquals(BuiltIn.DAY_CLOSE, NoticeOverlap.builtIn(ledger))
        assertNull(NoticeOverlap.builtIn(monthEnd))
    }

    @Test fun putInAsksOnlyAboutWhatIsOn() {
        val got = Logic(rules = listOf(summary, restDay))
        val hits = NoticeOverlap.check(Logic(), got, setOf(BuiltIn.TODAY))
        assertEquals(listOf(NoticeOverlap.Hit(summary, builtIn = BuiltIn.TODAY)), hits)
        assertEquals("‘오늘 요약’은 켜 둔 ‘잠금 화면 오늘 근무’ 알림과 같은 말을 해요", hits.single().say())
        assertTrue(NoticeOverlap.check(Logic(), got, emptySet()).isEmpty())
        // Already mine: nothing new.
        assertTrue(NoticeOverlap.check(Logic(rules = listOf(summary)), Logic(rules = listOf(summary)), setOf(BuiltIn.TODAY)).isEmpty())
    }

    @Test fun twoSummariesOnOpeningAreTwins() {
        assertTrue(NoticeOverlap.twin(dated, summary))
        assertFalse(NoticeOverlap.twin(dated, restDay))
        val hits = NoticeOverlap.check(Logic(rules = listOf(dated)), Logic(rules = listOf(summary)), emptySet())
        assertEquals(dated, hits.single().other)
        // Switched off: not asked about.
        assertTrue(NoticeOverlap.check(Logic(rules = listOf(dated)), Logic(rules = listOf(summary)), emptySet()) { false }.isEmpty())
    }

    @Test fun whatIsOnNowIsListed() {
        val mine = Logic(rules = listOf(dated, summary, ledger))
        val hits = NoticeOverlap.now(mine, setOf(BuiltIn.TODAY), ruleOn = { true })
        assertEquals(listOf(summary to BuiltIn.TODAY), hits.filter { it.builtIn != null }.map { it.rule to it.builtIn })
        assertEquals(listOf(summary), NoticeOverlap.repeating(mine, BuiltIn.TODAY) { true })
        assertTrue(NoticeOverlap.repeating(mine, BuiltIn.TODAY) { it != summary }.isEmpty())
    }

    @Test fun theShiftPartGoesIntoTheLockScreenNotice() {
        assertEquals("이달 쓴 돈 {원(이달지출)}", NoticeOverlap.withoutShift(summary.act.arg))
        assertEquals("", NoticeOverlap.withoutShift("오늘 {근무}"))
    }

    @Test fun aDayOffLeavesNoLoneTilde() {
        assertEquals("오늘 휴무 · 이달 쓴 돈 527,513원", NoticeOverlap.tidy("오늘 휴무 ~ · 이달 쓴 돈 527,513원"))
        assertEquals("오늘 주간 07:00~19:00 · 이달 쓴 돈 0원", NoticeOverlap.tidy("오늘 주간 07:00~19:00 · 이달 쓴 돈 0원"))
        assertEquals("오늘 · 이달 쓴 돈", NoticeOverlap.tidy("오늘  ~ ·  · 이달 쓴 돈"))
        assertEquals("07:00 ~ 19:00 · 물 ~ 2잔", NoticeOverlap.tidy("07:00 ~ 19:00 · 물 ~ 2잔"))
    }

    /** [RVM-03] PC-37: a rule saying how long I slept goes onto the app's own '몇 시간 잤어요' notice, with only its other words. */
    @Test fun aSleptRuleKeepsOnlyItsOtherWords() {
        assertTrue(NoticeOverlap.saysSlept("{지난시간(\"잠\")}시간 잤어요. 오늘 {근무}예요"))
        assertTrue(NoticeOverlap.saysSlept("{지난시간(\"잠\")}시간밖에 못 잤어요. 오늘 {근무}, 무리하지 말아요"))
        assertFalse(NoticeOverlap.saysSlept("오늘 현장 들어가기 전 안전 점검을 해요"))
        assertEquals("오늘 주간이에요", NoticeOverlap.withoutSlept("36분 잤어요. 오늘 주간이에요"))
        assertEquals("오늘 야간, 무리하지 말아요", NoticeOverlap.withoutSlept("5시간 10분밖에 못 잤어요. 오늘 야간, 무리하지 말아요"))
        assertEquals("물 2잔", NoticeOverlap.withoutSlept("7시간 잤어요 · 물 2잔"))
        assertEquals("", NoticeOverlap.withoutSlept("7시간 잤어요"))
    }
}
