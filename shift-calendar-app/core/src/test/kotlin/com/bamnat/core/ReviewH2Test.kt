package com.bamnat.core

import com.bamnat.core.guide.Moment
import com.bamnat.core.guide.WhatsNew
import com.bamnat.core.leave.LeaveSources
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.profile.AskScheduler
import com.bamnat.core.profile.FactKey
import com.bamnat.core.profile.FeatureNeeds
import com.bamnat.core.schedule.QuickText
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 2.0.0 검토 갈래 H 2묶음: 알바 연차(WORK-02)와 '쉬는 날 일하면 받는 방식'(WORK-20). */
class ReviewH2Test {
    private val today = LocalDate.of(2026, 11, 12)
    private val four = ShiftType(1, "오픈", "오", 0xFF27AE60, LocalTime.of(10, 0), LocalTime.of(14, 0))
    private val hourly = PayProfile(Occupation.CUSTOM, WageType.HOURLY, ValueTimeline(), hireDate = today.minusYears(2))

    /** [workDays] days of four hours in every seven, for the last three months. */
    private fun resolver(workDays: Int) =
        ScheduleResolver(listOf(four), WorkPattern.Rotation(today.minusDays(90), steps = List(7) { if (it < workDays) 1L else null }), emptyList())

    /** [RVH-38] 검토 WORK-02 Under 15 hours a week an hourly job makes no 연차; 15 and more counts days of weekly ÷ 5 hours. */
    @Test fun aPartTimerUnderFifteenHoursAWeekGetsNoLeave() {
        val few = LeaveSources.partTimeHours(hourly, today, resolver(2)::resolve)!!
        assertEquals(8.0, few, 0.01)
        assertTrue(LeaveSources.noAuto(hourly, few)!!.contains("주 8시간"))
        assertTrue(LeaveSources.autoLots(hourly, today, few).isEmpty())
        assertNull(LeaveSources.partTimeNote(few))

        val more = LeaveSources.partTimeHours(hourly, today, resolver(5)::resolve)!!
        assertEquals(20.0, more, 0.01)
        assertNull(LeaveSources.noAuto(hourly, more))
        assertTrue(LeaveSources.autoLots(hourly, today, more).isNotEmpty())
        assertEquals("주 20시간이라 연차 하루는 4시간으로 쳐요(주 시간 ÷ 5) · 참고용", LeaveSources.partTimeNote(more))

        // A monthly job is not judged by its weeks, and a part-timer with nothing on the calendar yet is not told there is no leave.
        assertNull(LeaveSources.partTimeHours(hourly.copy(wageType = WageType.MONTHLY), today, resolver(1)::resolve))
        assertNull(LeaveSources.noAuto(hourly, 0.0))
    }

    /**
     * [RVH-40] 검토 WORK-14 검토 TYPE-06·T30-103 새로 생긴 것은 그 유형 줄만 먼저(다섯 줄까지), 나머지는 접어요. 1.38부터의 줄은 그 유형에서 보일 때 그
     * 유형 말이에요(낱말 검사).
     */
    @Test fun whatsNewShowsEachKindItsOwnLinesInItsOwnWords() {
        val since = WhatsNew.since(51, 53)
        for (style in WorkStyle.entries) {
            val split = WhatsNew.forStyle(since, style)
            val mine = split.mine.flatMap { it.lines } + split.moreMine
            assertTrue("$style", split.mine.sumOf { it.lines.size } <= WhatsNew.MINE)
            assertEquals("$style: every line once", since.sumOf { it.lines.size }, mine.size + split.others.size)
            mine.forEach { line -> line.said(style).let { assertEquals("$style: $it", emptyList<String>(), TypeWords.leaks(style, it)) } }
        }
        val shop = WhatsNew.forStyle(since, WorkStyle.BUSINESS)
        assertTrue(shop.others.any { "명세서" in it.text })
        assertTrue((shop.mine.flatMap { it.lines } + shop.moreMine).none { "급여" in it.text || "팀 리더" in it.text })
        assertEquals("가게 하시는 분께 새로 생긴 것", WhatsNew.title(WorkStyle.BUSINESS))
    }

    /** [RVH-39] 검토 WORK-20 The value is how a day off worked is paid: its name says so, and a part-timer is not asked it. */
    @Test fun theDayOffPayWayIsNotAskedOfAPartTimer() {
        assertEquals("쉬는 날 일하면 받는 방식", FactKey.HOLIDAY_PAY.label)
        assertFalse(FactKey.HOLIDAY_PAY.fits(WorkStyle.HOURLY))
        assertTrue(FactKey.HOLIDAY_PAY.fits(WorkStyle.DAY))
        assertNull(AskScheduler.MOMENTS[Moment.HOURLY_15H])
        assertTrue(FeatureNeeds.all["hourly.weeks"].orEmpty().isEmpty())
    }

