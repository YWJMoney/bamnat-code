package com.bamnat.app.ui

import com.bamnat.core.layout.TabOrder
import com.bamnat.core.layout.TabSlots
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NAV2-01] 아래 칸에 둘 수 있는 화면 목록. */
class TabPicksTest {
    @Test fun everyScreenThePickerOffersIsKnownAndOpensByItsKey() {
        val all = TabPicks.all(WorkStyle.SHIFT, manageOn = true)
        assertEquals(all.map { it.key }.distinct(), all.map { it.key })
        assertEquals(all.map { it.label }.distinct(), all.map { it.label })
        for (p in all) {
            assertTrue(p.key, TabPicks.known(p.key))
            assertNotNull(p.key, TabPicks.of(p.key, WorkStyle.SHIFT))
            // A tab by its route, another screen by its feature place, or the app's own.
            assertTrue(p.key, TabPicks.topOf(p.key) != null || TabPicks.placeOf(p.key) != null || p.key.startsWith(TabSlots.EXTRA))
        }
        for (t in TabOrder.DEFAULT) assertTrue(t, all.any { it.key == t })
    }

    @Test fun theManageScreenIsOfferedOnlyWhileALeaderOrBossFlagIsOn() {
        assertFalse(TabPicks.all(WorkStyle.SHIFT, manageOn = false).any { it.key == TabPicks.MANAGE })
        assertTrue(TabPicks.all(WorkStyle.SHIFT, manageOn = true).any { it.key == TabPicks.MANAGE })
        // A bar that holds it keeps it (the flag can come back).
        assertTrue(TabPicks.known(TabPicks.MANAGE))
    }

    @Test fun screensOfOtherWorkWaitAfterButAreNeverHidden() {
        val shop = TabPicks.all(WorkStyle.BUSINESS, manageOn = false)
        val pay = shop.first { it.key == "f:PAY" }
        assertFalse("급여 does not fit a shop", pay.fits)
        assertTrue(shop.first { it.key == "f:SLEEP" }.fits)
        // No kind of work set yet: everything fits.
        assertTrue(TabPicks.all(null, manageOn = false).all { it.fits })
        // 1.39 PC NAV2: a shop or an investor reads 돈과 일정, a shop's 근무 주기 is its 영업일.
        assertEquals("돈과 일정", TabPicks.kindName(TabPicks.KIND_WORK, WorkStyle.BUSINESS))
        assertEquals("근무와 돈", TabPicks.kindName(TabPicks.KIND_WORK, WorkStyle.SHIFT))
        assertEquals("영업일", TabPicks.of("f:PATTERN", WorkStyle.BUSINESS)?.label)
        assertEquals("와", com.bamnat.core.text.Josa.gwawa("가계부"))
        assertEquals("와", com.bamnat.core.text.Josa.gwawa("연차"))
        assertEquals("과", com.bamnat.core.text.Josa.gwawa("받을 돈"))
        // The money tab is named for the work, as in the bar.
        assertEquals(TopLevel.MONEY.labelFor(WorkStyle.BUSINESS), TabPicks.of("money", WorkStyle.BUSINESS)?.label)
    }

    @Test fun aKeyThisVersionDoesNotKnowIsDropped() {
        assertFalse(TabPicks.known("f:NOPE"))
        assertFalse(TabPicks.known("x:nope"))
        assertEquals(null, TabPicks.of("f:NOPE", null))
        val bar = TabSlots.decode("calendar;f:NOPE;g|모음|f:SLEEP+x:nope", TabPicks.known)
        assertEquals(listOf(TabSlots.One("calendar"), TabSlots.One("f:SLEEP")), bar)
    }
}
