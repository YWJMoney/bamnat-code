package com.bamnat.app.ui.studio

import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.calendar.CalendarPart
import com.bamnat.app.ui.calendar.calendarAbove
import com.bamnat.app.ui.calendar.calendarParts
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CNV-02][CNV-03][CNV-05][CNV-06] 1.37 캔버스 스튜디오: 고르기·이름표, 빠른 도구가 쓰는 줄, 카드 옮기기, 확대 단계. */
class StudioCanvasTest {
    @Test fun theTrailGoesFromTheOutsideInWithoutTheApp() {
        assertEquals(listOf("cal", "cal.cell", "cal.cell.date"), StudioEdit.trail("cal.cell.date"))
        assertEquals(listOf("memo.card", "memo.card.title"), StudioEdit.trail("memo.card.title"))
        assertEquals(listOf("app"), StudioEdit.trail("app"))
        assertEquals(listOf("card:calendar:todayFlow"), StudioEdit.trail("card:calendar:todayFlow"))
    }

    @Test fun wordsStartWithLettersAndBoxesWithTheirFill() {
        assertEquals(StudioEdit.Quick.INK, StudioEdit.tools("cal.cell.date").first())
        assertEquals(StudioEdit.Quick.FILL, StudioEdit.tools("memo.card").first())
        assertTrue(StudioEdit.Quick.GAP in StudioEdit.tools("cal.cell"))
        assertFalse(StudioEdit.canHide("app"))
        assertFalse(StudioEdit.canHide("cal"))
        assertTrue(StudioEdit.canHide("cal.cell.lunar"))
    }

    @Test fun aQuickPickWritesThePartsLineForItsStateAndCondition() {
        val one = StudioEdit.set("", "memo.card", null, null, "fill" to 0xFFFFE2CCL)!!
        assertEquals(0xFFFFE2CCL, StudioEdit.line(one, "memo.card", null, null)["fill"])
        // A condition line is its own line beside the plain one.
        val w = "고정"
        val two = StudioEdit.set(one, "memo.card", null, w, "lineC" to 0xFFE5484DL, "line" to "solid")!!
        assertEquals(0xFFFFE2CCL, StudioEdit.line(two, "memo.card", null, null)["fill"])
        assertEquals("solid", StudioEdit.line(two, "memo.card", null, w)["line"])
        assertEquals(listOf(w), StudioEdit.conditions(two, "memo.card", null))
        // A state line too, and taking every field off takes the line away.
        val three = StudioEdit.set(two, "cal.cell", "today", null, "r" to 12L)!!
        assertEquals(12L, StudioEdit.line(three, "cal.cell", "today", null)["r"])
        val back = StudioEdit.set(three, "cal.cell", "today", null, "r" to null)!!
        assertTrue(StudioEdit.line(back, "cal.cell", "today", null).isEmpty())
        assertEquals(Blueprint.decode(two).encode(), Blueprint.decode(back).encode())
        assertEquals("", StudioEdit.set(one, "memo.card", null, null, "fill" to null))
    }

    @Test fun aCardMovesAndHidesInItsPagesLine() {
        val ids = listOf("a", "b", "c", "d")
        val moved = StudioEdit.moveCard("", ids, setOf("a"), "c", -1)
        assertEquals("a,c,b,d", moved)
        // The fixed one stays where it is; a card the page does not show is stepped over.
        assertEquals(moved, StudioEdit.moveCard(moved, ids, setOf("a"), "c", -1))
        assertEquals("a,c,d,b", StudioEdit.moveCard("", ids, setOf("a"), "b", 1) { it != "c" })
        val hidden = StudioEdit.showCard("", ids, "b", false)
        assertFalse(StudioEdit.cardShown(hidden, ids, "b"))
        assertTrue(StudioEdit.cardShown(StudioEdit.showCard(hidden, ids, "b", true), ids, "b"))
    }

