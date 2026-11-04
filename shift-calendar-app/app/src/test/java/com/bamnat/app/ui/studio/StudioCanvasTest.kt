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
        assertEquals(true, cards.above(d, CalendarPart.SUMMARY.id))
        val under = cards.across!!(d, CalendarPart.SUMMARY.id)
        assertNotEquals(d, under)
        assertFalse(calendarAbove(under.calendarParts().map { it.part })(CalendarPart.SUMMARY))
        val up = cards.across!!(under, CalendarPart.SUMMARY.id)
        assertTrue(calendarAbove(up.calendarParts().map { it.part })(CalendarPart.SUMMARY))
        // PC 확인 1.37 A1: down and back up leaves 하루 카드 (a card with its own switch) as the line had it.
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
}
