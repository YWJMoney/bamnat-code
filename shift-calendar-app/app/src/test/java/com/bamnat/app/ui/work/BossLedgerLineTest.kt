package com.bamnat.app.ui.work

import com.bamnat.app.ui.TabPicks
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.layout.TabSlots
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 G2 통합 5차 PC: 식당 사장 폰에 '관리'가 아래 칸에 없어 급여 대장까지 멀었어요. */
class BossLedgerLineTest {
    private val bar = TabSlots.resolve("", TabOrder.DEFAULT, TabPicks.known)

    @Test fun `only a shop's calendar with the ledger on and no 관리 on the bar`() {
        assertTrue(BossLedgerLine.wanted(WorkStyle.BUSINESS, true, bar))
        assertFalse(BossLedgerLine.wanted(WorkStyle.SHIFT, true, bar))
        assertFalse(BossLedgerLine.wanted(WorkStyle.BUSINESS, false, bar))
        assertFalse(BossLedgerLine.wanted(WorkStyle.BUSINESS, true, BossLedgerLine.added(bar)))
    }

    @Test fun `a full bar keeps five places with 관리 last, a short one gains a place`() {
        val full = BossLedgerLine.added(bar)
        assertEquals(bar.size.coerceAtMost(TabSlots.MAX), full.size.coerceAtMost(TabSlots.MAX))
        assertTrue(TabSlots.shows(TabSlots.onBar(full, wide = false), TabPicks.MANAGE))
        val short = bar.take(3)
        val more = BossLedgerLine.added(short)
        assertEquals(4, more.size)
        assertEquals(short, more.take(3))
    }
}
