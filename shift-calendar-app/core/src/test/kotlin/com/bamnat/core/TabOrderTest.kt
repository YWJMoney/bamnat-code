package com.bamnat.core

import com.bamnat.core.layout.TabOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** [NAV-05][NAV-06] 1.26 AD: the tabs' order, never losing one (D82). */
class TabOrderTest {
    private val d = TabOrder.DEFAULT

    @Test fun nothingSavedIsTheUsualOrder() {
        assertEquals(listOf("calendar", "money", "memos", "stats", "settings"), TabOrder.of(emptyList()))
        assertEquals("calendar", TabOrder.home(emptyList()))
    }

    @Test fun unknownAndTwiceAreDroppedAndMissingTabsComeAtTheEnd() {
        assertEquals(listOf("memos", "settings", "calendar", "money", "stats"), TabOrder.of(listOf("memos", "ghost", "settings", "memos")))
        assertEquals("memos", TabOrder.home(listOf("memos")))
    }

    @Test fun theUsualOrderIsSavedAsNothing() {
        assertEquals(emptyList<String>(), TabOrder.toSave(d))
        assertEquals(listOf("memos", "calendar", "money", "stats", "settings"), TabOrder.toSave(listOf("memos")))
    }

    @Test fun movesAndSteps() {
        assertEquals(listOf("memos", "calendar", "money", "stats", "settings"), TabOrder.move(d, "memos", 0))
        assertEquals(listOf("money", "memos", "stats", "settings", "calendar"), TabOrder.move(d, "calendar", 99))
        assertEquals(listOf("calendar", "memos", "money", "stats", "settings"), TabOrder.step(d, "memos", -1))
        assertEquals(d, TabOrder.step(d, "calendar", -1))
        assertEquals(d, TabOrder.step(d, "settings", +1))
        assertEquals(d, TabOrder.move(d, "ghost", 0))
    }
}
