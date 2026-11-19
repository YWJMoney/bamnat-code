package com.bamnat.app.ui

import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.settings.HelpFaq
import com.bamnat.app.ui.settings.forStyle
import com.bamnat.app.ui.work.MANAGE_NAME
import com.bamnat.app.ui.work.manageTitle
import com.bamnat.app.ui.work.manageWhat
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 갈래 H 10묶음: 유형에 맞지 않는 말과 경고 ([RVH-72] 검토 TYPE-03·T30-11·T30-90, [RVH-77] T30-104·TYPE-15,
 * [RVH-78] T30-105, [RVH-79] TYPE-19·T30-28).
 */
class TypeWordsH10Test {
    /** [RVH-72] 가게·현장·투자 달력에는 '연속 근무·연속 야간' 같은 근로자 휴식 경고가 뜨지 않아요. 끈 사람도 그대로 꺼져요. */
    @Test fun `rest warnings are for the kinds of work that have wages`() {
        assertTrue(AppSettings(workStyle = null).restSigns)
        for (s in listOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY)) assertTrue("$s", AppSettings(workStyle = s).restSigns)
        for (s in listOf(WorkStyle.BUSINESS, WorkStyle.FIELD, WorkStyle.INVEST)) assertFalse("$s", AppSettings(workStyle = s).restSigns)
        assertFalse(AppSettings(workStyle = WorkStyle.SHIFT, restCheck = false).restSigns)
    }

    /** [RVH-77] 도움말: 급여·연말정산 답은 급여가 있는 유형에만, '근무 종류 지우기'는 그 화면의 이름으로, 질문은 그 일의 말로. */
    @Test fun `help answers fit the kind of work and speak its words`() {
        val all = HelpFaq.topics()
        for (s in listOf(WorkStyle.BUSINESS, WorkStyle.FIELD, WorkStyle.INVEST)) {
            val ids = all.forStyle(s).map { it.id }
            for (wage in listOf("holidayPay", "yearEndInput", "payGap", "leaveTime", "memoShiftRemind")) assertFalse("$s $wage", wage in ids)
        }
        val day = all.forStyle(WorkStyle.DAY).map { it.id }
        assertTrue(day.containsAll(listOf("holidayPay", "yearEndInput", "leaveTime", "memoShiftRemind")))
        assertFalse("leaveTime" in all.forStyle(WorkStyle.SHIFT).map { it.id })
        assertFalse("deleteShiftType" in all.forStyle(WorkStyle.INVEST).map { it.id })

        val shop = all.forStyle(WorkStyle.BUSINESS).associateBy { it.id }
        val del = shop.getValue("deleteShiftType")
        assertEquals("만든 영업 종류를 지우고 싶어요", del.question)
        assertTrue(del.answer, del.answer.startsWith("설정 › 내 가게 › ‘영업시간’에서 영업 옆 휴지통을 눌러요. 그 영업이 달력·영업일·정기휴무에"))
        assertTrue(del.answer, "다른 영업으로" in del.answer)
        assertEquals("영업시간 열기", del.action?.first)
        assertEquals("하루만 영업을 바꾸고 싶어요", shop.getValue("oneday").question)
        assertEquals("홈 화면에 영업일을 띄우고 싶어요", shop.getValue("widget").question)

        val field = all.forStyle(WorkStyle.FIELD).associateBy { it.id }
        assertEquals("만든 일정 종류를 지우고 싶어요", field.getValue("deleteShiftType").question)
        assertTrue("다른 일정으로" in field.getValue("deleteShiftType").answer)
        assertEquals("워치 화면에 다음 일정까지 남은 시간을 띄우고 싶어요", field.getValue("watchNext").question)
        assertEquals("다른 캘린더 앱에 적어 둔 일정을 가져오고 싶어요", field.getValue("import").question)
        // 교대 근무자는 그대로.
        val shift = all.forStyle(WorkStyle.SHIFT).associateBy { it.id }
        assertEquals("만든 근무 종류를 지우고 싶어요", shift.getValue("deleteShiftType").question)
        assertEquals("하루만 근무를 바꾸고 싶어요", shift.getValue("oneday").question)
        assertEquals(all.first { it.id == "deleteShiftType" }, shift.getValue("deleteShiftType"))
        // 일근은 '출근 시간' 화면.
        val office = all.forStyle(WorkStyle.DAY).first { it.id == "deleteShiftType" }
        assertTrue(office.answer, office.answer.startsWith("설정 › 내 근무 › ‘출근 시간’에서 근무 옆 휴지통을 눌러요. 그 근무가 달력·일하는 요일에"))
    }

    /** [RVH-78] 관리 화면 이름이 맡은 것대로: 팀만이면 '팀 관리', 사업장만이면 '사업장 관리'. '가게'는 없어요. */
    @Test fun `the manage screen is named for what this phone leads`() {
        assertEquals("팀 관리", manageTitle(leader = true, boss = false))
        assertEquals("사업장 관리", manageTitle(leader = false, boss = true))
        assertEquals("팀·사업장 관리", manageTitle(leader = true, boss = true))
        assertEquals(MANAGE_NAME, manageTitle(leader = false, boss = false))
        assertEquals("팀과 사업장", manageWhat(true, true, "과"))
        assertEquals("팀이나 사업장", manageWhat(true, true, "이나"))
        assertEquals("사업장", manageWhat(false, true, "과"))
        assertFalse("가게" in MANAGE_NAME)
    }

    /** [RVH-79] 아래 메뉴 고르기: 일근은 '일하는 요일', 알바는 '출근 요일'(그 화면의 이름), 가게는 '영업일', 교대는 '근무 주기'. */
    @Test fun `the bar picker names the repeating screen as it is called`() {
        assertEquals("일하는 요일", TabPicks.of("f:PATTERN", WorkStyle.DAY)?.label)
        assertEquals("출근 요일", TabPicks.of("f:PATTERN", WorkStyle.HOURLY)?.label)
        assertEquals("영업일", TabPicks.of("f:PATTERN", WorkStyle.BUSINESS)?.label)
        assertEquals("근무 주기", TabPicks.of("f:PATTERN", WorkStyle.SHIFT)?.label)
        assertEquals("근무 주기", TabPicks.of("f:PATTERN", null)?.label)
        val office = TabPicks.all(WorkStyle.DAY, manageOn = false).map { it.label }
        assertFalse(office.toString(), "근무 주기" in office)
    }
}
