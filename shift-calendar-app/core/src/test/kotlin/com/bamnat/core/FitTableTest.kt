package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.guide.GuideState
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FIT-01][FIT-02] 1.25 묶음 Y (U3): each kind of work sees its own parts and tools, and what the user adds stays. */
class FitTableTest {
    private val workOnly = listOf(
        FitSurface.CALENDAR_PARTS to "counts", FitSurface.CALENDAR_PARTS to "hours", FitSurface.CALENDAR_PARTS to "rest",
        FitSurface.CALENDAR_PARTS to "todayFlow",
        FitSurface.CALENDAR_TOOLS to "pattern", FitSurface.CALENDAR_TOOLS to "teams", FitSurface.CALENDAR_TOOLS to "typeMonth",
        FitSurface.CALENDAR_TOOLS to "receive",
    ) + FitTable.of(FitSurface.STATS_PARTS).map { it.surface to it.id }

    @Test fun investAndShopCalendarsShowNoShiftParts() {
        for (style in listOf(WorkStyle.INVEST, WorkStyle.BUSINESS)) for ((surface, id) in workOnly) {
            assertFalse("$style $surface $id", FitTable.visible(surface, id, style, emptySet()))
        }
    }

    @Test fun shiftCalendarShowsNoInvestOrShopParts() {
        val shift = WorkStyle.SHIFT
        assertFalse(FitTable.visible(FitSurface.CALENDAR_PARTS, "todayWork", shift, emptySet()))
        assertFalse(FitTable.visible(FitSurface.MONEY_PARTS, "work", shift, emptySet()))
        for ((surface, id) in workOnly) assertTrue("$surface $id", FitTable.visible(surface, id, shift, emptySet()))
        // And no 투자 feature fits it.
        assertEquals(emptyList<String>(), FeatureRegistry.of(shift).filter { it.id.startsWith("invest.") }.map { it.id })
    }

    /** The whole table, kind × screen, as people will see it with nothing added. */
    @Test fun everyKindOfWorkSeesItsOwnList() {
        val expected = mapOf(
            WorkStyle.SHIFT to "ask,journal,todayFlow,summary,counts,dayCard,hours,rest,memos",
            WorkStyle.DAY to "ask,journal,todayFlow,summary,counts,dayCard,hours,memos",
            WorkStyle.HOURLY to "ask,journal,todayFlow,summary,counts,dayCard,hours,memos",
            WorkStyle.FIELD to "ask,journal,todayWork,summary,dayCard,memos",
            WorkStyle.BUSINESS to "ask,journal,todayWork,summary,dayCard,memos",
            WorkStyle.INVEST to "ask,journal,todayWork,summary,dayCard,memos",
        )
        for ((style, parts) in expected) assertEquals("$style", parts, FitTable.shown(FitSurface.CALENDAR_PARTS, style).joinToString(","))
        val tools = mapOf(
            WorkStyle.SHIFT to "edit,teams,typeMonth,share,year,receive,pattern,newCalendar,customize,viewSettings,help",
            WorkStyle.DAY to "edit,share,year,receive,pattern,newCalendar,customize,viewSettings,help",
            WorkStyle.HOURLY to "edit,typeMonth,share,year,receive,newCalendar,customize,viewSettings,help",
            WorkStyle.INVEST to "edit,share,year,newCalendar,customize,viewSettings,help",
        )
        for ((style, list) in tools) assertEquals("$style", list, FitTable.shown(FitSurface.CALENDAR_TOOLS, style).joinToString(","))
        // A main calendar from before kinds of work were asked is a shift calendar.
        assertEquals(FitTable.shown(FitSurface.CALENDAR_TOOLS, WorkStyle.SHIFT), FitTable.shown(FitSurface.CALENDAR_TOOLS, null))
    }

    @Test fun anAddedPartShowsOnlyOnItsCalendar() {
        val key = FitTable.item(FitSurface.CALENDAR_TOOLS, "pattern")!!.key
        val state = GuideState(added = setOf(GuideState.offKey("inv", key)))
        assertTrue(FitTable.visible(FitSurface.CALENDAR_TOOLS, "pattern", WorkStyle.INVEST, state.addedTo("inv")))
        assertFalse(FitTable.visible(FitSurface.CALENDAR_TOOLS, "pattern", WorkStyle.INVEST, state.addedTo("other")))
        // 더 보기 keeps offering it, so it can be taken back away.
        assertTrue(FitTable.others(FitSurface.CALENDAR_TOOLS, WorkStyle.INVEST).any { it.id == "pattern" })
    }

    @Test fun unknownPartsShowAndFeatureLinksExist() {
        assertTrue(FitTable.visible(FitSurface.CALENDAR_PARTS, "a-part-from-a-later-version", WorkStyle.INVEST, emptySet()))
        FitTable.all.mapNotNull { it.feature }.forEach { assertNotNull(it, FeatureRegistry.get(it)) }
        assertEquals("keys are unique", FitTable.all.size, FitTable.all.map { it.key }.toSet().size)
    }
}