    @Test fun theSummaryCanGoUnderTheMonthAndBack() {
        val cards = pageCards(TabOrder.CALENDAR, WorkStyle.SHIFT)
        val d = AppSettings(workStyle = WorkStyle.SHIFT)
        // [EASE-01] 1.38: the month comes first; 이번 달 요약 starts under it and goes up and back down.
        assertEquals(false, cards.above(d, CalendarPart.SUMMARY.id))
        val over = cards.across!!(d, CalendarPart.SUMMARY.id)
        assertNotEquals(d, over)
        assertTrue(calendarAbove(over.calendarParts().map { it.part })(CalendarPart.SUMMARY))
        val back = cards.across!!(over, CalendarPart.SUMMARY.id)
        assertFalse(calendarAbove(back.calendarParts().map { it.part })(CalendarPart.SUMMARY))
        val under = over
        val up = back
        // PC 확인 1.37 A1: across and back leaves 하루 카드 (a card with its own switch) as the line had it.
        val dayShown = { x: AppSettings -> com.bamnat.app.ui.studio.StudioEdit.cardShown(x.calendarLayout, CalendarPart.entries.map { it.id }, CalendarPart.DAY_CARD.id) }
        assertTrue(dayShown(d))
        assertTrue(dayShown(under))
        assertTrue(dayShown(up))
        // PC 확인 1.37 A2: and back where it was is no change (the base's own line again).
        assertEquals(d, up.sameLinesAs(d))
        assertNotEquals(d, under.sameLinesAs(d))
        // Hidden and put back from ‘카드 넣기’.
        val gone = cards.show(d, CalendarPart.COUNTS.id, false)
        assertTrue(CalendarPart.COUNTS.id in cards.hidden(gone))
        assertFalse(CalendarPart.COUNTS.id in cards.hidden(cards.show(gone, CalendarPart.COUNTS.id, true)))
    }

    /** 1.38 통합 12차: 오늘의 흐름 is over the month in the month-first line (one line until opened); the summary stays under it. */
    @Test fun todaysFlowIsOverTheMonthByDefault() {
        val above = calendarAbove(AppSettings().calendarParts().map { it.part })
        assertTrue(above(CalendarPart.TODAY_FLOW))
        assertFalse(above(CalendarPart.SUMMARY))
    }

    @Test fun zoomStepsAreTheUsualOnes() {
        assertEquals(1.25f, StudioEdit.zoomStep(1f, 1))
        assertEquals(0.75f, StudioEdit.zoomStep(1f, -1))
        assertEquals(1f, StudioEdit.zoomStep(0.9f, 1))
        assertEquals(StudioEdit.ZOOM_MAX, StudioEdit.zoomStep(3f, 1))
        assertEquals(StudioEdit.ZOOM_MIN, StudioEdit.zoomStep(0.5f, -1))
    }

    @Test fun aCardIdSaysItsPage() {
        assertEquals(TabOrder.MONEY to "budget", StudioEdit.card(StudioEdit.cardId(TabOrder.MONEY, "budget")))
        assertEquals(null, StudioEdit.card("memo.card"))
    }

    @Test fun myMoneyFillsTheCardsThatHaveItsNumbers() {
        val d = java.time.LocalDate.of(2026, 11, 3)
        val m = MyMoney(3_000_000, 1_200_000, listOf(com.bamnat.core.money.MoneyEntry(date = d, kind = com.bamnat.core.money.MoneyKind.EXPENSE,
            amount = 8_000, category = "식비", merchant = "김밥집")))
        assertEquals("1,800,000원", myMoneyBody("hero", m)!!.rows.last().second)
        assertEquals("김밥집" to "-8,000원", myMoneyBody("recent", m)!!.rows.single())
        assertEquals(null, myMoneyBody("budget", m))
    }

    /** PC 확인 1.37 A12: 플랫폼형 배민 5건 62,000원 is 1일 · 5건 · the money after costs, not 0일 · 0건 · 0원; 공수형 keeps its 공수. */
    @org.junit.Test fun aPlatformDayCountsInTheFieldSummary() {
        val day = java.time.LocalDate.of(2026, 11, 3)
        val m = java.time.YearMonth.from(day)
        val p = com.bamnat.core.money.PlatformWork.job(day, "배민", 5, 62_000, 0, 2_040)
        val got = jobMonth(listOf(p), month = m)
        org.junit.Assert.assertEquals(1, got.days)
        org.junit.Assert.assertEquals(5, got.count)
        org.junit.Assert.assertNull(got.units)
        org.junit.Assert.assertEquals("받을 돈 as the calendar says it", p.unpaid, got.amount)
        org.junit.Assert.assertEquals("5건", jobCell(p))
        // PC 확인 1.37 A14: 받을 돈 is the calendar's (Job.unpaid: 62,000 less 3.3% = 59,960), whatever 수수료 is set.
        val cut = com.bamnat.core.money.FieldRules(platformCosts = mapOf("배민" to com.bamnat.core.money.PlatformWork.Cut(perCall = 408)))
        org.junit.Assert.assertEquals(p.unpaid, jobMonth(listOf(p), cut, m).amount)
        val d = com.bamnat.core.money.DayRate.job(day.plusDays(1), 1.5, 300_000, "")
        val both = jobMonth(listOf(p, d), month = m)
        org.junit.Assert.assertEquals(2, both.days)
        org.junit.Assert.assertEquals(1.5, both.units!!, 0.0)
    }
}