    /** [RVH-41] 검토 TYPE-14 A field worker's or a shop's 휴가 is a day off on the calendar, never a 연차 (they have none). */
    @Test fun aShopsHolidayIsADayOffNotLeave() {
        val closed = ShiftType(9, "휴무", "휴", 0xFF888888, null, null, isOff = true)
        val shop = QuickText.parse("다음주 월화 휴가", today, today, listOf(four, closed), ExtraKinds(), leave = false)!!
        assertEquals(QuickText.Act.Off, shop.act)
        assertEquals(2, shop.dates.size)
        assertEquals(QuickText.Act.Shift(9), QuickText.parse("내일 휴무 연차", today, today, listOf(four, closed), ExtraKinds(), leave = false)!!.act)
        assertEquals(QuickText.Act.Leave(4), QuickText.parse("다음주 월화 휴가", today, today, listOf(four, closed), ExtraKinds())!!.act)
    }

    /** [RVH-41] A way into settings reads the page by the name it has for that kind ('내 근무' is a shop's '내 가게'). */
    @Test fun aWayIntoSettingsUsesThePagesOwnName() {
        val again = FeatureRegistry.get("setup.again")!!
        assertEquals("설정 › 내 가게 › 처음 설정 다시 받기", FeatureRegistry.whereFor(again, WorkStyle.BUSINESS))
        assertEquals("설정 › 내 근무 › 처음 설정 다시 받기", FeatureRegistry.whereFor(again, WorkStyle.SHIFT))
        val rate = FeatureRegistry.get("pay.rateHint")!!
        assertEquals("설정 › 일감과 받을 돈 › 광장 시세와 내 단가 견주기", FeatureRegistry.said(rate, WorkStyle.FIELD).where)
        // A staff schedule keeps its name in a shop's words.
        assertEquals("매출·영업 › 직원 인건비 › 근무표 짜기", FeatureRegistry.said(FeatureRegistry.get("shop.staff")!!, WorkStyle.BUSINESS).where)
    }

    /**
     * [RVH-41] 검토 T30-76·85·99·100: a settings row written for 교대, read in each kind's words. A phrase right for the kind ('직원 근무표')
     * stays, a text already right is left alone, and 교대 reads it as written.
     */
    @Test fun aSettingsRowIsSaidInTheKindsWords() {
        val row = "퇴근 후 잠들기까지"
        assertEquals(row, TypeWords.say(WorkStyle.SHIFT, row))
        assertEquals("일 끝난 뒤 잠들기까지", TypeWords.say(WorkStyle.FIELD, row))
        assertEquals("마감 후 잠들기까지", TypeWords.say(WorkStyle.BUSINESS, row))
        assertEquals("영업일마다 다른 소리", TypeWords.say(WorkStyle.BUSINESS, "근무마다 다른 소리"))
        assertEquals("날마다 다른 소리", TypeWords.say(WorkStyle.INVEST, "근무마다 다른 소리"))
        assertEquals("대타", TypeWords.say(WorkStyle.HOURLY, "대근"))
        assertEquals("직원 근무표와 휴무", TypeWords.say(WorkStyle.BUSINESS, "직원 근무표와 쉬는 날"))
        for (s in WorkStyle.entries) for (t in listOf(row, "근무마다 다른 소리", "근무표 받기", "쉬는 날 알람")) {
            assertTrue("$s: ${TypeWords.say(s, t)}", TypeWords.leaks(s, TypeWords.say(s, t)).isEmpty())
        }
    }

    /** [RVH-41] 검토 T30-75·99: the ringing alarm in each kind's words; 교대·일근·알바 as before. */
    @Test fun theRingingAlarmIsNamedInTheKindsWords() {
        val a = com.bamnat.core.alarm.AlarmWords
        assertEquals("오후 근무 알람", a.alarm("오후", WorkStyle.SHIFT))
        assertEquals("오후 알람", a.alarm("오후", WorkStyle.FIELD))
        assertEquals("영업 알람", a.alarm("영업", WorkStyle.BUSINESS))
        assertEquals("장 시작 알람", a.alarm("장 시작", WorkStyle.INVEST))
        assertEquals("오후 근무", a.work("오후", null))
        assertEquals("오후", a.work("오후", WorkStyle.FIELD))
        assertEquals("근무", a.noun("오후", WorkStyle.DAY))
        assertEquals("일정", a.noun("오후", WorkStyle.FIELD))
        assertEquals("영업", a.noun("오전", WorkStyle.BUSINESS))
        assertEquals("일정", a.noun("장 시작", WorkStyle.INVEST))
    }
}
