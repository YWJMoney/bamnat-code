package com.bamnat.core

import com.bamnat.core.layout.TabOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** [NAV-05][NAV-06] 1.26 AD: the tabs' order, never losing one (D82). */
class TabOrderTest {
    private val d = TabOrder.DEFAULT

    @Test fun nothingSavedIsTheUsualOrder() {
        assertEquals(listOf("calendar", "money", "memos", "stats", "plaza", "settings"), TabOrder.of(emptyList()))
        assertEquals("calendar", TabOrder.home(emptyList()))
    }

    @Test fun unknownAndTwiceAreDroppedAndMissingTabsComeAtTheEnd() {
        assertEquals(listOf("memos", "plaza", "settings", "calendar", "money", "stats"), TabOrder.of(listOf("memos", "ghost", "settings", "memos")))
        assertEquals("memos", TabOrder.home(listOf("memos")))
    }

    @Test fun theUsualOrderIsSavedAsNothing() {
        assertEquals(emptyList<String>(), TabOrder.toSave(d))
        assertEquals(listOf("memos", "calendar", "money", "stats", "plaza", "settings"), TabOrder.toSave(listOf("memos")))
    }

    @Test fun movesAndSteps() {
        assertEquals(listOf("memos", "calendar", "money", "stats", "plaza", "settings"), TabOrder.move(d, "memos", 0))
        assertEquals(listOf("money", "memos", "stats", "plaza", "settings", "calendar"), TabOrder.move(d, "calendar", 99))
        assertEquals(listOf("calendar", "memos", "money", "stats", "plaza", "settings"), TabOrder.step(d, "memos", -1))
        assertEquals(d, TabOrder.step(d, "calendar", -1))
        assertEquals(d, TabOrder.step(d, "settings", +1))
        assertEquals(d, TabOrder.move(d, "ghost", 0))
    }

    /** 1.35 [RCX-29] An order saved before 광장 came gets it in front of 설정, wherever 설정 was put. */
    @Test fun plazaComesInFrontOfSettingsInAnOldOrder() {
        assertEquals(listOf("plaza", "settings", "calendar", "money", "memos", "stats"),
            TabOrder.of(listOf("settings", "calendar", "money", "memos", "stats")))
        assertEquals(listOf("stats", "calendar", "money", "memos", "plaza", "settings"), TabOrder.of(listOf("stats", "calendar", "money", "memos", "settings")))
    }
}
